package com.example.onairtracker.ui.profile

import com.example.onairtracker.MainDispatcherRule
import com.example.onairtracker.data.DefaultDataRepository
import com.example.onairtracker.domain.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val testExercisePush = Exercise(
        id = "ex-push",
        name = "Développé Couché",
        category = ExerciseCategory.PUSH,
        primaryMuscle = "Pectoraux",
        trackingType = TrackingType.WEIGHT_REPS
    )

    private val testExerciseLeg = Exercise(
        id = "ex-leg",
        name = "Squat",
        category = ExerciseCategory.LEG,
        primaryMuscle = "Quadriceps",
        trackingType = TrackingType.WEIGHT_REPS
    )

    @Test
    fun viewModel_initialEmptyState_calculatesZeros() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val repository = DefaultDataRepository(storageDir = null, dispatcher = testDispatcher, scope = testScope)
        val viewModel = ProfileViewModel(repository)

        val state = viewModel.uiState.value
        assertEquals(0, state.totalWorkouts)
        assertEquals(0.0, state.cumulativeVolume, 0.0)
        // Since DefaultDataRepository loads default presets even with null storageDir
        assertEquals("Développé Couché", state.selectedExercise?.name)
        assertEquals(3, state.availableExercises.size)
        assertTrue(state.oneRepMaxEvolution.isEmpty())
        assertTrue(state.muscleGroupSets.isEmpty())
        assertTrue(state.sessionVolumes.isEmpty())
    }

    @Test
    fun viewModel_withWorkoutSessions_calculatesStatsCorrectly() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val repository = DefaultDataRepository(storageDir = null, dispatcher = testDispatcher, scope = testScope)
        
        // Add default exercises
        repository.addWorkoutSession(
            WorkoutSession(
                name = "Séance du 10 Mai",
                startTime = 1000000000000L,
                endTime = 1000000003600L,
                exercises = listOf(
                    ExerciseSession(
                        exercise = testExercisePush,
                        sets = listOf(
                            WorkoutSet(setNumber = 1, weight = 100.0, reps = 5, isCompleted = true),
                            WorkoutSet(setNumber = 2, weight = 100.0, reps = 5, isCompleted = true)
                        )
                    )
                )
            )
        )

        repository.addWorkoutSession(
            WorkoutSession(
                name = "Séance du 15 Mai",
                startTime = 1000000500000L,
                endTime = 1000000503600L,
                exercises = listOf(
                    ExerciseSession(
                        exercise = testExerciseLeg,
                        sets = listOf(
                            WorkoutSet(setNumber = 1, weight = 120.0, reps = 8, isCompleted = true),
                            WorkoutSet(setNumber = 2, weight = 120.0, reps = 8, isCompleted = false) // Not completed, ignored in muscle sets count
                        )
                    ),
                    ExerciseSession(
                        exercise = testExercisePush,
                        sets = listOf(
                            WorkoutSet(setNumber = 1, weight = 110.0, reps = 3, isCompleted = true)
                        )
                    )
                )
            )
        )

        val viewModel = ProfileViewModel(repository)
        val state = viewModel.uiState.value

        assertEquals(2, state.totalWorkouts)
        
        // Session 1: Push Volume = 100 * 5 * 2 = 1000
        // Session 2: Leg Volume = 120 * 8 * 1 = 960; Push Volume = 110 * 3 * 1 = 330; Total Session 2 = 1290
        // Cumulative volume = 1000 + 1290 = 2290
        assertEquals(2290.0, state.cumulativeVolume, 0.0)

        // Muscle groups splits: 
        // Pectoraux completed sets: 2 in session 1 + 1 in session 2 = 3
        // Quadriceps completed sets: 1 in session 2 = 1
        assertEquals(3, state.muscleGroupSets["Pectoraux"])
        assertEquals(1, state.muscleGroupSets["Quadriceps"])

        // Session volumes bar chart: last 6 session volumes
        assertEquals(2, state.sessionVolumes.size)
        assertEquals("10 Mai", state.sessionVolumes[0].first)
        assertEquals(1000.0, state.sessionVolumes[0].second, 0.0)
        assertEquals("15 Mai", state.sessionVolumes[1].first)
        assertEquals(1290.0, state.sessionVolumes[1].second, 0.0)

        // Selected exercise default is the first available or first in state.sessions (if any exercises are in repository)
        // Here, since repository default exercises are populated from workoutState which loaded exercises
        // Let's assert that we can manually select an exercise and test 1RM progression
        viewModel.selectExercise(testExercisePush)
        val selectedState = viewModel.uiState.value
        assertEquals("ex-push", selectedState.selectedExercise?.id)

        // 1RM Evolution for Push:
        // Session 1: 100 kg * 5 reps = Epley 1RM: 100 * (1 + 5/30) = 116.66
        // Session 2: 110 kg * 3 reps = Epley 1RM: 110 * (1 + 3/30) = 121.0
        assertEquals(2, selectedState.oneRepMaxEvolution.size)
        assertEquals(1000000000000L, selectedState.oneRepMaxEvolution[0].first)
        assertEquals(116.66, selectedState.oneRepMaxEvolution[0].second, 0.1)
        assertEquals(1000000500000L, selectedState.oneRepMaxEvolution[1].first)
        assertEquals(121.0, selectedState.oneRepMaxEvolution[1].second, 0.1)
    }
}
