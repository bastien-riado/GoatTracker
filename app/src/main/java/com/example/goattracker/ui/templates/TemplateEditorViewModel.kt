package com.example.goattracker.ui.templates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.goattracker.data.DataRepository
import com.example.goattracker.domain.model.Exercise
import com.example.goattracker.domain.model.TemplateEntry
import com.example.goattracker.domain.model.TemplateTargetMode
import com.example.goattracker.domain.model.TrackingType
import com.example.goattracker.domain.model.WeightUnit
import com.example.goattracker.domain.model.WorkoutTemplate
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

/**
 * One exercise slot under edition. Numeric fields stay raw STRINGS while typing (smooth input,
 * no cursor fights); parsing with sane fallbacks happens once, on save. Weight is displayed and
 * edited in the user's unit and converted back to kg on save (storage rule: always kg).
 */
data class EditorRow(
    val entryId: String,
    val exercise: Exercise,
    val setsText: String,
    val repsText: String,
    val isAmrap: Boolean,
    val weightText: String,
) {
    val showsRepTargets: Boolean
        get() = exercise.trackingType == TrackingType.WEIGHT_REPS ||
            exercise.trackingType == TrackingType.BODYWEIGHT_REPS

    val showsWeightTarget: Boolean
        get() = exercise.trackingType == TrackingType.WEIGHT_REPS
}

data class TemplateEditorUiState(
    val name: String = "",
    val rows: List<EditorRow> = emptyList(),
    val availableExercises: List<Exercise> = emptyList(),
    val weightUnit: WeightUnit = WeightUnit.KG,
) {
    val isSaveEnabled: Boolean
        get() = name.isNotBlank() && rows.isNotEmpty()
}

class TemplateEditorViewModel(
    private val repository: DataRepository,
    private val templateId: String?,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TemplateEditorUiState())
    val uiState: StateFlow<TemplateEditorUiState> = _uiState.asStateFlow()

    private val _savedEvents = MutableSharedFlow<Unit>()
    val savedEvents: SharedFlow<Unit> = _savedEvents.asSharedFlow()

    init {
        viewModelScope.launch {
            // Unit captured once at open: storage is kg, only the editing surface converts.
            val unit = repository.workoutState.first().userProfile.weightUnit
            _uiState.update { it.copy(weightUnit = unit) }
            if (templateId != null) {
                repository.templates.first().firstOrNull { it.id == templateId }?.let { template ->
                    _uiState.update {
                        it.copy(
                            name = template.name,
                            rows = template.entries.mapNotNull { entry -> entry.toRow(unit) },
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            // Reactive: an exercise created mid-edition (other screen) appears in the picker.
            repository.workoutState.collect { state ->
                _uiState.update { it.copy(availableExercises = state.exercises) }
            }
        }
    }

    private suspend fun TemplateEntry.toRow(unit: WeightUnit): EditorRow? {
        val exercise = repository.getExercise(exerciseId) ?: return null
        return EditorRow(
            entryId = id,
            exercise = exercise,
            setsText = targetSets.toString(),
            repsText = targetReps?.toString() ?: "",
            isAmrap = targetMode == TemplateTargetMode.AMRAP,
            weightText = targetWeightKg?.let { formatNumber(unit.fromKg(it)) } ?: "",
        )
    }

    fun updateName(name: String) = _uiState.update { it.copy(name = name) }

    fun addExercise(exercise: Exercise) {
        val row = EditorRow(
            entryId = UUID.randomUUID().toString(),
            exercise = exercise,
            setsText = "3",
            repsText = "10",
            isAmrap = false,
            weightText = "",
        )
        _uiState.update { it.copy(rows = it.rows + row) }
    }

    fun removeRow(entryId: String) =
        _uiState.update { state -> state.copy(rows = state.rows.filter { it.entryId != entryId }) }

    fun moveRow(entryId: String, delta: Int) = _uiState.update { state ->
        val index = state.rows.indexOfFirst { it.entryId == entryId }
        val target = index + delta
        if (index < 0 || target !in state.rows.indices) return@update state
        val reordered = state.rows.toMutableList().apply { add(target, removeAt(index)) }
        state.copy(rows = reordered)
    }

    fun updateSets(entryId: String, text: String) =
        updateRow(entryId) { it.copy(setsText = text.filter(Char::isDigit).take(2)) }

    fun updateReps(entryId: String, text: String) =
        updateRow(entryId) { it.copy(repsText = text.filter(Char::isDigit).take(3)) }

    fun updateWeight(entryId: String, text: String) =
        updateRow(entryId) { row ->
            row.copy(weightText = text.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(6))
        }

    fun toggleAmrap(entryId: String) = updateRow(entryId) { it.copy(isAmrap = !it.isAmrap) }

    private fun updateRow(entryId: String, transform: (EditorRow) -> EditorRow) =
        _uiState.update { state ->
            state.copy(rows = state.rows.map { if (it.entryId == entryId) transform(it) else it })
        }

    fun save() {
        val state = _uiState.value
        if (!state.isSaveEnabled) return
        val template = WorkoutTemplate(
            id = templateId ?: UUID.randomUUID().toString(),
            name = state.name.trim(),
            entries = state.rows.map { row ->
                TemplateEntry(
                    id = row.entryId,
                    exerciseId = row.exercise.id,
                    targetSets = row.setsText.toIntOrNull()?.coerceIn(1, 20) ?: 3,
                    // Reps stay even in AMRAP mode: they target the regular sets; only the LAST
                    // set is done to failure (see toDraftSession).
                    targetReps = row.repsText.toIntOrNull(),
                    targetWeightKg = row.weightText.replace(',', '.').toDoubleOrNull()
                        ?.let { state.weightUnit.toKg(it) },
                    targetMode = if (row.isAmrap) TemplateTargetMode.AMRAP else TemplateTargetMode.REPS,
                )
            },
        )
        viewModelScope.launch {
            repository.saveWorkoutTemplate(template)
            _savedEvents.emit(Unit)
        }
    }

    private fun formatNumber(value: Double): String =
        String.format(Locale.US, "%.1f", value).trimEnd('0').trimEnd('.')
}
