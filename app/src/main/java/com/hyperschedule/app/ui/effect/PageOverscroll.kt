package com.hyperschedule.app.ui.effect

import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import kotlin.math.abs
import kotlin.math.sign

/** 内容被拽到顶栏下方的距离：内容不足一屏时滚不动，只有边缘拉伸能把它拖上来，顶栏靠它出材质 */
@Stable
class PageOverscroll {
    /** 内容往上钻的距离（px） */
    var stretch by mutableFloatStateOf(0f)
        internal set

    /** 回弹结束时自增，各滚动容器据此丢掉自己的累计量 */
    internal var generation by mutableIntStateOf(0)
        private set

    /** 回弹结束：只复位累计量，[stretch] 留给顶栏做淡出 */
    fun settle() {
        generation++
    }
}

/** 顶栏与页面共用一个实例（同屏只有一页在手势中） */
val LocalPageOverscroll = compositionLocalOf { PageOverscroll() }

/** 累计位移上限，避免拖久了推到离谱的量级 */
private const val MaxStretchPx = 4000f

/** 只记录不消费：拉伸仍归 miuix 的 overScroll，[rangePx] 必须与它的阻尼口径一致 */
internal fun overscrollTracker(
    state: PageOverscroll,
    rangePx: Float,
): NestedScrollConnection = PageOverscrollConnection(state, rangePx)

private class PageOverscrollConnection(
    private val state: PageOverscroll,
    private val rangePx: Float,
) : NestedScrollConnection {
    private var generation = -1
    private var raw = 0f

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (source != NestedScrollSource.UserInput) return Offset.Zero
        if (generation != state.generation) {
            generation = state.generation
            raw = 0f
        }
        // 内容又能滚了：先把拉伸还回去，否则页面会一直留着「已钻到栏下」
        val consumedDelta = consumed.y
        if (raw != 0f && consumedDelta != 0f && sign(consumedDelta) != sign(raw)) {
            val left = raw + consumedDelta
            raw = if (sign(left) == sign(raw)) left else 0f
            state.stretch = -damped(raw)
        }
        val delta = available.y
        if (delta == 0f) return Offset.Zero
        raw = (raw + delta).coerceIn(-MaxStretchPx, MaxStretchPx)
        state.stretch = -damped(raw)
        return Offset.Zero
    }

    /** 与 miuix 的 obtainDampingDistance 同一套衰减，算出来才是内容实际走的距离 */
    private fun damped(raw: Float): Float {
        if (rangePx <= 0f) return raw
        val x = (abs(raw) / rangePx).coerceAtMost(1f)
        return sign(raw) * (x - x * x + x * x * x / 3f) * rangePx
    }
}
