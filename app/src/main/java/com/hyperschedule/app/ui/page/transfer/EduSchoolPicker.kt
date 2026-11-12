package com.hyperschedule.app.ui.page.transfer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperschedule.app.R
import com.hyperschedule.app.data.importer.edu.Adapter
import com.hyperschedule.app.data.importer.edu.School as EduSchool
import com.hyperschedule.app.theme.CardCornerRadius
import com.hyperschedule.app.theme.SheetMaxHeight
import com.hyperschedule.app.theme.sheetCardColor
import com.hyperschedule.app.ui.component.ConfirmDialog
import com.hyperschedule.app.ui.component.SheetBottomSpacer
import com.hyperschedule.app.ui.component.SheetScaffold
import com.hyperschedule.app.ui.component.SheetScrollColumn
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val IndexLetters = ('A'..'Z').map { it.toString() }

/** 通用工具进入前填网址，留空禁用确定 */
@Composable
internal fun UrlInputDialog(
    appBlur: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var url by remember { mutableStateOf("") }
    ConfirmDialog(
        title = "输入网址",
        message = "请输入要访问的学校教务系统网址",
        appBlur = appBlur,
        confirmEnabled = url.isNotBlank(),
        onDismiss = onDismiss,
        onConfirm = { onConfirm(url.trim()) },
    ) {
        TextField(
            value = url,
            onValueChange = { url = it },
            label = "网址",
            useLabelAsPlaceholder = true,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 适配器抽屉，点一个适配器进入网页导入 */
@Composable
internal fun AdapterSheet(
    school: EduSchool,
    show: Boolean,
    onOpen: (Adapter) -> Unit,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
) {
    val adapters = school.adapters.filter { it.id !in ShareToolIds }

    SheetScaffold(
        title = school.name,
        titleIcon = ImageVector.vectorResource(R.drawable.ic_school),
        show = show,
        onDismiss = onDismiss,
        onDismissFinished = onDismissFinished,
    ) {
        if (adapters.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 56.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "该学校暂无可用方案",
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                )
            }
        } else {
            SheetScrollColumn(maxHeight = SheetMaxHeight) {
                adapters.forEach { adapter -> AdapterCard(adapter) { onOpen(adapter) } }
                SheetBottomSpacer()
            }
        }
    }
}

/** 适配器卡片，名称、分类、描述与贡献者 */
@Composable
internal fun AdapterCard(adapter: Adapter, onClick: () -> Unit) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(CardCornerRadius))
                .clickable(onClick = onClick),
        cornerRadius = CardCornerRadius,
        insideMargin = PaddingValues(0.dp),
        colors =
            CardDefaults.defaultColors(
                color = sheetCardColor(),
                contentColor = MiuixTheme.colorScheme.onSurface,
            ),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = adapter.name,
                    style = MiuixTheme.textStyles.headline1,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = adapter.categoryLabel(),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = adapter.description.ifBlank { "暂无描述" },
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
            if (adapter.maintainer.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.ic_person),
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "贡献者：${adapter.maintainer}",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
    }
}

private fun Adapter.categoryLabel(): String =
    when (category) {
        Adapter.BACHELOR -> "本科"
        Adapter.POSTGRADUATE -> "研究生"
        Adapter.TOOL -> "通用工具"
        else -> "其他"
    }

@Composable
internal fun SchoolItem(
    school: EduSchool,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Text(
        text = school.name,
        style = MiuixTheme.textStyles.body1,
        fontWeight = FontWeight.Medium,
        color = MiuixTheme.colorScheme.onSurface,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(CardCornerRadius))
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    onClick()
                }
                .padding(horizontal = 26.dp, vertical = 20.dp),
    )
}

/** 右侧 A–Z 索引条，可点可拖 */
@Composable
internal fun AlphabetBar(
    available: Set<String>,
    active: String?,
    onJump: (letter: String, animate: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    var dragging by remember { mutableStateOf<String?>(null) }

    fun pick(
        y: Float,
        height: Int,
        drag: Boolean,
    ) {
        if (height <= 0) return
        val letter = IndexLetters[((y / height) * IndexLetters.size).toInt().coerceIn(0, IndexLetters.lastIndex)]
        if (letter !in available || (drag && letter == dragging)) return
        if (drag) dragging = letter
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onJump(letter, !drag)
    }

    Column(
        modifier =
            modifier.pointerInput(available) {
                detectVerticalDragGestures(
                    onDragStart = { pick(it.y, size.height, drag = true) },
                    onDragEnd = { dragging = null },
                    onDragCancel = { dragging = null },
                ) { change, _ ->
                    change.consume()
                    pick(change.position.y, size.height, drag = true)
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IndexLetters.forEach { letter ->
            val enabled = letter in available
            val selected = enabled && letter == (dragging ?: active)
            Text(
                text = letter,
                fontSize = 10.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color =
                    when {
                        selected -> MiuixTheme.colorScheme.primary
                        enabled -> MiuixTheme.colorScheme.onSurfaceVariantActions
                        else -> MiuixTheme.colorScheme.outline
                    },
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .then(
                            if (enabled) {
                                Modifier.clickable(interactionSource = null, indication = null) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onJump(letter, false)
                                }
                            } else {
                                Modifier
                            },
                        ),
            )
        }
    }
}

/** 居中的提示，用于加载中与空列表 */
@Composable
internal fun LoadingHint(
    text: String,
    showProgress: Boolean = false,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (showProgress) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
            }
            Text(
                text = text,
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
            )
        }
    }
}
