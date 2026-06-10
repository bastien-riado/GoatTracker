package com.example.goattracker.ui.settings

import androidx.activity.ComponentActivity
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.goattracker.theme.*
import com.example.goattracker.ui.components.isDevBuild
import com.example.goattracker.ui.components.rememberAppVersionLabel
import com.example.goattracker.update.ManualCheckState
import com.example.goattracker.update.UpdateViewModel

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
