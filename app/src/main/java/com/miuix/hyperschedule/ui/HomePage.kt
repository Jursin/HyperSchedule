package com.miuix.hyperschedule.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import com.miuix.hyperschedule.ui.component.BlurredTopAppBar
import com.miuix.hyperschedule.ui.component.PageScrollColumn
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold

@androidx.compose.runtime.Composable
fun HomePage() {
    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(topBar = { BlurredTopAppBar("首页", null, scrollBehavior) }) { paddingValues ->
        PageScrollColumn(
            blurBackdrop = null,
            scrollBehavior = scrollBehavior,
            contentPadding = paddingValues,
            bottomPadding = 80.dp,
        ) {
            Spacer(modifier = androidx.compose.ui.Modifier.height(800.dp))
        }
    }
}
