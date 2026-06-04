package com.example.goattracker.ui.create

import com.example.goattracker.data.DefaultDataRepository
import com.example.goattracker.domain.model.ExerciseCategory
import com.example.goattracker.domain.model.TrackingType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val repository = DefaultDataRepository(storageDir = null, dispatcher = testDispatcher, scope = testScope)
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
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val repository = DefaultDataRepository(storageDir = null, dispatcher = testDispatcher, scope = testScope)
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
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val repository = DefaultDataRepository(storageDir = null, dispatcher = testDispatcher, scope = testScope)
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
        assertEquals(4, repoState.exercises.size) // 3 defaults + 1 custom
        val savedExercise = repoState.exercises.first { it.name == "Curl Halteres" }
        assertEquals(ExerciseCategory.PULL, savedExercise.category)
        assertEquals("Biceps", savedExercise.primaryMuscle)
        assertEquals(TrackingType.WEIGHT_REPS, savedExercise.trackingType)
    }
}
