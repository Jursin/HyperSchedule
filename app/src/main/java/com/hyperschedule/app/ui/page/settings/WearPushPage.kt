package com.hyperschedule.app.ui.page.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hyperschedule.app.R
import com.hyperschedule.app.data.local.WearPushSettings
import com.hyperschedule.app.data.push.WearSchedule
import com.hyperschedule.app.data.push.WearSync
import com.hyperschedule.app.data.push.buildWearSchedule
import com.hyperschedule.app.data.push.describeWearError
import com.hyperschedule.app.data.push.encodeWearSchedule
import com.hyperschedule.app.data.repository.HolidayRepository
import com.hyperschedule.app.data.repository.ScheduleRepository
import com.hyperschedule.app.data.repository.TableSummary
import com.hyperschedule.app.schedule.DEFAULT_TOTAL_WEEKS
import com.hyperschedule.app.schedule.MAX_TOTAL_WEEKS
import com.hyperschedule.app.schedule.cjkDigitGap
import com.hyperschedule.app.schedule.parseDateOrNull
import com.hyperschedule.app.schedule.semesterStartMonday
import com.hyperschedule.app.theme.CardCornerRadius
import com.hyperschedule.app.theme.dangerColor
import com.hyperschedule.app.theme.dangerContainerColor
import com.hyperschedule.app.theme.successColor
import com.hyperschedule.app.theme.successContainerColor
import com.hyperschedule.app.ui.component.Container
import com.hyperschedule.app.ui.component.PreferenceScreen
import com.hyperschedule.app.ui.component.PreferenceSection
import com.hyperschedule.app.ui.component.pref.OverlayDropdownPreference
import com.hyperschedule.app.ui.component.pref.SwitchPreference
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Send
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

private const val PERMISSION_DENIED_HINT = "穿戴设备未授权"
private const val WEAR_APP_MISSING_HINT = "穿戴设备未安装快应用"

// 快应用获取链接
private const val WEAR_APP_URL = "https://astrobox.online/open?source=resv2&id=com.schedule.vela&provider=OfficialV2"

/** 快应用装了就直接拉起，未安装返回 false；签名不一致等异常交给调用方提示 */
private suspend fun WearSync.openWearApp(nodeId: String): Boolean {
    if (!isWearAppInstalled(nodeId)) return false
    launchWearApp(nodeId)
    return true
}

/** 组装好的推送内容 */
private class PushPayload(
    val schedule: WearSchedule,
    val holidayApplied: Boolean,
)

/** 读取课表并按放假调休调整后组装推送内容 */
private suspend fun buildPushPayload(
    tableId: String,
    excludeHoliday: Boolean,
    repository: ScheduleRepository,
    holidayRepository: HolidayRepository,
): PushPayload {
    val config = repository.configOnce(tableId)
    val courses = repository.coursesOnce(tableId)
    val startDate = parseDateOrNull(config?.semesterStartDate.orEmpty())
    require(startDate != null) { "请先设置开学日期" }
    val configuredWeeks = config?.semesterTotalWeeks ?: DEFAULT_TOTAL_WEEKS
    require(configuredWeeks in 1..MAX_TOTAL_WEEKS) { "请先设置学期周数" }
    // 课表里有课的最大周次也参与
    val courseWeeks = courses.maxOfOrNull { group -> group.weeks.maxOfOrNull { it.weekNumber } ?: 0 } ?: 0
    val lastWeek = maxOf(configuredWeeks, courseWeeks).coerceIn(1, MAX_TOTAL_WEEKS)
    // 没勾选排除节假日时按原始周次推送
    val startMonday = semesterStartMonday(startDate)
    val entries =
        if (excludeHoliday) {
            holidayRepository.entriesIn(startMonday, startMonday.plusWeeks(lastWeek.toLong()).minusDays(1))
        } else {
            emptyList()
        }
    val holidayApplied = entries.isNotEmpty()
    val schedule =
        buildWearSchedule(
            courses = courses,
            slots = repository.slotsOnce(tableId),
            timeTable = repository.effectiveTimeTableOnce(tableId),
            startDate = startDate,
            lastWeek = lastWeek,
            entries = entries,
        )
    require(schedule.courses.isNotEmpty()) { if (holidayApplied) "课程都被节假日排除了" else "没有可推送的课程，请检查课程时间" }
    // 按节次上的课缺了作息时间就排不出来，穿戴设备那边只会显示一片空白
    require(schedule.timeSlots.isNotEmpty() || schedule.courses.all { it.isCustomTime }) { "缺少节次时间，请先设置作息" }
    return PushPayload(
        schedule = schedule,
        holidayApplied = holidayApplied,
    )
}

