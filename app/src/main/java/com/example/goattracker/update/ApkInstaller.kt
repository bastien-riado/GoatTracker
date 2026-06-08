package com.example.goattracker.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/**
 * Hands a downloaded APK to the system package installer. Distribution is off-store, so the app needs
 * the user's "install unknown apps" permission (per-app since API 26) and must expose the file as a
 * content:// URI via [FileProvider] (file:// is illegal since API 24).
 */
class ApkInstaller(private val context: Context) {

    private val authority = "${context.packageName}.fileprovider"

    /** API 26+: the user must have granted this app permission to install packages. Pre-O: always true
     *  (the global "Unknown sources" toggle governs it and the installer itself prompts). */
    fun canInstall(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    /** Launches the system installer for [apk] via a temporarily-granted content URI. */
    fun install(apk: File) {
        val uri: Uri = FileProvider.getUriForFile(context, authority, apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** Settings screen to enable "install unknown apps" for THIS app (API 26+). */
    fun unknownSourcesSettingsIntent(): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        )
}
