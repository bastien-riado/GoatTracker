package com.example.goattracker.domain

import com.example.goattracker.domain.model.Exercise
import com.example.goattracker.domain.model.ExerciseCategory
import com.example.goattracker.domain.model.ExerciseSession
import com.example.goattracker.domain.model.TrackingType
import com.example.goattracker.domain.model.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression coverage for the core domain data classes. Volume/tonnage and display formatting moved
 * to [WorkoutMetrics] / [MetricFormatter] (they depend on the user profile) — see their tests.
 */
class WorkoutModelsTest {

    private fun exercise(track: TrackingType) =
        Exercise(name = "x", category = ExerciseCategory.PUSH, primaryMuscle = "Pectoraux", trackingType = track)

    @Test
    fun completedSetsCount_onlyCountsCompleted() {
        val es = ExerciseSession(
            exercise = exercise(TrackingType.WEIGHT_REPS),
            sets = listOf(
                WorkoutSet(setNumber = 1, weight = 100.0, reps = 5, isCompleted = true),
                WorkoutSet(setNumber = 2, weight = 100.0, reps = 5, isCompleted = true),
                WorkoutSet(setNumber = 3, weight = 90.0, reps = 8, isCompleted = false),
            ),
        )
        assertEquals(2, es.completedSetsCount)
    }
}
