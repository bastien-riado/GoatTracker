package com.example.goattracker.ui.exercise

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.goattracker.data.DefaultDataRepository
import com.example.goattracker.domain.model.Exercise
import com.example.goattracker.domain.model.ExerciseCategory
import com.example.goattracker.domain.model.TrackingType
import com.example.goattracker.ui.components.AppTextField
import com.example.goattracker.domain.model.WorkoutSession
import com.example.goattracker.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(
    exerciseId: String,
    onBackClick: () -> Unit,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // Fix navigation sharing viewmodel bug by injecting unique exerciseId as key
    val viewModel: ExerciseDetailViewModel = viewModel(key = exerciseId) {
        ExerciseDetailViewModel(
            DefaultDataRepository.getInstance(context.filesDir),
            exerciseId
        )
    }

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Declare scroll state at Scaffold level so TopAppBar can read its value for animation
    val scrollState = rememberScrollState()

    val density = LocalDensity.current
    val collapseDistancePx = with(density) { 80.dp.toPx() } // Perfect threshold for classic transition
    val collapseProgress by remember {
        derivedStateOf {
            if (scrollState.maxValue > 0) {
                (scrollState.value.toFloat() / collapseDistancePx).coerceIn(0f, 1f)
            } else {
                0f
            }
        }
    }

    // Auto navigate back if deleted
    LaunchedEffect(state) {
        if (state is ExerciseDetailUiState.Success && (state as ExerciseDetailUiState.Success).isDeleted) {
            onBackClick()
        }
    }

    Scaffold(
        topBar = {
            if (state is ExerciseDetailUiState.Success) {
                val exercise = (state as ExerciseDetailUiState.Success).exercise
                TopAppBar(
                    title = {
                        Text(
                            text = exercise.name,
                            color = Fg,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.graphicsLayer {
                                // Translate vertically: slides up and fades in seamlessly
                                translationY = (1f - collapseProgress) * 30.dp.toPx()
                                alpha = collapseProgress
                            }
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
                    actions = {
                        Box {
                            IconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(SurfaceElevated)
                                    .border(1.dp, Border, CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Menu",
                                    tint = Fg
                                )
                            }

                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                modifier = Modifier.background(SurfaceElevated)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Modifier", color = Fg) },
                                    onClick = {
                                        showMenu = false
                                        onEditClick()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Supprimer", color = Danger) },
                                    onClick = {
                                        showMenu = false
                                        showDeleteDialog = true
                                    }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = if (collapseProgress > 0.01f) Bg.copy(alpha = collapseProgress) else Color.Transparent
                    )
                )
            }
        },
        containerColor = Bg
    ) { innerPadding ->
        when (val uiState = state) {
            is ExerciseDetailUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Accent)
                }
            }
            is ExerciseDetailUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = uiState.message, color = Danger, style = MaterialTheme.typography.titleMedium)
                }
            }
            is ExerciseDetailUiState.Success -> {
                ExerciseDetailContentOverlay(
                    state = uiState,
                    scrollState = scrollState,
                    collapseProgress = collapseProgress,
                    innerPadding = innerPadding,
                    onNotesChange = { viewModel.updateNotes(it) },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    // Safety delete dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Supprimer l'exercice ?", color = Fg, fontWeight = FontWeight.Bold) },
            text = { Text("Cette action est irréversible. Toutes les données associées à cet exercice seront conservées dans vos séances mais l'exercice disparaîtra de la liste.", color = Fg2) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteExercise()
                    }
                ) {
                    Text("Supprimer", color = Danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Annuler", color = Fg)
                }
            },
            containerColor = SurfaceElevated
        )
    }
}

