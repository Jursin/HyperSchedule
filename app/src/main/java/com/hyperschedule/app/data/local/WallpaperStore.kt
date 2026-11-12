package com.hyperschedule.app.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import kotlin.math.max

/** 背景壁纸文件，只留最新一张，路径记在设置里 */
object WallpaperStore {
    private const val PREFIX = "wallpaper_"
    private const val TEMP_SUFFIX = ".tmp"

    /** 存盘 JPEG 质量 */
    private const val QUALITY = 92

    /** 裁剪前解码的长边上限 */
    private const val EDIT_MAX_SIDE = 2560

    /** 目录里所有壁纸文件 */
    private fun filesOf(context: Context): List<File> =
        context.filesDir.listFiles { file -> file.name.startsWith(PREFIX) }?.toList().orEmpty()

    /** 读入用户选的图，按 EXIF 摆正并按长边降采样，失败返回 null */
    fun decode(context: Context, uri: Uri): Bitmap? = runCatching { decodeOrThrow(context, uri) }.getOrNull()

    private fun decodeOrThrow(context: Context, uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // 只量尺寸这一趟 decodeStream 本来就不返回图，不能拿返回值判断成败
        val boundsInput = resolver.openInputStream(uri) ?: return null
        boundsInput.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        // 取能让长边落在上限内的最小降采样倍率
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > EDIT_MAX_SIDE) sample *= 2
        val bitmap =
            resolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return null
        // 相机出的图方向记在 EXIF 里，读不到就当正常方向
        val orientation =
            runCatching {
                resolver.openInputStream(uri)?.use { input ->
                    ExifInterface(input)
                        .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                }
            }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
        return bitmap.orient(orientation)
    }

    /** 先写临时文件再改名 */
    fun save(context: Context, bitmap: Bitmap): String? =
        runCatching {
            val stamp = System.currentTimeMillis()
            val target = File(context.filesDir, "$PREFIX$stamp.img")
            val temp = File(context.filesDir, "$PREFIX$stamp$TEMP_SUFFIX")
            val written = temp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
            if (!written || !temp.renameTo(target)) {
                temp.delete()
                return@runCatching null
            }
            target.absolutePath
        }.getOrNull()

    /** 设置里已经指向 [keep] 之后再清旧图 */
    fun prune(context: Context, keep: String) {
        filesOf(context).forEach { if (it.absolutePath != keep) it.delete() }
    }

    fun clear(context: Context) {
        filesOf(context).forEach { it.delete() }
    }
}

/** 按 EXIF 方向把位图摆正，方向正常时原样返回 */
private fun Bitmap.orient(orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.postRotate(90f)
            matrix.postScale(-1f, 1f)
        }

        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.postRotate(270f)
            matrix.postScale(-1f, 1f)
        }

        else -> return this
    }
    val straightened = Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    if (straightened !== this) recycle()
    return straightened
}