/** 推送到穿戴设备 */
@Composable
fun WearPushPage(
    tables: List<TableSummary>,
    currentTableId: String?,
    setting: WearPushSettings,
    onSettingChange: (WearPushSettings) -> Unit,
    repository: ScheduleRepository,
    holidayRepository: HolidayRepository,
    appBlur: Boolean = false,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val wear = remember(context) { WearSync(context) }

    var deviceId by remember { mutableStateOf<String?>(null) }
    var deviceName by remember { mutableStateOf("") }
    // null 表示还没查（未连接或刚进页面），false 才是设备上确实没装
    var appInstalled by remember { mutableStateOf<Boolean?>(null) }
    var pushing by remember { mutableStateOf(false) }

    val ids = tables.map { it.table.id }
    val activeId = setting.tableId?.takeIf { it in ids } ?: currentTableId?.takeIf { it in ids } ?: ids.firstOrNull()
    val selectedIndex = ids.indexOf(activeId).coerceAtLeast(0)

    fun toast(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    }

    // 拉起快应用，签名不一致、未安装等失败原因都提示出来
    suspend fun openWearAppSafely(nodeId: String) {
        runCatching { wear.openWearApp(nodeId) }
            .onSuccess { installed ->
                appInstalled = installed
                if (!installed) toast(WEAR_APP_MISSING_HINT)
            }
            .onFailure { toast(describeWearError(it)) }
    }

    // 每秒查一次连接状态，新设备连上时申请一次穿戴设备权限并拉起快应用
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(wear, lifecycleOwner) {
        var lastNodeId: String? = null
        var wasResumed = false
        while (isActive) {
            val resumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            // 退到后台就不再查，别让穿戴服务每秒被问一次
            if (resumed) {
                val node = runCatching { wear.connectedNode() }.getOrNull()
                deviceId = node?.id
                deviceName = node?.name.orEmpty()
                if (node == null) {
                    lastNodeId = null
                    appInstalled = null
                } else if (node.id != lastNodeId) {
                    lastNodeId = node.id
                    // 权限通过才去拉起快应用；签名不一致时申请权限就会失败，这里已经不重复提示
                    runCatching { wear.ensurePermission(node.id) }.fold(
                        onSuccess = { granted ->
                            if (granted) {
                                toast("已连接${node.name.orEmpty().ifBlank { "穿戴设备" }}".cjkDigitGap())
                                openWearAppSafely(node.id)
                            } else {
                                toast(PERMISSION_DENIED_HINT)
                            }
                        },
                        onFailure = { toast(describeWearError(it)) },
                    )
                } else if (!wasResumed) {
                    appInstalled = runCatching { wear.isWearAppInstalled(node.id) }.getOrNull()
                }
            }
            wasResumed = resumed
            delay(1000.milliseconds)
        }
    }

    fun push() {
        if (pushing) return
        val tableId = activeId ?: return toast("暂无课表")
        val device = deviceId ?: return toast("未连接穿戴设备")
        pushing = true
        scope.launch {
            // 发送前先落实权限，否则会等到发出去才回一个 permission denied
            val granted =
                runCatching { wear.ensurePermission(device) }.getOrElse {
                    pushing = false
                    return@launch toast(describeWearError(it))
                }
            if (!granted) {
                pushing = false
                return@launch toast(PERMISSION_DENIED_HINT)
            }
            // 课表内容不满足推送条件时抛的是本地异常
            val payload =
                runCatching { buildPushPayload(tableId, setting.excludeHoliday, repository, holidayRepository) }
                    .getOrElse {
                        pushing = false
                        return@launch toast(it.message ?: "读取课表失败")
                    }
            val result = runCatching { wear.send(device, encodeWearSchedule(payload.schedule)) }
            pushing = false
            result.fold(
                onSuccess = {
                    val count = payload.schedule.courses.map { it.name }.distinct().size
                    val suffix = if (payload.holidayApplied) "（已排除节假日与调休）" else ""
                    toast("已推送${count}门课程$suffix".cjkDigitGap())
                },
                onFailure = { toast("推送失败：${describeWearError(it)}") },
            )
        }
    }

    PreferenceScreen(
        title = "推送到穿戴设备",
        appBlur = appBlur,
        onBack = onBack,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { push() },
                containerColor =
                    if (pushing) {
                        MiuixTheme.colorScheme.disabledPrimaryButton
                    } else {
                        MiuixTheme.colorScheme.primary
                    },
            ) {
                if (pushing) {
                    CircularProgressIndicator(
                        size = 24.dp,
                        colors =
                            ProgressIndicatorDefaults.progressIndicatorColors(
                                foregroundColor = MiuixTheme.colorScheme.disabledOnPrimaryButton,
                                backgroundColor = Color.Transparent,
                            ),
                    )
                } else {
                    Icon(
                        imageVector = MiuixIcons.Send,
                        contentDescription = "推送",
                        tint = MiuixTheme.colorScheme.onPrimary,
                    )
                }
            }
        },
    ) {
        ConnectionStatusCard(
            isConnected = deviceId != null,
            deviceName = deviceName,
            onOpenApp = { deviceId?.let { device -> scope.launch { openWearAppSafely(device) } } },
        )

        PreferenceSection {
            if (tables.isEmpty()) {
                Text(
                    text = "暂无课表",
                    modifier = Modifier.padding(16.dp),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            } else {
                OverlayDropdownPreference(
                    title = "选择课表",
                    items = tables.map { it.table.name.ifBlank { "未命名" } },
                    selectedIndex = selectedIndex,
                    onSelectedIndexChange = { index -> onSettingChange(setting.copy(tableId = ids[index])) },
                )
            }
            SwitchPreference(
                checked = setting.excludeHoliday,
                onCheckedChange = { checked -> onSettingChange(setting.copy(excludeHoliday = checked)) },
                title = "推送调整后课表",
                summary = "推送排除假期与调休后课表",
            )
        }

        if (deviceId != null && appInstalled == false) {
            Container(
                text = "通过 AstroBox 获取穿戴设备快应用",
                onClick = { uriHandler.openUri(WEAR_APP_URL) },
            )
        }
    }
}

