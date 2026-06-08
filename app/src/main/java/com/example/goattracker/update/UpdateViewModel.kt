package com.example.goattracker.update

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/** UI state for the self-update flow. Idle and ReadyToInstall render no dialog (installer takes over). */
sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data class Available(val info: ReleaseInfo) : UpdateUiState
    data class Downloading(val progress: Float) : UpdateUiState
    data class ReadyToInstall(val apk: File, val info: ReleaseInfo) : UpdateUiState
}

/**
 * Owns the update flow for the lifetime of the Activity. Reads its own versionCode from the
 * PackageManager (the module has buildConfig disabled), checks once per process, and stays completely
 * silent on "up to date" or any failure — only a genuinely-newer, non-snoozed version surfaces a dialog.
 */
class UpdateViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val repository = UpdateRepository(
        currentVersionCode = currentVersionCode(app),
        cacheDir = app.cacheDir,
    )

    private val _uiState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    private var hasChecked = false

    /** Silent check; safe to call on every composition (runs at most once per process). */
    fun checkForUpdate() {
        if (hasChecked) return
        hasChecked = true
        viewModelScope.launch {
            val result = repository.checkForUpdate()
            if (result is UpdateCheckResult.Available && result.info.versionCode != snoozedVersion()) {
                _uiState.value = UpdateUiState.Available(result.info)
            }
            // UpToDate / Failed / snoozed -> stay Idle, no UI (offline must be invisible).
        }
    }

    /** "Plus tard": remember this version so it won't nag again, but a NEWER one still will. */
    fun dismiss() {
        (_uiState.value as? UpdateUiState.Available)?.let { snooze(it.info.versionCode) }
        _uiState.value = UpdateUiState.Idle
    }

    /** "Mettre à jour": download (with progress) then expose the APK for the installer. */
    fun downloadAndPrepare() {
        val info = (_uiState.value as? UpdateUiState.Available)?.info ?: return
        viewModelScope.launch {
            _uiState.value = UpdateUiState.Downloading(0f)
            runCatching {
                repository.downloadApk(info) { progress ->
                    _uiState.value = UpdateUiState.Downloading(progress)
                }
            }.onSuccess { apk ->
                _uiState.value = UpdateUiState.ReadyToInstall(apk, info)
            }.onFailure {
                _uiState.value = UpdateUiState.Idle // download/checksum failure -> abort silently
            }
        }
    }

    /** Called once the installer (or the unknown-sources round-trip) has been launched. */
    fun onInstallLaunched() {
        _uiState.value = UpdateUiState.Idle
    }

    private fun snooze(versionCode: Long) = prefs.edit().putLong(KEY_SNOOZED, versionCode).apply()
    private fun snoozedVersion(): Long = prefs.getLong(KEY_SNOOZED, -1L)

    companion object {
        private const val PREFS = "update_prefs"
        private const val KEY_SNOOZED = "snoozed_version_code"

        private fun currentVersionCode(context: Context): Long {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION") info.versionCode.toLong()
            }
        }
    }
}
