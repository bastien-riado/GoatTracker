package com.example.goattracker.ui.recovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.goattracker.data.DataRepository
import com.example.goattracker.domain.MuscleRecoveryCalculator
import com.example.goattracker.domain.model.MuscleGroup
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RecoveryRow(
    val group: MuscleGroup,
    /** Effective base hours: the user override, or the engine default. */
    val hours: Int,
    val isOverridden: Boolean,
)

/**
 * Per-muscle recovery-time settings. The table stores OVERRIDES only (no row = engine default),
 * so resetting really returns to the default rather than freezing a copy of it.
 */
class RecoverySettingsViewModel(
    private val repository: DataRepository,
) : ViewModel() {

    val rows: StateFlow<List<RecoveryRow>> = repository.muscleRecoveryOverrides
        .map { overrides ->
            MuscleGroup.entries.map { group ->
                val override = overrides[group.name]
                RecoveryRow(
                    group = group,
                    hours = override ?: DEFAULT_HOURS,
                    isOverridden = override != null,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun adjust(group: MuscleGroup, deltaHours: Int) {
        val current = rows.value.firstOrNull { it.group == group }?.hours ?: DEFAULT_HOURS
        val target = (current + deltaHours).coerceIn(MIN_HOURS, MAX_HOURS)
        viewModelScope.launch {
            if (target == DEFAULT_HOURS) {
                // Landing exactly on the default = no override needed; keep the table minimal.
                repository.clearMuscleRecoveryOverride(group.name)
            } else {
                repository.saveMuscleRecoveryOverride(group.name, target)
            }
        }
    }

    fun reset(group: MuscleGroup) {
        viewModelScope.launch { repository.clearMuscleRecoveryOverride(group.name) }
    }

    companion object {
        /** The engine's default base — shown as the non-overridden value. */
        val DEFAULT_HOURS = MuscleRecoveryCalculator.DEFAULT_BASE_RECOVERY_HOURS.toInt()
        const val MIN_HOURS = 12
        const val MAX_HOURS = 120
        const val STEP_HOURS = 6
    }
}
