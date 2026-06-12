package com.example.goattracker.domain

import com.example.goattracker.domain.model.Exercise
import com.example.goattracker.domain.model.ExerciseCategory
import com.example.goattracker.domain.model.ExerciseSession
import com.example.goattracker.domain.model.TrackingType
import com.example.goattracker.domain.model.WorkoutSession
import com.example.goattracker.domain.model.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionInsightsTest {

    private val bench = Exercise(
        id = "ex-bench", name = "Développé Couché", category = ExerciseCategory.PUSH,
        primaryMuscle = "Pectoraux", trackingType = TrackingType.WEIGHT_REPS,
    )
    private val run = Exercise(
        id = "ex-run", name = "Course", category = ExerciseCategory.CARDIO,
        primaryMuscle = "Cardio", trackingType = TrackingType.DISTANCE,
    )

    private fun pushSession(id: String, startTime: Long, weight: Double, templateId: String? = "tpl") =
        WorkoutSession(
            id = id,
            startTime = startTime,
            endTime = startTime + 3_600_000L, // 1h
            name = "Push",
            templateId = templateId,
            exercises = listOf(
                ExerciseSession(
                    exercise = bench,
                    sets = listOf(
                        WorkoutSet(setNumber = 1, weight = weight, reps = 10, isCompleted = true),
                        WorkoutSet(setNumber = 2, weight = weight, reps = 10, isCompleted = true),
                        WorkoutSet(setNumber = 3, weight = weight, reps = 10, isCompleted = false),
                    ),
                )
            ),
        )

    @Test
    fun summarize_computesKpisFromCompletedSetsOnly() {
        val session = pushSession("s1", 1_000L, weight = 80.0)

        val summary = SessionInsights.summarize(session, listOf(session), bodyWeightKg = null)

        assertEquals(3_600, summary.durationSeconds)
        assertEquals(1_600.0, summary.strengthVolumeKg, 0.001) // 2 × 80×10
        assertEquals(2, summary.completedSets)
        assertEquals(1, summary.exerciseCount)
        assertEquals(mapOf("Pectoraux" to 2), summary.setsPerMuscle)
        assertEquals(1_600.0, summary.perExercise.single().strengthVolumeKg, 0.001)
        assertNull(summary.volumeDeltaVsPrevious) // no previous comparable session
    }

    @Test
    fun cardioTotals_aggregateDistanceAndTime() {
        val session = WorkoutSession(
            id = "s1", startTime = 0L, endTime = null, name = "Cardio",
            exercises = listOf(
                ExerciseSession(
                    exercise = run,
                    sets = listOf(
                        WorkoutSet(setNumber = 1, distanceKm = 5.0, durationSeconds = 1_500, isCompleted = true),
                        WorkoutSet(setNumber = 2, distanceKm = 2.5, durationSeconds = 800, isCompleted = true),
                    ),
                )
            ),
        )

        val summary = SessionInsights.summarize(session, listOf(session), bodyWeightKg = null)

        assertEquals(7.5, summary.totalDistanceKm, 0.001)
        assertEquals(2_300, summary.totalCardioSeconds)
        assertEquals(0.0, summary.strengthVolumeKg, 0.0)
        assertNull(summary.durationSeconds) // no endTime
    }

    @Test
    fun volumeDelta_comparesToPreviousSessionOfSameTemplate() {
        val previous = pushSession("s1", 1_000L, weight = 80.0)   // 1600 kg
        val current = pushSession("s2", 2_000L, weight = 88.0)    // 1760 kg => +10%
        val unrelated = pushSession("s3", 1_500L, weight = 200.0, templateId = "autre")

        val summary = SessionInsights.summarize(current, listOf(previous, current, unrelated), null)

        assertEquals(0.10, summary.volumeDeltaVsPrevious!!, 0.0001)
    }

    @Test
    fun volumeDelta_fallsBackToSameName_whenNoTemplate() {
        val previous = pushSession("s1", 1_000L, weight = 100.0, templateId = null) // 2000 kg
        val current = pushSession("s2", 2_000L, weight = 90.0, templateId = null)   // 1800 kg => -10%

        val summary = SessionInsights.summarize(current, listOf(previous, current), null)

        assertEquals(-0.10, summary.volumeDeltaVsPrevious!!, 0.0001)
    }
}
