package com.example.goattracker.update

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.goattracker.ui.components.isDevBuild

/**
 * Drop-in entry point for the self-update feature. Render once near the top of the Activity's content;
 * it triggers the silent check, shows the dialog when (and only when) a newer version exists, and drives
 * the download -> install handoff including the "install unknown apps" permission round-trip.
 */
@Composable
fun UpdateGate() {
    val context = LocalContext.current
    // Self-update is a prod-channel concept: the dev app (GoatTrackerDev) runs whatever the IDE
    // deploys, so never check/prompt there (the release it would find is the prod APK).
    if (isDevBuild(context)) return
    val vm: UpdateViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val installer = remember { ApkInstaller(context) }

    // Returning from the "install unknown apps" settings screen: install if now permitted, then clear
    // state (re-prompts on next launch if the user installed nothing — the version wasn't snoozed).
    val unknownSourcesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        val apk = (vm.uiState.value as? UpdateUiState.ReadyToInstall)?.apk
        if (apk != null && installer.canInstall()) installer.install(apk)
        vm.onInstallLaunched()
    }

    LaunchedEffect(Unit) { vm.checkForUpdate() }

    // When the APK is ready, install it — or first send the user to grant the install permission.
    val readyApk = (state as? UpdateUiState.ReadyToInstall)?.apk
    LaunchedEffect(readyApk) {
        if (readyApk != null) {
            if (installer.canInstall()) {
                installer.install(readyApk)
                vm.onInstallLaunched()
            } else {
                unknownSourcesLauncher.launch(installer.unknownSourcesSettingsIntent())
            }
        }
    }

    UpdateDialog(
        state = state,
        onUpdate = vm::downloadAndPrepare,
        onDismiss = vm::dismiss,
    )
}

@Composable
private fun UpdateDialog(
    state: UpdateUiState,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        is UpdateUiState.Available -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Mise à jour disponible") },
            text = { Text("La version ${state.info.versionName} est disponible.") },
            confirmButton = { TextButton(onClick = onUpdate) { Text("Mettre à jour") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Plus tard") } },
        )

        is UpdateUiState.Downloading -> AlertDialog(
            // Non-cancellable while the download runs.
            onDismissRequest = {},
            title = { Text("Téléchargement…") },
            text = {
                Column {
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("${(state.progress * 100).toInt()} %")
                }
            },
            confirmButton = {},
        )

        UpdateUiState.Idle, is UpdateUiState.ReadyToInstall -> Unit
    }
}
