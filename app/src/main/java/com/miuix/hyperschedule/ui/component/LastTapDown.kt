package com.miuix.hyperschedule.ui.component

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset

val LocalLastTapDown = staticCompositionLocalOf<MutableState<Offset?>> { mutableStateOf(null) }
