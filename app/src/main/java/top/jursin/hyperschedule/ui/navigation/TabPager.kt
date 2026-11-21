package top.jursin.hyperschedule.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints

/** 主 Tab 翻页容器 */
@Composable
internal fun TabPager(
    pageCount: Int,
    position: () -> Float,
    vertical: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (Int) -> Unit,
) {
    // 位移与方向都在绘制阶段读，改它们只重绘不重组
    val positionState = rememberUpdatedState(position)
    val verticalState = rememberUpdatedState(vertical)
    Layout(
        modifier = modifier.clipToBounds(),
        content = {
            repeat(pageCount) { page ->
                key(page) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val delta = page - positionState.value()
                                    if (verticalState.value) {
                                        translationY = delta * size.height
                                    } else {
                                        translationX = delta * size.width
                                    }
                                },
                    ) { content(page) }
                }
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        // 页大小固定为整块，换方向只改位移分量
        val placeables = measurables.map { it.measure(Constraints.fixed(width, height)) }
        layout(width, height) {
            placeables.forEach { it.placeRelative(0, 0) }
        }
    }
}
