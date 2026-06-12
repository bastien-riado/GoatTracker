package com.example.goattracker.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.goattracker.data.local.RoomDataRepository
import com.example.goattracker.domain.MetricFormatter
import com.example.goattracker.domain.WorkoutMetrics
import com.example.goattracker.domain.model.UserProfile
import com.example.goattracker.domain.model.WorkoutSession
import com.example.goattracker.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsListScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: SessionsListViewModel = viewModel {
        SessionsListViewModel(RoomDataRepository.getInstance(context))
    }

    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()

    var sessionToDelete by remember { mutableStateOf<WorkoutSession?>(null) }
    val dateFormat = remember { SimpleDateFormat("EEE d MMM yyyy 'à' HH:mm", Locale.FRENCH) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Historique des séances",
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
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Retour",
                            tint = Fg
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Surface,
                    titleContentColor = Fg
                )
            )
        },
        containerColor = Bg
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Sort Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${sessions.size} séance(s)",
                    color = Muted,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                )

                // Filter Dropdown
                var expanded by remember { mutableStateOf(false) }
                Box {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceElevated)
                            .border(1.dp, BorderSoft, RoundedCornerShape(8.dp))
                            .clickable { expanded = true }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Tri: ${sortOrder.displayName}",
                            color = Accent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Trier",
                            tint = Accent,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.background(SurfaceElevated)
                    ) {
                        SessionSortOrder.values().forEach { order ->
                            DropdownMenuItem(
                                text = { Text(order.displayName, color = Fg) },
                                onClick = {
                                    viewModel.updateSortOrder(order)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            if (sessions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Aucune séance enregistrée pour le moment.\nCommencez par démarrer une séance !",
                        color = Muted,
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(sessions, key = { it.id }) { session ->
                        SessionItemCard(
                            session = session,
                            userProfile = userProfile,
                            dateFormat = dateFormat,
                            onDeleteClick = { sessionToDelete = session }
                        )
                    }
                }
            }
        }
    }

    // Deletion confirmation modal
    if (sessionToDelete != null) {
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            title = {
                Text(
                    text = "Supprimer la séance ?",
                    color = Fg,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Text(
                    text = "Attention ! Cette action est irréversible et toutes les données de cette séance (\"${sessionToDelete?.name}\") seront définitivement perdues.",
                    color = Fg2,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        sessionToDelete?.let {
                            viewModel.deleteSession(it.id)
                        }
                        sessionToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Danger)
                ) {
                    Text("Supprimer", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { sessionToDelete = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = Muted)
                ) {
                    Text("Conserver", fontWeight = FontWeight.Medium)
                }
            },
            containerColor = SurfaceWarm,
            titleContentColor = Fg,
            textContentColor = Fg2,
            modifier = Modifier.border(1.dp, Border, RoundedCornerShape(28.dp))
        )
    }
}

@Composable
fun SessionItemCard(
    session: WorkoutSession,
    userProfile: UserProfile,
    dateFormat: SimpleDateFormat,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateStr = remember(session.startTime) {
        dateFormat.format(Date(session.startTime)).replaceFirstChar { it.uppercase() }
    }

    val volText = remember(session, userProfile) {
        MetricFormatter.tonnage(
            WorkoutMetrics.sessionStrengthVolumeKg(session, userProfile.bodyWeightKg),
            userProfile.weightUnit
        )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, BorderSoft, RoundedCornerShape(12.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Name and Trash Icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.name,
                        color = Fg,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = dateStr,
                        color = Muted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal
                    )
                }

                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Surface)
                        .border(1.dp, BorderSoft, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Supprimer la séance",
                        tint = Danger.copy(alpha = 0.85f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = BorderSoft, thickness = 1.dp)
            Spacer(modifier = Modifier.height(12.dp))

            // Volume Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Volume total : ",
                    color = Muted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = volText,
                    color = AccentSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (session.exercises.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Exercices effectués :",
                    color = Muted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    session.exercises.forEach { exSession ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Accent)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${exSession.exercise.name} (${exSession.completedSetsCount} séries • ${MetricFormatter.exerciseSummary(exSession, userProfile)})",
                                color = Fg2,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}
