package com.example.goattracker.data

import com.example.goattracker.domain.model.Exercise
import com.example.goattracker.domain.model.UserProfile
import com.example.goattracker.domain.model.WorkoutSession
import com.example.goattracker.domain.model.WorkoutState
import com.example.goattracker.domain.model.WorkoutTemplate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The app's data contract. Production implementation is the Room-backed
 * [com.example.goattracker.data.local.RoomDataRepository]; the legacy single-file JSON
 * implementation was retired by the Room migration (its on-disk format lives on only in
 * [com.example.goattracker.data.dto] for the one-shot import).
 */
interface DataRepository {
    val workoutState: Flow<WorkoutState>

    /** Emits true once initial load (and first-launch init) has completed (gates the splash). */
    val isReady: StateFlow<Boolean>

    fun getLatestState(): WorkoutState
    suspend fun addExercise(exercise: Exercise)
    suspend fun deleteExercise(exerciseId: String)
    suspend fun addWorkoutSession(session: WorkoutSession)
    suspend fun updateWorkoutSession(session: WorkoutSession)
    suspend fun deleteWorkoutSession(sessionId: String)

    /** Persist (or clear, when null) the in-progress live session so it survives process death. */
    suspend fun saveActiveDraft(session: WorkoutSession?)

    /** Persist the user profile (body weight, unit preference, Health Connect opt-in). */
    suspend fun saveUserProfile(profile: UserProfile)

    /** Reusable workout templates ("Push", "Pull"...), in user list order. */
    val templates: Flow<List<WorkoutTemplate>>

    /**
     * Resolves an exercise by id, ARCHIVED ONES INCLUDED — unlike [WorkoutState.exercises], which
     * only carries the active catalog. Needed wherever a stored reference (template slot, history
     * row) must resolve even after the exercise left the catalog.
     */
    suspend fun getExercise(exerciseId: String): Exercise?

    suspend fun saveWorkoutTemplate(template: WorkoutTemplate)

    suspend fun deleteWorkoutTemplate(templateId: String)
}
