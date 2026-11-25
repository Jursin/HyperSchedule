package top.jursin.hyperschedule.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import top.jursin.hyperschedule.theme.SectionSpacing
import top.jursin.hyperschedule.ui.effect.LocalPageGlassBackdrop
import top.yukonga.miuix.kmp.glass.GlassBottomSheet
import top.yukonga.miuix.kmp.glass.GlassBottomSheetDefaults

/** 抽屉内可滚动的内容列，最大高度由调用方定 */
@Composable
fun SheetScrollColumn(
    maxHeight: Dp = 320.dp,
    contentPadding: PaddingValues = PaddingValues(),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clearFocusOnPress(LocalFocusManager.current)
                .heightIn(max = maxHeight)
                .verticalScroll(rememberScrollState())
                .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(SectionSpacing),
        content = content,
    )
}

/** 底部抽屉外壳 */
@Composable
fun SheetScaffold(
    title: String,
    show: Boolean,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
    startAction: (@Composable () -> Unit)? = null,
    endAction: (@Composable () -> Unit)? = null,
    scrollOffset: (() -> Float)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    GlassBottomSheet(
        show = show,
        onDismissRequest = onDismiss,
        onDismissFinished = onDismissFinished,
        title = title,
        startAction = startAction,
        endAction = endAction,
        backdrop = LocalPageGlassBackdrop.current,
        material = GlassBottomSheetDefaults.material(),
        stroke = GlassBottomSheetDefaults.stroke(),
        scrollOffset = scrollOffset,
        insideMargin = DpSize(16.dp, 16.dp),
        content = content,
    )
}
