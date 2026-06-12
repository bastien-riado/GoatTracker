package com.example.goattracker.ui.celebration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.goattracker.data.DataRepository
import com.example.goattracker.domain.PersonalRecord
import com.example.goattracker.domain.RecordDetector
import com.example.goattracker.domain.SessionInsights
import com.example.goattracker.domain.SessionSummary
import com.example.goattracker.domain.model.UserProfile
import com.example.goattracker.domain.model.WorkoutSession
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface CelebrationUiState {
    /**
     * The finish write is asynchronous and this screen is pushed immediately after: Loading simply
     * waits for the session to become observable — it always lands, no timeout needed.
     */
    data object Loading : CelebrationUiState

    data class Ready(
        val session: WorkoutSession,
        val summary: SessionSummary,
        val records: List<PersonalRecord>,
        val userProfile: UserProfile,
    ) : CelebrationUiState
}

class SessionCelebrationViewModel(
    private val dataRepository: DataRepository,
    private val sessionId: String,
    defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val _uiState = MutableStateFlow<CelebrationUiState>(CelebrationUiState.Loading)
    val uiState: StateFlow<CelebrationUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(defaultDispatcher) {
            dataRepository.workoutState.collect { state ->
                val session = state.sessions.firstOrNull { it.id == sessionId } ?: return@collect
                val bodyWeightKg = state.userProfile.bodyWeightKg
                _uiState.value = CelebrationUiState.Ready(
                    session = session,
                    summary = SessionInsights.summarize(session, state.sessions, bodyWeightKg),
                    records = RecordDetector.detect(session, state.sessions, bodyWeightKg),
                    userProfile = state.userProfile,
                )
            }
        }
    }
}
