package com.miuix.hyperschedule.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miuix.hyperschedule.data.model.Course
import com.miuix.hyperschedule.theme.BadgeFontSize
import com.miuix.hyperschedule.theme.CourseTintAlpha
import com.miuix.hyperschedule.theme.ScheduleStyle
import com.miuix.hyperschedule.theme.isDarkTheme
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 卡内字号的基准系数 */
private const val CardTextScale = 0.84f

/** 卡片与格子边界的间距 */
internal val CardInset = 1.dp

/** 非本周（该格本周不上）的灰：底色与文字的透明度，深色主题另给一组 */
private const val InactiveCardAlpha = 0.105f
private const val InactiveCardAlphaDark = 0.04f
private const val InactiveTextAlpha = 0.45f
private const val InactiveTextAlphaDark = 0.22f

/** 课程调色板：按课名首次出现顺序取用，同名同色 */
internal val CoursePalette =
    listOf(
        0xFF4CAF50, // 绿
        0xFF2196F3, // 蓝
        0xFFFF9800, // 橙
        0xFFF44336, // 红
        0xFFE6B422, // 金
        0xFFE91E63, // 粉
        0xFF00BCD4, // 青
        0xFF3F51B5, // 靛
        0xFFAB47BC, // 紫
        0xFF009688, // 蓝绿
        0xFF673AB7, // 深紫
    ).map { Color(it) }

/** 课表网格里的课程卡片；样式全部来自外观设置 */
@Composable
fun ScheduleCourseCard(
    course: Course,
    heightOverride: Dp,
    modifier: Modifier = Modifier,
    style: ScheduleStyle = ScheduleStyle.Default,
    isCurrentWeek: Boolean = true,
    // 放假当天：卡片一律灰显并标「假」
    isHoliday: Boolean = false,
    // 补课当天：这一列的课是从被调那天挪过来的，标「调」
    isWorkSwap: Boolean = false,
    showBadge: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    // 放假这天不上课，按非本周的灰画
    val colors = rememberCourseCardColors(course, isCurrentWeek && !isHoliday)
    val shape = RoundedCornerShape(style.cardCorner.dp)

    val scale = CardTextScale * style.textScale
    val infoFontSize = 11.sp * scale
    val nameFontSize = 12.7.sp * scale
    val lineHeight = 14.2.sp * scale
    val infoLineHeight = 12.sp * scale

    val showPosition = style.showPosition && course.position.isNotBlank()
    val showTeacher = style.showTeacher && course.teacher.isNotBlank()
    // 卡片高度能放下几行就显示几行，超出省略
    val availableHeight = heightOverride.value - 16f
    val reservedForInfo =
        ((if (showPosition) 1 else 0) + (if (showTeacher) 1 else 0)) * infoLineHeight.value
    val nameMaxLines = ((availableHeight - reservedForInfo) / lineHeight.value).toInt().coerceAtLeast(1)
    val maxLines = nameMaxLines + (if (showPosition) 1 else 0) + (if (showTeacher) 1 else 0)

    // 卡片会随滚动/换页反复组合，文本只在内容或字号变化时重建
    val bodyText =
        remember(
            course.name,
            course.position,
            course.teacher,
            nameFontSize,
            infoFontSize,
            showPosition,
            showTeacher,
            style.positionPrefix,
        ) {
            buildAnnotatedString {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = nameFontSize))
                append(course.name)
                pop()
                if (showPosition) {
                    append('\n')
                    pushStyle(SpanStyle(fontSize = infoFontSize))
                    if (style.positionPrefix) append('@')
                    append(course.position)
                    pop()
                }
                if (showTeacher) {
                    append('\n')
                    pushStyle(SpanStyle(fontSize = infoFontSize))
                    append(course.teacher)
                    pop()
                }
            }
        }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(heightOverride)
                .padding(CardInset)
                .clip(shape)
                .background(colors.background)
                .then(
                    if (style.cardBorder) {
                        Modifier.border(1.dp, colors.border, shape)
                    } else {
                        Modifier
                    }
                )
                // 无涟漪：卡片本身已经有按压反馈的视觉语言，点按直接弹详情
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    }
                ),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalAlignment = if (style.contentCenter) Alignment.CenterHorizontally else Alignment.Start,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = bodyText,
                lineHeight = lineHeight,
                color = colors.text,
                textAlign = if (style.contentCenter) TextAlign.Center else TextAlign.Start,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        // 同格还有没画出来的课：右下角圆点，颜色取卡片文字色
        if (showBadge) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(5.dp)
                        .size(8.dp)
                        .background(colors.text, CircleShape),
            )
        }
        val mark = markOf(isHoliday, isWorkSwap)
        if (mark != null) {
            HolidayMarkBadge(
                mark = mark,
                course = course,
                accent = isCurrentWeek,
                fontSize = BadgeFontSize * scale,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 5.dp, end = 5.dp),
            )
        }
    }
}

/** 一张课程卡最终的底色、文字色与边框色 */
@Immutable
private data class CourseCardColors(val background: Color, val text: Color, val border: Color)

/** 卡片取色 */
@Composable
private fun rememberCourseCardColors(course: Course, isCurrentWeek: Boolean): CourseCardColors {
    val isDark = isDarkTheme()
    val tint = rememberCourseColor(course)
    val background = MiuixTheme.colorScheme.background
    // 主题灰先合成为实色再压透明度：静态主题里 onSurface 是纯黑/纯白，
    // 直接 copy(alpha) 会丢掉色相变成发白的灰片。
    // 浅色用中灰做基色，深色用白色压到很低透明度
    val neutralSource =
        when {
            MiuixTheme.isDynamicColor -> MiuixTheme.colorScheme.onSurfaceVariantActions
            isDark -> MiuixTheme.colorScheme.onSurface
            else -> Color(0xFF9E9E9E)
        }
    val inactive = remember(neutralSource, background) { neutralSource.compositeOver(background) }

    return remember(tint, inactive, isCurrentWeek, isDark) {
        val cardColor =
            if (isCurrentWeek) {
                tint.copy(alpha = CourseTintAlpha)
            } else {
                inactive.copy(alpha = if (isDark) InactiveCardAlphaDark else InactiveCardAlpha)
            }
        val text =
            if (isCurrentWeek) {
                tint
            } else {
                inactive.copy(alpha = if (isDark) InactiveTextAlphaDark else InactiveTextAlpha)
            }
        // 边框压得比文字淡一档
        val border =
            if (isCurrentWeek) {
                tint.copy(alpha = 0.45f)
            } else {
                inactive.copy(alpha = if (isDark) 0.2f else 0.28f)
            }
        CourseCardColors(cardColor, text, border)
    }
}

/** 课程色：自定义色优先，否则按 [Course.colorInt] 取色板，越界取模 */
@Composable
internal fun rememberCourseColor(course: Course): Color {
    val argb = course.colorArgb
    val index = course.colorInt
    return remember(argb, index) { courseColorOf(index, argb) }
}

/** 课程色 */
internal fun courseColorOf(colorInt: Int, colorArgb: Int?): Color {
    colorArgb?.let { return Color(it) }
    val size = CoursePalette.size
    return CoursePalette[((colorInt % size) + size) % size]
}
