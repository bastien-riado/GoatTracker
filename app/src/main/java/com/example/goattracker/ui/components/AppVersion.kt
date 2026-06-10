package com.example.goattracker.ui.components

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Build identity for display, e.g. `dev 1.0-dev (1)` or `release 1.0.1 (2)`.
 *
 * - **channel** comes from the debuggable flag: the dev build (GoatTrackerDev) is debuggable, the
 *   release/prod build is not — this is what distinguishes the two side-by-side installs.
 * - **versionName / versionCode** are read from the PackageManager (the module has buildConfig
 *   disabled, so there is no BuildConfig.VERSION_*). versionCode is the value the self-update compares.
 */
fun appVersionLabel(context: Context): String {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    val versionName = info.versionName ?: "?"
    val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        info.longVersionCode
    } else {
        @Suppress("DEPRECATION") info.versionCode.toLong()
    }
    val channel = if (isDebuggable(context)) "dev" else "release"
    return "$channel $versionName ($versionCode)"
}

private fun isDebuggable(context: Context): Boolean =
    (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

@Composable
fun rememberAppVersionLabel(): String {
    val context = LocalContext.current
    return remember { appVersionLabel(context) }
}
