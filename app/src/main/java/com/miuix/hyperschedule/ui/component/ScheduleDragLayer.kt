package com.miuix.hyperschedule.ui.component

import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.miuix.hyperschedule.data.model.Course
import com.miuix.hyperschedule.theme.ScheduleStyle
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 拖动浮层里只有跨格才变的部分 */
internal data class DragVisual(
    val course: Course,
    // 落点预览左上角，null 表示无效
    val previewOrigin: Offset?,
    val width: Dp,
    val height: Dp,
)

@Composable
internal fun ScheduleDragLayer(
    visualProvider: () -> DragVisual?,
    // 跟手位移在布局/绘制阶段求值
    cardOrigin: () -> Offset,
    style: ScheduleStyle,
) {
    val visual = visualProvider() ?: return
    // 预览用短动画跟随
    val preview by
        animateOffsetAsState(
            targetValue = visual.previewOrigin ?: Offset.Zero,
            animationSpec = tween(durationMillis = 110),
            label = "dragPreview",
        )
    if (visual.previewOrigin != null) {
        // 只有落到卡片后面还没跟上时才看得见
        Box(
            modifier =
                Modifier
                    .offset { preview.toIntOffset() }
                    .size(width = visual.width, height = visual.height)
                    .padding(2.dp)
                    .clip(RoundedCornerShape(style.cardCorner.dp))
                    .graphicsLayer {
                        val lag = (preview - cardOrigin()).getDistance()
                        alpha = (lag / 48f).coerceIn(0f, 1f) * 0.12f
                    }
                    .background(MiuixTheme.colorScheme.onSurface),
        )
    }
    Box(
        modifier =
            Modifier
                .offset { cardOrigin().toIntOffset() }
                .size(width = visual.width, height = visual.height),
    ) {
        ScheduleCourseCard(
            course = visual.course,
            style = style,
            isCurrentWeek = true,
            heightOverride = visual.height,
            modifier = Modifier.matchParentSize(),
        )
    }
}

private fun Offset.toIntOffset(): IntOffset = IntOffset(x.roundToInt(), y.roundToInt())
