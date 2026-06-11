package com.example.goattracker.ui.profile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.goattracker.data.DefaultDataRepository
import com.example.goattracker.domain.MetricFormatter
import com.example.goattracker.domain.model.Exercise
import com.example.goattracker.domain.model.WeightUnit
import com.example.goattracker.ui.bodyheatmap.BodyModelAssets
import com.example.goattracker.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBackClick: () -> Unit,
    onSessionsClick: () -> Unit,
    onBodyHeatmapClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: ProfileViewModel = viewModel {
        ProfileViewModel(DefaultDataRepository.getInstance(context.filesDir))
    }

    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Warm up the Filament engine + body model bytes while the user is still here, so navigating
    // to the 3D heatmap is instant (see BodyModelAssets — app-scoped, created once).
    LaunchedEffect(Unit) { BodyModelAssets.prewarm(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Statistiques & Profil",
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
                            contentDescription = "Back",
                            tint = Fg
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onSettingsClick,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(SurfaceElevated)
                            .border(1.dp, Border, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Paramètres",
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
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 1. Overview Cards
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
                        modifier = Modifier
                            .weight(1f)
                            .border(1.dp, BorderSoft, RoundedCornerShape(12.dp))
                            .clickable { onSessionsClick() }
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text("SÉANCES TOTALES", color = Muted, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = state.totalWorkouts.toString(),
                                color = Accent,
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold)
                            )
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
                        modifier = Modifier
                            .weight(1.3f)
                            .border(1.dp, BorderSoft, RoundedCornerShape(12.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text("VOLUME CUMULÉ", color = Muted, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp))
                            Spacer(modifier = Modifier.height(6.dp))
                            val volText = MetricFormatter.tonnage(state.cumulativeVolume, state.userProfile.weightUnit)
                            Text(
                                text = volText,
                                color = AccentSecondary,
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold)
                            )
                        }
                    }
                }
            }

            // 1b. 3D muscle heatmap entry point
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderSoft, RoundedCornerShape(12.dp))
                        .clickable { onBodyHeatmapClick() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("RÉCUPÉRATION MUSCULAIRE", color = Muted, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp))
                            Text("Carte musculaire 3D", color = Fg, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Tournez le corps pour voir les muscles récupérés", color = Muted, fontSize = 11.sp)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Accent.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = Accent)
                        }
                    }
                }
            }

            // 2. 1RM Evolution line chart
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderSoft, RoundedCornerShape(12.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("ÉVOLUTION ESTIMÉE DU 1RM", color = Muted, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp))
                                Text("Progression du 1RM", color = Fg, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            }

                            // Exercise selector dropdown
                            var expanded by remember { mutableStateOf(false) }
                            Box {
                                Row(
                                    modifier = Modifier
                                        .widthIn(max = 150.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Surface)
                                        .border(1.dp, BorderSoft, RoundedCornerShape(8.dp))
                                        .clickable { expanded = true }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = state.selectedExercise?.name ?: "Sélectionner",
                                        color = Accent,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Select", tint = Accent)
                                }

                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false },
                                    modifier = Modifier.background(SurfaceElevated)
                                ) {
                                    state.availableExercises.forEach { exercise ->
                                        DropdownMenuItem(
                                            text = { Text(exercise.name, color = Fg) },
                                            onClick = {
                                                viewModel.selectExercise(exercise)
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        if (state.oneRepMaxEvolution.size < 2) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Enregistrez au moins 2 séances pour cet exercice\npour tracer l'évolution du 1RM !",
                                    color = Muted,
                                    textAlign = TextAlign.Center,
                                    fontSize = 12.sp
                                )
                            }
                        } else {
                            OneRepMaxLineChart(
                                points = state.oneRepMaxEvolution,
                                weightUnit = state.userProfile.weightUnit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                            )
                        }
                    }
                }
            }

            // 3. Muscle splits radar chart
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderSoft, RoundedCornerShape(12.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text("RÉPARTITION DU TRAVAIL MUSCULAIRE", color = Muted, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp))
                        Text("Volume par groupe musculaire (Séries)", color = Fg, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))

                        Spacer(modifier = Modifier.height(16.dp))

                        if (state.muscleGroupSets.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(260.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Aucune série validée pour le moment", color = Muted)
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                MuscleRadarChart(
                                    data = state.muscleGroupSets,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // 2-column muscle chips legend list
                            val muscles = listOf("Pectoraux", "Dos", "Épaules", "Quadriceps", "Ischio-jambiers", "Biceps", "Triceps", "Abdominaux")
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                for (i in 0 until 4) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        val m1 = muscles[i * 2]
                                        val m2 = muscles[i * 2 + 1]
                                        
                                        MuscleLegendItem(name = m1, count = state.muscleGroupSets[m1] ?: 0, modifier = Modifier.weight(1f))
                                        MuscleLegendItem(name = m2, count = state.muscleGroupSets[m2] ?: 0, modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. Session volumes tonnage chart
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderSoft, RoundedCornerShape(12.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text("HISTORIQUE DES SÉANCES", color = Muted, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp))
                        Text(
                            "Volume total par séance (${state.userProfile.weightUnit.suffix})",
                            color = Fg,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        if (state.sessionVolumes.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Aucune séance complétée", color = Muted)
                            }
                        } else {
                            SessionVolumesBarChart(
                                volumes = state.sessionVolumes,
                                weightUnit = state.userProfile.weightUnit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OneRepMaxLineChart(
    points: List<Pair<Long, Double>>,
    weightUnit: WeightUnit = WeightUnit.KG,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.semantics { contentDescription = "Graphique d'évolution du 1RM estimé" }) {
        val maxVal = points.maxOf { it.second }
        val minVal = points.minOf { it.second }
        val valueRange = if (maxVal == minVal) 1.0 else maxVal - minVal
        
        val width = size.width
        val height = size.height
        
        val labelHeight = 24.dp.toPx()
        val valueHeight = 20.dp.toPx()
        val marginX = 24.dp.toPx()
        
        val chartWidth = width - 2 * marginX
        val chartHeight = height - labelHeight - valueHeight
        
        val xSpacing = if (points.size > 1) chartWidth / (points.size - 1) else chartWidth
        
        val pathPoints = points.mapIndexed { index, pair ->
            val x = marginX + index * xSpacing
            // Invert Y coordinate because canvas y grows downwards
            val normalizedY = if (valueRange == 0.0) 0.5 else (pair.second - minVal) / valueRange
            val y = valueHeight + chartHeight - (normalizedY * chartHeight).toFloat()
            Offset(x, y)
        }

        // Draw dotted grid lines inside the chart bounds
        val gridLines = 3
        for (i in 0..gridLines) {
            val y = valueHeight + i * chartHeight / gridLines
            drawLine(
                color = BorderSoft,
                start = Offset(marginX, y),
                end = Offset(width - marginX, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        // Create curved/line paths
        val linePath = Path().apply {
            val first = pathPoints.first()
            moveTo(first.x, first.y)
            for (i in 1 until pathPoints.size) {
                val point = pathPoints[i]
                lineTo(point.x, point.y)
            }
        }

        // Create area sweep path for transparent gradient fill under the line
        val areaPath = Path().apply {
            val first = pathPoints.first()
            moveTo(first.x, valueHeight + chartHeight)
            lineTo(first.x, first.y)
            for (i in 1 until pathPoints.size) {
                val point = pathPoints[i]
                lineTo(point.x, point.y)
            }
            lineTo(pathPoints.last().x, valueHeight + chartHeight)
            close()
        }

        // 1. Draw area transparent gradient sweep from orange-pink to transparent
        drawPath(
            path = areaPath,
            brush = Brush.verticalGradient(
                colors = listOf(AccentSecondary.copy(alpha = 0.25f), Color.Transparent),
                startY = valueHeight,
                endY = valueHeight + chartHeight
            )
        )

        // 2. Draw glowing premium horizontal gradient line from orange to pink/red
        drawPath(
            path = linePath,
            brush = Brush.horizontalGradient(
                colors = listOf(AccentSecondary, Accent),
                startX = marginX,
                endX = width - marginX
            ),
            style = Stroke(width = 3.dp.toPx())
        )

        val dateFormat = SimpleDateFormat("dd/MM", Locale.getDefault())

        // 3. Draw circles and text labels at points
        pathPoints.forEachIndexed { index, offset ->
            val pair = points[index]
            
            // Draw background and foreground circles
            drawCircle(
                color = SurfaceElevated,
                radius = 5.dp.toPx(),
                center = offset
            )
            drawCircle(
                color = AccentSecondary,
                radius = 3.dp.toPx(),
                center = offset
            )

            // Draw the weight value above the point (1RM is stored in kg; convert for display)
            val weightText = MetricFormatter.weight(pair.second, weightUnit)
            val valuePaint = android.graphics.Paint().apply {
                color = Fg.toArgb()
                textSize = 9.sp.toPx()
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                textAlign = android.graphics.Paint.Align.CENTER
            }
            drawContext.canvas.nativeCanvas.drawText(
                weightText,
                offset.x,
                offset.y - 8.dp.toPx(),
                valuePaint
            )

            // Draw the date text below the point on the X axis
            val dateText = dateFormat.format(Date(pair.first))
            val labelPaint = android.graphics.Paint().apply {
                color = Muted.toArgb()
                textSize = 9.sp.toPx()
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
                textAlign = android.graphics.Paint.Align.CENTER
            }
            drawContext.canvas.nativeCanvas.drawText(
                dateText,
                offset.x,
                height - 4.dp.toPx(),
                labelPaint
            )
        }
    }
}

@Composable
fun MuscleRadarChart(
    data: Map<String, Int>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.semantics { contentDescription = "Graphique radar de la répartition du travail musculaire" }) {
        val muscles = listOf("Pectoraux", "Dos", "Épaules", "Quadriceps", "Ischio-jambiers", "Biceps", "Triceps", "Abdominaux")
        val maxVal = data.values.maxOrNull() ?: 1
        val maxSets = if (maxVal == 0) 1 else maxVal

        val center = Offset(size.width / 2, size.height / 2)
        val minDimension = minOf(size.width, size.height)
        
        // Slightly reduced radius to allow comfortable, unclipped label placement
        val maxRadius = minDimension / 2 * 0.62f

        // ==========================================
        // 1. Stylized Cyber holographic body croquis
        // ==========================================
        val blueprintColor = BorderSoft.copy(alpha = 0.25f)
        
        // Head
        drawCircle(
            color = blueprintColor,
            radius = maxRadius * 0.08f,
            center = Offset(center.x, center.y - maxRadius * 0.42f),
            style = Stroke(width = 1.dp.toPx())
        )
        // Neck
        drawLine(
            color = blueprintColor,
            start = Offset(center.x, center.y - maxRadius * 0.34f),
            end = Offset(center.x, center.y - maxRadius * 0.30f),
            strokeWidth = 1.dp.toPx()
        )
        // Shoulders
        drawLine(
            color = blueprintColor,
            start = Offset(center.x - maxRadius * 0.20f, center.y - maxRadius * 0.30f),
            end = Offset(center.x + maxRadius * 0.20f, center.y - maxRadius * 0.30f),
            strokeWidth = 1.dp.toPx()
        )
        // Torso / Chest Left
        drawLine(
            color = blueprintColor,
            start = Offset(center.x - maxRadius * 0.20f, center.y - maxRadius * 0.30f),
            end = Offset(center.x - maxRadius * 0.12f, center.y + maxRadius * 0.08f),
            strokeWidth = 1.dp.toPx()
        )
        // Torso / Chest Right
        drawLine(
            color = blueprintColor,
            start = Offset(center.x + maxRadius * 0.20f, center.y - maxRadius * 0.30f),
            end = Offset(center.x + maxRadius * 0.12f, center.y + maxRadius * 0.08f),
            strokeWidth = 1.dp.toPx()
        )
        // Hips Left
        drawLine(
            color = blueprintColor,
            start = Offset(center.x - maxRadius * 0.12f, center.y + maxRadius * 0.08f),
            end = Offset(center.x - maxRadius * 0.08f, center.y + maxRadius * 0.18f),
            strokeWidth = 1.dp.toPx()
        )
        // Hips Right
        drawLine(
            color = blueprintColor,
            start = Offset(center.x + maxRadius * 0.12f, center.y + maxRadius * 0.08f),
            end = Offset(center.x + maxRadius * 0.08f, center.y + maxRadius * 0.18f),
            strokeWidth = 1.dp.toPx()
        )
        // Waist
        drawLine(
            color = blueprintColor,
            start = Offset(center.x - maxRadius * 0.08f, center.y + maxRadius * 0.18f),
            end = Offset(center.x + maxRadius * 0.08f, center.y + maxRadius * 0.18f),
            strokeWidth = 1.dp.toPx()
        )
        // Left Arm (upper & forearm)
        drawLine(
            color = blueprintColor,
            start = Offset(center.x - maxRadius * 0.20f, center.y - maxRadius * 0.30f),
            end = Offset(center.x - maxRadius * 0.25f, center.y - maxRadius * 0.08f),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = blueprintColor,
            start = Offset(center.x - maxRadius * 0.25f, center.y - maxRadius * 0.08f),
            end = Offset(center.x - maxRadius * 0.28f, center.y + maxRadius * 0.12f),
            strokeWidth = 1.dp.toPx()
        )
        // Right Arm (upper & forearm)
        drawLine(
            color = blueprintColor,
            start = Offset(center.x + maxRadius * 0.20f, center.y - maxRadius * 0.30f),
            end = Offset(center.x + maxRadius * 0.25f, center.y - maxRadius * 0.08f),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = blueprintColor,
            start = Offset(center.x + maxRadius * 0.25f, center.y - maxRadius * 0.08f),
            end = Offset(center.x + maxRadius * 0.28f, center.y + maxRadius * 0.12f),
            strokeWidth = 1.dp.toPx()
        )
        // Left Leg (thigh & calf)
        drawLine(
            color = blueprintColor,
            start = Offset(center.x - maxRadius * 0.07f, center.y + maxRadius * 0.18f),
            end = Offset(center.x - maxRadius * 0.09f, center.y + maxRadius * 0.42f),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = blueprintColor,
            start = Offset(center.x - maxRadius * 0.09f, center.y + maxRadius * 0.42f),
            end = Offset(center.x - maxRadius * 0.10f, center.y + maxRadius * 0.62f),
            strokeWidth = 1.dp.toPx()
        )
        // Right Leg (thigh & calf)
        drawLine(
            color = blueprintColor,
            start = Offset(center.x + maxRadius * 0.07f, center.y + maxRadius * 0.18f),
            end = Offset(center.x + maxRadius * 0.09f, center.y + maxRadius * 0.42f),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = blueprintColor,
            start = Offset(center.x + maxRadius * 0.09f, center.y + maxRadius * 0.42f),
            end = Offset(center.x + maxRadius * 0.10f, center.y + maxRadius * 0.62f),
            strokeWidth = 1.dp.toPx()
        )

        // Concentric octagon grids
        val rings = 4
        for (r in 1..rings) {
            val radius = maxRadius * (r.toFloat() / rings)
            val octagonPath = Path()
            for (i in muscles.indices) {
                val angle = i * (2 * Math.PI / muscles.size) - Math.PI / 2
                val x = center.x + radius * cos(angle).toFloat()
                val y = center.y + radius * sin(angle).toFloat()
                
                if (i == 0) octagonPath.moveTo(x, y) else octagonPath.lineTo(x, y)
            }
            octagonPath.close()
            drawPath(
                path = octagonPath,
                color = BorderSoft,
                style = Stroke(width = 1.dp.toPx())
            )
        }

        // Draw axes lines from center
        for (i in muscles.indices) {
            val angle = i * (2 * Math.PI / muscles.size) - Math.PI / 2
            val endX = center.x + maxRadius * cos(angle).toFloat()
            val endY = center.y + maxRadius * sin(angle).toFloat()
            
            drawLine(
                color = BorderSoft,
                start = center,
                end = Offset(endX, endY),
                strokeWidth = 1.dp.toPx()
            )
        }

        // Draw data filled polygon
        val polygonPath = Path()
        var polygonStarted = false

        for (i in muscles.indices) {
            val muscle = muscles[i]
            val completedSets = data[muscle] ?: 0
            val intensity = completedSets.toFloat() / maxSets
            val radius = maxRadius * intensity.coerceAtLeast(0.08f) // Ensure minimum small polygon so it plots

            val angle = i * (2 * Math.PI / muscles.size) - Math.PI / 2
            val x = center.x + radius * cos(angle).toFloat()
            val y = center.y + radius * sin(angle).toFloat()

            if (!polygonStarted) {
                polygonPath.moveTo(x, y)
                polygonStarted = true
            } else {
                polygonPath.lineTo(x, y)
            }
        }
        polygonPath.close()

        // Create premium radial gradient: red-pink (Accent) for low worked, orange (AccentSecondary) for highly worked
        val radarBrush = Brush.radialGradient(
            colors = listOf(
                Accent.copy(alpha = 0.15f),       // Center (reddish)
                AccentSecondary.copy(alpha = 0.45f) // Outer (orangey)
            ),
            center = center,
            radius = maxRadius
        )

        // Radial gradient for boundary stroke
        val strokeBrush = Brush.radialGradient(
            colors = listOf(
                Accent,
                AccentSecondary
            ),
            center = center,
            radius = maxRadius
        )

        // 1. Draw solid filled area using radial gradient brush
        drawPath(
            path = polygonPath,
            brush = radarBrush
        )

        // 2. Draw outer border stroke using radial gradient brush
        drawPath(
            path = polygonPath,
            brush = strokeBrush,
            style = Stroke(width = 2.5.dp.toPx())
        )

        // ==========================================
        // 5. Dynamic text labels around vertices (Sentence Case, unobtrusive)
        // ==========================================
        for (i in muscles.indices) {
            val muscle = muscles[i]
            val completedSets = data[muscle] ?: 0
            val angle = i * (2 * Math.PI / muscles.size) - Math.PI / 2
            
            val labelRadius = maxRadius + 14.dp.toPx()
            val x = center.x + labelRadius * cos(angle).toFloat()
            val y = center.y + labelRadius * sin(angle).toFloat()
            
            val labelText = muscle
            
            val labelPaint = android.graphics.Paint().apply {
                color = (if (completedSets > 0) Fg2 else Muted).toArgb()
                textSize = 9.sp.toPx()
                typeface = android.graphics.Typeface.create(
                    android.graphics.Typeface.DEFAULT, 
                    if (completedSets > 0) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL
                )
                textAlign = when {
                    cos(angle) > 0.1 -> android.graphics.Paint.Align.LEFT
                    cos(angle) < -0.1 -> android.graphics.Paint.Align.RIGHT
                    else -> android.graphics.Paint.Align.CENTER
                }
            }

            // Adjust vertical offset for top and bottom axes to avoid overlapping
            val yOffset = when {
                sin(angle) < -0.9 -> -4.dp.toPx()  // Top axis (Pectoraux)
                sin(angle) > 0.9 -> 12.dp.toPx()   // Bottom axis (Ischio-jambiers)
                else -> 3.dp.toPx()
            }

            drawContext.canvas.nativeCanvas.drawText(
                labelText,
                x,
                y + yOffset,
                labelPaint
            )
        }
    }
}

@Composable
fun MuscleLegendItem(
    name: String,
    count: Int,
    modifier: Modifier = Modifier
) {
    // If not worked: grey dot. If little worked: red dot. If highly worked: orange dot.
    val dotColor = when {
        count == 0 -> Muted.copy(alpha = 0.3f)
        count < 3 -> Accent
        else -> AccentSecondary
    }
    
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Surface)
            .border(1.dp, BorderSoft, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = name,
                color = if (count > 0) Fg2 else Muted,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        // Vertical divider to separate label from value
        Box(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .width(1.dp)
                .height(12.dp)
                .background(BorderSoft)
        )
        
        Text(
            text = if (count > 0) "$count s." else "-",
            color = if (count > 0) Color.White else Muted,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
        )
    }
}

@Composable
fun SessionVolumesBarChart(
    volumes: List<Pair<String, Double>>,
    weightUnit: WeightUnit = WeightUnit.KG,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.semantics { contentDescription = "Graphique du volume total par séance" }) {
        val maxVolume = volumes.maxOfOrNull { it.second } ?: 0.0
        val maxVolumeVal = if (maxVolume == 0.0) 1.0 else maxVolume

        val barCount = volumes.size
        val width = size.width
        val height = size.height

        val labelHeight = 24.dp.toPx()
        val valueHeight = 20.dp.toPx()
        val chartHeight = height - labelHeight - valueHeight

        // Dynamic bar width so that if there are fewer than 6, it looks very balanced
        val barWidth = (width / (barCount + (barCount + 1) * 0.5f)).coerceIn(16.dp.toPx(), 48.dp.toPx())
        val spacing = (width - (barWidth * barCount)) / (barCount + 1)
        val cornerRadius = 4.dp.toPx()

        volumes.forEachIndexed { index, pair ->
            val label = pair.first
            val volume = pair.second
            
            val barHeight = if (maxVolumeVal == 0.0) 0f else (chartHeight * (volume / maxVolumeVal)).toFloat().coerceAtLeast(6.dp.toPx())
            val left = spacing + index * (barWidth + spacing)
            val top = valueHeight + (chartHeight - barHeight)

            // Draw rounded bar with PremiumGradient
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(AccentSecondary, Accent)
                ),
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(cornerRadius, cornerRadius)
            )

            // Draw volume text above the bar
            val volumeText = MetricFormatter.tonnage(volume, weightUnit)

            val valuePaint = android.graphics.Paint().apply {
                color = Fg.toArgb()
                textSize = 9.sp.toPx()
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                textAlign = android.graphics.Paint.Align.CENTER
            }

            drawContext.canvas.nativeCanvas.drawText(
                volumeText,
                left + barWidth / 2,
                top - 6.dp.toPx(),
                valuePaint
            )

            // Draw the session name/date below the bar
            val labelPaint = android.graphics.Paint().apply {
                color = Muted.toArgb()
                textSize = 9.sp.toPx()
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
                textAlign = android.graphics.Paint.Align.CENTER
            }

            drawContext.canvas.nativeCanvas.drawText(
                label,
                left + barWidth / 2,
                height - 4.dp.toPx(),
                labelPaint
            )
        }
    }
}
