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

class WorkoutMetricsTest {

    private fun exercise(track: TrackingType) =
        Exercise(name = "x", category = ExerciseCategory.PUSH, primaryMuscle = "Pectoraux", trackingType = track)

    private fun es(track: TrackingType, vararg sets: WorkoutSet) =
        ExerciseSession(exercise = exercise(track), sets = sets.toList())

    // --- strengthVolumeKg ---

    @Test
    fun weightReps_volume_sumsWeightTimesReps_completedOnly() {
        val session = es(
            TrackingType.WEIGHT_REPS,
            WorkoutSet(setNumber = 1, weight = 100.0, reps = 5, isCompleted = true),
            WorkoutSet(setNumber = 2, weight = 100.0, reps = 5, isCompleted = true),
            WorkoutSet(setNumber = 3, weight = 90.0, reps = 8, isCompleted = false),
        )
        assertEquals(1000.0, WorkoutMetrics.strengthVolumeKg(session, bodyWeightKg = null), 0.0)
    }

    @Test
    fun bodyweight_volume_usesUserBodyWeight() {
        val session = es(
            TrackingType.BODYWEIGHT_REPS,
            WorkoutSet(setNumber = 1, reps = 10, isCompleted = true),
            WorkoutSet(setNumber = 2, reps = 8, isCompleted = true),
        )
        assertEquals(18 * 72.5, WorkoutMetrics.strengthVolumeKg(session, bodyWeightKg = 72.5), 0.001)
    }

    @Test
    fun bodyweight_volume_zeroWhenBodyWeightUnknown() {
        val session = es(
            TrackingType.BODYWEIGHT_REPS,
            WorkoutSet(setNumber = 1, reps = 10, isCompleted = true),
        )
        assertEquals(0.0, WorkoutMetrics.strengthVolumeKg(session, bodyWeightKg = null), 0.0)
    }

    @Test
    fun enduranceTypes_neverContributeToTonnage() {
        val time = es(TrackingType.TIME, WorkoutSet(setNumber = 1, durationSeconds = 600, isCompleted = true))
        val dist = es(TrackingType.DISTANCE, WorkoutSet(setNumber = 1, distanceKm = 10.0, isCompleted = true))
        assertEquals(0.0, WorkoutMetrics.strengthVolumeKg(time, 80.0), 0.0)
        assertEquals(0.0, WorkoutMetrics.strengthVolumeKg(dist, 80.0), 0.0)
    }

    @Test
    fun sessionTonnage_mixedSession_onlyStrengthCounts() {
        val session = WorkoutSession(
            name = "mix",
            exercises = listOf(
                es(TrackingType.WEIGHT_REPS, WorkoutSet(setNumber = 1, weight = 60.0, reps = 10, isCompleted = true)),
                es(TrackingType.BODYWEIGHT_REPS, WorkoutSet(setNumber = 1, reps = 10, isCompleted = true)),
                es(TrackingType.DISTANCE, WorkoutSet(setNumber = 1, distanceKm = 5.0, durationSeconds = 1500, isCompleted = true)),
                es(TrackingType.TIME, WorkoutSet(setNumber = 1, durationSeconds = 90, isCompleted = true)),
            ),
        )
        // 600 (barbell) + 700 (10 reps x 70 kg bodyweight); the 5 km run and the plank add NOTHING.
        assertEquals(1300.0, WorkoutMetrics.sessionStrengthVolumeKg(session, bodyWeightKg = 70.0), 0.001)
    }

    // --- effectiveLoadKg ---

    @Test
    fun effectiveLoad_perType() {
        val set = WorkoutSet(setNumber = 1, weight = 80.0, reps = 5)
        assertEquals(80.0, WorkoutMetrics.effectiveLoadKg(TrackingType.WEIGHT_REPS, set, 70.0)!!, 0.0)
        assertEquals(70.0, WorkoutMetrics.effectiveLoadKg(TrackingType.BODYWEIGHT_REPS, set, 70.0)!!, 0.0)
        assertNull(WorkoutMetrics.effectiveLoadKg(TrackingType.BODYWEIGHT_REPS, set, null))
        assertNull(WorkoutMetrics.effectiveLoadKg(TrackingType.TIME, set, 70.0))
        assertNull(WorkoutMetrics.effectiveLoadKg(TrackingType.DISTANCE, set, 70.0))
    }

    // --- progressionValue ---

    @Test
    fun progression_bodyweight_fallsBackToRepsWithoutBodyWeight() {
        val session = es(
            TrackingType.BODYWEIGHT_REPS,
            WorkoutSet(setNumber = 1, reps = 10, isCompleted = true),
            WorkoutSet(setNumber = 2, reps = 12, isCompleted = true),
        )
        assertEquals(22.0, WorkoutMetrics.progressionValue(session, null), 0.0)
        assertEquals(22 * 70.0, WorkoutMetrics.progressionValue(session, 70.0), 0.001)
    }

    @Test
    fun progression_enduranceTypes_useTheirOwnUnit() {
        val time = es(TrackingType.TIME, WorkoutSet(setNumber = 1, durationSeconds = 90, isCompleted = true))
        val dist = es(TrackingType.DISTANCE, WorkoutSet(setNumber = 1, distanceKm = 5.5, isCompleted = true))
        assertEquals(90.0, WorkoutMetrics.progressionValue(time, null), 0.0)
        assertEquals(5.5, WorkoutMetrics.progressionValue(dist, null), 0.0)
    }

    // --- pace / speed ---

    @Test
    fun pace_and_speed_derivation() {
        // 5 km in 25 min -> 5:00 /km -> 12 km/h
        assertEquals(300.0, WorkoutMetrics.paceSecPerKm(1500, 5.0)!!, 0.001)
        assertEquals(12.0, WorkoutMetrics.speedKmh(1500, 5.0)!!, 0.001)
    }

    @Test
    fun pace_and_speed_nullWhenIncomplete() {
        assertNull(WorkoutMetrics.paceSecPerKm(0, 5.0))
        assertNull(WorkoutMetrics.paceSecPerKm(1500, 0.0))
        assertNull(WorkoutMetrics.speedKmh(0, 5.0))
        assertNull(WorkoutMetrics.speedKmh(1500, 0.0))
    }
}
