package com.example.goattracker.ui.templates

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.goattracker.data.local.RoomDataRepository
import com.example.goattracker.domain.model.WorkoutTemplate
import com.example.goattracker.theme.Accent
import com.example.goattracker.theme.Bg
import com.example.goattracker.theme.Border
import com.example.goattracker.theme.BorderSoft
import com.example.goattracker.theme.Fg
import com.example.goattracker.theme.Muted
import com.example.goattracker.theme.PremiumGradient
import com.example.goattracker.theme.Surface
import com.example.goattracker.theme.SurfaceElevated

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesScreen(
    onBackClick: () -> Unit,
    onCreateClick: () -> Unit,
    onEditClick: (String) -> Unit,
    onLaunched: () -> Unit,
    hasActiveSession: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: TemplatesViewModel = viewModel {
        TemplatesViewModel(RoomDataRepository.getInstance(context))
    }
    val items by viewModel.items.collectAsStateWithLifecycle()
    var templateToDelete by remember { mutableStateOf<WorkoutTemplate?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Mes Workouts",
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
        floatingActionButton = {
            // Create lives in the FAB once at least one workout exists (the home-screen idiom);
            // the empty state keeps its full CTA. Bottom padding clears the docked mini-player.
            if (items.isNotEmpty()) {
                FloatingActionButton(
                    onClick = onCreateClick,
                    containerColor = Color.Transparent,
                    elevation = FloatingActionButtonDefaults.elevation(0.dp),
                    modifier = Modifier
                        .padding(bottom = if (hasActiveSession) 80.dp else 0.dp)
                        .size(56.dp)
                        .border(1.dp, Border, CircleShape)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(PremiumGradient, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Créer un workout", tint = Fg, modifier = Modifier.size(28.dp))
                    }
                }
            }
        },
        containerColor = Bg
    ) { innerPadding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (items.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Aucun workout pour le moment",
                            color = Fg,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Crée tes séances types (Push, Pull, Leg…) pour les lancer en un geste, exercices et objectifs déjà en place.",
                            color = Muted,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onCreateClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .border(1.dp, Border, RoundedCornerShape(12.dp)),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            contentPadding = PaddingValues(0.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(PremiumGradient),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = Fg, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Créer un workout",
                                        color = Fg,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            items(items, key = { it.template.id }) { item ->
                TemplateCard(
                    item = item,
                    showLaunch = !hasActiveSession,
                    onLaunch = { viewModel.launch(item.template) { onLaunched() } },
                    onEdit = { onEditClick(item.template.id) },
                    onDelete = { templateToDelete = item.template },
                )
            }

            // Clearance for the session mini-player docked by the navigation root.
            item { Spacer(modifier = Modifier.height(88.dp)) }
        }
    }

    templateToDelete?.let { template ->
        AlertDialog(
            onDismissRequest = { templateToDelete = null },
            containerColor = SurfaceElevated,
            title = { Text("Supprimer « ${template.name} » ?", color = Fg) },
            text = {
                Text(
                    "Le workout sera supprimé. Tes séances déjà enregistrées ne sont pas touchées.",
                    color = Muted
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(template.id)
                    templateToDelete = null
                }) { Text("Supprimer", color = Accent) }
            },
            dismissButton = {
                TextButton(onClick = { templateToDelete = null }) { Text("Annuler", color = Muted) }
            }
        )
    }
}

@Composable
private fun TemplateCard(
    item: TemplateListItem,
    showLaunch: Boolean,
    onLaunch: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderSoft, RoundedCornerShape(12.dp))
            .clickable { onEdit() }
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.template.name,
                        color = Fg,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    // Full exercise list, wrapped — the card IS the workout's description.
                    Text(
                        text = "${item.exerciseNames.size} exercice${if (item.exerciseNames.size > 1) "s" else ""}" +
                            if (item.exerciseNames.isEmpty()) "" else " · " + item.exerciseNames.joinToString(", "),
                        color = Muted,
                        fontSize = 12.sp
                    )
                }
                Box {
                    var menuOpen by remember { mutableStateOf(false) }
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = Muted, modifier = Modifier.size(20.dp))
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        modifier = Modifier.background(SurfaceElevated)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Modifier", color = Fg) },
                            onClick = {
                                menuOpen = false
                                onEdit()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Supprimer", color = Fg) },
                            onClick = {
                                menuOpen = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            if (showLaunch) {
                Button(
                    onClick = onLaunch,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .border(1.dp, Border, RoundedCornerShape(10.dp)),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(0.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(PremiumGradient),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Fg, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Lancer",
                                color = Fg,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        }
    }
}
