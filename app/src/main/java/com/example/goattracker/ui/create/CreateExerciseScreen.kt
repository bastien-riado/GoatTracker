package com.example.goattracker.ui.create

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.goattracker.data.DefaultDataRepository
import com.example.goattracker.domain.model.ExerciseCategory
import com.example.goattracker.domain.model.TrackingType
import com.example.goattracker.theme.*
import com.example.goattracker.ui.components.AppTextField

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateExerciseScreen(
    exerciseId: String? = null,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uniqueKey = remember(exerciseId) { exerciseId ?: java.util.UUID.randomUUID().toString() }
    val viewModel: CreateExerciseViewModel = viewModel(key = uniqueKey) {
        CreateExerciseViewModel(DefaultDataRepository.getInstance(context.filesDir), exerciseId)
    }

    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Navigate back when the save completes — a one-shot event, so it fires exactly once.
    LaunchedEffect(Unit) {
        viewModel.savedEvents.collect { onBackClick() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (exerciseId != null) "Modifier l'Exercice" else "Créer un Exercice",
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Surface,
                    titleContentColor = Fg
                ),
                modifier = Modifier.border(0.dp, Color.Transparent)
            )
        },
        // No bottomBar: the save button scrolls WITH the form as its last element, so the
        // navigation-root mini-player (only present during an active session) never hides it —
        // extra bottom clearance below keeps it tappable even with the pill docked.
        containerColor = Bg
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 1. Name Input
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FormLabel(text = "Nom de l'exercice")
                AppTextField(
                    value = state.name,
                    onValueChange = { viewModel.updateName(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "ex: Développé Incliné Haltères"
                )
            }

            // 2. Category Selectable Grid
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FormLabel(text = "Catégorie (Mouvement)")
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExerciseCategory.values().forEach { category ->
                        SelectableTag(
                            text = category.displayName,
                            isSelected = state.category == category,
                            onClick = { viewModel.selectCategory(category) }
                        )
                    }
                }
            }

            // 3. Muscle Group Dropdown
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FormLabel(text = "Groupe Musculaire Principal")
                var expanded by remember { mutableStateOf(false) }
                val muscleGroups = listOf(
                    "Pectoraux", "Dos", "Épaules", "Quadriceps",
                    "Ischio-jambiers", "Fessiers", "Mollets", "Biceps", "Triceps", "Abdominaux",
                    // For running/rowing-style exercises; intentionally unmapped on the 3D heatmap.
                    "Cardio"
                )

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    AppTextField(
                        value = state.primaryMuscle,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        placeholder = "Sélectionner un groupe",
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.background(SurfaceElevated)
                    ) {
                        muscleGroups.forEach { muscle ->
                            DropdownMenuItem(
                                text = { Text(muscle, color = Fg) },
                                onClick = {
                                    viewModel.updatePrimaryMuscle(muscle)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            // 4. Tracking Type Selectable Grid
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FormLabel(text = "Type de suivi")
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TrackingType.values().forEach { type ->
                        SelectableTag(
                            text = type.displayName,
                            isSelected = state.trackingType == type,
                            onClick = { viewModel.selectTrackingType(type) }
                        )
                    }
                }
            }

            // 5. Rest Time Configuration
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FormLabel(text = "Temps de repos entre les séries")

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Surface, RoundedCornerShape(12.dp))
                        .border(1.dp, BorderSoft, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Time display with +/- buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Minus button
                        IconButton(
                            onClick = { viewModel.updateRestTime(state.restTimeSeconds - 15) },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(SurfaceElevated)
                                .border(1.dp, Border, CircleShape)
                        ) {
                            Text(
                                text = "−",
                                color = Fg,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(24.dp))

                        // Time display
                        val restMinutes = state.restTimeSeconds / 60
                        val restSeconds = state.restTimeSeconds % 60
                        Text(
                            text = String.format("%d:%02d", restMinutes, restSeconds),
                            color = AccentSecondary,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 32.sp
                            )
                        )

                        Spacer(modifier = Modifier.width(24.dp))

                        // Plus button
                        IconButton(
                            onClick = { viewModel.updateRestTime(state.restTimeSeconds + 15) },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(SurfaceElevated)
                                .border(1.dp, Border, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Augmenter",
                                tint = Fg
                            )
                        }
                    }

                    // Quick presets
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
                    ) {
                        listOf(30, 60, 90, 120, 180).forEach { preset ->
                            val label = "${preset}s"
                            SelectableTag(
                                text = label,
                                isSelected = state.restTimeSeconds == preset,
                                onClick = { viewModel.updateRestTime(preset) }
                            )
                        }
                    }
                }
            }

            // 6. Save button — last element of the form, scrolls with it.
            Button(
                onClick = { viewModel.saveExercise() },
                enabled = state.isSaveEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .border(1.dp, if (state.isSaveEnabled) Border else Color.Transparent, RoundedCornerShape(12.dp)),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    disabledContainerColor = SurfaceElevated
                ),
                contentPadding = PaddingValues(0.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                val buttonBgModifier = if (state.isSaveEnabled) {
                    Modifier.background(PremiumGradient)
                } else {
                    Modifier.background(Color.Transparent)
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(buttonBgModifier),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (exerciseId != null) "Enregistrer les modifications" else "Enregistrer l'exercice",
                        color = if (state.isSaveEnabled) Fg else Muted,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            // Clearance for the session mini-player docked by the navigation root, so the save
            // button is always scrollable above it during an active session.
            Spacer(modifier = Modifier.height(88.dp))
        }
    }
}

@Composable
fun FormLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = Muted,
        style = MaterialTheme.typography.bodySmall.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 0.05.sp
        )
    )
}

@Composable
fun SelectableTag(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) Accent.copy(alpha = 0.15f) else Surface)
            .border(
                width = 1.dp,
                color = if (isSelected) Accent else Border,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isSelected) Accent else Fg,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
            )
        )
    }
}
