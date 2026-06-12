package com.example.goattracker.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.goattracker.data.DataRepository
import com.example.goattracker.domain.MetricFormatter
import com.example.goattracker.domain.WorkoutMetrics
import com.example.goattracker.domain.model.Exercise
import com.example.goattracker.domain.model.ExerciseCategory
import com.example.goattracker.domain.model.UserProfile
import com.example.goattracker.domain.model.WorkoutTemplate
import com.example.goattracker.ui.templates.TemplateLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
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
            exercise.id to calculateStatsForExercise(exercise, state.sessions, state.userProfile)
        }

        MainScreenUiState.Success(
            exercises = filteredExercises,
            exerciseStats = statsMap
        )
    }.map<MainScreenUiState.Success, MainScreenUiState> { it }
    // Per-exercise stats are recomputed on every state/query change; run that off the main thread.
    .flowOn(Dispatchers.Default)
    .catch {
        emit(MainScreenUiState.Error(it))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainScreenUiState.Loading
    )

    /** Drives the "Démarrer une séance" chooser: empty list = launch a free session directly. */
    val templates: StateFlow<List<WorkoutTemplate>> = dataRepository.templates.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    private val templateLauncher = TemplateLauncher(dataRepository)

    // Double-tap guard: two fast taps in the sheet must not race two draft writes.
    private var launchingTemplate = false

    /** Persists the pre-filled draft (and awaits its visibility), then [onOpen] navigates. */
    fun launchTemplate(template: WorkoutTemplate, onOpen: () -> Unit) {
        if (launchingTemplate) return
        launchingTemplate = true
        viewModelScope.launch {
            try {
                templateLauncher.launch(template)
                onOpen()
            } finally {
                launchingTemplate = false
            }
        }
    }

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

    private fun calculateStatsForExercise(
        exercise: Exercise,
        sessions: List<com.example.goattracker.domain.model.WorkoutSession>,
        userProfile: UserProfile
    ): ExerciseStats {
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
            "Dernier: ${MetricFormatter.lastWorkoutLine(exercise.trackingType, completedSets, userProfile)}"
        } else {
            "Aucune série complétée"
        }

        // Progression of the last 4 workouts, in the type's own metric (the chart is normalized so
        // only relative values matter).
        val last4Sessions = exerciseSessions.takeLast(4)
        val volumes = last4Sessions.map { session ->
            val exSession = session.exercises.first { it.exercise.id == exercise.id }
            WorkoutMetrics.progressionValue(exSession, userProfile.bodyWeightKg)
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
