package com.example.goattracker.ui.settings

import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.goattracker.data.local.RoomDataRepository
import com.example.goattracker.domain.MetricFormatter
import com.example.goattracker.domain.model.BodyWeightSource
import com.example.goattracker.domain.model.WeightUnit
import com.example.goattracker.health.BodyWeightSyncer
import com.example.goattracker.health.HealthConnectWeightProvider
import com.example.goattracker.theme.*
import com.example.goattracker.ui.components.AppTextField
import com.example.goattracker.ui.components.isDevBuild
import com.example.goattracker.ui.components.rememberAppVersionLabel
import com.example.goattracker.update.ManualCheckState
import com.example.goattracker.update.UpdateViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onPatchNotesClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Resolve the Activity-scoped UpdateViewModel (same instance UpdateGate uses) so a manual check
    // here surfaces the shared update dialog.
    val activity = LocalContext.current as ComponentActivity
    val updateVm: UpdateViewModel = viewModel(viewModelStoreOwner = activity)
    val manualState by updateVm.manualCheckState.collectAsStateWithLifecycle()
    val versionLabel = rememberAppVersionLabel()

    val context = LocalContext.current
    val settingsVm: SettingsViewModel = viewModel {
        val repository = RoomDataRepository.getInstance(context)
        SettingsViewModel(
            dataRepository = repository,
            syncer = BodyWeightSyncer(HealthConnectWeightProvider(context.applicationContext), repository),
        )
    }
    val profile by settingsVm.userProfile.collectAsStateWithLifecycle()
    val hcStatus by settingsVm.healthConnectStatus.collectAsStateWithLifecycle()

    val isHealthConnectSupported = remember { HealthConnectWeightProvider.isSupported(context) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        settingsVm.onHealthConnectPermissionResult(
            granted.containsAll(HealthConnectWeightProvider.PERMISSIONS)
        )
    }

    var isWeightDialogOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Paramètres",
                        color = Fg,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(SurfaceElevated)
                            .border(1.dp, Border, CircleShape),
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour", tint = Fg)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Surface,
                    titleContentColor = Fg,
                ),
            )
        },
        containerColor = Bg,
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Profil : poids corporel + unité + Health Connect
            SettingsCard {
                Text(
                    "PROFIL",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                )
                Spacer(Modifier.height(12.dp))

                // -- Poids corporel (saisie manuelle) --
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { isWeightDialogOpen = true }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = Accent)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Poids corporel", color = Fg, style = MaterialTheme.typography.bodyMedium)
                        val subtitle = profile.bodyWeightKg?.let { _ ->
                            val date = profile.bodyWeightUpdatedAt?.let {
                                SimpleDateFormat("d MMM yyyy", Locale.FRENCH).format(Date(it))
                            }
                            val source = when (profile.bodyWeightSource) {
                                BodyWeightSource.HEALTH_CONNECT -> "Santé Connect"
                                BodyWeightSource.MANUAL -> "Saisie manuelle"
                            }
                            listOfNotNull(source, date).joinToString(" • ")
                        } ?: "Utilisé pour le volume des exercices au poids de corps"
                        Text(subtitle, color = Muted, fontSize = 11.sp)
                    }
                    Text(
                        text = profile.bodyWeightKg?.let { MetricFormatter.weight(it, profile.weightUnit) } ?: "À définir",
                        color = if (profile.bodyWeightKg != null) AccentSecondary else Muted,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    )
                }

                Spacer(Modifier.height(10.dp))

                // -- Unité de poids --
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Unité de poids",
                        color = Fg,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        WeightUnit.entries.forEach { unit ->
                            val isSelected = profile.weightUnit == unit
                            Text(
                                text = unit.suffix,
                                color = if (isSelected) Accent else Muted,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Accent.copy(alpha = 0.15f) else Surface)
                                    .border(
                                        1.dp,
                                        if (isSelected) Accent else Border,
                                        RoundedCornerShape(8.dp),
                                    )
                                    .clickable { settingsVm.setWeightUnit(unit) }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // -- Health Connect --
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Default.Favorite, contentDescription = null, tint = Accent)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Synchroniser via Santé Connect", color = Fg, style = MaterialTheme.typography.bodyMedium)
                        val hint = when {
                            !isHealthConnectSupported -> "Indisponible sur cet appareil"
                            hcStatus == HealthConnectStatus.SYNCING -> "Synchronisation…"
                            hcStatus == HealthConnectStatus.SYNCED -> "Poids mis à jour ✓"
                            hcStatus == HealthConnectStatus.PERMISSION_DENIED -> "Autorisation refusée"
                            hcStatus == HealthConnectStatus.NO_DATA -> "Aucune mesure de poids trouvée"
                            profile.healthConnectSyncEnabled -> "Mise à jour automatique à l'ouverture"
                            else -> "Récupère votre poids automatiquement"
                        }
                        Text(hint, color = Muted, fontSize = 11.sp)
                    }
                    Switch(
                        checked = profile.healthConnectSyncEnabled,
                        onCheckedChange = { wantsEnable ->
                            if (wantsEnable) {
                                // The flag is persisted only after the permission comes back granted.
                                permissionLauncher.launch(HealthConnectWeightProvider.PERMISSIONS)
                            } else {
                                settingsVm.disableHealthConnect()
                            }
                        },
                        enabled = isHealthConnectSupported,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = Accent,
                            uncheckedTrackColor = Surface,
                            uncheckedBorderColor = Border,
                        ),
                    )
                }
            }

            // À propos
            SettingsCard {
                Text(
                    "À PROPOS",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Version", color = Fg, style = MaterialTheme.typography.bodyMedium)
                    Text(versionLabel, color = Muted, style = MaterialTheme.typography.bodyMedium)
                }
            }

            // Vérifier les mises à jour (manual check; reuses the shared update dialog if newer).
            // Hidden on the dev build: updates/CI are a prod-channel concept (see isDevBuild).
            if (!isDevBuild(activity)) {
                SettingsRow(
                    icon = Icons.Default.Refresh,
                    label = "Vérifier les mises à jour",
                    onClick = { updateVm.checkForUpdate(force = true) },
                    trailing = {
                        when (manualState) {
                            ManualCheckState.Checking ->
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Accent)
                            ManualCheckState.UpToDate ->
                                Text("À jour", color = Muted, style = MaterialTheme.typography.bodySmall)
                            ManualCheckState.Failed ->
                                Text("Hors-ligne ?", color = Muted, style = MaterialTheme.typography.bodySmall)
                            ManualCheckState.Idle -> Unit
                        }
                    },
                )
            }

            // Notes de version (dedicated page)
            SettingsRow(
                icon = Icons.Default.Info,
                label = "Notes de version",
                onClick = onPatchNotesClick,
            )
        }
    }

    if (isWeightDialogOpen) {
        BodyWeightDialog(
            currentWeightKg = profile.bodyWeightKg,
            unit = profile.weightUnit,
            onConfirm = { value ->
                settingsVm.setBodyWeight(value, profile.weightUnit)
                isWeightDialogOpen = false
            },
            onDismiss = { isWeightDialogOpen = false },
        )
    }
}

