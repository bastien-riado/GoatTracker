package com.example.goattracker.ui.bodyheatmap

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.goattracker.data.DefaultDataRepository
import com.example.goattracker.domain.MuscleHeatColor
import com.example.goattracker.domain.MuscleStatus
import com.example.goattracker.domain.Rgb
import com.example.goattracker.domain.model.MuscleGroup
import com.example.goattracker.theme.*
import io.github.sceneview.Scene
import io.github.sceneview.SurfaceType
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.rememberCameraNode
import kotlin.math.roundToInt

private fun Rgb.toColor(): Color = Color(r, g, b)

/**
 * Tints each muscle of the loaded body model by recovery (color logic lives in [MuscleHeatColor]).
 * The glTF material name equals the [MuscleGroup.id], so we match by
 * [com.google.android.filament.MaterialInstance.getName] and push the color into Filament's
 * `baseColorFactor`. Non-muscle materials (head, body) stay neutral.
 */
private fun applyHeatmap(
    instance: ModelInstance,
    statuses: Map<MuscleGroup, MuscleStatus>,
    selected: MuscleGroup?,
    selectionPulse: Float = 0f,
) {
    instance.materialInstances.forEach { mi ->
        val group = MuscleGroup.fromId(mi.name)
        val base = if (group == null) MuscleHeatColor.NEUTRAL else MuscleHeatColor.forStatus(statuses[group])
        // The selected muscle pulses toward white (driven by selectionPulse 0..1) so the eye finds
        // it immediately on the body.
        val rgb = if (group != null && group == selected) {
            MuscleHeatColor.lerp(base, Rgb(1f, 1f, 1f), 0.15f + 0.45f * selectionPulse)
        } else base
        mi.setParameter("baseColorFactor", rgb.r, rgb.g, rgb.b, 1.0f)
    }
}

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

    // Engine, loaders, IBL environment, view/renderer and GLB bytes are app-scoped (see
    // BodyModelAssets): opening this screen only creates a Model from the in-memory buffer and a
    // camera, and leaving it never tears anything heavy down — so the model shows up immediately
    // and back-navigation stays jank-free.
    val shared = remember { BodyModelAssets.sceneResources(context) }
    val cameraNode = rememberCameraNode(shared.engine) {
        // Default framing distance for the 1.8 m body — lower = bigger model at startup.
        position = Position(z = 3.4f)
    }
    val model = remember { BodyModelAssets.createModel(context) }
    DisposableEffect(model) {
        // Runs after the Scene's ModelNode destroyed the model entities (reverse composition order).
        onDispose { BodyModelAssets.destroyModel(model) }
    }
    val modelInstance: ModelInstance = model.instance

    // Oscillating highlight for the selected muscle. Reading the transition value only inside
    // snapshotFlow keeps the per-frame work out of recomposition (just 17 material writes).
    val pulse by rememberInfiniteTransition(label = "musclePulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(650, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse",
    )

    // Re-tint whenever the recovery data / selection changes; while a muscle is selected, keep
    // re-tinting every animation frame so its highlight pulses.
    LaunchedEffect(modelInstance, state.statuses, state.selected) {
        if (state.selected == null) {
            applyHeatmap(modelInstance, state.statuses, null)
        } else {
            snapshotFlow { pulse }.collect { p ->
                applyHeatmap(modelInstance, state.statuses, state.selected, p)
            }
        }
    }

    // Immersive edge-to-edge layout: the scene fills the whole screen, drawing behind the
    // (transparent) status and navigation bars. Back arrow, legend and chips are foreground
    // overlays — the model passes BEHIND them when rotated/zoomed, never gets clipped.
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Scene(
            modifier = Modifier.fillMaxSize(),
            // TextureSurface composites inline with the Compose tree, so the 3D view is removed
            // in lockstep with this screen instead of a SurfaceView lingering ~300ms over the
            // Profile page after back-navigation.
            surfaceType = SurfaceType.TextureSurface,
            engine = shared.engine,
            modelLoader = shared.modelLoader,
            materialLoader = shared.materialLoader,
            environmentLoader = shared.environmentLoader,
            environment = shared.environment,
            view = shared.view,
            renderer = shared.renderer,
            scene = shared.scene,
            mainLightNode = shared.mainLight,
            cameraNode = cameraNode,
            // The default cameraManipulator provides 360° orbit + pinch-to-zoom.
        ) {
            // rotation 180°: the glTF export faces -Z, so without it the camera greets the user
            // with the model's back.
            ModelNode(
                modelInstance = modelInstance,
                scaleToUnits = 1.8f,
                rotation = Rotation(y = 180f),
            )
        }

        if (state.isLoading) {
            CircularProgressIndicator(
                color = Accent,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        // Floating top row: bare white back arrow (no chip background — it aligns better with the
        // legend over the black scene) and the recovery legend beside it.
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            IconButton(onClick = onBackClick, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Retour",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
            HeatLegend(modifier = Modifier.weight(1f))
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SelectedMuscleReadout(state)
            MuscleChipsRow(
                statuses = state.statuses,
                selected = state.selected,
                onSelect = viewModel::select,
            )
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
        Box(Modifier.size(10.dp).clip(CircleShape).background(MuscleHeatColor.forStatus(status).toColor()))
        Text(selected.label, color = Fg, fontWeight = FontWeight.Bold)
        Text(
            text = if (pct != null) "Récupération $pct %" else "Jamais travaillé",
            color = Muted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun HeatLegend(modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Brush.horizontalGradient(listOf(MuscleHeatColor.FATIGUED.toColor(), MuscleHeatColor.MID.toColor(), MuscleHeatColor.READY.toColor()))),
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
                Box(Modifier.size(8.dp).clip(CircleShape).background(MuscleHeatColor.forStatus(statuses[group]).toColor()))
                Text(group.label, color = if (isSelected) Fg else Muted, fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}
