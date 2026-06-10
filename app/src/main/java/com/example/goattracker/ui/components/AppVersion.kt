package com.example.goattracker.ui.components

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Build identity for display, e.g. `dev-1.0` or `release-1.0.1` — `<channel>-<versionName>`.
 *
 * The **channel** comes from the debuggable flag: the dev build (GoatTrackerDev) is debuggable, the
 * release/prod build is not — this is what distinguishes the two side-by-side installs. The
 * **versionName** is read from the PackageManager (the module has buildConfig disabled, so there is
 * no BuildConfig.VERSION_NAME). The self-update still compares versionCode internally; it just isn't
 * shown here.
 */
fun appVersionLabel(context: Context): String {
    val versionName = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    val channel = if (isDebuggable(context)) "dev" else "release"
    return "$channel-$versionName"
}

/**
 * `true` on the dev install (GoatTrackerDev, debuggable / `.dev` suffix). The self-update + CI
 * release concept only applies to the prod channel: dev builds always run whatever Android Studio
 * deploys, so the update check is skipped entirely for them (a "new version available" dialog on
 * the dev app would be misleading — it points at the prod APK).
 */
fun isDevBuild(context: Context): Boolean = isDebuggable(context)

private fun isDebuggable(context: Context): Boolean =
    (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

@Composable
fun rememberAppVersionLabel(): String {
    val context = LocalContext.current
    return remember { appVersionLabel(context) }
}
