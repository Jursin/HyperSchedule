package com.miuix.hyperschedule.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miuix.hyperschedule.ui.component.BlurredTopAppBar
import com.miuix.hyperschedule.ui.component.DatePickerDialog
import com.miuix.hyperschedule.ui.component.PageScrollColumn
import com.miuix.hyperschedule.ui.component.ProvideBackToNow
import com.miuix.hyperschedule.ui.navigation.LocalMainTabNavigating
import com.miuix.hyperschedule.ui.navigation.LocalPageIsCurrent
import com.miuix.hyperschedule.ui.theme.rememberAppBlurBackdrop
import java.time.LocalDate
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowUpDown
import top.yukonga.miuix.kmp.icon.extended.Background
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun TodayPage(appBlur: Boolean = false) {
    val scrollBehavior = MiuixScrollBehavior()
    val blurBackdrop = rememberAppBlurBackdrop(appBlur)
    val scope = rememberCoroutineScope()
    val dateState = rememberDatePagerState()

    var dateDraft by remember { mutableStateOf(dateState.today) }
    var showJumpDialog by remember { mutableStateOf(false) }

    val pageIsCurrent = LocalPageIsCurrent.current
    val tabNavigating = LocalMainTabNavigating.current
    // 离开过就记一笔，切回那一刻或导航结束时复位
    var pendingReset by remember { mutableStateOf(false) }
    LaunchedEffect(pageIsCurrent, tabNavigating) {
        if (!pageIsCurrent) pendingReset = true
        // 导航结束（已在屏外）或刚切回（此刻 pager 还没动，本页仍在屏外）时复位
        if (pendingReset && (pageIsCurrent || !tabNavigating)) {
            dateState.snapToDate(dateState.today)
            pendingReset = false
        }
    }

    // 非今天时显示「今」按钮，待复位期间不显示
    val showBackToNow =
        remember(dateState.pagerState) { derivedStateOf { !pendingReset && !dateState.isToday } }

    ProvideBackToNow(
        visible = showBackToNow,
        onClick = { scope.launch { dateState.backToToday() } },
    )

    Scaffold(
        topBar = {
            BlurredTopAppBar(
                title = "今日课表",
                blurBackdrop = blurBackdrop,
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = {}) {
                        Icon(MiuixIcons.Background, contentDescription = "课表外观")
                    }
                },
            )
        },
    ) { paddingValues ->
        DatePager(
            dateState = dateState,
            modifier = Modifier.fillMaxSize(),
            // 底部导航切页动画期间禁用手势，避免和过渡动画互抢
            userScrollEnabled = !tabNavigating,
        ) { pageDate, _ ->
            PageScrollColumn(
                // 只让当前页往 backdrop 里录：同一个 backdrop 只有一个采集点
                blurBackdrop = blurBackdrop.takeIf { pageDate == dateState.date },
                scrollBehavior = scrollBehavior,
                // 顶栏高度作为滚动内容的内边距：内容才能滚到顶栏下方，顶栏有东西可模糊
                contentPadding = PaddingValues(top = paddingValues.calculateTopPadding()),
                bottomPadding = 80.dp,
                topPadding = 0.dp,
            ) {
                DateTitleRow(
                    date = pageDate,
                    onJumpClick = {
                        dateDraft = pageDate
                        showJumpDialog = true
                    },
                )
                EmptyCourseHint()
            }
        }
    }

    if (showJumpDialog) {
        DatePickerDialog(
            title = "跳转日期",
            date = dateDraft,
            onDateChange = { dateDraft = it },
            appBlur = appBlur,
            onDismiss = { showJumpDialog = false },
            onConfirm = {
                scope.launch { dateState.animateToDate(dateDraft) }
                showJumpDialog = false
            },
        )
    }
}

@Composable
private fun DateTitleRow(date: LocalDate, onJumpClick: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .offset(x = (-15).dp)
                .padding(start = 28.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${date.year}年${date.monthValue}月${date.dayOfMonth}日 周${"一二三四五六日"[date.dayOfWeek.value - 1]}",
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = MiuixTheme.colorScheme.onSurface,
        )
        IconButton(
            onClick = onJumpClick,
            modifier = Modifier.padding(start = 6.dp),
            minWidth = 32.dp,
            minHeight = 32.dp,
        ) {
            Icon(
                imageVector = MiuixIcons.Basic.ArrowUpDown,
                contentDescription = "跳转日期",
                modifier = Modifier.size(20.dp),
                tint = MiuixTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun EmptyCourseHint() {
    Box(modifier = Modifier.fillMaxWidth().height(250.dp), contentAlignment = Alignment.Center) {
        Text(
            text = "这天没有课程",
            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
            style = MiuixTheme.textStyles.body1.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium),
        )
    }
}
