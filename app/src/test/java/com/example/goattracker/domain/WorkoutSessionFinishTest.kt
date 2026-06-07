package com.example.goattracker.domain

import com.example.goattracker.domain.model.Exercise
import com.example.goattracker.domain.model.ExerciseCategory
import com.example.goattracker.domain.model.ExerciseSession
import com.example.goattracker.domain.model.TrackingType
import com.example.goattracker.domain.model.WorkoutSession
import com.example.goattracker.domain.model.WorkoutSet
import com.example.goattracker.domain.model.toFinishedOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers [toFinishedOrNull], the single finish rule shared by the in-screen ViewModel and the
 * out-of-screen ActiveSessionController (notification "Terminer"). Pure — no coroutines, no Android,
 * so it is fast and cannot hang.
 */
class WorkoutSessionFinishTest {

    private fun exercise(name: String) = Exercise(
        name = name,
        category = ExerciseCategory.PUSH,
        primaryMuscle = "Pecs",
        trackingType = TrackingType.WEIGHT_REPS,
    )

    @Test
    fun `keeps only completed sets, drops empty exercises, stamps end time`() {
        val session = WorkoutSession(
            name = "Test",
            exercises = listOf(
                ExerciseSession(
                    exercise = exercise("Bench"),
                    sets = listOf(
                        WorkoutSet(setNumber = 1, isCompleted = true),
                        WorkoutSet(setNumber = 2, isCompleted = false),
                    ),
                ),
                ExerciseSession(
                    exercise = exercise("Squat"),
                    sets = listOf(WorkoutSet(setNumber = 1, isCompleted = false)),
                ),
            ),
        )

        val finished = session.toFinishedOrNull(now = 123_456L)

        assertTrue("Expected a session to save", finished != null)
        finished!!
        assertEquals(123_456L, finished.endTime)
        // The exercise with no completed set is dropped.
        assertEquals(1, finished.exercises.size)
        assertEquals("Bench", finished.exercises[0].exercise.name)
        // The incomplete set is dropped; only completed sets remain.
        assertEquals(1, finished.exercises[0].sets.size)
        assertTrue(finished.exercises[0].sets.all { it.isCompleted })
    }

    @Test
    fun `returns null when nothing was completed`() {
        val session = WorkoutSession(
            name = "Empty",
            exercises = listOf(
                ExerciseSession(
                    exercise = exercise("Bench"),
                    sets = listOf(WorkoutSet(setNumber = 1, isCompleted = false)),
                ),
            ),
        )

        assertNull(session.toFinishedOrNull(now = 1L))
    }

    @Test
    fun `returns null for a session with no exercises`() {
        val session = WorkoutSession(name = "Empty", exercises = emptyList())
        assertNull(session.toFinishedOrNull(now = 1L))
    }
}
