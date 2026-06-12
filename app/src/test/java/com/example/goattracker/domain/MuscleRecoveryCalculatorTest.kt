package com.example.goattracker.domain

import com.example.goattracker.domain.model.Exercise
import com.example.goattracker.domain.model.ExerciseCategory
import com.example.goattracker.domain.model.ExerciseSession
import com.example.goattracker.domain.model.MuscleGroup
import com.example.goattracker.domain.model.TrackingType
import com.example.goattracker.domain.model.WorkoutSession
import com.example.goattracker.domain.model.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MuscleRecoveryCalculatorTest {

    private val now = 1_000_000_000_000L
    private val hour = 3_600_000L
    private val calc = MuscleRecoveryCalculator() // base 48h, +4h/set, cap 96h

    private fun session(endHoursAgo: Long?, muscle: String, completedSets: Int): WorkoutSession {
        val ex = Exercise(
            name = "ex-$muscle",
            category = ExerciseCategory.PUSH,
            primaryMuscle = muscle,
            trackingType = TrackingType.WEIGHT_REPS,
        )
        val sets = (1..completedSets).map { WorkoutSet(setNumber = it, weight = 50.0, reps = 8, isCompleted = true) }
        return WorkoutSession(
            startTime = now - (endHoursAgo ?: 0) * hour - hour,
            endTime = endHoursAgo?.let { now - it * hour },
            name = "s-$muscle",
            exercises = listOf(ExerciseSession(exercise = ex, sets = sets)),
        )
    }

    @Test
    fun untrainedMuscleHasNoData() {
        val statuses = calc.compute(emptyList(), now)
        val biceps = statuses.getValue(MuscleGroup.BICEPS)
        assertFalse(biceps.hasData)
        assertTrue(biceps.recovery.isNaN())
        assertEquals(null, biceps.lastWorkedAt)
    }

    @Test
    fun justTrainedIsFullyFatigued() {
        val statuses = calc.compute(listOf(session(endHoursAgo = 0, muscle = "Quadriceps", completedSets = 3)), now)
        assertEquals(0f, statuses.getValue(MuscleGroup.QUADS).recovery, 0.0001f)
    }

    @Test
    fun recoveryIsElapsedOverVolumeScaledWindow() {
        // 3 completed sets -> window = 48 + 4*3 = 60h. Trained 30h ago -> 30/60 = 0.5.
        val statuses = calc.compute(listOf(session(endHoursAgo = 30, muscle = "Pectoraux", completedSets = 3)), now)
        val chest = statuses.getValue(MuscleGroup.CHEST)
        assertEquals(0.5f, chest.recovery, 0.0001f)
        assertEquals(3, chest.recentSets)
        assertTrue(chest.hasData)
    }

    private fun benchWithSecondaries(endHoursAgo: Long, completedSets: Int): WorkoutSession {
        val ex = Exercise(
            name = "Développé Couché",
            category = ExerciseCategory.PUSH,
            primaryMuscle = "Pectoraux",
            trackingType = TrackingType.WEIGHT_REPS,
            secondaryMuscles = listOf("Triceps", "Épaules avant"),
        )
        val sets = (1..completedSets).map { WorkoutSet(setNumber = it, weight = 80.0, reps = 8, isCompleted = true) }
        return WorkoutSession(
            startTime = now - endHoursAgo * hour - hour,
            endTime = now - endHoursAgo * hour,
            name = "Push",
            exercises = listOf(ExerciseSession(exercise = ex, sets = sets)),
        )
    }

    @Test
    fun secondaryMuscles_getHalfDoseAndHalfBase_soTheyRecoverFaster() {
        // 4 sets, ended 26h ago.
        // CHEST (primary):   window = 48*1.0 + 4*4.0 = 64h  -> 26/64
        // TRICEPS (secondary): window = 48*0.5 + 4*2.0 = 32h -> 26/32
        val statuses = calc.compute(listOf(benchWithSecondaries(endHoursAgo = 26, completedSets = 4)), now)

        val chest = statuses.getValue(MuscleGroup.CHEST)
        val triceps = statuses.getValue(MuscleGroup.TRICEPS)
        val frontDelts = statuses.getValue(MuscleGroup.FRONT_DELTS)
        assertEquals(26f / 64f, chest.recovery, 0.0001f)
        assertEquals(26f / 32f, triceps.recovery, 0.0001f)
        assertTrue(frontDelts.hasData) // "Épaules avant" mapped as a secondary too
        assertEquals(2, triceps.recentSets) // ceil(4 × 0.5)
        assertTrue("a secondary recovers faster than the primary", triceps.recovery > chest.recovery)
    }

    @Test
    fun perMuscleOverride_replacesTheBaseWindow() {
        // 3 sets ended 42h ago, override QUADS at 72h: window = 72 + 12 = 84h -> 42/84 = 0.5
        // (default base would give 42/60 = 0.7).
        val sessions = listOf(session(endHoursAgo = 42, muscle = "Quadriceps", completedSets = 3))

        val withOverride = calc.compute(sessions, now, mapOf(MuscleGroup.QUADS to 72))
        val withoutOverride = calc.compute(sessions, now)

        assertEquals(0.5f, withOverride.getValue(MuscleGroup.QUADS).recovery, 0.0001f)
        assertEquals(0.7f, withoutOverride.getValue(MuscleGroup.QUADS).recovery, 0.0001f)
    }

    @Test
    fun overrideRaisesTheCap_soBigBasesAreNotClipped() {
        // Override 96h + 12 sets: window = 96 + 48 = 144h (the default 96h cap must not clip it).
        // Trained 72h ago -> 0.5.
        val sessions = listOf(session(endHoursAgo = 72, muscle = "Dos", completedSets = 12))

        val statuses = calc.compute(sessions, now, mapOf(MuscleGroup.LATS to 96))

        assertEquals(0.5f, statuses.getValue(MuscleGroup.LATS).recovery, 0.0001f)
    }

    @Test
    fun recoveryClampsToOneWhenFullyRested() {
        val statuses = calc.compute(listOf(session(endHoursAgo = 240, muscle = "Dos", completedSets = 2)), now)
        assertEquals(1f, statuses.getValue(MuscleGroup.LATS).recovery, 0.0001f)
    }

    @Test
    fun mostRecentSessionWins() {
        val statuses = calc.compute(
            listOf(
                session(endHoursAgo = 100, muscle = "Biceps", completedSets = 5), // old, fully recovered
                session(endHoursAgo = 0, muscle = "Biceps", completedSets = 2),    // newest, just trained
            ),
            now,
        )
        val biceps = statuses.getValue(MuscleGroup.BICEPS)
        assertEquals(0f, biceps.recovery, 0.0001f)
        assertEquals(2, biceps.recentSets) // from the newest session
    }

    @Test
    fun inProgressSessionIsIgnored() {
        val statuses = calc.compute(listOf(session(endHoursAgo = null, muscle = "Mollets", completedSets = 4)), now)
        assertFalse(statuses.getValue(MuscleGroup.CALVES).hasData)
    }

    @Test
    fun setsWithoutCompletionDoNotCount() {
        val ex = Exercise(name = "x", category = ExerciseCategory.LEG, primaryMuscle = "Quadriceps", trackingType = TrackingType.WEIGHT_REPS)
        val openSets = listOf(WorkoutSet(setNumber = 1, isCompleted = false), WorkoutSet(setNumber = 2, isCompleted = false))
        val s = WorkoutSession(startTime = now - 2 * hour, endTime = now - hour, name = "s", exercises = listOf(ExerciseSession(exercise = ex, sets = openSets)))
        assertFalse(calc.compute(listOf(s), now).getValue(MuscleGroup.QUADS).hasData)
    }
}