@Composable
fun ExerciseDetailContentOverlay(
    state: ExerciseDetailUiState.Success,
    scrollState: ScrollState,
    collapseProgress: Float,
    innerPadding: PaddingValues,
    onNotesChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val exercise = state.exercise
    var notesText by remember(exercise.notes) { mutableStateOf(exercise.notes) }
    val focusManager = LocalFocusManager.current

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isVisible = true
    }

    Box(
        modifier = modifier
            .background(Bg)
            .imePadding() // Secret to push layout up and keep focused note textfield above the keyboard
    ) {
        // Scrollable Content Column
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                // Padded at bottom by navigation bar to avoid cuts
                .padding(bottom = innerPadding.calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Perfect dynamic top spacing: TopAppBar + Status Bar height to clear floating buttons precisely without gaps
            Spacer(modifier = Modifier.height(innerPadding.calculateTopPadding() + 8.dp))

            // A. Expanded Header Section (Fades and moves out on scroll)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Icon Box
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Accent.copy(alpha = 0.10f))
                            .graphicsLayer {
                                alpha = 1f - collapseProgress
                                scaleX = 1f - 0.2f * collapseProgress
                                scaleY = 1f - 0.2f * collapseProgress
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(36.dp)) {
                            when (exercise.category) {
                                ExerciseCategory.PUSH -> {
                                    drawRect(color = Accent, topLeft = Offset(3.dp.toPx(), 13.5.dp.toPx()), size = Size(6.dp.toPx(), 9.dp.toPx()))
                                    drawRect(color = Accent, topLeft = Offset(27.dp.toPx(), 13.5.dp.toPx()), size = Size(6.dp.toPx(), 9.dp.toPx()))
                                    drawLine(color = Accent, start = Offset(9.dp.toPx(), 18.dp.toPx()), end = Offset(27.dp.toPx(), 18.dp.toPx()), strokeWidth = 3.dp.toPx())
                                }
                                ExerciseCategory.PULL -> {
                                    drawLine(color = Accent, start = Offset(3.dp.toPx(), 6.dp.toPx()), end = Offset(33.dp.toPx(), 6.dp.toPx()), strokeWidth = 3.dp.toPx())
                                    drawCircle(color = Accent, radius = 4.5.dp.toPx(), center = Offset(18.dp.toPx(), 18.dp.toPx()))
                                }
                                ExerciseCategory.LEG -> {
                                    drawCircle(color = Accent, radius = 7.5.dp.toPx(), center = Offset(18.dp.toPx(), 18.dp.toPx()), style = Stroke(width = 3.dp.toPx()))
                                }
                                else -> {
                                    drawCircle(color = Accent, radius = 6.dp.toPx(), center = Offset(18.dp.toPx(), 18.dp.toPx()))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        // Title that fades out and slides up slightly
                        Text(
                            text = exercise.name,
                            color = Fg,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.02).sp
                            ),
                            modifier = Modifier.graphicsLayer {
                                alpha = 1f - collapseProgress
                                translationY = -collapseProgress * 16.dp.toPx()
                            }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        // Badges that fade out
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.graphicsLayer {
                                alpha = 1f - collapseProgress
                            }
                        ) {
                            BadgeTag(text = exercise.category.displayName)
                            BadgeTag(text = exercise.primaryMuscle)
                            BadgeTag(text = exercise.trackingType.displayName, isSecondary = true)
                        }
                    }
                }
            }

            // B. Records Personnels Grid
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn() // Simple fade in animation to match the rest of the application
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel(text = "Records personnels")
                    RecordsGrid(exercise = exercise, state = state)
                }
            }

            // C. Dernière Séance Premium Block (Simplified UI to match user feedback!)
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn() // Simple fade in animation to match the rest of the application
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel(text = "Dernière séance")
                    LastWorkoutPanel(state = state)
                }
            }

            // D. Progress Chart
            if (state.volumeHistory.size >= 2) {
                AnimatedVisibility(
                    visible = isVisible,
                    enter = fadeIn() // Simple fade in animation to match the rest of the application
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionLabel(text = "Surcharge Progressive")
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, BorderSoft, RoundedCornerShape(12.dp)),
                            colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Évolution du volume total",
                                    color = Fg2,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                ExerciseProgressChart(
                                    volumes = state.volumeHistory,
                                    labels = state.volumeHistoryLabels,
                                    trackingType = exercise.trackingType,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(160.dp)
                                )
                            }
                        }
                    }
                }
            }

            // E. Notes & Posture
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn() // Simple fade in animation to match the rest of the application
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel(text = "Notes & Posture")
                    AppTextField(
                        value = notesText,
                        onValueChange = {
                            notesText = it
                            onNotesChange(it)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 90.dp),
                        placeholder = "Position des mains, repères de sécurité, sensations...",
                        singleLine = false,
                        imeAction = ImeAction.Done,
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        shape = RoundedCornerShape(12.dp),
                        containerColor = SurfaceElevated
                    )
                }
            }

            // F. History
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(text = "Historique d'entraînement")
                
                if (state.sessions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Aucune séance enregistrée pour cet exercice", color = Muted, style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    state.sessions.forEach { session ->
                        val sessionExercise = session.exercises.first { it.exercise.id == exercise.id }
                        val dateString = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(Date(session.startTime))
                        
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                                .border(1.dp, BorderSoft, RoundedCornerShape(12.dp)),
                            colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = session.name,
                                        color = Fg,
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = dateString,
                                        color = Muted,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(color = BorderSoft, thickness = 1.dp)
                                Spacer(modifier = Modifier.height(8.dp))

                                sessionExercise.sets.forEach { set ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .clip(CircleShape)
                                                    .background(if (set.isCompleted) Accent.copy(alpha = 0.15f) else Surface),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = set.setNumber.toString(),
                                                    color = if (set.isCompleted) Accent else Muted,
                                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            
                                            val setText = when (exercise.trackingType) {
                                                TrackingType.WEIGHT_REPS -> "${set.reps} reps @ ${set.weight.toInt()} kg"
                                                TrackingType.BODYWEIGHT_REPS -> "${set.reps} reps (PDC)"
                                                TrackingType.TIME -> {
                                                    val m = set.durationSeconds / 60
                                                    val s = set.durationSeconds % 60
                                                    String.format("%02d:%02d", m, s)
                                                }
                                                TrackingType.DISTANCE -> String.format("%.2f km", set.distanceKm)
                                            }
                                            
                                            Text(
                                                text = setText,
                                                color = if (set.isCompleted) Fg2 else Muted,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }

                                        if (set.isCompleted) {
                                            Text(
                                                text = "Validé",
                                                color = Accent,
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            )
                                        } else {
                                            Text(
                                                text = "Non complété",
                                                color = Meta,
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun ExerciseProgressChart(
    volumes: List<Double>,
    labels: List<String>,
    trackingType: TrackingType,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (volumes.size < 2) return@Canvas

        val maxVolume = volumes.maxOrNull() ?: 1.0
        val minVolume = volumes.minOrNull() ?: 0.0
        val valueRange = if (maxVolume == minVolume) 1.0 else maxVolume - minVolume

        val paddingLeft = 24.dp.toPx()
        val paddingRight = 24.dp.toPx()
        val paddingTop = 28.dp.toPx()    // Extra space for numerical values above points
        val paddingBottom = 24.dp.toPx()

        val width = size.width - paddingLeft - paddingRight
        val height = size.height - paddingTop - paddingBottom

        val points = volumes.mapIndexed { index, vol ->
            val fractionX = index.toFloat() / (volumes.size - 1)
            val fractionY = ((vol - minVolume) / valueRange).toFloat()

            val x = paddingLeft + fractionX * width
            val y = paddingTop + (1f - fractionY) * height
            Offset(x, y)
        }

        // Draw dotted background grid lines
        val gridLines = 3
        for (i in 0..gridLines) {
            val y = paddingTop + i * height / gridLines
            drawLine(
                color = BorderSoft,
                start = Offset(paddingLeft, y),
                end = Offset(size.width - paddingRight, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        // Area path (gradient sweep under the line)
        val areaPath = Path().apply {
            moveTo(points.first().x, paddingTop + height)
            points.forEach { lineTo(it.x, it.y) }
            lineTo(points.last().x, paddingTop + height)
            close()
        }

        drawPath(
            path = areaPath,
            brush = Brush.verticalGradient(
                colors = listOf(AccentSecondary.copy(alpha = 0.25f), Color.Transparent),
                startY = paddingTop,
                endY = paddingTop + height
            )
        )

        // Curve lines
        val linePath = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 1 until points.size) {
                lineTo(points[i].x, points[i].y)
            }
        }

        // Glowing horizontal PremiumGradient brush line
        drawPath(
            path = linePath,
            brush = Brush.horizontalGradient(
                colors = listOf(AccentSecondary, Accent),
                startX = paddingLeft,
                endX = size.width - paddingRight
            ),
            style = Stroke(width = 3.dp.toPx())
        )

        // Points with glowing halo and value text labels
        points.forEachIndexed { index, point ->
            val vol = volumes[index]
            
            drawCircle(
                color = AccentSecondary.copy(alpha = 0.35f),
                radius = 8.dp.toPx(),
                center = point
            )
            drawCircle(
                color = Fg,
                radius = 4.dp.toPx(),
                center = point
            )

            // Dynamic format based on tracking type
            val volumeText = when (trackingType) {
                TrackingType.WEIGHT_REPS -> "${vol.toInt()} kg"
                TrackingType.BODYWEIGHT_REPS -> "${vol.toInt()} reps"
                TrackingType.TIME -> {
                    val m = vol.toInt() / 60
                    val s = vol.toInt() % 60
                    String.format("%d:%02d", m, s)
                }
                TrackingType.DISTANCE -> String.format("%.2f km", vol / 1000.0)
            }

            val valuePaint = android.graphics.Paint().apply {
                color = Color.White.toArgb()
                textSize = 9.sp.toPx()
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                textAlign = android.graphics.Paint.Align.CENTER
            }

            drawContext.canvas.nativeCanvas.drawText(
                volumeText,
                point.x,
                point.y - 8.dp.toPx(),
                valuePaint
            )
        }

        // Draw date labels below the chart
        labels.forEachIndexed { index, label ->
            val x = paddingLeft + (index.toFloat() / (labels.size - 1)) * width
            drawContext.canvas.nativeCanvas.drawText(
                label,
                x,
                size.height - 4.dp.toPx(),
                android.graphics.Paint().apply {
                    color = Muted.toArgb()
                    textSize = 9.sp.toPx()
                    textAlign = android.graphics.Paint.Align.CENTER
                    typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                }
            )
        }
    }
}

@Composable
fun BadgeTag(text: String, isSecondary: Boolean = false) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (isSecondary) BorderSoft else Accent.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = if (isSecondary) Muted else Accent,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            )
        )
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = Muted,
        style = MaterialTheme.typography.bodySmall.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            letterSpacing = 0.05.sp
        ),
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
fun RecordsGrid(exercise: Exercise, state: ExerciseDetailUiState.Success) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CompactStatCard(
                title = "Charge Max",
                value = if (state.maxWeight > 0) "${state.maxWeight.toInt()} kg" else "--",
                description = "Série la plus lourde",
                modifier = Modifier.weight(1f)
            )
            
            if (exercise.trackingType == TrackingType.WEIGHT_REPS) {
                CompactStatCard(
                    title = "1RM Estimé",
                    value = if (state.estimatedOneRepMax > 0) String.format("%.1f kg", state.estimatedOneRepMax) else "--",
                    description = "Force max théorique",
                    modifier = Modifier.weight(1f)
                )
            } else {
                CompactStatCard(
                    title = "Séries cumulées",
                    value = "${state.totalSets} séries",
                    description = "Historique cumulé",
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        val volumeMaxText = when (exercise.trackingType) {
            TrackingType.WEIGHT_REPS -> "${state.maxSessionVolume.toInt()} kg"
            TrackingType.BODYWEIGHT_REPS -> "${state.maxSessionVolume.toInt()} reps"
            TrackingType.TIME -> {
                val m = state.maxSessionVolume.toInt() / 60
                val s = state.maxSessionVolume.toInt() % 60
                String.format("%02d:%02d", m, s)
            }
            TrackingType.DISTANCE -> String.format("%.2f km", state.maxSessionVolume / 1000.0)
        }
        
        CompactStatCard(
            title = "Volume Max sur une séance",
            value = volumeMaxText,
            description = "Meilleure performance globale sur une séance",
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun CompactStatCard(
    title: String,
    value: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.border(1.dp, BorderSoft, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                text = title,
                color = Muted,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                color = Fg,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    letterSpacing = (-0.01).sp
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                color = Meta,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp)
            )
        }
    }
}

@Composable
fun LastWorkoutPanel(state: ExerciseDetailUiState.Success) {
    val lastSession = state.lastExerciseSession
    
    if (lastSession == null || lastSession.sets.isEmpty()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderSoft, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
            shape = RoundedCornerShape(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Aucune séance précédente enregistrée",
                    color = Muted,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        return
    }

    val lastWorkoutSession = state.sessions.firstOrNull()
    val dateString = lastWorkoutSession?.let {
        SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(Date(it.startTime))
    } ?: ""

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderSoft, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Effectué le $dateString",
                color = Fg2,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
            )
            HorizontalDivider(color = BorderSoft, thickness = 1.dp)

            // Simply display each set cleanly like in active session exercise cards
            lastSession.sets.forEach { set ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Circular badge with PremiumGradient background and Fg (white) text!
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(PremiumGradient),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = set.setNumber.toString(),
                            color = Fg,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    val performanceText = when (state.exercise.trackingType) {
                        TrackingType.WEIGHT_REPS -> "${set.reps} répétitions à ${set.weight.toInt()}kg"
                        TrackingType.BODYWEIGHT_REPS -> "${set.reps} répétitions au poids de corps"
                        TrackingType.TIME -> {
                            val m = set.durationSeconds / 60
                            val s = set.durationSeconds % 60
                            if (m > 0) {
                                if (s > 0) "$m minutes et $s secondes" else "$m minutes"
                            } else {
                                "$s secondes"
                            }
                        }
                        TrackingType.DISTANCE -> {
                            if (set.distanceKm >= 1.0) {
                                String.format(Locale.US, "%.2f kilomètres", set.distanceKm)
                            } else {
                                "${(set.distanceKm * 1000).toInt()} mètres"
                            }
                        }
                    }
                    
                    Text(
                        text = performanceText,
                        color = Fg,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }
        }
    }
}
