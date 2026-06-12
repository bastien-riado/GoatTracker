package com.example.goattracker.data

import com.example.goattracker.data.local.DefaultSeed
import com.example.goattracker.domain.model.BodyWeightEntry
import com.example.goattracker.domain.model.Exercise
import com.example.goattracker.domain.model.UserProfile
import com.example.goattracker.domain.model.WorkoutSession
import com.example.goattracker.domain.model.WorkoutState
import com.example.goattracker.domain.model.WorkoutTemplate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory [DataRepository] for ViewModel tests: synchronous, deterministic, no disk, no
 * Robolectric. Replaces the old trick of instantiating the JSON repository with
 * `storageDir = null` — ViewModels are tested against the interface; the real Room implementation
 * has its own suite (RoomDataRepositoryTest).
 *
 * Mutation semantics mirror the legacy in-memory behavior the existing tests were written
 * against (filter+append on add, map-replace on update, no-op for unknown update ids).
 */
class FakeDataRepository(
    initialState: WorkoutState = WorkoutState(exercises = DefaultSeed.exercises()),
) : DataRepository {

    private val _workoutState = MutableStateFlow(initialState)
    override val workoutState: Flow<WorkoutState> = _workoutState.asStateFlow()

    override val isReady: StateFlow<Boolean> = MutableStateFlow(true)

    override fun getLatestState(): WorkoutState = _workoutState.value

    override suspend fun addExercise(exercise: Exercise) {
        _workoutState.update { current ->
            current.copy(exercises = current.exercises.filter { it.id != exercise.id } + exercise)
        }
    }

    override suspend fun deleteExercise(exerciseId: String) {
        _workoutState.update { current ->
            current.copy(exercises = current.exercises.filter { it.id != exerciseId })
        }
    }

    override suspend fun addWorkoutSession(session: WorkoutSession) {
        _workoutState.update { current ->
            current.copy(sessions = current.sessions.filter { it.id != session.id } + session)
        }
    }

    override suspend fun updateWorkoutSession(session: WorkoutSession) {
        _workoutState.update { current ->
            current.copy(sessions = current.sessions.map { if (it.id == session.id) session else it })
        }
    }

    override suspend fun deleteWorkoutSession(sessionId: String) {
        _workoutState.update { current ->
            current.copy(sessions = current.sessions.filter { it.id != sessionId })
        }
    }

    override suspend fun saveActiveDraft(session: WorkoutSession?) {
        _workoutState.update { it.copy(activeDraft = session) }
    }

    override suspend fun saveUserProfile(profile: UserProfile) {
        _workoutState.update { it.copy(userProfile = profile) }
        // Mirror the Room behavior: every distinct weight observation feeds the history.
        val weightKg = profile.bodyWeightKg ?: return
        val measuredAt = profile.bodyWeightUpdatedAt ?: 0L
        _bodyWeightHistory.update { current ->
            val latest = current.lastOrNull()
            if (latest != null && latest.weightKg == weightKg && latest.measuredAt == measuredAt) current
            else current + BodyWeightEntry(weightKg, measuredAt, profile.bodyWeightSource)
        }
    }

    private val _bodyWeightHistory = MutableStateFlow<List<BodyWeightEntry>>(emptyList())
    override val bodyWeightHistory: Flow<List<BodyWeightEntry>> = _bodyWeightHistory.asStateFlow()

    private val _templates = MutableStateFlow<List<WorkoutTemplate>>(emptyList())
    override val templates: Flow<List<WorkoutTemplate>> = _templates.asStateFlow()

    override suspend fun getExercise(exerciseId: String): Exercise? =
        _workoutState.value.exercises.firstOrNull { it.id == exerciseId }

    override suspend fun saveWorkoutTemplate(template: WorkoutTemplate) {
        _templates.update { current -> current.filter { it.id != template.id } + template }
    }

    override suspend fun deleteWorkoutTemplate(templateId: String) {
        _templates.update { current -> current.filter { it.id != templateId } }
    }
}
