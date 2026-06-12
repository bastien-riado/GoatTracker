package com.example.goattracker.ui.celebration

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.goattracker.data.local.RoomDataRepository
import com.example.goattracker.domain.MetricFormatter
import com.example.goattracker.domain.PersonalRecord
import com.example.goattracker.domain.RecordKind
import com.example.goattracker.domain.model.UserProfile
import com.example.goattracker.theme.Accent
import com.example.goattracker.theme.AccentSecondary
import com.example.goattracker.theme.Bg
import com.example.goattracker.theme.Border
import com.example.goattracker.theme.BorderSoft
import com.example.goattracker.theme.Fg
import com.example.goattracker.theme.Muted
import com.example.goattracker.theme.PremiumGradient
import com.example.goattracker.theme.SurfaceElevated
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun SessionCelebrationScreen(
    sessionId: String,
    onClose: () -> Unit,
    onOpenRecap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: SessionCelebrationViewModel = viewModel(key = sessionId) {
        SessionCelebrationViewModel(RoomDataRepository.getInstance(context), sessionId)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(containerColor = Bg) { innerPadding ->
        Box(modifier = modifier.fillMaxSize()) {
            val ready = state as? CelebrationUiState.Ready
            if (ready != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Spacer(modifier = Modifier.height(24.dp))

                    Text(text = "💪", fontSize = 56.sp)
                    Text(
                        text = "Séance enregistrée !",
                        color = Fg,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = congratsLine(ready.records.size, ready.summary.completedSets),
                        color = Muted,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )

                    MiniRecapCard(ready)

                    if (ready.records.isNotEmpty()) {
                        RecordsCard(ready.records, ready.userProfile)
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = onOpenRecap,
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
                            Text(
                                text = "Voir le récap complet",
                                color = Fg,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                    TextButton(onClick = onClose) {
                        Text("Fermer", color = Muted, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            // On top so the confetti rains over the content; non-interactive by construction.
            ConfettiOverlay(modifier = Modifier.fillMaxSize())
        }
    }
}

private fun congratsLine(recordCount: Int, completedSets: Int): String = when {
    recordCount > 1 -> "Énorme : $recordCount records battus aujourd'hui 🔥"
    recordCount == 1 -> "Et un nouveau record au passage 🔥"
    completedSets > 0 -> "Le travail paie — encore une pierre à l'édifice."
    else -> "Séance bouclée."
}

@Composable
private fun MiniRecapCard(state: CelebrationUiState.Ready) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderSoft, RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            state.summary.durationSeconds?.let {
                RecapStat(label = "DURÉE", value = MetricFormatter.duration(it))
            }
            if (state.summary.strengthVolumeKg > 0) {
                RecapStat(
                    label = "TONNAGE",
                    value = MetricFormatter.tonnage(state.summary.strengthVolumeKg, state.userProfile.weightUnit),
                    delta = state.summary.volumeDeltaVsPrevious,
                )
            }
            RecapStat(label = "SÉRIES", value = state.summary.completedSets.toString())
        }
    }
}

@Composable
private fun RecapStat(label: String, value: String, delta: Double? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            value,
            color = AccentSecondary,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold)
        )
        if (delta != null) {
            val up = delta >= 0
            Text(
                text = (if (up) "▲ +" else "▼ ") + "${(delta * 100).toInt()}%",
                color = if (up) Accent else Muted,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun RecordsCard(records: List<PersonalRecord>, profile: UserProfile) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Accent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "🏆 NOUVEAU${if (records.size > 1) "X" else ""} RECORD${if (records.size > 1) "S" else ""}",
                color = Accent,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp)
            )
            records.forEach { record ->
                Column {
                    Text(
                        text = record.exerciseName ?: "Séance entière",
                        color = Fg,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${record.kind.displayName} : ${formatRecordValue(record.kind, record.value, profile)}" +
                            "  (ancien : ${formatRecordValue(record.kind, record.previousBest, profile)})",
                        color = Muted,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

private fun formatRecordValue(kind: RecordKind, value: Double, profile: UserProfile): String = when (kind) {
    RecordKind.MAX_WEIGHT, RecordKind.EST_ONE_RM -> MetricFormatter.weight(value, profile.weightUnit)
    RecordKind.MAX_REPS -> "${value.toInt()} reps"
    RecordKind.MAX_DISTANCE -> MetricFormatter.distance(value)
    RecordKind.MAX_DURATION -> MetricFormatter.duration(value.toInt())
    RecordKind.BEST_PACE -> MetricFormatter.pace(value)
    RecordKind.SESSION_VOLUME -> MetricFormatter.tonnage(value, profile.weightUnit)
}

/**
 * One-shot confetti rain, pure Compose Canvas (no dependency). Particles are seeded once;
 * a single progress animation drives fall + sway + spin, fading out at the end.
 */
@Composable
private fun ConfettiOverlay(modifier: Modifier = Modifier) {
    val particles = remember {
        val random = Random(System.nanoTime())
        val palette = listOf(Accent, AccentSecondary, Color(0xFFFFD54F), Color(0xFFFF8A65), Color(0xFF4FC3F7))
        List(90) {
            ConfettiParticle(
                xFraction = random.nextFloat(),
                delay = random.nextFloat() * 0.35f,
                speed = 0.65f + random.nextFloat() * 0.55f,
                swayAmplitude = 18f + random.nextFloat() * 42f,
                swayPhase = random.nextFloat() * 6.28f,
                rotationSpeed = (random.nextFloat() - 0.5f) * 1_080f,
                size = 8f + random.nextFloat() * 10f,
                color = palette[random.nextInt(palette.size)],
            )
        }
    }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, animationSpec = tween(durationMillis = 3_000, easing = LinearEasing))
    }

    if (progress.value >= 1f) return
    val alpha = (1f - progress.value).coerceIn(0f, 1f).let { if (it > 0.3f) 1f else it / 0.3f }

    Canvas(modifier = modifier) {
        val t = progress.value
        particles.forEach { p ->
            val local = ((t - p.delay) / (1f - p.delay)).coerceIn(0f, 1f) * p.speed
            if (local <= 0f) return@forEach
            val y = local * (size.height + 200f) - 100f
            val x = p.xFraction * size.width + p.swayAmplitude * sin(p.swayPhase + local * 12f)
            rotate(degrees = p.rotationSpeed * local, pivot = Offset(x, y)) {
                drawRect(
                    color = p.color.copy(alpha = alpha),
                    topLeft = Offset(x - p.size / 2f, y - p.size / 4f),
                    size = androidx.compose.ui.geometry.Size(p.size, p.size / 2f),
                )
            }
        }
    }
}

private data class ConfettiParticle(
    val xFraction: Float,
    val delay: Float,
    val speed: Float,
    val swayAmplitude: Float,
    val swayPhase: Float,
    val rotationSpeed: Float,
    val size: Float,
    val color: Color,
)
