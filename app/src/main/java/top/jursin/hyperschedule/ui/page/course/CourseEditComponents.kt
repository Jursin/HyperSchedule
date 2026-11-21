package top.jursin.hyperschedule.ui.page.course

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.jursin.hyperschedule.R
import top.jursin.hyperschedule.data.db.entity.Course
import top.jursin.hyperschedule.theme.CardCornerRadius
import top.jursin.hyperschedule.theme.CardInsidePadding
import top.jursin.hyperschedule.theme.SectionSpacing
import top.jursin.hyperschedule.theme.courseColorAt
import top.jursin.hyperschedule.theme.rememberCoursePalette
import top.jursin.hyperschedule.theme.subtleFillColor
import top.jursin.hyperschedule.ui.component.WideActionButton
import top.jursin.hyperschedule.ui.component.dayLabel
import top.jursin.hyperschedule.ui.component.formatWeeks
import top.jursin.hyperschedule.ui.component.maxLengthInput
import top.jursin.hyperschedule.ui.component.rememberSyncedInput
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Add
import top.yukonga.miuix.kmp.icon.glass.Background
import top.yukonga.miuix.kmp.icon.glass.ClockOutline
import top.yukonga.miuix.kmp.icon.glass.Contacts
import top.yukonga.miuix.kmp.icon.glass.Delete
import top.yukonga.miuix.kmp.icon.glass.File
import top.yukonga.miuix.kmp.icon.glass.Weeks
import top.yukonga.miuix.kmp.icon.glass.Years
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 备注长度上限 */
private const val RemarkMaxLength = 300

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
        Card(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = CardCornerRadius,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                InfoRow(icon = MiuixIcons.Glass.Years, label = "上课周次", iconSize = 22.dp, onClick = onPickWeeks) {
                    if (group.weeks.isEmpty()) {
                        ValueText("必填", placeholder = true)
                    } else {
                        ValueText(formatWeeks(group.weeks.toList()))
                    }
                }
                InfoRow(icon = MiuixIcons.Glass.Weeks, label = "上课星期", iconSize = 22.dp, onClick = onPickDay) {
                    ValueText(dayLabel(group.day))
                }
                InfoRow(icon = MiuixIcons.Glass.ClockOutline, label = "上课时间", iconSize = 22.dp, onClick = onPickTime) {
                    val text = timeText(group)
                    if (text.isEmpty()) ValueText("必填", placeholder = true) else ValueText(text)
                }
                ThinDivider()
                InfoRow(icon = ImageVector.vectorResource(R.drawable.ic_location_on), label = "地点") {
                    InlineField(
                        value = group.position,
                        onValueChange = { onChange(group.copy(position = it)) },
                        hint = "选填",
                    )
                }
                InfoRow(icon = MiuixIcons.Glass.Contacts, label = "教师", iconSize = 22.dp) {
                    InlineField(
                        value = group.teacher,
                        onValueChange = { onChange(group.copy(teacher = it)) },
                        hint = "选填",
                    )
                }
                ThinDivider()
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(start = CardInsidePadding, end = CardInsidePadding, top = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FieldIcon(MiuixIcons.Glass.File, 22.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "备注",
                        style = MiuixTheme.textStyles.headline1,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "${group.remark.length} / $RemarkMaxLength",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                RemarkField(
                    value = group.remark,
                    onValueChange = { onChange(group.copy(remark = it)) },
                    modifier = Modifier.fillMaxWidth().padding(start = 44.dp, end = 16.dp, top = 8.dp, bottom = 17.dp),
                )
            }
        }
        if (canDelete) {
            WideActionButton(
                text = "删除",
                icon = MiuixIcons.Glass.Delete,
                onClick = onDelete,
                contentColor = MiuixTheme.colorScheme.error,
            )
        }
    }
}

