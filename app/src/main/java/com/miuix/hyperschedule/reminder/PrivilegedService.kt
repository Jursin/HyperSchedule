package com.miuix.hyperschedule.reminder

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import rikka.shizuku.Shizuku

/** 以 shell 身份运行的服务：绑定管理与服务实现都在这里 */
internal object PrivilegedService {

    private const val TAG = "CourseReminder"

    /**
     * 首次绑定要等 Shevery 起进程：`app_process` 冷启动约 1 秒，
     * 失败时服务端还会退避重试三次，留够余量。
     */
    private const val BIND_TIMEOUT_SECONDS = 8L

    @Volatile
    private var service: IPrivilegedService? = null

    @Volatile
    private var latch = CountDownLatch(1)

    private val connection =
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                // 回调走主线程，这里不能碰日志以外的东西，否则等于让调用方多等一轮
                service = binder?.takeIf { it.pingBinder() }?.let { IPrivilegedService.Stub.asInterface(it) }
                if (service != null) latch.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                service = null
                latch = CountDownLatch(1)
            }
        }

    /** 已绑过直接复用，否则现绑一次；拿不到就返回 null，由调用方退回应用内直连 */
    @Synchronized
    fun get(context: Context): IPrivilegedService? {
        service?.takeIf { it.asBinder().pingBinder() }?.let { return it }
        service = null
        if (!ShizukuAccess.hasPermission) {
            Log.w(TAG, "Shizuku 没在运行或本应用没拿到授权")
            return null
        }
        val args = userServiceArgs(context)
        return runCatching {
            latch = CountDownLatch(1)
            Shizuku.bindUserService(args, connection)
            if (latch.await(BIND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                service
            } else {
                // 进程没起来就撤掉记录，否则同版本号的后续绑定会一直去复用它
                runCatching { Shizuku.unbindUserService(args, connection, true) }
                Log.w(TAG, "绑定超时（${BIND_TIMEOUT_SECONDS}s），Shevery 没把服务进程拉起来")
                null
            }
        }.getOrElse {
            Log.w(TAG, "绑定 Shizuku 服务失败", it)
            null
        }
    }

    /**
     * `debuggable` 固定 false：置为 true 时 Shevery 会给 `app_process` 加一串 JDWP 调试参数，
     * 没接调试器时纯属多余。
     */
    private fun userServiceArgs(context: Context): Shizuku.UserServiceArgs =
        Shizuku.UserServiceArgs(
            ComponentName(context.packageName, PrivilegedServiceImpl::class.java.name),
        )
            .daemon(false)
            .processNameSuffix("privileged")
            .debuggable(false)
            .version(1)
}

/**
 * `IConnectivityManager$Stub` 的类名。`\u0024` 就是 `$`，写成转义免得被当成字符串插值前缀。
 * 隐藏 API 只能按名字反射拿，两个文件共用这一份。
 */
internal const val CONNECTIVITY_STUB_CLASS = "android.net.IConnectivityManager\u0024Stub"

/** 由 Shizuku 以 shell 身份拉起的服务实现 */
class PrivilegedServiceImpl : IPrivilegedService.Stub() {

    /** shell 侧不受隐藏 API 限制，防火墙只能靠反射调 */
    @SuppressLint("PrivateApi")
    override fun setPackageNetworkingEnabled(uid: Int, enabled: Boolean): Boolean =
        runCatching {
            val binder =
                Class.forName("android.os.ServiceManager")
                    .getMethod("getService", String::class.java)
                    .invoke(null, "connectivity") as? IBinder ?: return@runCatching false
            val manager =
                Class.forName(CONNECTIVITY_STUB_CLASS)
                    .getMethod("asInterface", IBinder::class.java)
                    .invoke(null, binder) ?: return@runCatching false
            manager.javaClass
                .getMethod("setFirewallChainEnabled", Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
                .invoke(manager, FIREWALL_CHAIN_OEM_DENY, true)
            manager.javaClass
                .getMethod(
                    "setUidFirewallRule",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                )
                .invoke(manager, FIREWALL_CHAIN_OEM_DENY, uid, if (enabled) RULE_ALLOW else RULE_DENY)
            true
        }.getOrElse {
            Log.w(TAG, "切换 uid=$uid 网络失败", it)
            false
        }

    private companion object {
        const val TAG = "CourseReminder"

        /** 防火墙链编号与规则值取自 ConnectivityManager 的内部常量 */
        const val FIREWALL_CHAIN_OEM_DENY = 9
        const val RULE_ALLOW = 0
        const val RULE_DENY = 2
    }
}
