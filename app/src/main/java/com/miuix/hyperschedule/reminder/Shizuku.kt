package com.miuix.hyperschedule.reminder

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import androidx.core.net.toUri

/** Shizuku 系服务的接入状态与授权 */
internal object ShizukuAccess {

    /** 优先 Shevery；装了原版 Shizuku 也认 */
    private val MANAGER_PACKAGES = listOf("com.hamondev.shevery", "moe.shizuku.privileged.api")

    private const val REQUEST_CODE = 0x5A1

    /** 服务 binder 是否已经送到本进程 */
    val isRunning: Boolean
        get() = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    val hasPermission: Boolean
        get() =
            isRunning &&
                runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }
                    .getOrDefault(false)

    /** 已安装的管理器包名：服务没起时靠它把用户领过去 */
    private fun managerPackage(context: Context): String? =
        MANAGER_PACKAGES.firstOrNull {
            runCatching { context.packageManager.getPackageInfo(it, 0) }.isSuccess
        }

    fun openManager(context: Context) {
        val packageName = managerPackage(context) ?: return
        val launch =
            context.packageManager.getLaunchIntentForPackage(packageName)
                ?: Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    "package:$packageName".toUri())
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(launch) }
    }

    /** 请求授权；结果通过 [onResult] 异步回来 */
    fun requestPermission(onResult: (Boolean) -> Unit) {
        if (!isRunning) return onResult(false)
        if (hasPermission) return onResult(true)
        val first = synchronized(pending) { pending.add(onResult); pending.size == 1 }
        if (!first) return
        Shizuku.addRequestPermissionResultListener(resultListener)
        runCatching { Shizuku.requestPermission(REQUEST_CODE) }
            .onFailure {
                Shizuku.removeRequestPermissionResultListener(resultListener)
                deliver(false)
            }
    }

    /** 先挂上「binder 到达」监听，等它到了再弹授权框 */
    fun requestPermissionWhenReady(onResult: (Boolean) -> Unit) {
        if (isRunning) return requestPermission(onResult)
        val listener =
            object : Shizuku.OnBinderReceivedListener {
                override fun onBinderReceived() {
                    Shizuku.removeBinderReceivedListener(this)
                    requestPermission(onResult)
                }
            }
        Shizuku.addBinderReceivedListener(listener)
        // 注册前 binder 可能刚好送进来，补一次判断
        if (isRunning) {
            Shizuku.removeBinderReceivedListener(listener)
            requestPermission(onResult)
        }
    }

    private val pending = mutableListOf<(Boolean) -> Unit>()

    private val resultListener =
        object : Shizuku.OnRequestPermissionResultListener {
            override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                if (requestCode != REQUEST_CODE) return
                Shizuku.removeRequestPermissionResultListener(this)
                deliver(grantResult == PackageManager.PERMISSION_GRANTED)
            }
        }

    private fun deliver(granted: Boolean) {
        val callbacks = synchronized(pending) { pending.toList().also { pending.clear() } }
        callbacks.forEach { it(granted) }
    }
}

/** 发岛绕行：临时切断「小米服务框架」的网络，让系统侧校验连不上网 */
internal object ShizukuBypass {

    private const val TAG = "CourseReminder"
    private const val XMSF_PACKAGE = "com.xiaomi.xmsf"

    /** 防火墙链编号与规则值取自 ConnectivityManager 的内部常量 */
    private const val CHAIN_OEM_DENY = 9
    private const val RULE_ALLOW = 0
    private const val RULE_DENY = 2
    private const val RESTORE_DELAY_MILLIS = 100L

    /**
     * 断网 → [block] → 恢复，返回是否真的断过网。没授权、断网失败都直接跑 [block]，
     * 恢复放在 finally 里，保证网络不会一直是被切断的状态。
     *
     * 加锁串行执行：课前提醒和上课闹钟可能同时触发，两次断网交错会把防火墙状态弄乱。
     */
    @Synchronized
    fun withBypass(context: Context, block: () -> Unit): Boolean {
        if (!ShizukuAccess.hasPermission || !setXmsfNetwork(context, enabled = false)) {
            block()
            return false
        }
        return try {
            block()
            // 给系统一点时间读完通知再恢复，太早恢复等于没断
            Thread.sleep(RESTORE_DELAY_MILLIS)
            true
        } finally {
            setXmsfNetwork(context, enabled = true)
        }
    }

    /**
     * 先借 shell 身份的 [PrivilegedService] 去调：应用进程里这两个方法是隐藏 API，
     * 高版本上反射会直接抛 `NoSuchMethodException`；服务不可用时退回应用内直连。
     */
    private fun setXmsfNetwork(context: Context, enabled: Boolean): Boolean {
        val uid =
            runCatching { context.packageManager.getPackageUid(XMSF_PACKAGE, 0) }
                .onFailure { Log.w(TAG, "取不到 $XMSF_PACKAGE 的 uid", it) }
                .getOrNull() ?: return false
        val service = PrivilegedService.get(context)
        if (service != null) {
            val viaService = runCatching { service.setPackageNetworkingEnabled(uid, enabled) }
            if (viaService.getOrNull() == true) return true
            Log.w(TAG, "服务内切换 uid=$uid 网络失败", viaService.exceptionOrNull())
        }
        return setXmsfNetworkInProcess(uid, enabled)
    }

    /** 应用内直连：用 Shizuku 的 binder 包装换成 shell 身份再发出去 */
    @SuppressLint("PrivateApi")
    private fun setXmsfNetworkInProcess(uid: Int, enabled: Boolean): Boolean =
        runCatching {
            val service = SystemServiceHelper.getSystemService("connectivity") ?: return@runCatching false
            val manager =
                Class.forName(CONNECTIVITY_STUB_CLASS)
                    .getMethod("asInterface", IBinder::class.java)
                    .invoke(null, ShizukuBinderWrapper(service))
            manager.javaClass
                .getMethod("setFirewallChainEnabled", Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
                .invoke(manager, CHAIN_OEM_DENY, true)
            manager.javaClass
                .getMethod(
                    "setUidFirewallRule",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                )
                .invoke(manager, CHAIN_OEM_DENY, uid, if (enabled) RULE_ALLOW else RULE_DENY)
            true
        }.getOrElse {
            Log.w(TAG, "切换 uid=$uid 网络失败", it)
            false
        }
}
