package com.miuix.hyperschedule.ui.effect

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

// 弹簧换算：FolmeEase.spring(damping, response) → dampingRatio = damping、stiffness = (2π/response)²
private val SpecTrail = spring<Float>(dampingRatio = 0.75f, stiffness = 157.9f) // N 落后边 0.75, 0.5
private val SpecAdvance = spring<Float>(dampingRatio = 0.7f, stiffness = 246.7f) // M 领先边 0.7, 0.4
private val SpecSnap = spring<Float>(dampingRatio = 1f, stiffness = 1755f) // O 主弹簧 1.0, 0.15

/**
 * 底栏指示器的左右边界动画，对应日历 `miuix.bottomnavigation` 的 `la.n`
 *
 * 两条边各自独立：跨项时领先边用 [SpecAdvance]、落后边用 [SpecTrail]，
 * 于是胶囊在途中被拉长再收回。坐标在逻辑 LTR 序下，RTL 由调用方镜像
 */
class NavIndicatorAnimation(
    private val scope: CoroutineScope,
    initialIndex: Int,
    private val slotWidth: Float,
    private val innerWidth: Float,
    private val overhang: Float,
) {
    private val left = Animatable(initialIndex * slotWidth - overhang)
    private val right = Animatable((initialIndex + 1) * slotWidth + overhang)

    /** 当前项，拖动中随最近项变化 */
    var index = initialIndex
        private set

    private var dragAnchorX = 0f
    private var dragLeft = 0f

    private val lastIndex = (innerWidth / slotWidth).roundToInt() - 1

    // 日历把钳制写在动画属性 setter 里（left 不小于首项左边、right 不大于末项右边），
    // 弹簧过冲也不会越界；这里等价地夹在读取值上
    val leftPx: Float get() = left.value.coerceAtLeast(-overhang)

    val rightPx: Float get() = right.value.coerceAtMost(innerWidth + overhang)

    private fun boundsOf(i: Int): Pair<Float, Float> =
        (i * slotWidth - overhang) to ((i + 1) * slotWidth + overhang)

    /** 手指落在哪一项：按项中心取最近 */
    fun nearestTo(x: Float): Int = ((x / slotWidth) - 0.5f).roundToInt().coerceIn(0, lastIndex)

    /** 切到第 [newIndex] 项；往右跳时左边界落后、右边界领先，往左跳相反 */
    fun select(newIndex: Int, animate: Boolean = true) {
        val moving = newIndex.compareTo(index)
        index = newIndex
        val (l, r) = boundsOf(newIndex)
        if (!animate) {
            scope.launch {
                left.snapTo(l)
                right.snapTo(r)
            }
            return
        }
        val (specLeft, specRight) =
            when {
                moving > 0 -> SpecTrail to SpecAdvance
                moving < 0 -> SpecAdvance to SpecTrail
                else -> SpecTrail to SpecTrail
            }
        settle(left, l, specLeft)
        settle(right, r, specRight)
    }

    /** 拖动开始：记下按下位置与当前左边界，作为增量基准 */
    fun startDrag(fingerX: Float) {
        dragAnchorX = fingerX
        dragLeft = left.value
    }

    /** 拖动中的一帧，返回应高亮的项；[prevFrameX] 用于算橡皮筋拉伸量（每帧增量 ×4，上限 60px） */
    fun dragTo(fingerX: Float, prevFrameX: Float): Int {
        // 钳制到容器内边，上限按指示器自身宽度（槽 + 两侧 overhang）算，否则右端会顶出容器
        val target =
            (dragLeft + (fingerX - dragAnchorX))
                .coerceIn(-overhang, innerWidth - slotWidth - overhang)
        val targetRight = target + slotWidth + overhang * 2f
        val frameDx = fingerX - prevFrameX
        val nearest = nearestTo(fingerX)

        if (nearest != index) {
            // 越过项中心：换项，两边按拖动方向分别用落后/领先弹簧
            index = nearest
            settle(left, target, if (frameDx > 0f) SpecTrail else SpecAdvance)
            settle(right, targetRight, if (frameDx > 0f) SpecAdvance else SpecTrail)
            return nearest
        }

        // 同一项内：反向边让出橡皮筋，正向边跟到手指
        val stretch = (abs(frameDx) * 4f).coerceAtMost(60f)
        when {
            frameDx > 0f && targetRight < innerWidth + overhang -> {
                settle(left, (target - stretch).coerceAtLeast(-overhang), SpecSnap)
                settle(right, targetRight, SpecSnap)
            }

            frameDx < 0f && target > -overhang -> {
                settle(left, target, SpecSnap)
                settle(right, (targetRight + stretch).coerceAtMost(innerWidth + overhang), SpecSnap)
            }

            else -> {
                settle(left, target, SpecSnap)
                settle(right, targetRight, SpecSnap)
            }
        }
        return nearest
    }

    // 每帧重定向且不 await（日历 Folme 的行为）；await 会把手势循环挂住
    private fun settle(target: Animatable<Float, *>, value: Float, spec: AnimationSpec<Float>) {
        scope.launch { target.animateTo(value, spec) }
    }
}
