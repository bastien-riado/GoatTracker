package com.example.goattracker.ui.live

import com.example.goattracker.data.FakeDataRepository
import com.example.goattracker.domain.model.ExerciseSession
import com.example.goattracker.domain.model.WorkoutSession
import com.example.goattracker.domain.model.WorkoutSet
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ActiveSessionControllerTest {

    private class RecordingRestTimer : RestTimer {
        override val state: StateFlow<RestTimerState> = MutableStateFlow(RestTimerState.Idle)
        var lastStartSeconds: Int? = null
        override fun start(durationSeconds: Int) { lastStartSeconds = durationSeconds }
        override fun acknowledge() {}
        override fun cancelAll() {}
        override fun remainingSeconds(): Int = 0
    }

    private class FakeService : SessionServiceController {
        override fun ensureRunning() {}
        override fun stop() {}
    }

    private fun TestScope.controller(
        repository: FakeDataRepository,
        restTimer: RecordingRestTimer,
    ): ActiveSessionController {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        return ActiveSessionController(
            repository = repository,
            restTimer = restTimer,
            service = FakeService(),
            scope = TestScope(dispatcher),
            clock = { 42_000L },
            ticker = flowOf(Unit),
        )
    }

    @Test
    fun completeNextSet_checksTheFirstPendingSet_stampsIt_andStartsTheRest() = runTest {
        val repository = FakeDataRepository()
        val bench = repository.workoutState.first().exercises.first() // rest 120s
        val draft = WorkoutSession(
            name = "Push",
            exercises = listOf(
                ExerciseSession(
                    exercise = bench,
                    sets = listOf(
                        WorkoutSet(id = "s1", setNumber = 1, weight = 80.0, reps = 8, isCompleted = true),
                        WorkoutSet(id = "s2", setNumber = 2, weight = 80.0, reps = 8, isCompleted = false),
                        WorkoutSet(id = "s3", setNumber = 3, weight = 80.0, reps = 8, isCompleted = false),
                    ),
                )
            ),
        )
        repository.saveActiveDraft(draft)
        val restTimer = RecordingRestTimer()

        controller(repository, restTimer).completeNextSet()

        val sets = repository.getLatestState().activeDraft!!.exercises.single().sets
        assertTrue(sets.first { it.id == "s2" }.isCompleted)
        assertEquals(42_000L, sets.first { it.id == "s2" }.completedAt)
        assertTrue("the following set stays pending", !sets.first { it.id == "s3" }.isCompleted)
        assertEquals(bench.restTimeSeconds, restTimer.lastStartSeconds)
    }

    @Test
    fun completeNextSet_skipsFullyCompletedExercises() = runTest {
        val repository = FakeDataRepository()
        val state = repository.workoutState.first()
        val bench = state.exercises[0]
        val squat = state.exercises[2] // rest 150s
        val draft = WorkoutSession(
            name = "Full",
            exercises = listOf(
                ExerciseSession(
                    exercise = bench,
                    sets = listOf(WorkoutSet(setNumber = 1, weight = 80.0, reps = 8, isCompleted = true)),
                ),
                ExerciseSession(
                    exercise = squat,
                    sets = listOf(WorkoutSet(id = "next", setNumber = 1, weight = 100.0, reps = 5, isCompleted = false)),
                ),
            ),
        )
        repository.saveActiveDraft(draft)
        val restTimer = RecordingRestTimer()

        controller(repository, restTimer).completeNextSet()

        val updated = repository.getLatestState().activeDraft!!
        assertTrue(updated.exercises[1].sets.single().isCompleted)
        assertEquals(squat.restTimeSeconds, restTimer.lastStartSeconds)
    }

    @Test
    fun completeNextSet_isANoOp_whenNothingIsPending() = runTest {
        val repository = FakeDataRepository()
        val bench = repository.workoutState.first().exercises.first()
        val draft = WorkoutSession(
            name = "Done",
            exercises = listOf(
                ExerciseSession(
                    exercise = bench,
                    sets = listOf(WorkoutSet(setNumber = 1, weight = 80.0, reps = 8, isCompleted = true)),
                )
            ),
        )
        repository.saveActiveDraft(draft)
        val restTimer = RecordingRestTimer()

        controller(repository, restTimer).completeNextSet()

        assertEquals(draft, repository.getLatestState().activeDraft)
        assertNull(restTimer.lastStartSeconds)
    }

    @Test
    fun completeNextSet_isANoOp_withoutAnActiveDraft() = runTest {
        val repository = FakeDataRepository()
        repository.workoutState.first()
        val restTimer = RecordingRestTimer()

        controller(repository, restTimer).completeNextSet()

        assertNull(repository.getLatestState().activeDraft)
        assertNull(restTimer.lastStartSeconds)
    }
}
