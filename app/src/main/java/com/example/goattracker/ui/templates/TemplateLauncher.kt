package com.example.goattracker.ui.templates

import com.example.goattracker.data.DataRepository
import com.example.goattracker.domain.model.WorkoutTemplate
import com.example.goattracker.domain.model.toDraftSession
import kotlinx.coroutines.flow.first

/**
 * The single launch path for "lancer une séance depuis un workout", shared by every entry point
 * (templates screen, home bottom sheet).
 *
 * Persists the pre-filled draft, then SUSPENDS until it is observable in the repository state:
 * the live screen's [startOrResumeSession] reads the synchronous mirror, so navigating before the
 * draft lands there would race it into creating a fresh empty session on top.
 */
class TemplateLauncher(private val repository: DataRepository) {

    /**
     * Returns true when the template was launched, false when a session is already live — in that
     * case nothing is written and the caller should simply open the existing session (the launch
     * UI is hidden during an active session, this guard is the process-level backstop).
     */
    suspend fun launch(template: WorkoutTemplate, now: Long = System.currentTimeMillis()): Boolean {
        if (repository.getLatestState().activeDraft != null) return false
        val resolved = template.entries
            .map { it.exerciseId }
            .distinct()
            .mapNotNull { id -> repository.getExercise(id)?.let { id to it } }
            .toMap()
        val draft = template.toDraftSession(resolved::get, now)
        repository.saveActiveDraft(draft)
        repository.workoutState.first { it.activeDraft?.id == draft.id }
        return true
    }
}
