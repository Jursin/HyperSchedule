package com.hyperschedule.app.reminder

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import rikka.shizuku.Shizuku

/** 以 shell 身份运行的服务 */
internal object PrivilegedService {

    private const val TAG = "CourseReminder"

    private const val BIND_TIMEOUT_SECONDS = 8L

    @Volatile
    private var service: IPrivilegedService? = null

    @Volatile
    private var latch = CountDownLatch(1)

    private val connection =
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                service = binder?.takeIf { it.pingBinder() }?.let { IPrivilegedService.Stub.asInterface(it) }
                if (service != null) latch.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                service = null
                latch = CountDownLatch(1)
            }
        }

    @Synchronized
    fun get(context: Context): IPrivilegedService? {
        service?.takeIf { it.asBinder().pingBinder() }?.let { return it }
        service = null
        ShizukuAccess.init(context)
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
                runCatching { Shizuku.unbindUserService(args, connection, true) }
                Log.w(TAG, "绑定超时（${BIND_TIMEOUT_SECONDS}s），Shevery 没把服务进程拉起来")
                null
            }
        }.getOrElse {
            Log.w(TAG, "绑定 Shizuku 服务失败", it)
            null
        }
    }

    /** `debuggable` 固定 false */
    private fun userServiceArgs(context: Context): Shizuku.UserServiceArgs =
        Shizuku.UserServiceArgs(
            ComponentName(context.packageName, PrivilegedServiceImpl::class.java.name),
        )
            .daemon(false)
            .processNameSuffix("privileged")
            .debuggable(false)
}

/** 隐藏 API 只能按名字反射拿，$$ 让 $ 不被当成插值前缀 */
internal const val CONNECTIVITY_STUB_CLASS = $$"android.net.IConnectivityManager$Stub"

/** 由 Shizuku 以 shell 身份拉起的服务实现 */
class PrivilegedServiceImpl : IPrivilegedService.Stub() {

    /** 收下 APK 后交给 pm 安装，返回 pm 的输出，含 Success 即成功 */
    override fun installApk(source: ParcelFileDescriptor): String {
        val temp = File("/data/local/tmp/hyperschedule-update.apk")
        return runCatching {
                ParcelFileDescriptor.AutoCloseInputStream(source).use { input ->
                    temp.outputStream().use { output -> input.copyTo(output) }
                }
                val process =
                    ProcessBuilder("/system/bin/pm", "install", "-r", temp.absolutePath)
                        .redirectErrorStream(true)
                        .start()
                process.inputStream.bufferedReader().readText().also { process.waitFor() }
            }
            .getOrElse { it.message ?: "安装失败" }
            .also { temp.delete() }
    }

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
