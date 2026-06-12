package com.example.goattracker.ui.celebration

import com.example.goattracker.MainDispatcherRule
import com.example.goattracker.data.FakeDataRepository
import com.example.goattracker.domain.RecordKind
import com.example.goattracker.domain.model.ExerciseSession
import com.example.goattracker.domain.model.WorkoutSession
import com.example.goattracker.domain.model.WorkoutSet
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionCelebrationViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun benchSession(repository: FakeDataRepository, id: String, time: Long, weight: Double) =
        WorkoutSession(
            id = id, startTime = time, endTime = time + 3_600_000L, name = "Push",
            exercises = listOf(
                ExerciseSession(
                    exercise = repository.getLatestState().exercises.first(),
                    sets = listOf(WorkoutSet(setNumber = 1, weight = weight, reps = 5, isCompleted = true)),
                )
            ),
        )

    @Test
    fun ready_exposesSummaryAndDetectedRecords() = runTest {
        val repository = FakeDataRepository()
        repository.workoutState.first() // seeded
        repository.addWorkoutSession(benchSession(repository, "s1", 1_000L, weight = 100.0))
        repository.addWorkoutSession(benchSession(repository, "s2", 2_000L, weight = 110.0))

        val viewModel = SessionCelebrationViewModel(
            repository, "s2", defaultDispatcher = UnconfinedTestDispatcher(testScheduler),
        )
        val state = viewModel.uiState
            .first { it is CelebrationUiState.Ready } as CelebrationUiState.Ready

        assertEquals(550.0, state.summary.strengthVolumeKg, 0.001)
        val kinds = state.records.map { it.kind }.toSet()
        assertTrue(RecordKind.MAX_WEIGHT in kinds)
        assertTrue(RecordKind.SESSION_VOLUME in kinds)
    }

    @Test
    fun loading_resolvesWhenTheInFlightWriteLands() = runTest {
        val repository = FakeDataRepository()
        repository.workoutState.first()
        // Screen pushed BEFORE the finish write is observable (the real-life order).
        val viewModel = SessionCelebrationViewModel(
            repository, "s1", defaultDispatcher = UnconfinedTestDispatcher(testScheduler),
        )
        assertTrue(viewModel.uiState.value is CelebrationUiState.Loading)

        repository.addWorkoutSession(benchSession(repository, "s1", 1_000L, weight = 80.0))

        val state = viewModel.uiState.first { it is CelebrationUiState.Ready } as CelebrationUiState.Ready
        assertEquals("s1", state.session.id)
        assertTrue(state.records.isEmpty()) // first ever: quiet
    }
}
