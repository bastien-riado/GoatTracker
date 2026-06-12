package com.example.goattracker.ui.main

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import com.example.goattracker.CreateExercise
import com.example.goattracker.LiveWorkout
import com.example.goattracker.Profile
import com.example.goattracker.ExerciseDetail
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.example.goattracker.data.local.RoomDataRepository
import com.example.goattracker.domain.model.Exercise
import com.example.goattracker.ui.components.AppTextField
import com.example.goattracker.domain.model.ExerciseCategory
import com.example.goattracker.domain.model.TrackingType
import com.example.goattracker.theme.*

@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    // When a session is live the navigation-root bottom slot renders the mini-player at this exact
    // spot, so the local "Démarrer une séance" button hides instead of stacking under it.
    hasActiveSession: Boolean = false,
) {
    val context = LocalContext.current
    val viewModel: MainScreenViewModel = viewModel { 
        MainScreenViewModel(RoomDataRepository.getInstance(context))
    }
    
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Bg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Header with OnAir branding and User Avatar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Exercices",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = Fg,
                        letterSpacing = (-0.02).sp
                    )
                )
                
                // Profile Avatar with dynamic outline
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(SurfaceElevated)
                        .border(1.dp, Border, CircleShape)
                        .clickable { onItemClick(Profile) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(android.R.drawable.ic_menu_myplaces),
                        contentDescription = "Profil",
                        tint = Accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // 2. Search bar with Neon accent border focus
            AppTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                placeholder = "Rechercher un exercice...",
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Muted
                    )
                },
                capitalization = KeyboardCapitalization.None
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Category selector tags horizontally scrolling
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // "Tous" chip
                CategoryChip(
                    text = "Tous",
                    isSelected = selectedCategory == null,
                    onClick = { viewModel.selectCategory(null) }
                )
                
                ExerciseCategory.values().forEach { category ->
                    CategoryChip(
                        text = category.displayName,
                        isSelected = selectedCategory == category,
                        onClick = { viewModel.selectCategory(category) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 4. Exercises List
            when (state) {
                is MainScreenUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Accent)
                    }
                }
                is MainScreenUiState.Success -> {
                    val successState = state as MainScreenUiState.Success
                    if (successState.exercises.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                            Text("Aucun exercice trouvé", color = Muted)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(successState.exercises) { exercise ->
                                val stats = successState.exerciseStats[exercise.id] ?: ExerciseStats("Aucun", emptyList())
                                ExerciseCard(
                                    exercise = exercise,
                                    stats = stats,
                                    onClick = { onItemClick(ExerciseDetail(exercise.id)) }
                                )
                            }
                        }
                    }
                }
                is MainScreenUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text("Erreur: ${(state as MainScreenUiState.Error).throwable.message}", color = Danger)
                    }
                }
            }
        }

        // 5. Floating Bottom Navigation Bar (Gradient Session Trigger Button). Hidden entirely
        // (backdrop included) while a session is live: the navigation-root slot draws the
        // mini-player + its own backdrop at this exact spot, and stacking the two fades would
        // double-darken the bottom of the screen.
        if (!hasActiveSession) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Bg.copy(alpha = 0.95f)),
                            startY = 0f
                        )
                    )
                    .padding(16.dp)
            ) {
                Button(
                    onClick = { onItemClick(LiveWorkout(null)) },
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
                            .background(PremiumGradient)
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Start",
                                tint = Fg,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Démarrer une séance",
                                color = Fg,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { onItemClick(CreateExercise()) },
            containerColor = Color.Transparent,
            elevation = FloatingActionButtonDefaults.elevation(0.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 96.dp, end = 16.dp)
                .size(56.dp)
                .border(1.dp, Border, CircleShape)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PremiumGradient, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Créer Exercice",
                    tint = Fg,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
fun CategoryChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(9999.dp))
            .background(if (isSelected) Accent.copy(alpha = 0.15f) else SurfaceElevated)
            .border(
                width = 1.dp,
                color = if (isSelected) Accent else Border,
                shape = RoundedCornerShape(9999.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isSelected) Accent else Fg2,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
            )
        )
    }
}

@Composable
fun ExerciseCard(
    exercise: Exercise,
    stats: ExerciseStats,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceElevated)
            .border(1.dp, BorderSoft, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Exercise icon (pink background box)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Accent.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                // Vector shapes as dynamic icons matching movement
                Canvas(modifier = Modifier.size(24.dp)) {
                    when (exercise.category) {
                        ExerciseCategory.PUSH -> {
                            // Dumbbell-like barbell vector
                            drawRect(color = Accent, topLeft = Offset(2.dp.toPx(), 9.dp.toPx()), size = Size(4.dp.toPx(), 6.dp.toPx()))
                            drawRect(color = Accent, topLeft = Offset(18.dp.toPx(), 9.dp.toPx()), size = Size(4.dp.toPx(), 6.dp.toPx()))
                            drawLine(color = Accent, start = Offset(6.dp.toPx(), 12.dp.toPx()), end = Offset(18.dp.toPx(), 12.dp.toPx()), strokeWidth = 2.dp.toPx())
                        }
                        ExerciseCategory.PULL -> {
                            // Pullup bar / arrow vector
                            drawLine(color = Accent, start = Offset(2.dp.toPx(), 4.dp.toPx()), end = Offset(22.dp.toPx(), 4.dp.toPx()), strokeWidth = 2.dp.toPx())
                            drawCircle(color = Accent, radius = 3.dp.toPx(), center = Offset(12.dp.toPx(), 12.dp.toPx()))
                        }
                        ExerciseCategory.LEG -> {
                            // Squat barbell vector
                            drawCircle(color = Accent, radius = 5.dp.toPx(), center = Offset(12.dp.toPx(), 12.dp.toPx()), style = Stroke(width = 2.dp.toPx()))
                        }
                        else -> {
                            // Cardio/Core general vector
                            drawCircle(color = Accent, radius = 4.dp.toPx(), center = Offset(12.dp.toPx(), 12.dp.toPx()))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Exercise Title and stats
            Column {
                Text(
                    text = exercise.name,
                    color = Fg,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${exercise.category.displayName} • ${exercise.primaryMuscle}",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
                )
                
                Spacer(modifier = Modifier.height(6.dp))

                // Stats and mini bar chart container
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (stats.volumeHistory.isNotEmpty()) {
                        ExerciseMiniChart(
                            history = stats.volumeHistory,
                            modifier = Modifier
                                .width(24.dp)
                                .height(16.dp)
                        )
                    }
                    Text(
                        text = stats.lastWorkoutText,
                        color = Meta,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    )
                }
            }
        }

        // Chevron Arrow right button
        Icon(
            painter = painterResource(android.R.drawable.ic_media_play),
            contentDescription = "Details",
            tint = Muted,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
fun ExerciseMiniChart(
    history: List<Float>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val barWidth = 4.dp.toPx()
        val spacing = 2.dp.toPx()
        val cornerRadius = 2.dp.toPx()
        
        history.forEachIndexed { index, value ->
            val barHeight = size.height * value
            val left = index * (barWidth + spacing)
            val top = size.height - barHeight
            
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(AccentSecondary, Accent)
                ),
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(cornerRadius, cornerRadius)
            )
        }
    }
}
