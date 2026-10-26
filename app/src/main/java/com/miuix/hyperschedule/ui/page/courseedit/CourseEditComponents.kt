package com.miuix.hyperschedule.ui.page.courseedit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.miuix.hyperschedule.theme.PageHorizontalPadding
import com.miuix.hyperschedule.theme.SectionSpacing
import com.miuix.hyperschedule.ui.component.CoursePalette
import com.miuix.hyperschedule.ui.component.WideActionButton
import com.miuix.hyperschedule.ui.component.dayLabel
import com.miuix.hyperschedule.ui.component.formatWeeks
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Background
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Location
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Weeks
import top.yukonga.miuix.kmp.icon.extended.WorldClock
import top.yukonga.miuix.kmp.icon.extended.Years
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun GroupCard(
    group: GroupDraft,
    canDelete: Boolean,
    onChange: (GroupDraft) -> Unit,
    onPickDay: () -> Unit,
    onPickTime: () -> Unit,
    onPickWeeks: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(SectionSpacing)) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                InfoRow(icon = MiuixIcons.Location, label = "地点") {
                    InlineField(
                        value = group.position,
                        onValueChange = { onChange(group.copy(position = it)) },
                        hint = "选填",
                    )
                }
                InfoRow(icon = MiuixIcons.Contacts, label = "教师") {
                    InlineField(
                        value = group.teacher,
                        onValueChange = { onChange(group.copy(teacher = it)) },
                        hint = "选填",
                    )
                }
                ThinDivider()
                InfoRow(icon = MiuixIcons.Years, label = "上课周次", onClick = onPickWeeks) {
                    if (group.weeks.isEmpty()) {
                        ValueText("必填", placeholder = true)
                    } else {
                        ValueText(formatWeeks(group.weeks.toList()))
                    }
                }
                InfoRow(icon = MiuixIcons.Weeks, label = "上课星期", onClick = onPickDay) {
                    ValueText(dayLabel(group.day))
                }
                InfoRow(icon = MiuixIcons.WorldClock, label = "上课时间", onClick = onPickTime) {
                    val text = timeText(group)
                    if (text.isEmpty()) ValueText("必填", placeholder = true) else ValueText(text)
                }
                ThinDivider()
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(start = PageHorizontalPadding, end = PageHorizontalPadding, top = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FieldIcon(MiuixIcons.Notes)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "备注",
                        style = MiuixTheme.textStyles.headline1,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "${group.remark.length} / 300",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                RemarkField(
                    value = group.remark,
                    onValueChange = { onChange(group.copy(remark = it.take(300))) },
                    modifier = Modifier.fillMaxWidth().padding(start = 44.dp, end = 16.dp, top = 8.dp, bottom = 17.dp),
                )
            }
        }
        if (canDelete) {
            WideActionButton(
                text = "删除",
                icon = MiuixIcons.Delete,
                onClick = onDelete,
                contentColor = MiuixTheme.colorScheme.error,
            )
        }
    }
}

/** 一行 = 左侧图标 + 标签 + 右侧内容（内容区整体右对齐）；给了 [onClick] 时整行可点 */
@Composable
internal fun InfoRow(
    icon: ImageVector,
    label: String,
    top: Dp = 14.dp,
    bottom: Dp = 14.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
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
                )
                .padding(start = PageHorizontalPadding, end = PageHorizontalPadding, top = top, bottom = bottom),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FieldIcon(icon)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MiuixTheme.textStyles.headline1,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            content()
        }
    }
}

/** 字段左侧的小图标 */
@Composable
internal fun FieldIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(20.dp),
        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

@Composable
internal fun ValueText(text: String, placeholder: Boolean = false) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth(),
        style = MiuixTheme.textStyles.headline1,
        fontWeight = FontWeight.Medium,
        color =
            if (placeholder) {
                MiuixTheme.colorScheme.onSurfaceVariantSummary
            } else {
                MiuixTheme.colorScheme.onSurface
            },
        textAlign = TextAlign.End,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** 行内输入框：无底框，右对齐；空值显示灰色提示，有值用正文色 */
