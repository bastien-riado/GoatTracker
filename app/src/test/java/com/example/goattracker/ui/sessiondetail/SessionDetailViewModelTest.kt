package com.example.goattracker.ui.sessiondetail

import com.example.goattracker.MainDispatcherRule
import com.example.goattracker.data.FakeDataRepository
import com.example.goattracker.domain.model.ExerciseSession
import com.example.goattracker.domain.model.TemplateEntry
import com.example.goattracker.domain.model.WorkoutSession
import com.example.goattracker.domain.model.WorkoutSet
import com.example.goattracker.domain.model.WorkoutTemplate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun success_exposesSummaryAndTemplateName() = runTest {
        val repository = FakeDataRepository()
        val bench = repository.workoutState.first().exercises.first()
        val template = WorkoutTemplate(
            id = "tpl-push", name = "Push",
            entries = listOf(TemplateEntry(exerciseId = bench.id)),
        )
        repository.saveWorkoutTemplate(template)
        val session = WorkoutSession(
            id = "s-1",
            startTime = 1_000L,
            endTime = 1_000L + 45 * 60_000L,
            name = "Push",
            templateId = "tpl-push",
            exercises = listOf(
                ExerciseSession(
                    exercise = bench,
                    sets = listOf(
                        WorkoutSet(setNumber = 1, weight = 80.0, reps = 10, isCompleted = true),
                        WorkoutSet(setNumber = 2, weight = 80.0, reps = 8, isCompleted = true),
                    ),
                )
            ),
        )
        repository.addWorkoutSession(session)

        val viewModel = SessionDetailViewModel(
            repository, "s-1", defaultDispatcher = UnconfinedTestDispatcher(testScheduler),
        )
        val state = viewModel.uiState
            .first { it is SessionDetailUiState.Success } as SessionDetailUiState.Success

        assertEquals("Push", state.templateName)
        assertEquals(45 * 60, state.summary.durationSeconds)
        assertEquals(1_440.0, state.summary.strengthVolumeKg, 0.001)
        assertEquals(2, state.summary.completedSets)
    }

    @Test
    fun missingSession_isGone() = runTest {
        val repository = FakeDataRepository()
        val viewModel = SessionDetailViewModel(
            repository, "inconnu", defaultDispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        assertTrue(viewModel.uiState.first { it !is SessionDetailUiState.Loading } is SessionDetailUiState.Gone)
    }
}
