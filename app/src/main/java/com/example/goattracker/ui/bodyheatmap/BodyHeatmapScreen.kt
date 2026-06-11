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
import androidx.compose.ui.draw.shadow
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
) {
    instance.materialInstances.forEach { mi ->
        val group = MuscleGroup.fromId(mi.name)
        val base = if (group == null) MuscleHeatColor.NEUTRAL else MuscleHeatColor.forStatus(statuses[group])
        // Brighten the selected muscle so it stands out.
        val rgb = if (group != null && group == selected) MuscleHeatColor.lerp(base, Rgb(1f, 1f, 1f), 0.35f) else base
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

    // Engine + loader + GLB bytes are app-scoped (see BodyModelAssets): opening this screen only
    // creates a Model from the in-memory buffer, and leaving it never tears the engine down — so
    // both the enter and the back-navigation stay jank-free.
    val engine = remember { BodyModelAssets.engine(context) }
    val modelLoader = remember { BodyModelAssets.modelLoader(context) }
    val cameraNode = rememberCameraNode(engine) {
        position = Position(z = 3.6f) // pulled back to frame the ~1.8 m body
    }
    val model = remember { BodyModelAssets.createModel(context) }
    DisposableEffect(model) {
        // Runs after the Scene's ModelNode destroyed the model entities (reverse composition order).
        onDispose { BodyModelAssets.destroyModel(model) }
    }
    val modelInstance: ModelInstance = model.instance

    // Re-tint whenever the recovery data / selection changes.
    LaunchedEffect(modelInstance, state.statuses, state.selected) {
        applyHeatmap(modelInstance, state.statuses, state.selected)
    }

    // Immersive edge-to-edge layout: no header, content draws behind the (transparent) status and
    // navigation bars; the back button, legend and chips float over the black scene.
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Scene(
            // The scene viewport is padded down to the unobstructed middle of the screen (between
            // the top legend row and the bottom chips), so the auto-centered model lands visually
            // centered between the overlays. The viewport bounds stay invisible: the scene clear
            // color and the screen background are both black.
            modifier = Modifier
                .fillMaxSize()
                .padding(top = statusBarPadding + 60.dp, bottom = navBarPadding + 72.dp),
            // TextureSurface composites inline with the Compose tree, so the 3D view is removed
            // in lockstep with this screen instead of a SurfaceView lingering ~300ms over the
            // Profile page after back-navigation.
            surfaceType = SurfaceType.TextureSurface,
            engine = engine,
            modelLoader = modelLoader,
            cameraNode = cameraNode,
            // The default cameraManipulator provides 360° orbit + pinch-to-zoom.
        ) {
            ModelNode(modelInstance = modelInstance, scaleToUnits = 1.8f)
        }

        if (state.isLoading) {
            CircularProgressIndicator(
                color = Accent,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        // Floating back button (same circle style as the other screens' nav icon, plus a shadow
        // so it reads as floating over the scene) with the legend beside it.
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .size(40.dp)
                    .shadow(10.dp, CircleShape)
                    .clip(CircleShape)
                    .background(SurfaceElevated)
                    .border(1.dp, Border, CircleShape),
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Retour", tint = Fg)
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
