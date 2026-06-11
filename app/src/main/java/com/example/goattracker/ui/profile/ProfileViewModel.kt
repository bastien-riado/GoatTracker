package com.example.goattracker.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.goattracker.data.DataRepository
import com.example.goattracker.domain.OneRepMaxFormula
import com.example.goattracker.domain.OneRepMaxStrategy
import com.example.goattracker.domain.WorkoutMetrics
import com.example.goattracker.domain.model.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val totalWorkouts: Int = 0,
    val cumulativeVolume: Double = 0.0, // Strength tonnage in kg (endurance types excluded)
    val selectedExercise: Exercise? = null,
    val availableExercises: List<Exercise> = emptyList(),
    val oneRepMaxEvolution: List<Pair<Long, Double>> = emptyList(), // Timestamp -> 1RM kg
    val muscleGroupSets: Map<String, Int> = emptyMap(), // Muscle -> Sets count
    val sessionVolumes: List<Pair<String, Double>> = emptyList(), // Session Name -> Tonnage kg
    val userProfile: UserProfile = UserProfile()
)

class ProfileViewModel(
    private val dataRepository: DataRepository,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val epleyStrategy = OneRepMaxFormula.EPLEY.strategy

    init {
        // Stats are O(sessions x exercises x sets); compute off the main thread.
        viewModelScope.launch(defaultDispatcher) {
            dataRepository.workoutState.collect { state ->
                calculateStats(state)
            }
        }
    }

    fun selectExercise(exercise: Exercise) {
        _uiState.update { it.copy(selectedExercise = exercise) }
        viewModelScope.launch(defaultDispatcher) {
            val latestState = dataRepository.getLatestState()
            calculateOneRepMaxProgression(exercise, latestState.sessions)
        }
    }

    private fun calculateStats(state: WorkoutState) {
        val totalWorkouts = state.sessions.size
        val bodyWeightKg = state.userProfile.bodyWeightKg
        // Strength tonnage only: bodyweight reps count via the user's body weight; endurance
        // exercises (time/distance) no longer pollute a kg total.
        val cumulativeVolume = state.sessions.sumOf { WorkoutMetrics.sessionStrengthVolumeKg(it, bodyWeightKg) }

        // Calculate muscle splits
        val muscleSetsMap = mutableMapOf<String, Int>()
        state.sessions.forEach { session ->
            session.exercises.forEach { exSession ->
                val muscle = exSession.exercise.primaryMuscle
                val completedSets = exSession.sets.count { it.isCompleted }
                muscleSetsMap[muscle] = (muscleSetsMap[muscle] ?: 0) + completedSets
            }
        }

        // Calculate last 6 session volumes for bar chart
        val last6Sessions = state.sessions.sortedBy { it.startTime }.takeLast(6)
        val sessionVolumes = last6Sessions.map { session ->
            val shortName = session.name.replace("Séance du ", "")
            shortName to WorkoutMetrics.sessionStrengthVolumeKg(session, bodyWeightKg)
        }

        // Filter exercises present in at least one session, sorted alphabetically
        val exercisesInSessions = state.sessions
            .flatMap { it.exercises }
            .map { it.exercise }
            .distinctBy { it.id }
            .sortedBy { it.name }

        // Select default exercise for 1RM chart if none is selected yet
        val currentSelected = _uiState.value.selectedExercise ?: exercisesInSessions.firstOrNull()

        _uiState.update {
            it.copy(
                totalWorkouts = totalWorkouts,
                cumulativeVolume = cumulativeVolume,
                availableExercises = exercisesInSessions,
                selectedExercise = currentSelected,
                muscleGroupSets = muscleSetsMap,
                sessionVolumes = sessionVolumes,
                userProfile = state.userProfile
            )
        }

        if (currentSelected != null) {
            calculateOneRepMaxProgression(currentSelected, state.sessions)
        }
    }

    private fun calculateOneRepMaxProgression(exercise: Exercise, sessions: List<WorkoutSession>) {
        // Only makes sense to calculate 1RM for WEIGHT_REPS tracking type
        if (exercise.trackingType != TrackingType.WEIGHT_REPS) {
            _uiState.update { it.copy(oneRepMaxEvolution = emptyList()) }
            return
        }

        // Filter sessions containing this exercise, sorted by time
        val evolutionPoints = sessions.filter { session ->
            session.exercises.any { it.exercise.id == exercise.id }
        }.sortedBy { it.startTime }.mapNotNull { session ->
            val exSession = session.exercises.first { it.exercise.id == exercise.id }
            val completedSets = exSession.sets.filter { it.isCompleted }
            
            if (completedSets.isNotEmpty()) {
                val max1RMInSession = completedSets.map { set ->
                    epleyStrategy.calculate(set.weight, set.reps)
                }.maxOrNull() ?: 0.0
                
                if (max1RMInSession > 0.0) {
                    session.startTime to max1RMInSession
                } else null
            } else null
        }

        _uiState.update { it.copy(oneRepMaxEvolution = evolutionPoints) }
    }
}
