package com.example.onairtracker.ui.live

import com.example.onairtracker.MainDispatcherRule
import com.example.onairtracker.data.DefaultDataRepository
import com.example.onairtracker.domain.model.Exercise
import com.example.onairtracker.domain.model.ExerciseCategory
import com.example.onairtracker.domain.model.TrackingType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LiveWorkoutViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val testExercise = Exercise(
        id = "ex-1",
        name = "Développé Couché",
        category = ExerciseCategory.PUSH,
        primaryMuscle = "Pectoraux",
        trackingType = TrackingType.WEIGHT_REPS
    )

    // --- Helper to create a fresh ViewModel ---
    private fun createTestViewModel(testScheduler: kotlinx.coroutines.test.TestCoroutineScheduler, startSession: Boolean = true): Pair<LiveWorkoutViewModel, DefaultDataRepository> {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val repository = DefaultDataRepository(storageDir = null, dispatcher = testDispatcher, scope = testScope)
        val viewModel = LiveWorkoutViewModel(repository)
        if (startSession) {
            viewModel.startNewSession()
        }
        return viewModel to repository
    }

    @Test
    fun viewModel_initialState_noSessionStarted() = runTest {
        val (viewModel, _) = createTestViewModel(testScheduler, startSession = false)

        val state = viewModel.uiState.value
        assertNull(state.activeSession)
    }

    @Test
    fun viewModel_afterStartNewSession_startsNewEmptySession() = runTest {
        val (viewModel, _) = createTestViewModel(testScheduler) // calls startNewSession

        val state = viewModel.uiState.value
        val session = state.activeSession
        assertNotNull(session)
        assertTrue(session!!.exercises.isEmpty())
        assertEquals(0, state.elapsedSeconds)
        assertEquals(0, state.totalCompletedSets)
        assertEquals(0, state.totalExercises)
        assertNull(state.timerRemainingSeconds)
        assertFalse(state.isRestTimerVibrating)
        assertFalse(state.isFinishModalOpen)
    }

    @Test
    fun viewModel_addExercise_updatesReactiveCounters() = runTest {
        val (viewModel, _) = createTestViewModel(testScheduler)

        viewModel.addExerciseToSession(testExercise)

        val state = viewModel.uiState.value
        assertEquals(1, state.totalExercises)
        assertEquals(0, state.totalCompletedSets)
        assertEquals(1, state.activeSession!!.exercises.size)

        val exerciseSession = state.activeSession!!.exercises.first()
        assertEquals("ex-1", exerciseSession.exercise.id)
        assertEquals(1, exerciseSession.sets.size)
        assertEquals(20.0, exerciseSession.sets.first().weight, 0.0)
        assertEquals(10, exerciseSession.sets.first().reps)
    }

    @Test
    fun viewModel_addSet_incrementsAndCopiesValues() = runTest {
        val (viewModel, _) = createTestViewModel(testScheduler)

        viewModel.addExerciseToSession(testExercise)
        val firstSetId = viewModel.uiState.value.activeSession!!.exercises.first().sets.first().id
        viewModel.updateSetValues("ex-1", firstSetId, weight = 60.0, reps = 8)
        viewModel.addSetToExercise("ex-1")

        val sets = viewModel.uiState.value.activeSession!!.exercises.first().sets
        assertEquals(2, sets.size)
        assertEquals(2, sets[1].setNumber)
        assertEquals(60.0, sets[1].weight, 0.0)
        assertEquals(8, sets[1].reps)
        assertFalse(sets[1].isCompleted)
    }

    @Test
    fun viewModel_deleteSet_recalculatesSetNumbers() = runTest {
        val (viewModel, _) = createTestViewModel(testScheduler)

        viewModel.addExerciseToSession(testExercise)
        viewModel.addSetToExercise("ex-1")
        viewModel.addSetToExercise("ex-1")

        val firstSetId = viewModel.uiState.value.activeSession!!.exercises.first().sets[0].id
        viewModel.deleteSetFromExercise("ex-1", firstSetId)

        val sets = viewModel.uiState.value.activeSession!!.exercises.first().sets
        assertEquals(2, sets.size)
        assertEquals(1, sets[0].setNumber)
        assertEquals(2, sets[1].setNumber)
    }

    @Test
    fun viewModel_toggleSetCompletion_updatesReactiveCountersAndStartsTimer() = runTest {
        val (viewModel, _) = createTestViewModel(testScheduler)

        viewModel.addExerciseToSession(testExercise)
        val firstSet = viewModel.uiState.value.activeSession!!.exercises.first().sets.first()
        viewModel.updateSetValues("ex-1", firstSet.id, weight = 100.0, reps = 5)

        viewModel.toggleSetCompletion("ex-1", firstSet.id)

        val state = viewModel.uiState.value
        assertTrue(state.activeSession!!.exercises.first().sets.first().isCompleted)
        assertEquals(1, state.totalCompletedSets)
        assertEquals(90, state.timerRemainingSeconds)
    }

    @Test
    fun viewModel_requestFinishSession_opensModal() = runTest {
        val (viewModel, _) = createTestViewModel(testScheduler)

        viewModel.requestFinishSession()

        assertTrue(viewModel.uiState.value.isFinishModalOpen)
    }

    @Test
    fun viewModel_dismissFinishModal_closesModal() = runTest {
        val (viewModel, _) = createTestViewModel(testScheduler)

        viewModel.requestFinishSession()
        viewModel.dismissFinishModal()

        assertFalse(viewModel.uiState.value.isFinishModalOpen)
    }

    @Test
    fun viewModel_confirmSaveSession_persistsAndDismisses() = runTest {
        val (viewModel, repository) = createTestViewModel(testScheduler)

        viewModel.addExerciseToSession(testExercise)
        val firstSet = viewModel.uiState.value.activeSession!!.exercises.first().sets.first()
        viewModel.updateSetValues("ex-1", firstSet.id, weight = 80.0, reps = 10)
        viewModel.toggleSetCompletion("ex-1", firstSet.id)

        viewModel.confirmSaveSession()

        assertFalse(viewModel.uiState.value.isFinishModalOpen)

        val dbState = repository.workoutState.first()
        assertEquals(1, dbState.sessions.size)
        assertEquals(800.0, dbState.sessions.first().totalVolume, 0.0)
    }

    @Test
    fun viewModel_discardSession_doesNotPersist() = runTest {
        val (viewModel, repository) = createTestViewModel(testScheduler)

        viewModel.addExerciseToSession(testExercise)
        val firstSet = viewModel.uiState.value.activeSession!!.exercises.first().sets.first()
        viewModel.toggleSetCompletion("ex-1", firstSet.id)

        viewModel.discardSession()

        assertFalse(viewModel.uiState.value.isFinishModalOpen)
        val dbState = repository.workoutState.first()
        assertEquals(0, dbState.sessions.size)
    }

    @Test
    fun viewModel_acknowledgeRestTimer_clearsState() = runTest {
        val (viewModel, _) = createTestViewModel(testScheduler)

        viewModel.addExerciseToSession(testExercise)
        val firstSet = viewModel.uiState.value.activeSession!!.exercises.first().sets.first()
        viewModel.toggleSetCompletion("ex-1", firstSet.id)

        // Timer should have been started at 90s
        assertEquals(90, viewModel.uiState.value.timerRemainingSeconds)

        viewModel.acknowledgeRestTimer()

        assertNull(viewModel.uiState.value.timerRemainingSeconds)
        assertFalse(viewModel.uiState.value.isRestTimerVibrating)
    }

    @Test
    fun viewModel_totalCompletedSets_isReactive() = runTest {
        val (viewModel, _) = createTestViewModel(testScheduler)

        val secondExercise = Exercise(
            id = "ex-2",
            name = "Squat",
            category = ExerciseCategory.LEG,
            primaryMuscle = "Quadriceps",
            trackingType = TrackingType.WEIGHT_REPS
        )

        viewModel.addExerciseToSession(testExercise)
        viewModel.addExerciseToSession(secondExercise)
        assertEquals(2, viewModel.uiState.value.totalExercises)
        assertEquals(0, viewModel.uiState.value.totalCompletedSets)

        val set1 = viewModel.uiState.value.activeSession!!.exercises[0].sets.first()
        viewModel.toggleSetCompletion("ex-1", set1.id)
        assertEquals(1, viewModel.uiState.value.totalCompletedSets)

        val set2 = viewModel.uiState.value.activeSession!!.exercises[1].sets.first()
        viewModel.toggleSetCompletion("ex-2", set2.id)
        assertEquals(2, viewModel.uiState.value.totalCompletedSets)

        // Remove an exercise
        viewModel.removeExerciseFromSession("ex-1")
        assertEquals(1, viewModel.uiState.value.totalExercises)
        assertEquals(1, viewModel.uiState.value.totalCompletedSets)
    }
}
