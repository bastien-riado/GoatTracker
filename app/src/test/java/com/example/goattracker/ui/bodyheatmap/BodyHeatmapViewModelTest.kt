package com.example.goattracker.ui.bodyheatmap

import com.example.goattracker.MainDispatcherRule
import com.example.goattracker.data.FakeDataRepository
import com.example.goattracker.domain.MuscleRecoveryCalculator
import com.example.goattracker.domain.model.Exercise
import com.example.goattracker.domain.model.ExerciseCategory
import com.example.goattracker.domain.model.ExerciseSession
import com.example.goattracker.domain.model.MuscleGroup
import com.example.goattracker.domain.model.TrackingType
import com.example.goattracker.domain.model.WorkoutSession
import com.example.goattracker.domain.model.WorkoutSet
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BodyHeatmapViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val now = 2_000_000_000_000L
    private val hour = 3_600_000L

    private fun chestSessionEndedHoursAgo(h: Long): WorkoutSession {
        val ex = Exercise(name = "DC", category = ExerciseCategory.PUSH, primaryMuscle = "Pectoraux", trackingType = TrackingType.WEIGHT_REPS)
        return WorkoutSession(
            startTime = now - h * hour - hour,
            endTime = now - h * hour,
            name = "Push",
            exercises = listOf(ExerciseSession(exercise = ex, sets = (1..4).map { WorkoutSet(setNumber = it, weight = 80.0, reps = 8, isCompleted = true) })),
        )
    }

    @Test
    fun computesRecoveryForTrainedMuscles_andNeutralForUntrained() = runTest {
        val td = UnconfinedTestDispatcher(testScheduler)
        val repo = FakeDataRepository()
        repo.addWorkoutSession(chestSessionEndedHoursAgo(6))
        val vm = BodyHeatmapViewModel(repo, MuscleRecoveryCalculator(), td, now = { now })

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        val chest = state.statuses.getValue(MuscleGroup.CHEST)
        assertTrue(chest.hasData)
        assertTrue("freshly trained chest should be far from recovered", chest.recovery < 0.3f)
        // A muscle never trained stays neutral (no data).
        assertFalse(state.statuses.getValue(MuscleGroup.BICEPS).hasData)
    }

    @Test
    fun selectTogglesSelection() = runTest {
        val td = UnconfinedTestDispatcher(testScheduler)
        val repo = FakeDataRepository()
        repo.addWorkoutSession(chestSessionEndedHoursAgo(6))
        val vm = BodyHeatmapViewModel(repo, MuscleRecoveryCalculator(), td, now = { now })

        assertNull(vm.uiState.value.selected)
        vm.select(MuscleGroup.CHEST)
        assertEquals(MuscleGroup.CHEST, vm.uiState.value.selected)
        vm.select(MuscleGroup.CHEST) // tapping the selected one clears it
        assertNull(vm.uiState.value.selected)
        vm.select(MuscleGroup.QUADS)
        assertEquals(MuscleGroup.QUADS, vm.uiState.value.selected)
    }
}
