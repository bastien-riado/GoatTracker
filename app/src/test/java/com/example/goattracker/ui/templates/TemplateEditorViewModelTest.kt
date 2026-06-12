package com.example.goattracker.ui.templates

import com.example.goattracker.MainDispatcherRule
import com.example.goattracker.data.FakeDataRepository
import com.example.goattracker.domain.model.TemplateEntry
import com.example.goattracker.domain.model.TemplateTargetMode
import com.example.goattracker.domain.model.UserProfile
import com.example.goattracker.domain.model.WeightUnit
import com.example.goattracker.domain.model.WorkoutTemplate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TemplateEditorViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun validation_requiresNameAndAtLeastOneExercise() = runTest {
        val repository = FakeDataRepository()
        val bench = repository.workoutState.first().exercises.first()
        val viewModel = TemplateEditorViewModel(repository, templateId = null)

        assertFalse(viewModel.uiState.value.isSaveEnabled)
        viewModel.updateName("Push")
        assertFalse(viewModel.uiState.value.isSaveEnabled)
        viewModel.addExercise(bench)
        assertTrue(viewModel.uiState.value.isSaveEnabled)
        viewModel.removeRow(viewModel.uiState.value.rows.single().entryId)
        assertFalse(viewModel.uiState.value.isSaveEnabled)
    }

    @Test
    fun save_buildsTheTemplate_withParsedTargets() = runTest {
        val repository = FakeDataRepository()
        val bench = repository.workoutState.first().exercises.first()
        val viewModel = TemplateEditorViewModel(repository, templateId = null)

        viewModel.updateName("  Push ")
        viewModel.addExercise(bench)
        val rowId = viewModel.uiState.value.rows.single().entryId
        viewModel.updateSets(rowId, "4")
        viewModel.updateReps(rowId, "8")
        viewModel.updateWeight(rowId, "82,5") // comma input must parse
        viewModel.save()

        val saved = repository.templates.first { it.isNotEmpty() }.single()
        assertEquals("Push", saved.name)
        val entry = saved.entries.single()
        assertEquals(bench.id, entry.exerciseId)
        assertEquals(4, entry.targetSets)
        assertEquals(8, entry.targetReps)
        assertEquals(82.5, entry.targetWeightKg!!, 0.0001)
        assertEquals(TemplateTargetMode.REPS, entry.targetMode)
    }

    @Test
    fun amrapToggle_savesAmrapWithoutRepTarget() = runTest {
        val repository = FakeDataRepository()
        val bench = repository.workoutState.first().exercises.first()
        val viewModel = TemplateEditorViewModel(repository, templateId = null)

        viewModel.updateName("Push")
        viewModel.addExercise(bench)
        viewModel.toggleAmrap(viewModel.uiState.value.rows.single().entryId)
        viewModel.save()

        val entry = repository.templates.first { it.isNotEmpty() }.single().entries.single()
        assertEquals(TemplateTargetMode.AMRAP, entry.targetMode)
        assertNull(entry.targetReps)
    }

    @Test
    fun weightInput_isInProfileUnit_storedInKg() = runTest {
        val repository = FakeDataRepository()
        repository.saveUserProfile(UserProfile(bodyWeightKg = 80.0, weightUnit = WeightUnit.LBS))
        val bench = repository.workoutState.first().exercises.first()
        val viewModel = TemplateEditorViewModel(repository, templateId = null)

        viewModel.updateName("Push")
        viewModel.addExercise(bench)
        viewModel.updateWeight(viewModel.uiState.value.rows.single().entryId, "100")
        viewModel.save()

        val entry = repository.templates.first { it.isNotEmpty() }.single().entries.single()
        assertEquals(WeightUnit.LBS.toKg(100.0), entry.targetWeightKg!!, 0.0001)
    }

    @Test
    fun editingExistingTemplate_loadsRows_andKeepsIdOnSave() = runTest {
        val repository = FakeDataRepository()
        val bench = repository.workoutState.first().exercises.first()
        val squat = repository.workoutState.first().exercises[2]
        val original = WorkoutTemplate(
            id = "tpl-1",
            name = "Push",
            entries = listOf(
                TemplateEntry(exerciseId = bench.id, targetSets = 4, targetReps = 8, targetWeightKg = 80.0),
                TemplateEntry(exerciseId = squat.id, targetSets = 3),
            ),
        )
        repository.saveWorkoutTemplate(original)

        val viewModel = TemplateEditorViewModel(repository, templateId = "tpl-1")
        viewModel.uiState.first { it.rows.size == 2 }

        assertEquals("Push", viewModel.uiState.value.name)
        assertEquals("4", viewModel.uiState.value.rows[0].setsText)
        assertEquals("80", viewModel.uiState.value.rows[0].weightText)

        // Reorder, rename, save: same id, swapped entries.
        viewModel.moveRow(viewModel.uiState.value.rows[1].entryId, -1)
        viewModel.updateName("Push lourd")
        viewModel.save()

        val saved = repository.templates.first().single()
        assertEquals("tpl-1", saved.id)
        assertEquals("Push lourd", saved.name)
        assertEquals(listOf(squat.id, bench.id), saved.entries.map { it.exerciseId })
    }
}
