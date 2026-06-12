package com.example.goattracker.ui.sessiondetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.goattracker.data.DataRepository
import com.example.goattracker.domain.SessionInsights
import com.example.goattracker.domain.SessionSummary
import com.example.goattracker.domain.model.UserProfile
import com.example.goattracker.domain.model.WorkoutSession
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

sealed interface SessionDetailUiState {
    data object Loading : SessionDetailUiState

    /** The session no longer exists (deleted elsewhere) — the screen navigates back. */
    data object Gone : SessionDetailUiState

    data class Success(
        val session: WorkoutSession,
        val summary: SessionSummary,
        val userProfile: UserProfile,
        /** Resolved name of the template the session was launched from, when any (and still alive). */
        val templateName: String?,
    ) : SessionDetailUiState
}

class SessionDetailViewModel(
    private val dataRepository: DataRepository,
    private val sessionId: String,
    defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val _uiState = MutableStateFlow<SessionDetailUiState>(SessionDetailUiState.Loading)
    val uiState: StateFlow<SessionDetailUiState> = _uiState.asStateFlow()

    init {
        // Whole-history aggregation (the comparison scans every session); off the main thread.
        viewModelScope.launch(defaultDispatcher) {
            combine(dataRepository.workoutState, dataRepository.templates) { state, templates ->
                val session = state.sessions.firstOrNull { it.id == sessionId }
                    ?: return@combine SessionDetailUiState.Gone
                SessionDetailUiState.Success(
                    session = session,
                    summary = SessionInsights.summarize(
                        session = session,
                        allSessions = state.sessions,
                        bodyWeightKg = state.userProfile.bodyWeightKg,
                    ),
                    userProfile = state.userProfile,
                    templateName = session.templateId
                        ?.let { id -> templates.firstOrNull { it.id == id }?.name },
                )
            }.collect { _uiState.value = it }
        }
    }
}