/** 一行 = 左侧图标 + 标签 + 右侧内容（内容区整体右对齐），给了 [onClick] 时整行可点 */
@Composable
internal fun InfoRow(
    icon: ImageVector,
    label: String,
    top: Dp = 14.dp,
    bottom: Dp = 14.dp,
    iconSize: Dp = 20.dp,
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
                .padding(start = CardInsidePadding, end = CardInsidePadding, top = top, bottom = bottom),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FieldIcon(icon, iconSize)
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
internal fun FieldIcon(icon: ImageVector, size: Dp = 20.dp) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(size),
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

/** 行内输入框，无底框，右对齐，空值显示灰色提示，有值用正文色 */
@Composable
internal fun InlineField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val state = rememberSyncedInput(value, onValueChange)
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        if (state.text.isEmpty()) ValueText(hint, placeholder = true)
        BasicTextField(
            state = state,
            modifier = Modifier.fillMaxWidth(),
            textStyle =
                MiuixTheme.textStyles.headline1.copy(
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    textAlign = TextAlign.End,
                ),
            keyboardOptions = keyboardOptions,
            lineLimits = TextFieldLineLimits.SingleLine,
            cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
        )
    }
}

@Composable
internal fun RemarkField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberSyncedInput(value, onValueChange)
    Box(modifier = modifier) {
        if (state.text.isEmpty()) {
            Text(
                text = "选填",
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        BasicTextField(
            state = state,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MiuixTheme.textStyles.body1.copy(color = MiuixTheme.colorScheme.onSurface),
            lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 2, maxHeightInLines = 4),
            inputTransformation = maxLengthInput(RemarkMaxLength),
            cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
        )
    }
}

@Composable
internal fun ThinDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = CardInsidePadding),
        thickness = 0.5.dp,
        color = MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.07f),
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
    val palette = rememberCoursePalette(isDark)
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = CardCornerRadius,
        insideMargin = PaddingValues(top = 14.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = CardInsidePadding, end = CardInsidePadding, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FieldIcon(MiuixIcons.Glass.Medium.Background, 22.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "课程颜色",
                style = MiuixTheme.textStyles.headline1,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
            )
        }
        val totalItems = Course.COLOR_COUNT + 1
        val rowsCount = (totalItems + PaletteColumns - 1) / PaletteColumns
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            // 宽屏下靠格子内边距把色块压在上限内，格子仍均匀铺满
            val slot =
                (maxWidth - PaletteEdge * 2 - PaletteCellSpacing * (PaletteColumns - 1)) / PaletteColumns
            val inset = ((slot - 46.dp) / 2).coerceAtLeast(CellInset)
            Column {
                for (row in 0 until rowsCount) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(start = PaletteEdge, end = PaletteEdge, bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(PaletteCellSpacing),
                    ) {
                        for (col in 0 until PaletteColumns) {
                            val index = row * PaletteColumns + col
                            if (index < Course.COLOR_COUNT) {
                                ColorCell(
                                    color = courseColorAt(index, palette),
                                    selected = colorArgb == null && colorInt == index,
                                    isDark = isDark,
                                    inset = inset,
                                    onClick = { onPick(index, null) },
                                    modifier = Modifier.weight(1f),
                                )
                            } else if (index == Course.COLOR_COUNT) {
                                ColorCell(
                                    color = if (colorArgb != null) Color(colorArgb) else null,
                                    selected = colorArgb != null,
                                    isDark = isDark,
                                    inset = inset,
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
    }
}

/** 色板一行放几个，13 个色 + 1 个自定义正好两整行 */
private const val PaletteColumns = 7
private val PaletteEdge = 8.dp
private val PaletteCellSpacing = 6.dp

/** 颜色格之间再让出一点，格子才不会顶满 */
private val CellInset = 2.5.dp

/** 颜色格选中描边宽度 */
private val BorderWidth = 2.dp

/** 颜色格外轮廓圆角，色块圆角取外轮廓圆角减去描边宽与间隙，两者同心 */
private val CellCornerRadius = 10.dp

/** 颜色格，内部填基础色，选中时外圈主题色描边 */
@Composable
internal fun ColorCell(
    color: Color?,
    selected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    inset: Dp = CellInset,
) {
    val borderAlpha = if (selected) 1f else 0f
    val ringSpace = BorderWidth + 2.dp
    Box(
        modifier =
            modifier
                .padding(inset)
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
                    .padding(ringSpace)
                    .squircleClip(CellCornerRadius - ringSpace)
                    .background(
                        when {
                            color != null -> color
                            isDark -> subtleFillColor()
                            else -> MiuixTheme.colorScheme.surface
                        }
                    ),
            contentAlignment = Alignment.Center,
        ) {
            if (color == null) {
                Icon(
                    imageVector = MiuixIcons.Glass.Add,
                    contentDescription = "自定义颜色",
                    modifier = Modifier.size(16.dp),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}
