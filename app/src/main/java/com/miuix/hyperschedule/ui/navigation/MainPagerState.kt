package com.miuix.hyperschedule.ui.navigation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlin.math.abs

private val MainTabPagerAnimSpec =
    spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 320f,
    )

private val MainTabPagerCrossPageAnimSpec =
    spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 240f,
    )

class MainPagerState(
    val pagerState: PagerState,
    private val coroutineScope: CoroutineScope,
) {
    var selectedPage by mutableIntStateOf(pagerState.currentPage)
        private set

    var isNavigating by mutableStateOf(false)
        private set

    private var navJob: Job? = null

    /**
     * 底栏应跟随的页码：导航中返回目标页，否则返回实际页
     * （直接读 [PagerState.currentPage] 会在跨页动画途中读到中间页）
     *
     * 包一层 [derivedStateOf]，读取方只在结果变化时重组，否则滑动每帧都会带动整棵子树
     */
    val targetPage: State<Int> =
        derivedStateOf {
            if (isNavigating) selectedPage else pagerState.currentPage
        }

    fun animateToPage(targetIndex: Int) {
        if (targetIndex == selectedPage) return

        navJob?.cancel()

        selectedPage = targetIndex
        isNavigating = true

        navJob =
            coroutineScope.launch {
                val myJob = coroutineContext.job
                try {
                    // PreventUserInput：否则动画途中起手的滑动会把它顶掉，表现为"切一半又弹回去"
                    pagerState.scroll(MutatePriority.PreventUserInput) {
                        val spec =
                            if (abs(targetIndex - pagerState.currentPage) > 1) {
                                MainTabPagerCrossPageAnimSpec
                            } else {
                                MainTabPagerAnimSpec
                            }
                        val layoutInfo = pagerState.layoutInfo
                        val pageSize = layoutInfo.pageSize + layoutInfo.pageSpacing
                        val currentDistanceInPages =
                            targetIndex - pagerState.currentPage - pagerState.currentPageOffsetFraction
                        val scrollPixels = currentDistanceInPages * pageSize

                        var previousValue = 0f
                        animate(
                            initialValue = 0f,
                            targetValue = scrollPixels,
                            animationSpec = spec,
                        ) { currentValue, _ ->
                            previousValue += scrollBy(currentValue - previousValue)
                        }
                    }

                    if (pagerState.currentPage != targetIndex ||
                        abs(pagerState.currentPageOffsetFraction) > 0.001f
                    ) {
                        pagerState.scrollToPage(targetIndex)
                    }
                } finally {
                    if (navJob == myJob) {
                        isNavigating = false
                        if (pagerState.currentPage != targetIndex) {
                            selectedPage = pagerState.currentPage
                        }
                    }
                }
            }
    }

    fun syncPage() {
        if (!isNavigating && selectedPage != pagerState.currentPage) {
            selectedPage = pagerState.currentPage
        }
    }
}

@Composable
fun rememberMainPagerState(
    pagerState: PagerState,
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
) = remember(pagerState, coroutineScope) {
    MainPagerState(pagerState, coroutineScope)
}
