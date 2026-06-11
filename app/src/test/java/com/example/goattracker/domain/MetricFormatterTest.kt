package com.example.goattracker.domain

import com.example.goattracker.domain.model.Exercise
import com.example.goattracker.domain.model.ExerciseCategory
import com.example.goattracker.domain.model.ExerciseSession
import com.example.goattracker.domain.model.TrackingType
import com.example.goattracker.domain.model.UserProfile
import com.example.goattracker.domain.model.WeightUnit
import com.example.goattracker.domain.model.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Test

class MetricFormatterTest {

    private val kgProfile = UserProfile(bodyWeightKg = 72.5, weightUnit = WeightUnit.KG)
    private val lbsProfile = UserProfile(bodyWeightKg = 72.5, weightUnit = WeightUnit.LBS)

    private fun exercise(track: TrackingType) =
        Exercise(name = "x", category = ExerciseCategory.PUSH, primaryMuscle = "Pectoraux", trackingType = track)

    // --- weight ---

    @Test
    fun weight_kg_keepsDecimals_trimsTrailingZero() {
        assertEquals("22,5 kg", MetricFormatter.weight(22.5, WeightUnit.KG))
        assertEquals("80 kg", MetricFormatter.weight(80.0, WeightUnit.KG))
    }

    @Test
    fun weight_lbs_convertsFromKg() {
        // 100 lbs entered -> stored as 45.359 kg -> displayed back as 100 lbs (stable round-trip)
        val storedKg = WeightUnit.LBS.toKg(100.0)
        assertEquals("100 lbs", MetricFormatter.weight(storedKg, WeightUnit.LBS))
    }

    @Test
    fun weightUnit_conversion_roundTrips() {
        val kg = WeightUnit.LBS.toKg(225.0)
        assertEquals(225.0, WeightUnit.LBS.fromKg(kg), 1e-9)
    }

    // --- tonnage ---

    @Test
    fun tonnage_kg_switchesToTonnes() {
        assertEquals("850 kg", MetricFormatter.tonnage(850.0, WeightUnit.KG))
        assertEquals("2,40 t", MetricFormatter.tonnage(2400.0, WeightUnit.KG))
    }

    @Test
    fun tonnage_lbs_groupedPounds_noTonnes() {
        assertEquals("2 205 lbs", MetricFormatter.tonnage(1000.0, WeightUnit.LBS))
    }

    // --- duration / distance / pace / speed ---

    @Test
    fun duration_formats() {
        assertEquals("45 s", MetricFormatter.duration(45))
        assertEquals("12:30", MetricFormatter.duration(750))
        assertEquals("1h05", MetricFormatter.duration(3900))
    }

    @Test
    fun distance_formats() {
        assertEquals("800 m", MetricFormatter.distance(0.8))
        assertEquals("5,00 km", MetricFormatter.distance(5.0))
    }

    @Test
    fun pace_and_speed_format() {
        assertEquals("5:00 /km", MetricFormatter.pace(300.0))
        assertEquals("12 km/h", MetricFormatter.speed(12.0))
        assertEquals("11,1 km/h", MetricFormatter.speed(11.1))
    }

    // --- composed lines ---

    @Test
    fun setLineCompact_perType() {
        assertEquals(
            "5 reps @ 100 kg",
            MetricFormatter.setLineCompact(
                TrackingType.WEIGHT_REPS,
                WorkoutSet(setNumber = 1, weight = 100.0, reps = 5),
                kgProfile,
            ),
        )
        assertEquals(
            "12 reps (PDC)",
            MetricFormatter.setLineCompact(
                TrackingType.BODYWEIGHT_REPS,
                WorkoutSet(setNumber = 1, reps = 12),
                kgProfile,
            ),
        )
        assertEquals(
            "5,00 km • 25:00 • 5:00 /km",
            MetricFormatter.setLineCompact(
                TrackingType.DISTANCE,
                WorkoutSet(setNumber = 1, distanceKm = 5.0, durationSeconds = 1500),
                kgProfile,
            ),
        )
        // Distance without duration: no pace segment
        assertEquals(
            "5,00 km",
            MetricFormatter.setLineCompact(
                TrackingType.DISTANCE,
                WorkoutSet(setNumber = 1, distanceKm = 5.0),
                kgProfile,
            ),
        )
    }

    @Test
    fun exerciseSummary_weightReps_usesHeaviestSet_inProfileUnit() {
        val session = ExerciseSession(
            exercise = exercise(TrackingType.WEIGHT_REPS),
            sets = listOf(
                WorkoutSet(setNumber = 1, weight = 100.0, reps = 5, isCompleted = true),
                WorkoutSet(setNumber = 2, weight = 90.0, reps = 8, isCompleted = true),
            ),
        )
        assertEquals("100 kg x 5", MetricFormatter.exerciseSummary(session, kgProfile))
        assertEquals("220,5 lbs x 5", MetricFormatter.exerciseSummary(session, lbsProfile))
    }

    @Test
    fun exerciseSummary_bodyweight_maxReps() {
        val session = ExerciseSession(
            exercise = exercise(TrackingType.BODYWEIGHT_REPS),
            sets = listOf(WorkoutSet(setNumber = 1, reps = 12, isCompleted = true)),
        )
        assertEquals("PDC x 12", MetricFormatter.exerciseSummary(session, kgProfile))
    }

    @Test
    fun lastWorkoutLine_perType() {
        val sets = listOf(
            WorkoutSet(setNumber = 1, weight = 80.0, reps = 10, isCompleted = true),
            WorkoutSet(setNumber = 2, weight = 80.0, reps = 10, isCompleted = true),
            WorkoutSet(setNumber = 3, weight = 80.0, reps = 10, isCompleted = true),
        )
        assertEquals("3x10 • 80 kg", MetricFormatter.lastWorkoutLine(TrackingType.WEIGHT_REPS, sets, kgProfile))

        val cardio = listOf(WorkoutSet(setNumber = 1, distanceKm = 5.0, durationSeconds = 1500, isCompleted = true))
        assertEquals("5,00 km • 5:00 /km", MetricFormatter.lastWorkoutLine(TrackingType.DISTANCE, cardio, kgProfile))
    }

    @Test
    fun progressionPoint_bodyweight_dependsOnProfileWeight() {
        val noWeight = UserProfile(bodyWeightKg = null)
        assertEquals("22 reps", MetricFormatter.progressionPoint(TrackingType.BODYWEIGHT_REPS, 22.0, noWeight))
        assertEquals("1,60 t", MetricFormatter.progressionPoint(TrackingType.BODYWEIGHT_REPS, 1595.0, kgProfile))
    }
}
