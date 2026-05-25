package com.example.onairtracker.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.onairtracker.data.DataRepository
import com.example.onairtracker.domain.model.Exercise
import com.example.onairtracker.domain.model.ExerciseCategory
import com.example.onairtracker.domain.model.TrackingType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ExerciseStats(
    val lastWorkoutText: String,
    val volumeHistory: List<Float> // Normalized between 0.0f and 1.0f for the mini-chart
)

class MainScreenViewModel(private val dataRepository: DataRepository) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _selectedCategory = MutableStateFlow<ExerciseCategory?>(null)
    val selectedCategory: StateFlow<ExerciseCategory?> = _selectedCategory

    val uiState: StateFlow<MainScreenUiState> = combine(
        dataRepository.workoutState,
        _searchQuery,
        _selectedCategory
    ) { state, query, category ->
        val filteredExercises = state.exercises.filter { exercise ->
            val matchesQuery = exercise.name.contains(query, ignoreCase = true) ||
                    exercise.primaryMuscle.contains(query, ignoreCase = true)
            val matchesCategory = category == null || exercise.category == category
            matchesQuery && matchesCategory
        }

        val statsMap = filteredExercises.associate { exercise ->
            exercise.id to calculateStatsForExercise(exercise, state.sessions)
        }

        MainScreenUiState.Success(
            exercises = filteredExercises,
            exerciseStats = statsMap
        )
    }.map<MainScreenUiState.Success, MainScreenUiState> { it }
    .catch { 
        emit(MainScreenUiState.Error(it)) 
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainScreenUiState.Loading
    )

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectCategory(category: ExerciseCategory?) {
        _selectedCategory.value = category
    }

    fun deleteExercise(exerciseId: String) {
        viewModelScope.launch {
            dataRepository.deleteExercise(exerciseId)
        }
    }

    private fun calculateStatsForExercise(exercise: Exercise, sessions: List<com.example.onairtracker.domain.model.WorkoutSession>): ExerciseStats {
        // Find all sessions containing this exercise, sorted by time ascending
        val exerciseSessions = sessions.filter { session ->
            session.exercises.any { it.exercise.id == exercise.id }
        }.sortedBy { it.startTime }

        if (exerciseSessions.isEmpty()) {
            return ExerciseStats(
                lastWorkoutText = "Aucun entraînement",
                volumeHistory = emptyList()
            )
        }

        // Get the latest session text
        val latestSession = exerciseSessions.last()
        val latestExerciseSession = latestSession.exercises.first { it.exercise.id == exercise.id }
        
        val completedSets = latestExerciseSession.sets.filter { it.isCompleted }
        val lastWorkoutText = if (completedSets.isNotEmpty()) {
            when (exercise.trackingType) {
                TrackingType.WEIGHT_REPS -> {
                    val sampleSet = completedSets.first()
                    "Dernier: ${completedSets.size}x${sampleSet.reps} • ${sampleSet.weight.toInt()}kg"
                }
                TrackingType.BODYWEIGHT_REPS -> {
                    val sampleSet = completedSets.first()
                    "Dernier: ${completedSets.size}x${sampleSet.reps} • PDC"
                }
                TrackingType.TIME -> {
                    val totalSec = completedSets.sumOf { it.durationSeconds }
                    val mins = totalSec / 60
                    val secs = totalSec % 60
                    String.format("Dernier: %02d:%02d", mins, secs)
                }
                TrackingType.DISTANCE -> {
                    val totalDist = completedSets.sumOf { it.distanceKm }
                    String.format("Dernier: %.2f km", totalDist)
                }
            }
        } else {
            "Aucune série complétée"
        }

        // Compute volume progression of the last 4 workouts
        val last4Sessions = exerciseSessions.takeLast(4)
        val volumes = last4Sessions.map { session ->
            val exSession = session.exercises.first { it.exercise.id == exercise.id }
            exSession.sets.filter { it.isCompleted }.sumOf { set ->
                when (exercise.trackingType) {
                    TrackingType.WEIGHT_REPS -> set.weight * set.reps
                    TrackingType.BODYWEIGHT_REPS -> set.reps.toDouble()
                    TrackingType.TIME -> set.durationSeconds.toDouble()
                    TrackingType.DISTANCE -> set.distanceKm * 1000.0
                }
            }
        }

        // Normalize volumes between 0.1f and 1.0f for bar chart drawing (0.1f minimum so bars are visible)
        val maxVolume = volumes.maxOrNull() ?: 0.0
        val normalizedHistory = volumes.map { vol ->
            if (maxVolume > 0.0) {
                (vol / maxVolume).toFloat().coerceAtLeast(0.1f)
            } else {
                0.1f
            }
        }

        return ExerciseStats(
            lastWorkoutText = lastWorkoutText,
            volumeHistory = normalizedHistory
        )
    }
}

sealed interface MainScreenUiState {
    object Loading : MainScreenUiState
    data class Error(val throwable: Throwable) : MainScreenUiState
    data class Success(
        val exercises: List<Exercise>,
        val exerciseStats: Map<String, ExerciseStats>
    ) : MainScreenUiState
}
