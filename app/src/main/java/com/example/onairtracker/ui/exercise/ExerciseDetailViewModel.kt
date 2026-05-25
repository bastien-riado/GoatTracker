package com.example.onairtracker.ui.exercise

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.onairtracker.data.DataRepository
import com.example.onairtracker.domain.model.Exercise
import com.example.onairtracker.domain.model.TrackingType
import com.example.onairtracker.domain.model.WorkoutSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface ExerciseDetailUiState {
    object Loading : ExerciseDetailUiState
    data class Error(val message: String) : ExerciseDetailUiState
    data class Success(
        val exercise: Exercise,
        val estimatedOneRepMax: Double,
        val maxWeight: Double,
        val maxSessionVolume: Double,
        val totalVolume: Double,
        val totalSets: Int,
        val totalReps: Int,
        val lastWorkoutText: String,
        val volumeHistory: List<Double>,
        val volumeHistoryLabels: List<String>,
        val sessions: List<WorkoutSession>,
        val lastExerciseSession: com.example.onairtracker.domain.model.ExerciseSession? = null,
        val isDeleted: Boolean = false
    ) : ExerciseDetailUiState
}

class ExerciseDetailViewModel(
    private val dataRepository: DataRepository,
    private val exerciseId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow<ExerciseDetailUiState>(ExerciseDetailUiState.Loading)
    val uiState: StateFlow<ExerciseDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            dataRepository.workoutState.collectLatest { state ->
                val exercise = state.exercises.firstOrNull { it.id == exerciseId }
                if (exercise == null) {
                    if (_uiState.value is ExerciseDetailUiState.Success && (_uiState.value as ExerciseDetailUiState.Success).isDeleted) {
                        // Safe state transition when already deleted
                        return@collectLatest
                    }
                    _uiState.value = ExerciseDetailUiState.Error("Exercice non trouvé")
                    return@collectLatest
                }

                // Filter sessions that contain this exercise
                val exerciseSessions = state.sessions.filter { session ->
                    session.exercises.any { it.exercise.id == exerciseId }
                }

                // All completed sets of this exercise
                val allCompletedSets = exerciseSessions.flatMap { session ->
                    session.exercises
                        .filter { it.exercise.id == exerciseId }
                        .flatMap { it.sets }
                }.filter { it.isCompleted }

                // 1. Calculate Max Weight & Estimated 1RM
                var maxWeight = 0.0
                var estimated1RM = 0.0
                allCompletedSets.forEach { set ->
                    if (set.weight > maxWeight) {
                        maxWeight = set.weight
                    }
                    if (exercise.trackingType == TrackingType.WEIGHT_REPS && set.weight > 0 && set.reps > 0) {
                        val epley1RM = set.weight * (1.0 + set.reps / 30.0)
                        if (epley1RM > estimated1RM) {
                            estimated1RM = epley1RM
                        }
                    }
                }

                // 2. Calculate totals
                val totalSets = allCompletedSets.size
                val totalReps = allCompletedSets.sumOf { it.reps }
                val totalVolume = allCompletedSets.sumOf { set ->
                    when (exercise.trackingType) {
                        TrackingType.WEIGHT_REPS -> set.weight * set.reps
                        TrackingType.BODYWEIGHT_REPS -> set.reps.toDouble()
                        TrackingType.TIME -> set.durationSeconds.toDouble()
                        TrackingType.DISTANCE -> set.distanceKm * 1000.0
                    }
                }

                // 3. Compute last workout text
                val latestSession = exerciseSessions.maxByOrNull { it.startTime }
                val latestCompletedSets = latestSession?.exercises
                    ?.firstOrNull { it.exercise.id == exerciseId }
                    ?.sets?.filter { it.isCompleted } ?: emptyList()

                val lastWorkoutText = if (latestCompletedSets.isNotEmpty()) {
                    when (exercise.trackingType) {
                        TrackingType.WEIGHT_REPS -> {
                            val sample = latestCompletedSets.first()
                            "${latestCompletedSets.size}x${sample.reps} • ${sample.weight.toInt()}kg"
                        }
                        TrackingType.BODYWEIGHT_REPS -> {
                            val sample = latestCompletedSets.first()
                            "${latestCompletedSets.size}x${sample.reps} • PDC"
                        }
                        TrackingType.TIME -> {
                            val totalSec = latestCompletedSets.sumOf { it.durationSeconds }
                            val mins = totalSec / 60
                            val secs = totalSec % 60
                            String.format("%02d:%02d", mins, secs)
                        }
                        TrackingType.DISTANCE -> {
                            val totalDist = latestCompletedSets.sumOf { it.distanceKm }
                            String.format("%.2f km", totalDist)
                        }
                    }
                } else {
                    "Aucune série"
                }

                // 4. Compute Volume history for last 6 sessions (sorted chronologically)
                val sortedChronologicalSessions = exerciseSessions.sortedBy { it.startTime }
                val last6Sessions = sortedChronologicalSessions.takeLast(6)
                val volumeHistory = last6Sessions.map { session ->
                    val exSession = session.exercises.first { it.exercise.id == exerciseId }
                    exSession.sets.filter { it.isCompleted }.sumOf { set ->
                        when (exercise.trackingType) {
                            TrackingType.WEIGHT_REPS -> set.weight * set.reps
                            TrackingType.BODYWEIGHT_REPS -> set.reps.toDouble()
                            TrackingType.TIME -> set.durationSeconds.toDouble()
                            TrackingType.DISTANCE -> set.distanceKm * 1000.0
                        }
                    }
                }
                
                val dateFormat = SimpleDateFormat("dd/MM", Locale.getDefault())
                val volumeHistoryLabels = last6Sessions.map { session ->
                    dateFormat.format(Date(session.startTime))
                }

                // Calculate max volume achieved in a single session
                val maxSessionVolume = exerciseSessions.map { session ->
                    val exSession = session.exercises.first { it.exercise.id == exerciseId }
                    exSession.sets.filter { it.isCompleted }.sumOf { set ->
                        when (exercise.trackingType) {
                            TrackingType.WEIGHT_REPS -> set.weight * set.reps
                            TrackingType.BODYWEIGHT_REPS -> set.reps.toDouble()
                            TrackingType.TIME -> set.durationSeconds.toDouble()
                            TrackingType.DISTANCE -> set.distanceKm * 1000.0
                        }
                    }
                }.maxOrNull() ?: 0.0

                val lastExerciseSession = latestSession?.exercises?.firstOrNull { it.exercise.id == exerciseId }

                _uiState.value = ExerciseDetailUiState.Success(
                    exercise = exercise,
                    estimatedOneRepMax = estimated1RM,
                    maxWeight = maxWeight,
                    maxSessionVolume = maxSessionVolume,
                    totalVolume = totalVolume,
                    totalSets = totalSets,
                    totalReps = totalReps,
                    lastWorkoutText = lastWorkoutText,
                    volumeHistory = volumeHistory,
                    volumeHistoryLabels = volumeHistoryLabels,
                    sessions = exerciseSessions.sortedByDescending { it.startTime },
                    lastExerciseSession = lastExerciseSession
                )
            }
        }
    }

    fun updateNotes(notes: String) {
        val currentState = _uiState.value
        if (currentState is ExerciseDetailUiState.Success) {
            viewModelScope.launch {
                val updatedExercise = currentState.exercise.copy(notes = notes)
                dataRepository.addExercise(updatedExercise)
            }
        }
    }

    fun deleteExercise() {
        val currentState = _uiState.value
        if (currentState is ExerciseDetailUiState.Success) {
            _uiState.value = currentState.copy(isDeleted = true)
            viewModelScope.launch {
                dataRepository.deleteExercise(exerciseId)
            }
        }
    }
}
