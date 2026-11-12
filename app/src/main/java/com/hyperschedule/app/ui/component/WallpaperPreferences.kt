package com.hyperschedule.app.ui.component

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hyperschedule.app.data.local.MAX_WALLPAPER_BLUR
import com.hyperschedule.app.data.local.WALLPAPER_BLUR_STEP
import com.hyperschedule.app.theme.CardItemPadding
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.BasicComponent

/** 壁纸分组，两个外观抽屉共用 */
@Composable
fun WallpaperPreferences(
    wallpaper: String,
    blur: Int,
    onPick: (Uri) -> Unit,
    onClear: () -> Unit,
    onBlurChange: (Int) -> Unit,
    onBlurCommit: () -> Unit,
) {
    WallpaperPreference(wallpaper = wallpaper, onPick = onPick, onClear = onClear)
    if (wallpaper.isNotEmpty()) {
        WallpaperBlurPreference(
            blur = blur,
            onBlurChange = onBlurChange,
            onBlurCommit = onBlurCommit,
        )
    }
}

/** 壁纸条目，点击选图后进裁剪页，长按清空 */
@Composable
private fun WallpaperPreference(
    wallpaper: String,
    onPick: (Uri) -> Unit,
    onClear: () -> Unit,
) {
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            // 取消选图就当没点过
            if (uri != null) onPick(uri)
        }
    BasicComponent(
        title = "背景壁纸",
        summary = if (wallpaper.isEmpty()) "点击选择图片" else "点击更换图片，长按清除",
        insideMargin = CardItemPadding,
        endActions = {
            ValueLabel(text = if (wallpaper.isEmpty()) "未设置" else "已设置", placeholder = wallpaper.isEmpty())
        },
        modifier =
            Modifier.combinedClickable(
                onClickLabel = "选择背景壁纸",
                onLongClickLabel = "清除背景壁纸",
                onLongClick = { if (wallpaper.isNotEmpty()) onClear() },
                onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            ),
    )
}

/** 壁纸自身的模糊强度 */
@Composable
private fun WallpaperBlurPreference(
    blur: Int,
    onBlurChange: (Int) -> Unit,
    onBlurCommit: () -> Unit,
) {
    StyleSlider(
        title = "壁纸模糊强度",
        value = blur.toFloat(),
        valueText = if (blur == 0) "关闭" else "${blur}dp",
        valueRange = 0f..MAX_WALLPAPER_BLUR.toFloat(),
        steps = MAX_WALLPAPER_BLUR / WALLPAPER_BLUR_STEP - 1,
        keyPoint = 0f,
        onValueChange = { value -> onBlurChange(value.roundToInt().coerceIn(0, MAX_WALLPAPER_BLUR)) },
        onValueChangeFinished = onBlurCommit,
    )
}
