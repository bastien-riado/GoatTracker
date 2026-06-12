package com.example.goattracker.domain

import com.example.goattracker.domain.model.Exercise
import com.example.goattracker.domain.model.ExerciseCategory
import com.example.goattracker.domain.model.TemplateEntry
import com.example.goattracker.domain.model.TemplateTargetMode
import com.example.goattracker.domain.model.TrackingType
import com.example.goattracker.domain.model.WorkoutTemplate
import com.example.goattracker.domain.model.toDraftSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateModelsTest {

    private val bench = Exercise(
        id = "ex-bench",
        name = "Développé Couché",
        category = ExerciseCategory.PUSH,
        primaryMuscle = "Pectoraux",
        trackingType = TrackingType.WEIGHT_REPS,
        restTimeSeconds = 120,
    )
    private val dips = Exercise(
        id = "ex-dips",
        name = "Dips",
        category = ExerciseCategory.PUSH,
        primaryMuscle = "Triceps",
        trackingType = TrackingType.BODYWEIGHT_REPS,
        restTimeSeconds = 90,
    )
    private val catalog = mapOf(bench.id to bench, dips.id to dips)

    private val pushTemplate = WorkoutTemplate(
        id = "tpl-push",
        name = "Push",
        entries = listOf(
            TemplateEntry(exerciseId = bench.id, targetSets = 3, targetReps = 8, targetWeightKg = 80.0),
            TemplateEntry(exerciseId = dips.id, targetSets = 2, targetMode = TemplateTargetMode.AMRAP, targetReps = null),
        ),
    )

    @Test
    fun toDraftSession_preCreatesTargetSets_incomplete() {
        val draft = pushTemplate.toDraftSession(catalog::get, now = 42L)

        assertEquals("Push", draft.name)
        assertEquals(42L, draft.startTime)
        assertEquals("tpl-push", draft.templateId)
        assertEquals(listOf("Développé Couché", "Dips"), draft.exercises.map { it.exercise.name })

        val benchSets = draft.exercises[0].sets
        assertEquals(listOf(1, 2, 3), benchSets.map { it.setNumber })
        assertTrue(benchSets.all { it.weight == 80.0 && it.reps == 8 })
        assertTrue("target sets must start unchecked", benchSets.none { it.isCompleted })
    }

    @Test
    fun amrapSlots_flagOnlyTheirLastSetToFailure() {
        // Failure is a per-set notion: "2 séries classiques + 1 à l'échec".
        val template = WorkoutTemplate(
            name = "Push",
            entries = listOf(
                TemplateEntry(
                    exerciseId = bench.id,
                    targetSets = 3,
                    targetReps = 10,
                    targetWeightKg = 80.0,
                    targetMode = TemplateTargetMode.AMRAP,
                )
            ),
        )

        val sets = template.toDraftSession(catalog::get, now = 0L).exercises.single().sets

        assertEquals(3, sets.size)
        // Regular sets keep their targets…
        assertEquals(listOf(10, 10), sets.take(2).map { it.reps })
        assertFalse(sets.take(2).any { it.isToFailure })
        // …only the last one is the failure set (no rep target, weight still prefilled).
        assertTrue(sets.last().isToFailure)
        assertEquals(0, sets.last().reps)
        assertEquals(80.0, sets.last().weight, 0.0)

        // The non-AMRAP exercise of the shared fixture stays a plain target.
        val plain = pushTemplate.toDraftSession(catalog::get, now = 0L)
        assertFalse(plain.exercises[0].sets.any { it.isToFailure })
    }

    @Test
    fun restOverride_ridesTheEmbeddedExerciseCopy() {
        val template = pushTemplate.copy(
            entries = listOf(
                TemplateEntry(exerciseId = bench.id, restOverrideSeconds = 240),
                TemplateEntry(exerciseId = dips.id), // no override
            ),
        )

        val draft = template.toDraftSession(catalog::get, now = 0L)

        assertEquals(240, draft.exercises[0].exercise.restTimeSeconds)
        assertEquals(90, draft.exercises[1].exercise.restTimeSeconds)
    }

    @Test
    fun unresolvableExercise_isSkipped_notFatal() {
        val template = pushTemplate.copy(
            entries = pushTemplate.entries + TemplateEntry(exerciseId = "ex-deleted"),
        )

        val draft = template.toDraftSession(catalog::get, now = 0L)

        assertEquals(2, draft.exercises.size)
    }

    @Test
    fun zeroTargetSets_coercesToOne() {
        val template = WorkoutTemplate(
            name = "Edge",
            entries = listOf(TemplateEntry(exerciseId = bench.id, targetSets = 0)),
        )

        assertEquals(1, template.toDraftSession(catalog::get, now = 0L).exercises.single().sets.size)
    }
}
