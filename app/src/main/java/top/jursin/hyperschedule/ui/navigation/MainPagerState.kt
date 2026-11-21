package top.jursin.hyperschedule.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

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
    val pageCount: Int,
    initialPage: Int,
    private val coroutineScope: CoroutineScope,
) {
    private val offset = Animatable(initialPage.toFloat())

    val position: Float
        get() = offset.value

    var selectedPage by mutableIntStateOf(initialPage)
        private set

    var isNavigating by mutableStateOf(false)
        private set

    var reselectTick by mutableIntStateOf(0)
        private set

    private var settledPage by mutableIntStateOf(initialPage)

    private var navJob: Job? = null

    /** 底栏应跟随的页码，导航中取目标页，否则取停稳的页 */
    val targetPage: State<Int> =
        derivedStateOf {
            if (isNavigating) selectedPage else settledPage
        }

    fun animateToPage(targetIndex: Int) {
        val target = targetIndex.coerceIn(0, pageCount - 1)
        if (target == selectedPage) {
            reselectTick++
            return
        }

        navJob?.cancel()
        selectedPage = target
        isNavigating = true

        navJob =
            coroutineScope.launch {
                val myJob = coroutineContext.job
                try {
                    val spec =
                        if (abs(target - settledPage) > 1) {
                            MainTabPagerCrossPageAnimSpec
                        } else {
                            MainTabPagerAnimSpec
                        }
                    offset.animateTo(target.toFloat(), spec)
                } finally {
                    if (navJob == myJob) {
                        settledPage = offset.value.roundToInt()
                        selectedPage = settledPage
                        isNavigating = false
                    }
                }
            }
    }
}

@Composable
fun rememberMainPagerState(
    pageCount: Int,
    initialPage: Int = 0,
): MainPagerState {
    val coroutineScope = rememberCoroutineScope()
    val startPage = initialPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
    // 停留的 Tab 跨配置变化保留
    var savedPage by rememberSaveable { mutableIntStateOf(startPage) }
    val state = remember(pageCount, coroutineScope) {
        MainPagerState(pageCount, savedPage, coroutineScope)
    }
    SideEffect { savedPage = state.selectedPage }
    return state
}
