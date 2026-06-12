package com.example.goattracker.ui.templates

import com.example.goattracker.MainDispatcherRule
import com.example.goattracker.data.FakeDataRepository
import com.example.goattracker.domain.model.TemplateEntry
import com.example.goattracker.domain.model.WorkoutTemplate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TemplatesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun items_resolveExerciseNamesForDisplay() = runTest {
        val repository = FakeDataRepository()
        val state = repository.workoutState.first()
        repository.saveWorkoutTemplate(
            WorkoutTemplate(
                name = "Push",
                entries = listOf(
                    TemplateEntry(exerciseId = state.exercises[0].id),
                    TemplateEntry(exerciseId = state.exercises[2].id),
                ),
            )
        )

        val items = TemplatesViewModel(repository).items.first { it.isNotEmpty() }

        assertEquals("Push", items.single().template.name)
        assertEquals(
            listOf("Développé Couché", "Squat Barre"),
            items.single().exerciseNames,
        )
    }

    @Test
    fun delete_removesTheTemplate() = runTest {
        val repository = FakeDataRepository()
        val template = WorkoutTemplate(name = "Pull")
        repository.saveWorkoutTemplate(template)
        val viewModel = TemplatesViewModel(repository)
        viewModel.items.first { it.isNotEmpty() }

        viewModel.delete(template.id)

        assertTrue(repository.templates.first().isEmpty())
    }

    @Test
    fun launch_persistsDraftThenInvokesNavigation() = runTest {
        val repository = FakeDataRepository()
        val bench = repository.workoutState.first().exercises.first()
        val template = WorkoutTemplate(
            name = "Push",
            entries = listOf(TemplateEntry(exerciseId = bench.id)),
        )
        repository.saveWorkoutTemplate(template)
        val viewModel = TemplatesViewModel(repository)
        var opened = false

        viewModel.launch(template) { opened = true }

        assertTrue(opened)
        val draft = repository.getLatestState().activeDraft
        assertNotNull(draft)
        assertEquals(template.id, draft!!.templateId)
    }
}
