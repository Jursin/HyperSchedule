package top.jursin.hyperschedule.ui.page.settings

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import top.jursin.hyperschedule.R
import top.jursin.hyperschedule.theme.CardCornerRadius
import top.jursin.hyperschedule.theme.CardItemPadding
import top.jursin.hyperschedule.theme.ContainerTintAlpha
import top.jursin.hyperschedule.theme.accentBlueColor
import top.jursin.hyperschedule.theme.SectionSpacing
import top.jursin.hyperschedule.ui.component.PreferenceScreen
import top.jursin.hyperschedule.ui.component.PreferenceSection
import top.jursin.hyperschedule.ui.component.pref.ArrowPreference
import top.jursin.hyperschedule.ui.component.toast
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SponsorPage(
    appBlur: Boolean,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val haptics = LocalHapticFeedback.current
    val storagePermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) context.saveQrCodeToGallery() else context.toast("保存图片需要存储权限")
        }

    PreferenceScreen(title = "赞助支持", appBlur = appBlur, onBack = onBack) {
        val hintColor = accentBlueColor()
        Card(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = CardCornerRadius,
            colors = CardDefaults.defaultColors(color = hintColor.copy(alpha = ContainerTintAlpha)),
        ) {
            Text(
                text = "如果您喜欢本应用，欢迎赞助支持～",
                modifier = Modifier.padding(16.dp),
                style = MiuixTheme.textStyles.body2,
                color = hintColor,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = CardCornerRadius,
            insideMargin = CardItemPadding,
            colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(R.drawable.mm_reward_qrcode),
                    contentDescription = "微信赞赏码",
                    modifier =
                        Modifier
                            .widthIn(max = 200.dp)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(CardCornerRadius))
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onLongPress = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                                            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                                        ) {
                                            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                        } else {
                                            context.saveQrCodeToGallery()
                                        }
                                    },
                                )
                            },
                )
            }
            Text(
                text = "微信赞赏码",
                modifier = Modifier.padding(top = SectionSpacing).align(Alignment.CenterHorizontally),
                fontSize = MiuixTheme.textStyles.headline1.fontSize,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onBackground,
            )
        }

        PreferenceSection {
            ArrowPreference(
                title = "爱发电",
                summary = "https://afdian.com/a/jursin",
                onClick = { uriHandler.openUri("https://afdian.com/a/jursin") },
            )
        }
    }
}

private fun Context.saveQrCodeToGallery() {
    val collection =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
    val values =
        ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "mm_reward_qrcode.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/HyperSchedule")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
    val resolver = contentResolver
    val uri = runCatching { resolver.insert(collection, values) }.getOrNull()
    if (uri == null) {
        toast("保存失败")
        return
    }
    runCatching {
        val bitmap = ContextCompat.getDrawable(this, R.drawable.mm_reward_qrcode)?.toBitmap()
            ?: error("找不到微信赞赏码图片")
        resolver.openOutputStream(uri)?.use { output ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) error("图片写入失败")
        } ?: error("无法打开相册输出流")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        }
    }.onSuccess {
        toast("已保存到相册")
    }.onFailure {
        resolver.delete(uri, null, null)
        toast("保存失败")
    }
}
