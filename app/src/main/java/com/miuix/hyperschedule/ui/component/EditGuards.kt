package com.miuix.hyperschedule.ui.component

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/** 短提示 */
fun Context.toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

/** 编辑页返回守卫：有未保存改动时先确认放弃，返回顶栏与返回键共用的回调 */
@Composable
fun rememberEditBackGuard(dirty: Boolean, appBlur: Boolean, onBack: () -> Unit): () -> Unit {
    var discardDialog by remember { mutableStateOf(false) }
    val requestBack: () -> Unit = {
        if (dirty) discardDialog = true else onBack()
    }
    BackHandler(enabled = dirty) { discardDialog = true }
    if (discardDialog) {
        DiscardChangesDialog(
            appBlur = appBlur,
            onDismiss = { discardDialog = false },
            onDiscard = {
                discardDialog = false
                onBack()
            },
        )
    }
    return requestBack
}
