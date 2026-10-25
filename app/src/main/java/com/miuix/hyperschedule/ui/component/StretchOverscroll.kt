package com.miuix.hyperschedule.ui.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.min

private val MaxStretch = 40.dp

/** 跟手比例 */
private const val StretchTracking = 0.3f

/** 剩余空间的最小比例 */
private const val MinTrackingRatio = 0.08f

@Stable
class StretchOverscrollState(
    private val maxStretchPx: Float,
) {
    var stretchPx by mutableFloatStateOf(0f)
        private set

    // 不弹过头：拉伸最多回到 0，冲过 0 会换成另一端为锚点，看起来会跳一下
    private val settleSpec =
        spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        )

    val connection =
        object : NestedScrollConnection {
            // 往回推时先把拉伸收掉，收完了才交给网格正常滚动
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                shrink(available.y) ?: Offset.Zero

            // 已经在顶端（往下）或底端（往上）还继续拖：位移全吃掉，换成拉伸
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                val delta = available.y
                if (delta == 0f || source != NestedScrollSource.UserInput) return Offset.Zero
                // 另一端的残余还没收完：先把那头收回来，这一帧不再继续张
                shrink(delta)?.let { return it }
                val dir = if (delta > 0f) 1f else -1f
                stretchPx = ((abs(stretchPx) + abs(delta) * tracking()).coerceAtMost(maxStretchPx)) * dir
                return Offset(0f, delta)
            }
        }

    /** 反向拖动时先收掉已有的拉伸，返回被吃掉的位移；同向（还在继续拉）时返回 null */
    private fun shrink(delta: Float): Offset? {
        if (delta == 0f || stretchPx == 0f) return null
        val dir = if (delta > 0f) 1f else -1f
        if (stretchPx * dir > 0f) return null
        val used = min(abs(delta), abs(stretchPx))
        stretchPx += dir * used
        return Offset(0f, dir * used)
    }

    private fun tracking(): Float {
        val room = 1f - abs(stretchPx) / maxStretchPx
        return StretchTracking * room.coerceIn(MinTrackingRatio, 1f)
    }

    /** 松手弹回原位；下一次拖动会直接把这段动画打断 */
    suspend fun settle() {
        if (stretchPx == 0f) return
        animate(initialValue = stretchPx, targetValue = 0f, animationSpec = settleSpec) { value, _ ->
            stretchPx = value
        }
        stretchPx = 0f
    }
}

/** 每页各来一份：左右邻页的滚动互不影响 */
@Composable
fun rememberStretchOverscrollState(): StretchOverscrollState {
    val density = LocalDensity.current
    return remember(density) {
        StretchOverscrollState(with(density) { MaxStretch.toPx() })
    }
}

fun Modifier.stretchOverscroll(state: StretchOverscrollState): Modifier =
    graphicsLayer {
        val extra = state.stretchPx
        val height = size.height
        // 两个属性每次都写：图层会沿用上一次的值，只写一半会把拉伸留在身上
        transformOrigin = TransformOrigin(0.5f, if (extra < 0f) 1f else 0f)
        scaleY = if (extra != 0f && height > 0f) (height + abs(extra)) / height else 1f
    }
