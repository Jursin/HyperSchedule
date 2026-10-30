package com.miuix.hyperschedule.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.storage.StorageManager
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.net.toUri
import com.miuix.hyperschedule.data.remote.AppUserAgent
import com.miuix.hyperschedule.reminder.PrivilegedService
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.concurrent.thread
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 安装包的下载、校验与安装 */
internal object UpdateApk {
    private const val NAME = "hyperschedule.apk"

    /** 放 filesDir，避免 cacheDir 被系统清掉 */
    private fun dir(context: Context): File = File(context.filesDir, "update")

    fun target(context: Context): File = File(dir(context), NAME)

    private fun part(context: Context): File = File(dir(context), "$NAME.part")

    /** 已下载且通过预检时直接复用 */
    fun reuse(context: Context, asset: ReleaseAsset): File? {
        val file = target(context)
        return if (verify(context, file, asset.sha256, asset.size) == null) file else null
    }

    /** 下载到 filesDir，进度 0f..1f；失败重试一次 */
    suspend fun download(
        context: Context,
        asset: ReleaseAsset,
        onProgress: suspend (Float) -> Unit,
    ): File =
        withContext(Dispatchers.IO) {
            val folder = dir(context)
            folder.mkdirs()
            reserveSpace(context, folder, asset.size)
            try {
                fetch(context, asset, onProgress)
            } catch (e: CancellationException) {
                throw e
            } catch (_: IOException) {
                // 只有网络类错误值得重试一次
                fetch(context, asset, onProgress)
            }
        }

    /** 先把空间划出来，避免写到一半被别的应用挤爆 */
    private fun reserveSpace(context: Context, folder: File, size: Long) {
        if (size <= 0L) return
        val storage = context.getSystemService(StorageManager::class.java) ?: return
        runCatching { storage.allocateBytes(storage.getUuidForPath(folder), size) }
            .onFailure { error("存储空间不足") }
    }

    private suspend fun fetch(
        context: Context,
        asset: ReleaseAsset,
        onProgress: suspend (Float) -> Unit,
    ): File {
        val file = target(context)
        val partFile = part(context)
        val connection =
            (withContext(Dispatchers.IO) {
                URL(asset.url).openConnection()
            } as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", AppUserAgent)
            }
        try {
            val code = connection.responseCode
            check(code in 200..299) { "HTTP $code" }
            val total = connection.contentLengthLong
            val digest = MessageDigest.getInstance("SHA-256")
            var downloaded = 0L
            var lastPercent = -1
            connection.inputStream.use { input ->
                FileOutputStream(partFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        downloaded += read
                        if (total > 0L) {
                            val percent = (downloaded * 100 / total).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent / 100f)
                            }
                        }
                    }
                    output.fd.sync()
                }
            }
            // 摘要已在写入时算过，这里只补结构与身份校验
            verify(context, partFile, null, asset.size)?.let { error(it) }
            val expected = asset.sha256
            if (!expected.isNullOrBlank()) {
                check(digest.hex().equals(expected, ignoreCase = true)) { "安装包校验失败" }
            }
            check(partFile.renameTo(file)) { "安装包保存失败" }
            onProgress(1f)
            return file
        } finally {
            connection.disconnect()
            partFile.delete()
        }
    }

    /** 校验安装包，返回 null 表示通过 */
    private fun verify(context: Context, file: File, sha256: String?, size: Long): String? {
        if (file.length() <= 0L) return "安装包为空"
        if (size > 0L && file.length() != size) return "安装包大小不符"
        val manager = context.packageManager
        val archive =
            runCatching {
                    manager.getPackageArchiveInfo(file.absolutePath, signatureFlags())
                }
                .getOrNull() ?: return "安装包无法解析"
        if (archive.packageName != context.packageName) return "安装包包名不符"
        val installed = manager.getPackageInfo(context.packageName, signatureFlags())
        val current = signatures(installed)
        val target = signatures(archive)
        if (current.none { it in target }) return "安装包签名与已安装应用不一致"
        if (!isNewerThanInstalled(manager, context.packageName, archive)) {
            return "安装包版本(${archive.code()})不高于当前(${installed.code()})"
        }
        if (!sha256.isNullOrBlank()) {
            val actual = sha256(file) ?: return "安装包摘要计算失败"
            if (!actual.equals(sha256, ignoreCase = true)) return "安装包校验失败"
        }
        return null
    }

    /** 是否已允许安装未知来源应用 */
    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    /** 跳到「安装未知应用」授权页；跳转失败返回 false，由调用方提示 */
    fun openPermissionSettings(context: Context): Boolean {
        val intent =
            Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    "package:${context.packageName}".toUri(),
                )
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    /** 交给系统安装器 */
    fun install(context: Context, file: File): Boolean =
        runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent =
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            context.startActivity(intent)
        }.isSuccess

    /** 已授权 Shizuku 时让 shell 服务静默安装，返回 null 表示成功 */
    suspend fun installSilently(context: Context, file: File): String? =
        withContext(Dispatchers.IO) {
            val service = PrivilegedService.get(context) ?: return@withContext "Shizuku 服务不可用"
            val pipe = ParcelFileDescriptor.createPipe()
            // 服务端同步读管道，写入必须另开线程，否则 binder 调用两边互相等死
            val writer =
                thread(isDaemon = true) {
                    runCatching {
                        ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { output ->
                            file.inputStream().use { input -> input.copyTo(output) }
                        }
                    }
                }
            val output =
                runCatching { service.installApk(pipe[0]) }
                    .getOrElse {
                        pipe[0].close()
                        writer.join()
                        return@withContext it.message ?: "安装失败"
                    }
            pipe[0].close()
            writer.join()
            when {
                output.contains("Success") -> null
                output.isBlank() -> "安装失败"
                else -> output.trim().lineSequence().first()
            }
        }

    /** 清掉不高于当前版本的安装包 */
    fun prune(context: Context) {
        part(context).delete()
        val file = target(context)
        if (!file.isFile) return
        val manager = context.packageManager
        val archive =
            runCatching { manager.getPackageArchiveInfo(file.absolutePath, 0) }.getOrNull()
        if (!isNewerThanInstalled(manager, context.packageName, archive)) file.delete()
    }

    /** API 28 以下没有 GET_SIGNING_CERTIFICATES，读签名只能走旧字段 */
    @Suppress("DEPRECATION")
    private fun signatureFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }

    private fun PackageInfo.code(): Long = PackageInfoCompat.getLongVersionCode(this)

    /** 是否比已安装的新 */
    private fun isNewerThanInstalled(
        manager: PackageManager,
        packageName: String,
        archive: PackageInfo?,
    ): Boolean = archive != null && archive.code() > manager.getPackageInfo(packageName, 0).code()

    /** 签名只要求有交集，兼容密钥轮换；读不到证书时判为不一致 */
    @Suppress("DEPRECATION")
    private fun signatures(info: PackageInfo): List<String> =
        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.signingInfo?.apkContentsSigners
            } else {
                info.signatures
            })
            ?.map { it.toCharsString() }
            .orEmpty()

    private fun sha256(file: File): String? =
        runCatching { file.inputStream().use { it.digest().hex() } }.getOrNull()

    private fun InputStream.digest(): MessageDigest {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
        return digest
    }

    private fun MessageDigest.hex(): String =
        digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
