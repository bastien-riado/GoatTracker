package com.example.goattracker.update

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
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

/** Outcome of a user-triggered check (the silent startup check uses [UpdateUiState] only). */
enum class ManualCheckState { Idle, Checking, UpToDate, Failed }

/**
 * Owns the update flow for the lifetime of the Activity. It is resolved against the Activity's
 * ViewModelStore (UpdateGate at the top level, Settings via the activity owner) so a manual check from
 * Settings drives the same dialog. Reads its own versionCode from the PackageManager (buildConfig is
 * disabled). The silent check is once-per-process and skipped on debug; everything fails silently.
 */
class UpdateViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val repository = UpdateRepository(
        currentVersionCode = currentVersionCode(app),
        cacheDir = app.cacheDir,
    )

    private val _uiState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    /** Latest release metadata (for the patch-notes screen); populated by any successful check. */
    private val _latestRelease = MutableStateFlow<ReleaseInfo?>(null)
    val latestRelease: StateFlow<ReleaseInfo?> = _latestRelease.asStateFlow()

    private val _manualCheckState = MutableStateFlow(ManualCheckState.Idle)
    val manualCheckState: StateFlow<ManualCheckState> = _manualCheckState.asStateFlow()

    private val isDebuggableBuild =
        (app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    private var hasChecked = false

    /**
     * @param force a user-initiated check (Settings button): runs even on debug builds and even for a
     *   snoozed version, and reports the result via [manualCheckState]. The silent startup check
     *   (force=false) runs at most once per process and is skipped on debuggable builds.
     */
    fun checkForUpdate(force: Boolean = false) {
        if (!force) {
            if (hasChecked) return
            hasChecked = true
            if (isDebuggableBuild) return
        }
        viewModelScope.launch {
            if (force) _manualCheckState.value = ManualCheckState.Checking
            when (val result = repository.checkForUpdate()) {
                is UpdateCheckResult.Available -> {
                    _latestRelease.value = result.info
                    if (force || result.info.versionCode != snoozedVersion()) {
                        _uiState.value = UpdateUiState.Available(result.info)
                    }
                    if (force) _manualCheckState.value = ManualCheckState.Idle
                }
                is UpdateCheckResult.UpToDate -> {
                    _latestRelease.value = result.info
                    if (force) _manualCheckState.value = ManualCheckState.UpToDate
                }
                is UpdateCheckResult.Failed -> {
                    if (force) _manualCheckState.value = ManualCheckState.Failed
                }
            }
        }
    }

    /** Loads latest release metadata for display (patch notes) WITHOUT surfacing the update dialog. */
    fun loadReleaseNotesIfNeeded() {
        if (_latestRelease.value != null) return
        viewModelScope.launch {
            when (val result = repository.checkForUpdate()) {
                is UpdateCheckResult.Available -> _latestRelease.value = result.info
                is UpdateCheckResult.UpToDate -> _latestRelease.value = result.info
                is UpdateCheckResult.Failed -> Unit
            }
        }
    }

    fun resetManualCheckState() {
        _manualCheckState.value = ManualCheckState.Idle
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
