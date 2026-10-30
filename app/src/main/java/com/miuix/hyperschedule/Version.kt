package com.miuix.hyperschedule

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat

fun getAppVersion(context: Context): String =
    runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
    }.getOrDefault("unknown")

fun getAppVersionCode(context: Context): String =
    runCatching {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        PackageInfoCompat.getLongVersionCode(packageInfo).toString()
    }.getOrDefault("unknown")
