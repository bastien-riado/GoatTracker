package com.example.goattracker.ui.exercise

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.goattracker.data.DataRepository
import com.example.goattracker.domain.OneRepMaxFormula
import com.example.goattracker.domain.WorkoutMetrics
import com.example.goattracker.domain.model.Exercise
import com.example.goattracker.domain.model.ExerciseSession
import com.example.goattracker.domain.model.TrackingType
import com.example.goattracker.domain.model.UserProfile
import com.example.goattracker.domain.model.WorkoutSession
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface ExerciseDetailUiState {
    object Loading : ExerciseDetailUiState
    data class Error(val message: String) : ExerciseDetailUiState
    data class Success(
        val exercise: Exercise,
        val userProfile: UserProfile,
        // Per-type records — only the fields relevant to exercise.trackingType are meaningful:
        val maxWeight: Double,          // WEIGHT_REPS: heaviest completed set (kg)
        val estimatedOneRepMax: Double, // WEIGHT_REPS: best Epley estimate (kg)
        val maxReps: Int,               // BODYWEIGHT_REPS: best single completed set
        val totalReps: Int,             // rep-based types: cumulative completed reps
        val maxDurationSeconds: Int,    // TIME: longest single completed set
        val maxDistanceKm: Double,      // DISTANCE: longest single completed set
        val bestPaceSecPerKm: Double?,  // DISTANCE: best pace over sets with distance AND duration
        // Progression metric (unit depends on type, see WorkoutMetrics.progressionValue):
        val maxSessionVolume: Double,
        val totalSets: Int,
        val volumeHistory: List<Double>,
        val volumeHistoryLabels: List<String>,
        val sessions: List<WorkoutSession>,
        val lastExerciseSession: ExerciseSession? = null
    ) : ExerciseDetailUiState
}

class ExerciseDetailViewModel(
    private val dataRepository: DataRepository,
    private val exerciseId: String,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _uiState = MutableStateFlow<ExerciseDetailUiState>(ExerciseDetailUiState.Loading)
    val uiState: StateFlow<ExerciseDetailUiState> = _uiState.asStateFlow()

    // One-shot "deleted, navigate away" signal (consumed once) instead of a sticky UiState flag.
    private val _deletedEvents = Channel<Unit>(Channel.BUFFERED)
    val deletedEvents: Flow<Unit> = _deletedEvents.receiveAsFlow()

    // Guards the brief window between requesting a delete and the workoutState collector seeing the
    // exercise gone, so it doesn't flash a "not found" error on the way out.
    @Volatile
    private var isDeleting = false

    init {
        // Heavy per-exercise aggregation; compute off the main thread.
        viewModelScope.launch(defaultDispatcher) {
            dataRepository.workoutState.collectLatest { state ->
                val exercise = state.exercises.firstOrNull { it.id == exerciseId }
                if (exercise == null) {
                    // A delete we initiated is in flight; navigation is handled via deletedEvents.
                    if (isDeleting) return@collectLatest
                    _uiState.value = ExerciseDetailUiState.Error("Exercice non trouvé")
                    return@collectLatest
                }

                val profile = state.userProfile
                val bodyWeightKg = profile.bodyWeightKg

                // Sessions that contain this exercise, and their per-session ExerciseSession
                val exerciseSessions = state.sessions.filter { session ->
                    session.exercises.any { it.exercise.id == exerciseId }
                }
                val perSession = exerciseSessions
                    .sortedBy { it.startTime }
                    .map { session -> session to session.exercises.first { it.exercise.id == exerciseId } }

                val allCompletedSets = perSession.flatMap { (_, es) -> es.sets }.filter { it.isCompleted }

                // --- Per-type records (single pass over completed sets) ---
                var maxWeight = 0.0
                var estimated1RM = 0.0
                var maxReps = 0
                var maxDurationSeconds = 0
                var maxDistanceKm = 0.0
                var bestPaceSecPerKm: Double? = null
                allCompletedSets.forEach { set ->
                    if (set.weight > maxWeight) maxWeight = set.weight
                    if (set.reps > maxReps) maxReps = set.reps
                    if (set.durationSeconds > maxDurationSeconds) maxDurationSeconds = set.durationSeconds
                    if (set.distanceKm > maxDistanceKm) maxDistanceKm = set.distanceKm
                    if (exercise.trackingType == TrackingType.WEIGHT_REPS && set.weight > 0 && set.reps > 0) {
                        val epley1RM = OneRepMaxFormula.EPLEY.strategy.calculate(set.weight, set.reps)
                        if (epley1RM > estimated1RM) estimated1RM = epley1RM
                    }
                    if (exercise.trackingType == TrackingType.DISTANCE) {
                        val pace = WorkoutMetrics.paceSecPerKm(set.durationSeconds, set.distanceKm)
                        if (pace != null && (bestPaceSecPerKm == null || pace < bestPaceSecPerKm!!)) {
                            bestPaceSecPerKm = pace
                        }
                    }
                }

                // --- Progression: one point per session, in the type's own unit ---
                val progressionPerSession = perSession.map { (session, es) ->
                    session to WorkoutMetrics.progressionValue(es, bodyWeightKg)
                }
                val last6 = progressionPerSession.takeLast(6)
                val dateFormat = SimpleDateFormat("dd/MM", Locale.getDefault())

                val latest = perSession.lastOrNull()

                _uiState.value = ExerciseDetailUiState.Success(
                    exercise = exercise,
                    userProfile = profile,
                    maxWeight = maxWeight,
                    estimatedOneRepMax = estimated1RM,
                    maxReps = maxReps,
                    totalReps = allCompletedSets.sumOf { it.reps },
                    maxDurationSeconds = maxDurationSeconds,
                    maxDistanceKm = maxDistanceKm,
                    bestPaceSecPerKm = bestPaceSecPerKm,
                    maxSessionVolume = progressionPerSession.maxOfOrNull { it.second } ?: 0.0,
                    totalSets = allCompletedSets.size,
                    volumeHistory = last6.map { it.second },
                    volumeHistoryLabels = last6.map { dateFormat.format(Date(it.first.startTime)) },
                    sessions = exerciseSessions.sortedByDescending { it.startTime },
                    lastExerciseSession = latest?.second
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
        if (_uiState.value !is ExerciseDetailUiState.Success) return
        isDeleting = true
        viewModelScope.launch {
            dataRepository.deleteExercise(exerciseId)
            _deletedEvents.send(Unit)
        }
    }
}