@Composable
internal fun InlineField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        textStyle =
            MiuixTheme.textStyles.headline1.copy(
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
            ),
        singleLine = true,
        keyboardOptions = keyboardOptions,
        cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.CenterEnd) {
                if (value.isEmpty()) {
                    ValueText(hint, placeholder = true)
                }
                innerTextField()
            }
        },
    )
}

@Composable
internal fun RemarkField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        textStyle = MiuixTheme.textStyles.body1.copy(color = MiuixTheme.colorScheme.onSurface),
        minLines = 2,
        maxLines = 4,
        cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            Box {
                if (value.isEmpty()) {
                    Text(
                        text = "选填",
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                innerTextField()
            }
        },
    )
}

@Composable
internal fun ThinDivider() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = PageHorizontalPadding)
                .height(0.5.dp)
                .background(MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.07f)),
    )
}

@Composable
internal fun ColorCard(
    colorInt: Int?,
    colorArgb: Int?,
    isDark: Boolean,
    onPick: (index: Int, argb: Int?) -> Unit,
    onPickCustom: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(top = 14.dp)) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = PageHorizontalPadding, end = PageHorizontalPadding, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FieldIcon(MiuixIcons.Background)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "课程颜色",
                style = MiuixTheme.textStyles.headline1,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
            )
        }
        val totalItems = CoursePalette.size + 1
        val rowsCount = (totalItems + PaletteColumns - 1) / PaletteColumns
        for (row in 0 until rowsCount) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = PaletteEdge, end = PaletteEdge, bottom = PaletteRowSpacing),
                horizontalArrangement = Arrangement.spacedBy(PaletteCellSpacing),
            ) {
                for (col in 0 until PaletteColumns) {
                    val index = row * PaletteColumns + col
                    if (index < CoursePalette.size) {
                        ColorCell(
                            color = CoursePalette[index],
                            selected = colorArgb == null && colorInt == index,
                            isDark = isDark,
                            onClick = { onPick(index, null) },
                            modifier = Modifier.weight(1f),
                        )
                    } else if (index == CoursePalette.size) {
                        ColorCell(
                            color = if (colorArgb != null) Color(colorArgb) else null,
                            selected = colorArgb != null,
                            isDark = isDark,
                            onClick = onPickCustom,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** 色板一行放几个；13 个色 + 1 个自定义正好两整行 */
private const val PaletteColumns = 7
private val PaletteEdge = 8.dp
private val PaletteCellSpacing = 6.dp
private val PaletteRowSpacing = 8.dp

/** 颜色格之间再让出一点，格子才不会顶满 */
private val CellInset = 2.5.dp

/** 颜色格选中描边宽度；色块向内让出的距离与圆角收缩量都用它，保证贴合 */
private val BorderWidth = 2.dp

/** 颜色格外轮廓圆角；色块圆角取 外轮廓圆角 - 描边宽，两者同心 */
private val CellCornerRadius = 10.dp

/** 颜色格：选中时外圈主题色描边，内部填淡化的课程色 */
@Composable
internal fun ColorCell(
    color: Color?,
    selected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderAlpha = if (selected) 1f else 0f
    Box(
        modifier =
            modifier
                .padding(CellInset)
                .aspectRatio(1f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .squircleBorder(
                        width = BorderWidth,
                        color = MiuixTheme.colorScheme.primary.copy(alpha = borderAlpha),
                        cornerRadius = CellCornerRadius,
                    )
                    .padding(BorderWidth)
                    .squircleClip(CellCornerRadius - BorderWidth)
                    .background(
                        when {
                            color != null -> color.copy(alpha = if (isDark) 0.40f else 0.16f)
                            isDark -> MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                            else -> MiuixTheme.colorScheme.surface
                        }
                    ),
            contentAlignment = Alignment.Center,
        ) {
            if (color == null) {
                Icon(
                    imageVector = MiuixIcons.Add,
                    contentDescription = "自定义颜色",
                    modifier = Modifier.size(16.dp),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}
