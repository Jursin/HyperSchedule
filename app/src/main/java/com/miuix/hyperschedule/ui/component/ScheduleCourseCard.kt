package com.miuix.hyperschedule.ui.component

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miuix.hyperschedule.data.model.Course
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 卡内字号相对课名基准（12.7sp）的缩放 */
private const val CardTextScale = 0.8f
private val CardCornerRadius = 10.dp
private val CardInset = 2.dp

/** 课程色叠在页面底色上的不透明度 */
private const val CardAlpha = 0.15f

/** 非本周（该格本周不上）的灰：底色与文字的透明度，深色主题另给一组 */
private const val InactiveCardAlpha = 0.105f
private const val InactiveCardAlphaDark = 0.04f
private const val InactiveTextAlpha = 0.45f
private const val InactiveTextAlphaDark = 0.22f

/** 课程调色板：导入时按课程名首次出现顺序取用（见 ScheduleRepository.importCourseTableJson），同名同色 */
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

/** 课表网格里的课程卡片 */
@Composable
fun ScheduleCourseCard(
    course: Course,
    sectionHeight: Dp,
    isCurrentWeek: Boolean = true,
    heightOverride: Dp? = null,
    showBadge: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val start = course.startSection
    val span =
        if (start != null) {
            ((course.endSection ?: start).coerceAtLeast(start) - start + 1).coerceAtLeast(1)
        } else {
            1
        }
    val cardHeight = heightOverride ?: (sectionHeight * span)
    val colors = rememberCourseCardColors(course, isCurrentWeek)

    val infoFontSize = 11.sp * CardTextScale
    val nameFontSize = 12.7.sp * CardTextScale
    val lineHeight = 14.2.sp * CardTextScale
    val infoLineHeight = 12.sp * CardTextScale

    val showPosition = course.position.isNotBlank()
    val showTeacher = course.teacher.isNotBlank()
    // 卡片高度能放下几行就显示几行，超出省略
    val availableHeight = cardHeight.value - 16f
    val reservedForInfo =
        ((if (showPosition) 1 else 0) + (if (showTeacher) 1 else 0)) * infoLineHeight.value
    val nameMaxLines = ((availableHeight - reservedForInfo) / lineHeight.value).toInt().coerceAtLeast(1)
    val maxLines = nameMaxLines + (if (showPosition) 1 else 0) + (if (showTeacher) 1 else 0)

    val bodyText =
        buildAnnotatedString {
            pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = nameFontSize))
            append(course.name)
            pop()
            if (showPosition) {
                append('\n')
                pushStyle(SpanStyle(fontSize = infoFontSize))
                append("@${course.position}")
                pop()
            }
            if (showTeacher) {
                append('\n')
                pushStyle(SpanStyle(fontSize = infoFontSize))
                append(course.teacher)
                pop()
            }
        }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(cardHeight)
                .padding(CardInset)
                .clip(RoundedCornerShape(CardCornerRadius))
                .background(colors.background)
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = bodyText,
                lineHeight = lineHeight,
                color = colors.text,
                textAlign = TextAlign.Center,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
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
    }
}

/** 一张课程卡最终的底色与文字色 */
@Immutable
private data class CourseCardColors(val background: Color, val text: Color)

/**
 * 卡片取色：
 * - 底色 = 课程色 @[CardAlpha]，半透明直接画，才会叠在**页面实际底色**上
 *   （若先与 `colorScheme.background` 合成成不透明色，深色主题下会被垫灰发白）
 * - 文字 = 课程色本身；非本周换成很淡的主题灰
 */
@Composable
private fun rememberCourseCardColors(course: Course, isCurrentWeek: Boolean): CourseCardColors {
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
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
                tint.copy(alpha = CardAlpha)
            } else {
                inactive.copy(alpha = if (isDark) InactiveCardAlphaDark else InactiveCardAlpha)
            }
        val text =
            if (isCurrentWeek) {
                tint
            } else {
                inactive.copy(alpha = if (isDark) InactiveTextAlphaDark else InactiveTextAlpha)
            }
        CourseCardColors(cardColor, text)
    }
}

/** 课程色：自定义色优先，否则按 [Course.colorInt] 取色板，越界取模 */
@Composable
internal fun rememberCourseColor(course: Course): Color {
    val argb = course.colorArgb
    val index = course.colorInt
    return remember(argb, index) { courseColorOf(index, argb) }
}

/** 课程色的非 Composable 版本，编辑页与课表卡共用 */
internal fun courseColorOf(colorInt: Int, colorArgb: Int?): Color {
    colorArgb?.let { return Color(it) }
    val size = CoursePalette.size
    return CoursePalette[((colorInt % size) + size) % size]
}
