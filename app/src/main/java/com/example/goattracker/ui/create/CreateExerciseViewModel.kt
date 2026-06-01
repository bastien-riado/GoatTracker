package com.example.goattracker.ui.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.goattracker.data.DataRepository
import com.example.goattracker.domain.model.Exercise
import com.example.goattracker.domain.model.ExerciseCategory
import com.example.goattracker.domain.model.TrackingType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CreateExerciseUiState(
    val name: String = "",
    val category: ExerciseCategory = ExerciseCategory.PUSH,
    val primaryMuscle: String = "",
    val trackingType: TrackingType = TrackingType.WEIGHT_REPS,
    val notes: String = "",
    val restTimeSeconds: Int = 90,
    val isSaved: Boolean = false
) {
    val isSaveEnabled: Boolean
        get() = name.isNotBlank() && primaryMuscle.isNotBlank()
}

class CreateExerciseViewModel(
    private val dataRepository: DataRepository,
    private val exerciseId: String? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateExerciseUiState())
    val uiState: StateFlow<CreateExerciseUiState> = _uiState.asStateFlow()

    init {
        if (exerciseId != null) {
            viewModelScope.launch {
                val state = dataRepository.getLatestState()
                state.exercises.firstOrNull { it.id == exerciseId }?.let { exercise ->
                    _uiState.update {
                        it.copy(
                            name = exercise.name,
                            category = exercise.category,
                            primaryMuscle = exercise.primaryMuscle,
                            trackingType = exercise.trackingType,
                            notes = exercise.notes,
                            restTimeSeconds = exercise.restTimeSeconds
                        )
                    }
                }
            }
        }
    }

    fun updateName(name: String) {
        _uiState.update { it.copy(name = name) }
    }

    fun selectCategory(category: ExerciseCategory) {
        _uiState.update { it.copy(category = category) }
    }

    fun updatePrimaryMuscle(muscle: String) {
        _uiState.update { it.copy(primaryMuscle = muscle) }
    }

    fun selectTrackingType(trackingType: TrackingType) {
        _uiState.update { it.copy(trackingType = trackingType) }
    }

    fun updateRestTime(seconds: Int) {
        _uiState.update { it.copy(restTimeSeconds = seconds.coerceIn(15, 600)) }
    }

    fun saveExercise() {
        val currentState = _uiState.value
        if (!currentState.isSaveEnabled) return

        viewModelScope.launch {
            val newExercise = Exercise(
                id = exerciseId ?: java.util.UUID.randomUUID().toString(),
                name = currentState.name.trim(),
                category = currentState.category,
                primaryMuscle = currentState.primaryMuscle,
                trackingType = currentState.trackingType,
                notes = currentState.notes,
                restTimeSeconds = currentState.restTimeSeconds
            )
            dataRepository.addExercise(newExercise)
            _uiState.update { it.copy(isSaved = true) }
        }
    }
}