@Composable
private fun ConnectionStatusCard(
    isConnected: Boolean,
    deviceName: String,
    onOpenApp: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    val dynamic = MiuixTheme.isDynamicColor
    val containerColor =
        when {
            dynamic -> if (isConnected) scheme.secondaryContainer else scheme.errorContainer
            isConnected -> successContainerColor()
            else -> dangerContainerColor()
        }
    val textColor =
        when {
            dynamic -> if (isConnected) scheme.onSecondaryContainer else scheme.onErrorContainer
            else -> scheme.onSurface
        }
    val iconColor =
        when {
            dynamic -> (if (isConnected) scheme.primary else scheme.error).copy(alpha = 0.8f)
            isConnected -> successColor()
            else -> dangerColor()
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = CardCornerRadius,
        colors = CardDefaults.defaultColors(color = containerColor),
        pressFeedbackType = PressFeedbackType.Sink,
        onClick = if (isConnected) onOpenApp else null,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .offset(50.dp, 38.dp),
                contentAlignment = Alignment.BottomEnd,
            ) {
                Icon(
                    modifier = Modifier.size(170.dp),
                    imageVector = ImageVector.vectorResource(if (isConnected) R.drawable.ic_check_circle else R.drawable.ic_error),
                    tint = iconColor,
                    contentDescription = null,
                )
            }
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
            ) {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = if (isConnected) "已连接" else "未连接",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = if (isConnected) deviceName.ifBlank { "穿戴设备" } else "请连接穿戴设备",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = textColor.copy(alpha = 0.8f),
                )
                Spacer(Modifier.height(36.dp))
            }
        }
    }
}
