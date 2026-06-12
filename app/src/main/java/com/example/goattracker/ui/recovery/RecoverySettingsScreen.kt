package com.example.goattracker.ui.recovery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.goattracker.data.local.RoomDataRepository
import com.example.goattracker.theme.Accent
import com.example.goattracker.theme.AccentSecondary
import com.example.goattracker.theme.Bg
import com.example.goattracker.theme.Border
import com.example.goattracker.theme.BorderSoft
import com.example.goattracker.theme.Fg
import com.example.goattracker.theme.Muted
import com.example.goattracker.theme.Surface
import com.example.goattracker.theme.SurfaceElevated

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecoverySettingsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: RecoverySettingsViewModel = viewModel {
        RecoverySettingsViewModel(RoomDataRepository.getInstance(context))
    }
    val rows by viewModel.rows.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Récupération musculaire",
                        color = Fg,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
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
                            .border(1.dp, Border, CircleShape)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Fg)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface, titleContentColor = Fg)
            )
        },
        containerColor = Bg
    ) { innerPadding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    text = "Temps de base dont chaque muscle a besoin pour récupérer après une séance " +
                        "(le volume de la séance l'allonge ensuite). Tout le monde n'est pas pareil : " +
                        "ajuste les tiens, la carte musculaire 3D s'adapte.",
                    color = Muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            items(rows, key = { it.group.name }) { row ->
                RecoveryRowCard(
                    row = row,
                    onMinus = { viewModel.adjust(row.group, -RecoverySettingsViewModel.STEP_HOURS) },
                    onPlus = { viewModel.adjust(row.group, +RecoverySettingsViewModel.STEP_HOURS) },
                    onReset = { viewModel.reset(row.group) },
                )
            }

            item { Spacer(modifier = Modifier.height(88.dp)) }
        }
    }
}

@Composable
private fun RecoveryRowCard(
    row: RecoveryRow,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onReset: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (row.isOverridden) Accent.copy(alpha = 0.4f) else BorderSoft, RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.group.label,
                    color = Fg,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (row.isOverridden) {
                    TextButton(
                        onClick = onReset,
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.height(24.dp)
                    ) {
                        Text("Réinitialiser (${RecoverySettingsViewModel.DEFAULT_HOURS} h)", color = Muted, fontSize = 11.sp)
                    }
                } else {
                    Text("Défaut", color = Muted, fontSize = 11.sp)
                }
            }

            IconButton(
                onClick = onMinus,
                enabled = row.hours > RecoverySettingsViewModel.MIN_HOURS,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Surface)
                    .border(1.dp, Border, CircleShape)
            ) {
                Text("−", color = Fg, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            }
            Text(
                text = "${row.hours} h",
                color = if (row.isOverridden) Accent else AccentSecondary,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp,
                modifier = Modifier.width(56.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            IconButton(
                onClick = onPlus,
                enabled = row.hours < RecoverySettingsViewModel.MAX_HOURS,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Surface)
                    .border(1.dp, Border, CircleShape)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Augmenter", tint = Fg, modifier = Modifier.size(16.dp))
            }
        }
    }
}
