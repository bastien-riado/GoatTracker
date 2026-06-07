package com.example.goattracker.ui.bodyheatmap

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.goattracker.data.DefaultDataRepository
import com.example.goattracker.domain.MuscleStatus
import com.example.goattracker.domain.model.MuscleGroup
import com.example.goattracker.theme.*
import io.github.sceneview.Scene
import io.github.sceneview.math.Position
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader
import kotlin.math.roundToInt

private const val BODY_MODEL_ASSET = "models/body_muscles.glb"

// Heatmap gradient: fresh-trained (red) -> recovering (amber) -> ready (green); no data -> neutral.
private val HeatNeutral = Color(0xFF6E6E6E)
private val HeatFatigued = Color(0xFFE53935)
private val HeatMid = Color(0xFFF5B301)
private val HeatReady = Color(0xFF2ECC71)

private fun heatColor(status: MuscleStatus?): Color {
    if (status == null || !status.hasData) return HeatNeutral
    val r = status.recovery.coerceIn(0f, 1f)
    return if (r < 0.5f) lerp(HeatFatigued, HeatMid, r / 0.5f)
    else lerp(HeatMid, HeatReady, (r - 0.5f) / 0.5f)
}

/**
 * Tints each muscle of the loaded body model by recovery. The glTF material name equals the
 * [MuscleGroup.id], so we match by [com.google.android.filament.MaterialInstance.getName] and push
 * the color into Filament's `baseColorFactor`. Non-muscle materials (head) stay neutral.
 */
private fun applyHeatmap(
    instance: ModelInstance,
    statuses: Map<MuscleGroup, MuscleStatus>,
    selected: MuscleGroup?,
) {
    instance.materialInstances.forEach { mi ->
        val group = MuscleGroup.fromId(mi.name)
        val base = if (group == null) HeatNeutral else heatColor(statuses[group])
        val color = if (group != null && group == selected) lerp(base, Color.White, 0.35f) else base
        mi.setParameter("baseColorFactor", color.red, color.green, color.blue, 1.0f)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyHeatmapScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: BodyHeatmapViewModel = viewModel {
        BodyHeatmapViewModel(DefaultDataRepository.getInstance(context.filesDir))
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val cameraNode = rememberCameraNode(engine) {
        position = Position(z = 3.6f) // pulled back to frame the ~1.8 m body
    }
    val modelInstance = rememberModelInstance(modelLoader, BODY_MODEL_ASSET)

    // Re-tint whenever the model finishes loading or the recovery data / selection changes.
    LaunchedEffect(modelInstance, state.statuses, state.selected) {
        val inst = modelInstance ?: return@LaunchedEffect
        applyHeatmap(inst, state.statuses, state.selected)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Carte musculaire 3D",
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface, titleContentColor = Fg),
            )
        },
        containerColor = Bg,
    ) { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Scene(
                modifier = Modifier.fillMaxSize(),
                engine = engine,
                modelLoader = modelLoader,
                cameraNode = cameraNode,
                // The default cameraManipulator provides 360° orbit + pinch-to-zoom.
            ) {
                modelInstance?.let { instance ->
                    ModelNode(modelInstance = instance, scaleToUnits = 1.8f)
                }
            }

            if (state.isLoading || modelInstance == null) {
                CircularProgressIndicator(
                    color = Accent,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SelectedMuscleReadout(state)
                HeatLegend()
                MuscleChipsRow(
                    statuses = state.statuses,
                    selected = state.selected,
                    onSelect = viewModel::select,
                )
            }
        }
    }
}

@Composable
private fun SelectedMuscleReadout(state: BodyHeatmapUiState) {
    val selected = state.selected ?: return
    val status = state.statuses[selected]
    val pct = status?.takeIf { it.hasData }?.let { (it.recovery * 100).roundToInt() }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceElevated)
            .border(1.dp, BorderSoft, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(heatColor(status)))
        Text(selected.label, color = Fg, fontWeight = FontWeight.Bold)
        Text(
            text = if (pct != null) "Récupération $pct %" else "Jamais travaillé",
            color = Muted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun HeatLegend() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Brush.horizontalGradient(listOf(HeatFatigued, HeatMid, HeatReady))),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Fatigué", color = Muted, fontSize = 10.sp)
            Text("Prêt", color = Muted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun MuscleChipsRow(
    statuses: Map<MuscleGroup, MuscleStatus>,
    selected: MuscleGroup?,
    onSelect: (MuscleGroup) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(MuscleGroup.entries) { group ->
            val isSelected = group == selected
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) SurfaceElevated else Surface)
                    .border(1.dp, if (isSelected) Accent else BorderSoft, RoundedCornerShape(8.dp))
                    .clickable { onSelect(group) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(heatColor(statuses[group])))
                Text(group.label, color = if (isSelected) Fg else Muted, fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}
