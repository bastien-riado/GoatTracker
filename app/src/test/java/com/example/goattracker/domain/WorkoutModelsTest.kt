package com.example.goattracker.domain

import com.example.goattracker.domain.model.Exercise
import com.example.goattracker.domain.model.ExerciseCategory
import com.example.goattracker.domain.model.ExerciseSession
import com.example.goattracker.domain.model.TrackingType
import com.example.goattracker.domain.model.WorkoutSession
import com.example.goattracker.domain.model.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Test

/** Regression coverage for the core domain computations (volume, completed-set counting, display). */
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

    @Test
    fun totalVolume_weightReps_sumsWeightTimesRepsForCompletedOnly() {
        val s = WorkoutSession(
            name = "s",
            exercises = listOf(
                ExerciseSession(
                    exercise = exercise(TrackingType.WEIGHT_REPS),
                    sets = listOf(
                        WorkoutSet(setNumber = 1, weight = 100.0, reps = 5, isCompleted = true),
                        WorkoutSet(setNumber = 2, weight = 100.0, reps = 5, isCompleted = true),
                        WorkoutSet(setNumber = 3, weight = 90.0, reps = 8, isCompleted = false),
                    ),
                ),
            ),
        )
        assertEquals(1000.0, s.totalVolume, 0.0) // 3rd set excluded (not completed)
    }

    @Test
    fun totalVolume_perTrackingType() {
        fun single(track: TrackingType, set: WorkoutSet) = WorkoutSession(
            name = "s", exercises = listOf(ExerciseSession(exercise = exercise(track), sets = listOf(set))),
        ).totalVolume

        assertEquals(20.0, single(TrackingType.BODYWEIGHT_REPS, WorkoutSet(setNumber = 1, reps = 20, isCompleted = true)), 0.0)
        assertEquals(90.0, single(TrackingType.TIME, WorkoutSet(setNumber = 1, durationSeconds = 90, isCompleted = true)), 0.0)
        assertEquals(5000.0, single(TrackingType.DISTANCE, WorkoutSet(setNumber = 1, distanceKm = 5.0, isCompleted = true)), 0.0)
    }

    @Test
    fun volumeMetricDisplay_weightAndBodyweight() {
        val weight = ExerciseSession(
            exercise = exercise(TrackingType.WEIGHT_REPS),
            sets = listOf(WorkoutSet(setNumber = 1, weight = 100.0, reps = 5, isCompleted = true)),
        )
        assertEquals("100kg x 5", weight.volumeMetricDisplay)

        val bw = ExerciseSession(
            exercise = exercise(TrackingType.BODYWEIGHT_REPS),
            sets = listOf(WorkoutSet(setNumber = 1, reps = 12, isCompleted = true)),
        )
        assertEquals("PDC x 12", bw.volumeMetricDisplay)
    }
}