@Composable
private fun BodyWeightDialog(
    currentWeightKg: Double?,
    unit: WeightUnit,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember {
        mutableStateOf(
            currentWeightKg?.let { MetricFormatter.weightValue(it, unit).replace(',', '.') } ?: ""
        )
    }
    val parsed = text.replace(',', '.').toDoubleOrNull()
    val isValid = parsed != null && parsed > 0.0 && unit.toKg(parsed) <= 500.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Poids corporel", color = Fg, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Ce poids sert de charge pour tous les exercices au poids de corps (séances passées incluses).",
                    color = Fg2,
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.weight(1f),
                        placeholder = if (unit == WeightUnit.KG) "ex: 72.5" else "ex: 160",
                        keyboardType = KeyboardType.Decimal,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(unit.suffix, color = Muted, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { parsed?.let(onConfirm) },
                enabled = isValid,
            ) {
                Text("Enregistrer", color = if (isValid) Accent else Muted, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler", color = Muted)
            }
        },
        containerColor = SurfaceWarm,
        modifier = Modifier.border(1.dp, Border, RoundedCornerShape(28.dp)),
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderSoft, RoundedCornerShape(12.dp)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderSoft, RoundedCornerShape(12.dp))
            .clickable { onClick() },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null, tint = Accent)
            Text(label, color = Fg, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            trailing()
        }
    }
}
