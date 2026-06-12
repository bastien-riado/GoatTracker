package com.example.goattracker.ui.templates

import com.example.goattracker.data.FakeDataRepository
import com.example.goattracker.domain.model.TemplateEntry
import com.example.goattracker.domain.model.TemplateTargetMode
import com.example.goattracker.domain.model.WorkoutSession
import com.example.goattracker.domain.model.WorkoutTemplate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateLauncherTest {

    @Test
    fun launch_persistsThePreFilledDraft_observableBeforeReturning() = runTest {
        val repository = FakeDataRepository()
        val bench = repository.workoutState.first().exercises.first()
        val template = WorkoutTemplate(
            id = "tpl-push",
            name = "Push",
            entries = listOf(
                TemplateEntry(exerciseId = bench.id, targetSets = 3, targetReps = 8, targetWeightKg = 80.0),
                TemplateEntry(exerciseId = bench.id, targetSets = 2, targetMode = TemplateTargetMode.AMRAP, targetReps = null),
            ),
        )

        val launched = TemplateLauncher(repository).launch(template, now = 42L)

        assertTrue(launched)
        val draft = repository.getLatestState().activeDraft
        assertNotNull(draft)
        assertEquals("Push", draft!!.name)
        assertEquals("tpl-push", draft.templateId)
        assertEquals(2, draft.exercises.size)
        assertEquals(3, draft.exercises[0].sets.size)
        assertEquals(80.0, draft.exercises[0].sets[0].weight, 0.0)
        assertTrue(draft.exercises[1].sets.all { it.isToFailure })
        assertTrue(draft.exercises.flatMap { it.sets }.none { it.isCompleted })
    }

    @Test
    fun launch_whileASessionIsLive_writesNothing() = runTest {
        val repository = FakeDataRepository()
        val bench = repository.workoutState.first().exercises.first()
        val existing = WorkoutSession(name = "Séance en cours")
        repository.saveActiveDraft(existing)
        val template = WorkoutTemplate(
            name = "Push",
            entries = listOf(TemplateEntry(exerciseId = bench.id)),
        )

        val launched = TemplateLauncher(repository).launch(template)

        assertFalse(launched)
        assertEquals(existing.id, repository.getLatestState().activeDraft?.id)
    }
}
