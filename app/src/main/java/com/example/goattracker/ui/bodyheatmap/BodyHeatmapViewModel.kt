package com.example.goattracker.ui.bodyheatmap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.goattracker.data.DataRepository
import com.example.goattracker.domain.MuscleRecoveryCalculator
import com.example.goattracker.domain.MuscleStatus
import com.example.goattracker.domain.model.MuscleGroup
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BodyHeatmapUiState(
    val statuses: Map<MuscleGroup, MuscleStatus> = emptyMap(),
    val selected: MuscleGroup? = null,
    val isLoading: Boolean = true,
)

/**
 * Drives the 3D muscle heatmap: collects workout history and projects it into a per-muscle
 * recovery map (off the main thread), which the screen renders as tint colors on the body model.
 * Mirrors [com.example.goattracker.ui.profile.ProfileViewModel]'s repository/StateFlow pattern.
 */
class BodyHeatmapViewModel(
    private val dataRepository: DataRepository,
    private val calculator: MuscleRecoveryCalculator = MuscleRecoveryCalculator(),
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BodyHeatmapUiState())
    val uiState: StateFlow<BodyHeatmapUiState> = _uiState.asStateFlow()

    init {
        // Recovery is O(sessions x exercises x sets); compute off the main thread.
        viewModelScope.launch(defaultDispatcher) {
            dataRepository.workoutState.collect { state ->
                val statuses = calculator.compute(state.sessions, now())
                _uiState.update { it.copy(statuses = statuses, isLoading = false) }
            }
        }
    }

    /** Toggle selection of a muscle (tapping the selected one clears it). */
    fun select(group: MuscleGroup?) {
        _uiState.update { it.copy(selected = if (it.selected == group) null else group) }
    }
}
