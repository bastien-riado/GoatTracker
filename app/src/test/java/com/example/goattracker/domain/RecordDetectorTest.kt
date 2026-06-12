package com.example.goattracker.domain

import com.example.goattracker.domain.model.Exercise
import com.example.goattracker.domain.model.ExerciseCategory
import com.example.goattracker.domain.model.ExerciseSession
import com.example.goattracker.domain.model.TrackingType
import com.example.goattracker.domain.model.WorkoutSession
import com.example.goattracker.domain.model.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordDetectorTest {

    private val bench = Exercise(
        id = "ex-bench", name = "Développé Couché", category = ExerciseCategory.PUSH,
        primaryMuscle = "Pectoraux", trackingType = TrackingType.WEIGHT_REPS,
    )
    private val pullUps = Exercise(
        id = "ex-pull", name = "Tractions", category = ExerciseCategory.PULL,
        primaryMuscle = "Dos", trackingType = TrackingType.BODYWEIGHT_REPS,
    )
    private val run = Exercise(
        id = "ex-run", name = "Course", category = ExerciseCategory.CARDIO,
        primaryMuscle = "Cardio", trackingType = TrackingType.DISTANCE,
    )

    private fun benchSession(id: String, time: Long, weight: Double, reps: Int) = WorkoutSession(
        id = id, startTime = time, endTime = time + 1, name = "Push",
        exercises = listOf(
            ExerciseSession(
                exercise = bench,
                sets = listOf(WorkoutSet(setNumber = 1, weight = weight, reps = reps, isCompleted = true)),
            )
        ),
    )

    @Test
    fun beatingWeightAndOneRm_yieldsBothRecords_plusSessionVolume() {
        val old = benchSession("s1", 1_000L, weight = 100.0, reps = 5) // e1RM Epley ≈ 116.7, volume 500
        val new = benchSession("s2", 2_000L, weight = 105.0, reps = 5) // e1RM ≈ 122.5, volume 525

        val records = RecordDetector.detect(new, listOf(old, new), bodyWeightKg = null)

        val kinds = records.map { it.kind }.toSet()
        assertEquals(setOf(RecordKind.MAX_WEIGHT, RecordKind.EST_ONE_RM, RecordKind.SESSION_VOLUME), kinds)
        val weight = records.first { it.kind == RecordKind.MAX_WEIGHT }
        assertEquals(105.0, weight.value, 0.001)
        assertEquals(100.0, weight.previousBest, 0.001)
        assertEquals("Développé Couché", weight.exerciseName)
    }

    @Test
    fun firstEverPerformance_staysQuiet() {
        val first = benchSession("s1", 1_000L, weight = 100.0, reps = 5)

        assertTrue(RecordDetector.detect(first, listOf(first), null).isEmpty())
    }

    @Test
    fun equalingTheRecord_isNotARecord() {
        val old = benchSession("s1", 1_000L, weight = 100.0, reps = 5)
        val same = benchSession("s2", 2_000L, weight = 100.0, reps = 5)

        assertTrue(RecordDetector.detect(same, listOf(old, same), null).isEmpty())
    }

    @Test
    fun onlyPriorSessionsCount_notLaterOnes() {
        val current = benchSession("s2", 2_000L, weight = 105.0, reps = 5)
        val laterStronger = benchSession("s3", 3_000L, weight = 120.0, reps = 5)
        val old = benchSession("s1", 1_000L, weight = 100.0, reps = 5)

        val records = RecordDetector.detect(current, listOf(old, current, laterStronger), null)

        // The later 120 kg session must not erase the record this session set at its time.
        assertTrue(records.any { it.kind == RecordKind.MAX_WEIGHT && it.value == 105.0 })
    }

    @Test
    fun bodyweightReps_recordOnMaxRepsOnly() {
        fun session(id: String, time: Long, reps: Int) = WorkoutSession(
            id = id, startTime = time, name = "Pull",
            exercises = listOf(
                ExerciseSession(
                    exercise = pullUps,
                    sets = listOf(WorkoutSet(setNumber = 1, reps = reps, isCompleted = true)),
                )
            ),
        )
        val old = session("s1", 1_000L, reps = 10)
        val new = session("s2", 2_000L, reps = 12)

        val records = RecordDetector.detect(new, listOf(old, new), bodyWeightKg = null)

        assertEquals(listOf(RecordKind.MAX_REPS), records.map { it.kind })
        assertEquals(12.0, records.single().value, 0.0)
    }

    @Test
    fun cardio_betterPaceIsLower_andDistanceRecordDetected() {
        fun session(id: String, time: Long, km: Double, seconds: Int) = WorkoutSession(
            id = id, startTime = time, name = "Cardio",
            exercises = listOf(
                ExerciseSession(
                    exercise = run,
                    sets = listOf(
                        WorkoutSet(setNumber = 1, distanceKm = km, durationSeconds = seconds, isCompleted = true)
                    ),
                )
            ),
        )
        val old = session("s1", 1_000L, km = 5.0, seconds = 1_600) // 320 s/km
        val new = session("s2", 2_000L, km = 6.0, seconds = 1_800) // 300 s/km — faster AND longer

        val records = RecordDetector.detect(new, listOf(old, new), null)

        val kinds = records.map { it.kind }.toSet()
        assertEquals(setOf(RecordKind.MAX_DISTANCE, RecordKind.BEST_PACE), kinds)
        assertEquals(300.0, records.first { it.kind == RecordKind.BEST_PACE }.value, 0.001)
    }

    @Test
    fun incompleteSets_neverCount() {
        val old = benchSession("s1", 1_000L, weight = 100.0, reps = 5)
        val new = WorkoutSession(
            id = "s2", startTime = 2_000L, name = "Push",
            exercises = listOf(
                ExerciseSession(
                    exercise = bench,
                    sets = listOf(WorkoutSet(setNumber = 1, weight = 150.0, reps = 5, isCompleted = false)),
                )
            ),
        )

        assertTrue(RecordDetector.detect(new, listOf(old, new), null).isEmpty())
    }
}
