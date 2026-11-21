package com.hyperschedule.app.ui.component

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.placeCursorAtEnd
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow

/** 超过 [max] 个字就截断 */
internal fun maxLengthInput(max: Int): InputTransformation =
    InputTransformation {
        if (length > max) {
            replace(0, length, asCharSequence().take(max).toString())
            placeCursorAtEnd()
        }
    }

/** 密码遮罩，逐字符替换，保持光标偏移映射不变 */
internal val PasswordMask: OutputTransformation =
    OutputTransformation {
        for (index in length - 1 downTo 0) {
            replace(index, index + 1, "•")
        }
    }

/** 以 [value] 为初值、外部改写时同步进来的输入态，给了 [onChange] 就把编辑结果写回外部值 */
@Composable
internal fun rememberSyncedInput(
    value: String,
    onChange: ((String) -> Unit)? = null,
): TextFieldState {
    val state = rememberTextFieldState(value)
    val pushed = remember { mutableStateOf(value) }
    if (onChange != null) {
        val current = rememberUpdatedState(onChange)
        LaunchedEffect(state) {
            snapshotFlow { state.text.toString() }.collect {
                pushed.value = it
                current.value(it)
            }
        }
    }
    // 比字符串内容而不是比对象，输入过程中的回写不会把光标顶到末尾
    LaunchedEffect(value) {
        if (value != pushed.value && state.text.toString() != value) {
            state.setTextAndPlaceCursorAtEnd(value)
            pushed.value = value
        }
    }
    return state
}
