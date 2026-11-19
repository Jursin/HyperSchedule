package com.hyperschedule.app.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.hyperschedule.app.ui.effect.LocalFabBackdrop
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.GlassFloatingActionButton
import top.yukonga.miuix.kmp.glass.GlassTopAppBarDefaults
import top.yukonga.miuix.kmp.material.GlassColorBlendMode
import top.yukonga.miuix.kmp.material.GlassColorLayer
import top.yukonga.miuix.kmp.material.GlassMaterial
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 玻璃浮动按钮 */
@Composable
fun GlassFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MiuixTheme.colorScheme.primary,
    backdrop: Backdrop? = LocalFabBackdrop.current,
    content: @Composable () -> Unit,
) {
    GlassFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        backdrop = backdrop,
        material = glassFabMaterial(color),
        fill = color,
        content = content,
    )
}

/** 与库内默认材质同构，只把底色换成给定主题色 */
@Composable
private fun glassFabMaterial(color: Color): GlassMaterial {
    val base = GlassTopAppBarDefaults.buttonMaterial()
    return remember(base, color) {
        base.copy(
            first = GlassColorLayer(color.copy(alpha = 0.92f), GlassColorBlendMode.SrcOver),
            second = null,
            third = null,
        )
    }
}
