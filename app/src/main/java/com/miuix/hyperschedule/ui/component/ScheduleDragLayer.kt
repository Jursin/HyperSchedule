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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.miuix.hyperschedule.data.model.Course
import com.miuix.hyperschedule.theme.ScheduleStyle
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 拖动中的浮层：跟手抬起的课程卡 + 略滞后的灰色落点预览 */
internal data class DragVisual(
    val course: Course,
    // 浮层左上角，相对浮层容器（px）
    val cardOrigin: Offset,
    // 落点预览左上角（px）；null 表示当前落点无效
    val previewOrigin: Offset?,
    val width: Dp,
    val height: Dp,
)

@Composable
internal fun ScheduleDragLayer(visualProvider: () -> DragVisual?, style: ScheduleStyle) {
    // 在浮层自己的作用域里读拖动状态：跟手位移不会带着整页重组
    val visual = visualProvider() ?: return
    // 预览用短动画跟随：手指停下时它还在往落点滑一点
    val preview by
        animateOffsetAsState(
            targetValue = visual.previewOrigin ?: visual.cardOrigin,
            animationSpec = tween(durationMillis = 110),
            label = "dragPreview",
        )
    if (visual.previewOrigin != null) {
        // 只有落到卡片后面还没跟上时才看得见：贴在原位时整块隐藏，
        // 否则灰色会透过半透明的课程卡显出来，像多套了一层矩形
        val lag = (preview - visual.cardOrigin).getDistance()
        val ghostAlpha = (lag / 48f).coerceIn(0f, 1f) * 0.12f
        Box(
            modifier =
                Modifier
                    .offset { IntOffset(preview.x.roundToInt(), preview.y.roundToInt()) }
                    .size(width = visual.width, height = visual.height)
                    .padding(2.dp)
                    .clip(RoundedCornerShape(style.cardCorner.dp))
                    .background(MiuixTheme.colorScheme.onSurface.copy(alpha = ghostAlpha)),
        )
    }
    Box(
        modifier =
            Modifier
                .offset { IntOffset(visual.cardOrigin.x.roundToInt(), visual.cardOrigin.y.roundToInt()) }
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
