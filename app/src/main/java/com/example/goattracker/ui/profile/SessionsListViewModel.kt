package com.example.goattracker.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.goattracker.data.DataRepository
import com.example.goattracker.domain.WorkoutMetrics
import com.example.goattracker.domain.model.UserProfile
import com.example.goattracker.domain.model.WorkoutSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SessionSortOrder(val displayName: String) {
    DATE_DESC("Plus récentes"),
    DATE_ASC("Plus anciennes"),
    VOLUME_DESC("Volume max"),
    VOLUME_ASC("Volume min")
}

class SessionsListViewModel(
    private val dataRepository: DataRepository
) : ViewModel() {

    private val _sortOrder = MutableStateFlow(SessionSortOrder.DATE_DESC)
    val sortOrder: StateFlow<SessionSortOrder> = _sortOrder

    /** Profile exposed so the list rows format volumes with the user's unit and body weight. */
    val userProfile: StateFlow<UserProfile> = dataRepository.workoutState
        .map { it.userProfile }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserProfile()
        )

    val sessions: StateFlow<List<WorkoutSession>> = combine(
        dataRepository.workoutState,
        _sortOrder
    ) { state, order ->
        val bodyWeightKg = state.userProfile.bodyWeightKg
        when (order) {
            SessionSortOrder.DATE_DESC -> state.sessions.sortedByDescending { it.startTime }
            SessionSortOrder.DATE_ASC -> state.sessions.sortedBy { it.startTime }
            SessionSortOrder.VOLUME_DESC ->
                state.sessions.sortedByDescending { WorkoutMetrics.sessionStrengthVolumeKg(it, bodyWeightKg) }
            SessionSortOrder.VOLUME_ASC ->
                state.sessions.sortedBy { WorkoutMetrics.sessionStrengthVolumeKg(it, bodyWeightKg) }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun updateSortOrder(order: SessionSortOrder) {
        _sortOrder.value = order
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            dataRepository.deleteWorkoutSession(sessionId)
        }
    }
}
