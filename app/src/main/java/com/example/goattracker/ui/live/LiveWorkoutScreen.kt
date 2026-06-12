package com.example.goattracker.ui.live

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.goattracker.data.local.RoomDataRepository
import com.example.goattracker.domain.MetricFormatter
import com.example.goattracker.domain.model.*
import com.example.goattracker.theme.*
import com.example.goattracker.ui.components.AppNumberField
import com.example.goattracker.ui.components.AppTextField
import com.example.goattracker.ui.main.CategoryChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveWorkoutScreen(
    sessionId: String?,
    onSessionExit: () -> Unit,
    onCreateExercise: () -> Unit,
    /** Saved session id after "Terminer" — the navigation layer opens the celebration with it. */
    onSessionFinished: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val viewModel: LiveWorkoutViewModel = viewModel {
        LiveWorkoutViewModel(
            dataRepository = RoomDataRepository.getInstance(context),
            restTimer = AndroidRestTimer(context)
        )
    }

    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Back / predictive-back no longer ENDS the session — it MINIMIZES it. Popping the live entry
    // returns to the previous screen, where the persistent mini-player keeps the session in view and
    // in reach. Ending the session still happens only through "Terminer" (save or discard).
    BackHandler(enabled = true) {
        onSessionExit()
    }

    // Start a fresh session when the screen is first displayed
    // Start a fresh session only if one doesn't already exist
    // (protects against recomposition or config change re-triggering)
    LaunchedEffect(Unit) {
        // Resume a persisted in-progress session (e.g. after process death) or start a fresh one.
        viewModel.startOrResumeSession()
    }

    // The bottom "Ajouter un exercice" control lives in the navigation-root slot (the morphing
    // mini-player pill), outside this screen — it signals through the controller's uiEvents.
    LaunchedEffect(Unit) {
        ActiveSessionController.getInstance(context).uiEvents.collect { action ->
            if (action is SessionAction.AddExercise) {
                viewModel.setExercisePickerOpen(true)
            }
        }
    }

    // Dynamic request for POST_NOTIFICATIONS permission on Android 13+
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
        ) { _ -> }
        LaunchedEffect(Unit) {
            val permissionCheck = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                "android.permission.POST_NOTIFICATIONS"
            )
            if (permissionCheck != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch("android.permission.POST_NOTIFICATIONS")
            }
        }
    }

    // Vibration is now handled by RestTimerManager/RestTimerReceiver
    // (works even when app is in background or killed)
    // The UI only uses isRestTimerVibrating for visual effects (red state)


    Scaffold(
        topBar = {
            // ========== HEADER SINGLETON (Réactif) ==========
            SessionHeader(
                elapsedSeconds = state.elapsedSeconds,
                plannedExercisesCount = state.plannedExercisesCount,
                plannedSetsCount = state.plannedSetsCount,
                completedExercisesCount = state.completedExercisesCount,
                completedSetsCount = state.completedSetsCount,
                onFinishClick = { viewModel.requestFinishSession() }
            )
        },
        // No bottomBar: "Ajouter un exercice" is hosted by the navigation-root bottom slot (the
        // mini-player pill morphs into it while this screen is on top).
        containerColor = Bg
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                // The Scaffold no longer carries an ime-aware bottom bar, so the content handles
                // the keyboard inset itself (set fields must scroll clear of the IME).
                .imePadding()
        ) {

            // ========== REST TIMER ZONE (sous le header) ==========
            AnimatedVisibility(
                visible = state.timerRemainingSeconds != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                val remainingSeconds = state.timerRemainingSeconds ?: 0
                val minutes = remainingSeconds / 60
                val seconds = remainingSeconds % 60
                val timeText = String.format("%02d:%02d", minutes, seconds)
                val isVibrating = state.isRestTimerVibrating

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isVibrating) Danger.copy(alpha = 0.12f) else SurfaceElevated)
                        .border(
                            width = 1.dp,
                            color = if (isVibrating) Danger else AccentSecondary.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(0.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Repos",
                            color = Muted,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = timeText,
                            color = if (isVibrating) Danger else AccentSecondary,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp
                            )
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { viewModel.acknowledgeRestTimer() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isVibrating) Danger else Surface
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "Passer",
                                color = Fg,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // ========== EXERCISES LIST ==========
            val exercises = state.activeSession?.exercises ?: emptyList()

            if (exercises.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Empty Session",
                            tint = Muted,
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = "Votre séance est vide.\nAjoutez des exercices pour commencer !",
                            color = Muted,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(exercises) { exerciseSession ->
                        val isFullyCompleted = exerciseSession.sets.isNotEmpty() &&
                                exerciseSession.sets.all { it.isCompleted }

                        ExerciseSessionCard(
                            session = exerciseSession,
                            userProfile = state.userProfile,
                            isFullyCompleted = isFullyCompleted,
                            onAddSet = { viewModel.addSetToExercise(exerciseSession.exercise.id) },
                            onDeleteSet = { setId -> viewModel.deleteSetFromExercise(exerciseSession.exercise.id, setId) },
                            onUpdateSetValues = { setId, weight, reps, durationSeconds, distanceKm ->
                                viewModel.updateSetValues(exerciseSession.exercise.id, setId, weight, reps, durationSeconds, distanceKm)
                            },
                            onToggleSet = { setId ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.toggleSetCompletion(exerciseSession.exercise.id, setId)
                            },
                            onRemoveExercise = { viewModel.removeExerciseFromSession(exerciseSession.exercise.id) }
                        )
                    }

                    // Bottom clearance so the last exercise card can scroll fully above the
                    // navigation-root pill (56dp) + its margins.
                    item {
                        Spacer(modifier = Modifier.height(104.dp))
                    }
                }
            }
        }
    }
    // ========== FINISH SESSION MODAL ==========
    if (state.isFinishModalOpen) {
        Dialog(onDismissRequest = { viewModel.dismissFinishModal() }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Surface),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .border(1.dp, Border, RoundedCornerShape(20.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header / Title
                    Text(
                        text = "Terminer la séance ?",
                        color = Fg,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        ),
                        textAlign = TextAlign.Center
                    )

                    // Workout summary card/visual box
                    val elapsed = state.elapsedSeconds
                    val h = elapsed / 3600
                    val m = (elapsed % 3600) / 60
                    val durationText = if (h > 0) "${h}h ${m}min" else "${m} min"

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SurfaceElevated, RoundedCornerShape(12.dp))
                            .border(1.dp, BorderSoft, RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "RÉCAPITULATIF DE LA SÉANCE",
                            color = Muted,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Duration info
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Durée",
                                    tint = AccentSecondary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = durationText,
                                    color = Fg,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "Durée",
                                    color = Muted,
                                    fontSize = 11.sp
                                )
                            }

                            // Divider
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(32.dp)
                                    .background(Border)
                            )

                            // Exercises info
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "Exercices",
                                    tint = Accent,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                     text = "${state.completedExercisesCount}",
                                     color = Fg,
                                     fontWeight = FontWeight.Bold,
                                     fontSize = 15.sp
                                 )
                                 Text(
                                     text = if (state.completedExercisesCount > 1) "Exercices" else "Exercice",
                                     color = Muted,
                                     fontSize = 11.sp
                                 )
                            }

                            // Divider
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(32.dp)
                                    .background(Border)
                            )

                            // Sets info
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Séries",
                                    tint = Success,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                     text = "${state.completedSetsCount}",
                                     color = Fg,
                                     fontWeight = FontWeight.Bold,
                                     fontSize = 15.sp
                                 )
                                 Text(
                                     text = if (state.completedSetsCount > 1) "Séries" else "Série",
                                     color = Muted,
                                     fontSize = 11.sp
                                 )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Buttons
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Button 1: Enregistrer (Save)
                        Button(
                            onClick = {
                                val finishedId = viewModel.confirmSaveSession()
                                // Celebrate when something was saved; an empty session just exits.
                                if (finishedId != null) onSessionFinished(finishedId) else onSessionExit()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            contentPadding = PaddingValues(vertical = 14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .background(PremiumGradient, RoundedCornerShape(12.dp)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Enregistrer la séance", color = Fg, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }

                        // Button 2: Continuer la séance (Resume)
                        OutlinedButton(
                            onClick = { viewModel.dismissFinishModal() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                                brush = Brush.linearGradient(listOf(Border, Border))
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Continuer la séance", color = Fg2, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }

                        // Button 3: Supprimer la séance (Discard/Cancel)
                        TextButton(
                            onClick = {
                                viewModel.discardSession()
                                onSessionExit()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                        ) {
                            Text("Annuler et supprimer la séance", color = Danger, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }

    // ========== EXERCISE PICKER DIALOG (unchanged) ==========
    if (state.isExercisePickerOpen) {
        Dialog(
            onDismissRequest = { viewModel.setExercisePickerOpen(false) },
            // decorFitsSystemWindows = false lets imePadding() react to the keyboard inside the
            // dialog window so the list/search rise above it; usePlatformDefaultWidth = false lets
            // us control the width ourselves.
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.85f)
                    .border(1.dp, Border, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Ajouter un exercice",
                            color = Fg,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        IconButton(onClick = { viewModel.setExercisePickerOpen(false) }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Fg)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    var pickerSearchQuery by remember { mutableStateOf("") }
                    var pickerCategoryFilter by remember { mutableStateOf<ExerciseCategory?>(null) }

                    AppTextField(
                        value = pickerSearchQuery,
                        onValueChange = { pickerSearchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = "Rechercher un exercice...",
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Muted) },
                        capitalization = KeyboardCapitalization.None,
                        containerColor = SurfaceElevated
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CategoryChip(
                            text = "Tous",
                            isSelected = pickerCategoryFilter == null,
                            onClick = { pickerCategoryFilter = null }
                        )
                        ExerciseCategory.values().forEach { category ->
                            CategoryChip(
                                text = category.displayName,
                                isSelected = pickerCategoryFilter == category,
                                onClick = { pickerCategoryFilter = category }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Create a brand-new exercise without leaving the session: navigates to the full
                    // create screen; on save we return here and the new exercise is auto-added.
                    OutlinedButton(
                        onClick = {
                            viewModel.prepareAutoAddOnReturn()
                            viewModel.setExercisePickerOpen(false)
                            onCreateExercise()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                            brush = Brush.linearGradient(listOf(Accent, Accent))
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Accent)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Créer un nouvel exercice", color = Accent, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val filteredPickerList = state.availableExercises.filter { exercise ->
                        val matchesQuery = exercise.name.contains(pickerSearchQuery, ignoreCase = true) ||
                                exercise.primaryMuscle.contains(pickerSearchQuery, ignoreCase = true)
                        val matchesCategory = pickerCategoryFilter == null || exercise.category == pickerCategoryFilter
                        matchesQuery && matchesCategory
                    }

                    if (filteredPickerList.isEmpty()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text("Aucun exercice trouvé", color = Muted)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredPickerList) { exercise ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(SurfaceElevated)
                                        .border(1.dp, BorderSoft, RoundedCornerShape(8.dp))
                                        .clickable {
                                            viewModel.addExerciseToSession(exercise)
                                            viewModel.setExercisePickerOpen(false)
                                        }
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(exercise.name, color = Fg, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = "${exercise.category.displayName} • ${exercise.primaryMuscle}",
                                            color = Muted,
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
                                        )
                                    }
                                    Icon(Icons.Default.Add, contentDescription = "Add", tint = Accent)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ========== SESSION HEADER COMPOSABLE ==========

@Composable
fun SessionHeader(
    elapsedSeconds: Int,
    plannedExercisesCount: Int,
    plannedSetsCount: Int,
    completedExercisesCount: Int,
    completedSetsCount: Int,
    onFinishClick: () -> Unit
) {
    val hours = elapsedSeconds / 3600
    val minutes = (elapsedSeconds % 3600) / 60
    val seconds = elapsedSeconds % 60
    val timerText = String.format("%02d:%02d:%02d", hours, minutes, seconds)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Timer global
            Text(
                text = timerText,
                color = Fg,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 32.sp,
                    letterSpacing = 2.sp
                )
            )

            // Bouton Terminer
            Button(
                onClick = onFinishClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                modifier = Modifier
                    .border(1.dp, Border, RoundedCornerShape(8.dp))
                    .background(PremiumGradient, RoundedCornerShape(8.dp))
            ) {
                Text("Terminer", color = Fg, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Ligne 1 : Prévu (Muted)
        Text(
            text = "$plannedExercisesCount exercice${if (plannedExercisesCount > 1) "s" else ""} prévu${if (plannedExercisesCount > 1) "s" else ""} • $plannedSetsCount série${if (plannedSetsCount > 1) "s" else ""} prévue${if (plannedSetsCount > 1) "s" else ""}",
            color = Muted,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp
            )
        )
        Spacer(modifier = Modifier.height(2.dp))
        // Ligne 2 : Réalisé (Brillant / Success)
        Text(
            text = "$completedExercisesCount exercice${if (completedExercisesCount > 1) "s" else ""} fait${if (completedExercisesCount > 1) "s" else ""} • $completedSetsCount série${if (completedSetsCount > 1) "s" else ""} complétée${if (completedSetsCount > 1) "s" else ""}",
            color = Success,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        )
    }
}

// ========== EXERCISE SESSION CARD (with completion effect) ==========

@Composable
fun ExerciseSessionCard(
    session: ExerciseSession,
    userProfile: UserProfile,
    isFullyCompleted: Boolean,
    onAddSet: () -> Unit,
    onDeleteSet: (String) -> Unit,
    onUpdateSetValues: (String, Double?, Int?, Int?, Double?) -> Unit,
    onToggleSet: (String) -> Unit,
    onRemoveExercise: () -> Unit
) {
    val borderColor = if (isFullyCompleted) Success else Border
    val cardBg = if (isFullyCompleted) Success.copy(alpha = 0.06f) else SurfaceElevated

    var isExplicitlyExpanded by remember(isFullyCompleted) { mutableStateOf(!isFullyCompleted) }

    Card(
        colors = CardDefaults.cardColors(containerColor = cardBg),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isFullyCompleted) 1.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .then(
                if (isFullyCompleted && !isExplicitlyExpanded) {
                    Modifier.clickable { isExplicitlyExpanded = true }
                } else {
                    Modifier
                }
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Header card row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.exercise.name,
                        color = Fg,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    if (isExplicitlyExpanded) {
                        Text(
                            text = "${session.exercise.category.displayName} • ${session.exercise.primaryMuscle}",
                            color = Muted,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isFullyCompleted) {
                        // Badge "Terminé"
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Success.copy(alpha = 0.15f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "✓ Terminé",
                                color = Success,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            )
                        }

                        if (isExplicitlyExpanded) {
                            // When expanded and fully checked: a chevron to collapse again
                            IconButton(onClick = { isExplicitlyExpanded = false }) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowUp,
                                    contentDescription = "Collapse",
                                    tint = Success
                                )
                            }
                        } else {
                            // When collapsed: a chevron to expand
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Expand",
                                tint = Success,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    } else {
                        // Not completed: show delete button
                        IconButton(onClick = onRemoveExercise) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove Exercise", tint = Muted)
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = isExplicitlyExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))

                    // Sets Table Column Labels — adapted to what the type actually captures
                    val (col2Label, col3Label) = when (session.exercise.trackingType) {
                        TrackingType.WEIGHT_REPS -> userProfile.weightUnit.suffix.uppercase() to "REPS"
                        TrackingType.BODYWEIGHT_REPS -> "CHARGE" to "REPS"
                        TrackingType.TIME -> "SECONDES" to ""
                        TrackingType.DISTANCE -> "KM" to "MIN"
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("SÉRIE", color = Muted, fontWeight = FontWeight.Bold, fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(0.22f))
                        Text(col2Label, color = Muted, fontWeight = FontWeight.Bold, fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(0.33f))
                        Text(col3Label, color = Muted, fontWeight = FontWeight.Bold, fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(0.20f))
                        Text("CHECK", color = Muted, fontWeight = FontWeight.Bold, fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(0.25f))
                    }

                    // Sets List Rows
                    session.sets.forEach { set ->
                        SetRowItem(
                            set = set,
                            trackingType = session.exercise.trackingType,
                            userProfile = userProfile,
                            onDelete = { onDeleteSet(set.id) },
                            onUpdateValues = { weight, reps, durationSeconds, distanceKm ->
                                onUpdateSetValues(set.id, weight, reps, durationSeconds, distanceKm)
                            },
                            onToggle = { onToggleSet(set.id) }
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Add Set Text Button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAddSet() }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Set", tint = Accent, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Ajouter une série",
                            color = Accent,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}

// ========== SET ROW ITEM ==========

/** Editable representation of a decimal value: dot separator, no trailing ".0", empty when unset. */
private fun Double.toEditableString(): String = when {
    this <= 0.0 -> ""
    this % 1.0 == 0.0 -> toInt().toString()
    else -> toString()
}

/**
 * Weight as shown in an editable field, in the user's unit, rounded to 2 decimals. The rounding is
 * what keeps the kg↔lbs round-trip stable while typing: 100 lbs stored as 45.359237 kg would
 * otherwise re-display as "100.00000000000001".
 */
private fun editableWeight(kg: Double, unit: WeightUnit): String =
    (kotlin.math.round(unit.fromKg(kg) * 100) / 100.0).toEditableString()

/** Tolerant decimal parse: accepts both "72.5" and "72,5" (French numeric keyboards emit commas). */
private fun String.parseDecimal(): Double? = replace(',', '.').toDoubleOrNull()

@Composable
fun SetRowItem(
    set: WorkoutSet,
    trackingType: TrackingType,
    userProfile: UserProfile,
    onDelete: () -> Unit,
    onUpdateValues: (Double?, Int?, Int?, Double?) -> Unit,
    onToggle: () -> Unit
) {
    val completedBg = if (set.isCompleted) Success.copy(alpha = 0.08f) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(completedBg)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Set index circle pill
        Box(
            modifier = Modifier
                .weight(0.22f)
                .height(32.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Surface),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = set.setNumber.toString(),
                color = if (set.isCompleted) Success else Muted,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp
            )
        }

        // Column 2: load (weight types) / seconds (time) / distance (cardio)
        Box(
            modifier = Modifier
                .weight(0.33f)
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            when (trackingType) {
                TrackingType.WEIGHT_REPS -> {
                    val unit = userProfile.weightUnit
                    // Edited in the user's unit, stored in kg. Decimal-preserving: the old
                    // `weight.toInt()` displayed a 22.5 kg set as "22".
                    var weightText by remember(set.weight, unit) {
                        mutableStateOf(editableWeight(set.weight, unit))
                    }
                    AppNumberField(
                        value = weightText,
                        onValueChange = {
                            weightText = it
                            it.parseDecimal()?.let { v -> onUpdateValues(unit.toKg(v), null, null, null) }
                        },
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                    )
                }
                TrackingType.BODYWEIGHT_REPS -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Surface)
                            .border(1.dp, BorderSoft, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        // Show the actual load when the body weight is known: it IS the charge moved.
                        val bw = userProfile.bodyWeightKg
                        Text(
                            text = if (bw != null) "PDC • ${MetricFormatter.weight(bw, userProfile.weightUnit)}" else "PDC",
                            color = Muted,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
                TrackingType.TIME -> {
                    // Seconds (not minutes): lossless and lets sub-minute durations (e.g. a 45s plank)
                    // be entered. Wired to the ViewModel so the value is actually persisted (audit P0-2).
                    var secondsText by remember(set.durationSeconds) {
                        mutableStateOf(if (set.durationSeconds > 0) set.durationSeconds.toString() else "")
                    }
                    AppNumberField(
                        value = secondsText,
                        onValueChange = {
                            secondsText = it
                            it.toIntOrNull()?.let { secs -> onUpdateValues(null, null, secs, null) }
                        }
                    )
                }
                TrackingType.DISTANCE -> {
                    var distText by remember(set.distanceKm) {
                        mutableStateOf(set.distanceKm.toEditableString())
                    }
                    AppNumberField(
                        value = distText,
                        onValueChange = {
                            distText = it
                            it.parseDecimal()?.let { km -> onUpdateValues(null, null, null, km) }
                        },
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                    )
                }
            }
        }

        // Column 3: reps for rep-based types, duration (minutes) for cardio, empty for TIME —
        // the column is kept either way so rows stay aligned with the header.
        Box(
            modifier = Modifier
                .weight(0.20f)
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            when (trackingType) {
                TrackingType.WEIGHT_REPS, TrackingType.BODYWEIGHT_REPS -> {
                    var repsText by remember(set.reps) { mutableStateOf(if (set.reps > 0) set.reps.toString() else "") }
                    AppNumberField(
                        value = repsText,
                        onValueChange = {
                            repsText = it
                            val parsed = it.toIntOrNull()
                            if (parsed != null) {
                                onUpdateValues(null, parsed, null, null)
                            }
                        }
                    )
                }
                TrackingType.DISTANCE -> {
                    // Whole minutes; pace/speed derive from distance + duration. Stored as seconds.
                    var minutesText by remember(set.durationSeconds) {
                        mutableStateOf(if (set.durationSeconds > 0) (set.durationSeconds / 60).toString() else "")
                    }
                    AppNumberField(
                        value = minutesText,
                        onValueChange = {
                            minutesText = it
                            it.toIntOrNull()?.let { min -> onUpdateValues(null, null, min * 60, null) }
                        }
                    )
                }
                TrackingType.TIME -> Unit
            }
        }

        // Validation Check Box Button
        Box(
            modifier = Modifier
                .weight(0.25f),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (set.isCompleted) Success else Surface)
                        .border(
                            width = 1.dp,
                            color = if (set.isCompleted) Success else Border,
                            shape = RoundedCornerShape(6.dp)
                        )
                        .clickable { onToggle() },
                    contentAlignment = Alignment.Center
                ) {
                    if (set.isCompleted) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Completed",
                            tint = Surface,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Delete Set Cross Button
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Delete Set",
                        tint = Muted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
