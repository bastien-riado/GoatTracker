package com.example.goattracker.ui.create

import com.example.goattracker.data.FakeDataRepository
import com.example.goattracker.domain.model.ExerciseCategory
import com.example.goattracker.domain.model.TrackingType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import com.example.goattracker.MainDispatcherRule
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CreateExerciseViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()


    @Test
    fun viewModel_initialState_isInvalid() = runTest {
        val repository = FakeDataRepository()
        val viewModel = CreateExerciseViewModel(repository)

        val state = viewModel.uiState.value
        assertEquals("", state.name)
        assertEquals(ExerciseCategory.PUSH, state.category)
        assertEquals("", state.primaryMuscle)
        assertEquals(TrackingType.WEIGHT_REPS, state.trackingType)
        assertFalse(state.isSaveEnabled)
    }

    @Test
    fun viewModel_updatingFields_correctlyValidatesAndEnablesSave() = runTest {
        val repository = FakeDataRepository()
        val viewModel = CreateExerciseViewModel(repository)

        // 1. Enter blank name
        viewModel.updateName("   ")
        viewModel.updatePrimaryMuscle("Dos")
        assertFalse(viewModel.uiState.value.isSaveEnabled)

        // 2. Enter valid name but empty muscle
        viewModel.updateName("Traction")
        viewModel.updatePrimaryMuscle("")
        assertFalse(viewModel.uiState.value.isSaveEnabled)

        // 3. Both valid
        viewModel.updateName("Traction")
        viewModel.updatePrimaryMuscle("Dos")
        assertTrue(viewModel.uiState.value.isSaveEnabled)
    }

    @Test
    fun viewModel_saveExercise_callsRepositoryAndSetsSavedFlag() = runTest {
        val repository = FakeDataRepository()
        val viewModel = CreateExerciseViewModel(repository)

        // Set inputs
        viewModel.updateName("Curl Halteres")
        viewModel.selectCategory(ExerciseCategory.PULL)
        viewModel.updatePrimaryMuscle("Biceps")
        viewModel.selectTrackingType(TrackingType.WEIGHT_REPS)

        // Save
        viewModel.saveExercise()

        // saveExercise emits a one-shot "saved" event (consumed once) instead of a sticky flag.
        assertEquals(Unit, viewModel.savedEvents.first())

        // Verify that the exercise has been successfully inserted into repository database
        val repoState = repository.workoutState.first()
        assertEquals(5, repoState.exercises.size) // 4 defaults + 1 custom
        val savedExercise = repoState.exercises.first { it.name == "Curl Halteres" }
        assertEquals(ExerciseCategory.PULL, savedExercise.category)
        assertEquals("Biceps", savedExercise.primaryMuscle)
        assertEquals(TrackingType.WEIGHT_REPS, savedExercise.trackingType)
    }

    @Test
    fun secondaryMuscles_toggle_saveRoundTrip_andPrimaryPromotionCleansUp() = runTest {
        val repository = FakeDataRepository()
        val viewModel = CreateExerciseViewModel(repository)

        viewModel.updateName("Développé Incliné")
        viewModel.updatePrimaryMuscle("Pectoraux")
        viewModel.toggleSecondaryMuscle("Triceps")
        viewModel.toggleSecondaryMuscle("Épaules")
        // Toggling the primary itself is a no-op.
        viewModel.toggleSecondaryMuscle("Pectoraux")
        assertEquals(listOf("Triceps", "Épaules"), viewModel.uiState.value.secondaryMuscles)

        // Promoting a secondary to primary removes it from the secondaries.
        viewModel.updatePrimaryMuscle("Triceps")
        assertEquals(listOf("Épaules"), viewModel.uiState.value.secondaryMuscles)

        viewModel.saveExercise()
        viewModel.savedEvents.first()

        val saved = repository.workoutState.first().exercises.first { it.name == "Développé Incliné" }
        assertEquals("Triceps", saved.primaryMuscle)
        assertEquals(listOf("Épaules"), saved.secondaryMuscles)
    }

    @Test
    fun editingAnExercise_loadsItsSecondaryMuscles() = runTest {
        val repository = FakeDataRepository()
        val bench = repository.workoutState.first().exercises.first()
        repository.addExercise(bench.copy(secondaryMuscles = listOf("Triceps")))

        val viewModel = CreateExerciseViewModel(repository, bench.id)

        viewModel.uiState.first { it.secondaryMuscles.isNotEmpty() }
        assertEquals(listOf("Triceps"), viewModel.uiState.value.secondaryMuscles)
    }
}
