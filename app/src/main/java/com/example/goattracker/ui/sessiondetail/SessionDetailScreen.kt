package com.example.goattracker.ui.sessiondetail

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.example.goattracker.domain.ExerciseBreakdown
import com.example.goattracker.domain.MetricFormatter
import com.example.goattracker.domain.WorkoutMetrics
import com.example.goattracker.domain.model.UserProfile
import com.example.goattracker.theme.Accent
import com.example.goattracker.theme.AccentSecondary
import com.example.goattracker.theme.Bg
import com.example.goattracker.theme.Border
import com.example.goattracker.theme.BorderSoft
import com.example.goattracker.theme.Fg
import com.example.goattracker.theme.Muted
import com.example.goattracker.theme.Surface
import com.example.goattracker.theme.SurfaceElevated
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    sessionId: String,
    onBackClick: () -> Unit,
    onExerciseClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: SessionDetailViewModel = viewModel(key = sessionId) {
        SessionDetailViewModel(RoomDataRepository.getInstance(context), sessionId)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Deleted from elsewhere while open: leave rather than display a ghost.
    LaunchedEffect(state) {
        if (state is SessionDetailUiState.Gone) onBackClick()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Récap de séance",
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
        val success = state as? SessionDetailUiState.Success ?: return@Scaffold

        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { HeaderCard(success) }
            item { KpiRow(success) }
            if (success.summary.totalDistanceKm > 0 || success.summary.totalCardioSeconds > 0) {
                item { CardioCard(success) }
            }
            if (success.summary.setsPerMuscle.isNotEmpty()) {
                item { MuscleSplitCard(success) }
            }
            item {
                Text(
                    text = "DÉTAIL DES EXERCICES",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp)
                )
            }
            success.summary.perExercise.forEach { breakdown ->
                item(key = breakdown.exerciseSession.id) {
                    ExerciseBreakdownCard(
                        breakdown = breakdown,
                        profile = success.userProfile,
                        onClick = { onExerciseClick(breakdown.exerciseSession.exercise.id) },
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(88.dp)) }
        }
    }
}

@Composable
private fun HeaderCard(state: SessionDetailUiState.Success) {
    val dateStr = remember(state.session.startTime) {
        SimpleDateFormat("EEEE d MMMM yyyy 'à' HH:mm", Locale.FRANCE)
            .format(Date(state.session.startTime))
            .replaceFirstChar { it.uppercase() }
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderSoft, RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = state.session.name,
                color = Fg,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold)
            )
            Text(text = dateStr, color = Muted, fontSize = 12.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                state.summary.durationSeconds?.let { duration ->
                    Text(
                        text = "Durée : ${MetricFormatter.duration(duration)}",
                        color = AccentSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                state.templateName?.let { name ->
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Accent.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("Workout : $name", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun KpiRow(state: SessionDetailUiState.Success) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        KpiCard(
            label = "TONNAGE",
            value = MetricFormatter.tonnage(state.summary.strengthVolumeKg, state.userProfile.weightUnit),
            modifier = Modifier.weight(1.2f),
            // The progressive-overload signal: vs the previous session of the same workout.
            delta = state.summary.volumeDeltaVsPrevious,
        )
        KpiCard(
            label = "SÉRIES",
            value = state.summary.completedSets.toString(),
            modifier = Modifier.weight(0.8f),
        )
        KpiCard(
            label = "EXERCICES",
            value = state.summary.exerciseCount.toString(),
            modifier = Modifier.weight(0.9f),
        )
    }
}

@Composable
private fun KpiCard(label: String, value: String, modifier: Modifier = Modifier, delta: Double? = null) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
        modifier = modifier.border(1.dp, BorderSoft, RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label,
                color = Muted,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                color = Accent,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold)
            )
            if (delta != null) {
                val up = delta >= 0
                Text(
                    text = (if (up) "▲ +" else "▼ ") + "${(delta * 100).toInt()}% vs précédente",
                    color = if (up) Accent else Muted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun CardioCard(state: SessionDetailUiState.Success) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderSoft, RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "CARDIO",
                color = Muted,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (state.summary.totalDistanceKm > 0) {
                    Text(
                        text = MetricFormatter.distance(state.summary.totalDistanceKm),
                        color = AccentSecondary,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
                if (state.summary.totalCardioSeconds > 0) {
                    Text(
                        text = MetricFormatter.duration(state.summary.totalCardioSeconds),
                        color = AccentSecondary,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
                WorkoutMetrics.paceSecPerKm(state.summary.totalCardioSeconds, state.summary.totalDistanceKm)
                    ?.let { pace ->
                        Text(
                            text = MetricFormatter.pace(pace),
                            color = AccentSecondary,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
            }
        }
    }
}

@Composable
private fun MuscleSplitCard(state: SessionDetailUiState.Success) {
    val maxSets = state.summary.setsPerMuscle.values.max()
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderSoft, RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "RÉPARTITION MUSCULAIRE",
                color = Muted,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp)
            )
            state.summary.setsPerMuscle.forEach { (muscle, sets) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(muscle, color = Fg, fontSize = 13.sp, modifier = Modifier.width(110.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Surface)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction = sets.toFloat() / maxSets)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Accent)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$sets série${if (sets > 1) "s" else ""}",
                        color = Muted,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ExerciseBreakdownCard(
    breakdown: ExerciseBreakdown,
    profile: UserProfile,
    onClick: () -> Unit,
) {
    val es = breakdown.exerciseSession
    val completedSets = es.sets.filter { it.isCompleted }
    val skipped = es.sets.size - completedSets.size
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderSoft, RoundedCornerShape(12.dp))
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = es.exercise.name,
                    color = Fg,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f)
                )
                if (breakdown.strengthVolumeKg > 0) {
                    Text(
                        text = MetricFormatter.tonnage(breakdown.strengthVolumeKg, profile.weightUnit),
                        color = AccentSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            completedSets.forEachIndexed { index, set ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${index + 1}",
                        color = Muted,
                        fontSize = 11.sp,
                        modifier = Modifier.width(20.dp)
                    )
                    Text(
                        text = MetricFormatter.setLineVerbose(es.exercise.trackingType, set, profile),
                        color = Fg,
                        fontSize = 13.sp
                    )
                }
            }
            if (completedSets.isEmpty()) {
                Text("Aucune série complétée", color = Muted, fontSize = 12.sp)
            } else if (skipped > 0) {
                Text(
                    text = "$skipped série${if (skipped > 1) "s" else ""} non complétée${if (skipped > 1) "s" else ""}",
                    color = Muted,
                    fontSize = 11.sp
                )
            }
        }
    }
}
