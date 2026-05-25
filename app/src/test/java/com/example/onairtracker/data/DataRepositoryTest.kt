package com.example.onairtracker.data

import com.example.onairtracker.domain.model.Exercise
import com.example.onairtracker.domain.model.ExerciseCategory
import com.example.onairtracker.domain.model.ExerciseSession
import com.example.onairtracker.domain.model.TrackingType
import com.example.onairtracker.domain.model.WorkoutSession
import com.example.onairtracker.domain.model.WorkoutSet
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DataRepositoryTest {

    @Test
    fun repository_initializesWithDefaultExercises() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val repository = DefaultDataRepository(
            storageDir = null,
            dispatcher = testDispatcher,
            scope = testScope
        )
        val state = repository.workoutState.first()
        
        assertEquals(3, state.exercises.size)
        assertEquals("Développé Couché", state.exercises[0].name)
        assertEquals("Tractions Pronation", state.exercises[1].name)
        assertEquals("Squat Barre", state.exercises[2].name)
    }

    @Test
    fun repository_canAddCustomExercise() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val repository = DefaultDataRepository(
            storageDir = null,
            dispatcher = testDispatcher,
            scope = testScope
        )
        val customExercise = Exercise(
            name = "Curl Biceps",
            category = ExerciseCategory.PULL,
            primaryMuscle = "Biceps",
            trackingType = TrackingType.WEIGHT_REPS
        )
        
        repository.addExercise(customExercise)
        
        val state = repository.workoutState.first()
        assertEquals(4, state.exercises.size)
        assertTrue(state.exercises.any { it.name == "Curl Biceps" })
    }

    @Test
    fun repository_canDeleteExercise() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val repository = DefaultDataRepository(
            storageDir = null,
            dispatcher = testDispatcher,
            scope = testScope
        )
        val stateBefore = repository.workoutState.first()
        val targetId = stateBefore.exercises[0].id
        
        repository.deleteExercise(targetId)
        
        val stateAfter = repository.workoutState.first()
        assertEquals(2, stateAfter.exercises.size)
        assertTrue(stateAfter.exercises.none { it.id == targetId })
    }

    @Test
    fun repository_sessionCRUD_andTonnageVolumeCalculations() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val repository = DefaultDataRepository(
            storageDir = null,
            dispatcher = testDispatcher,
            scope = testScope
        )
        val state = repository.workoutState.first()
        
        val exercise = state.exercises[0] // Développé Couché
        val session = WorkoutSession(name = "Push A Force")
        
        // Add session
        repository.addWorkoutSession(session)
        var currentState = repository.workoutState.first()
        assertEquals(1, currentState.sessions.size)
        assertEquals("Push A Force", currentState.sessions[0].name)
        
        // Log sets and complete them
        val sets = listOf(
            WorkoutSet(setNumber = 1, weight = 80.0, reps = 10, isCompleted = true),
            WorkoutSet(setNumber = 2, weight = 85.0, reps = 8, isCompleted = true),
            WorkoutSet(setNumber = 3, weight = 90.0, reps = 5, isCompleted = false) // Not completed
        )
        
        val exerciseSession = ExerciseSession(exercise = exercise, sets = sets)
        val updatedSession = session.copy(exercises = listOf(exerciseSession))
        
        // Update session
        repository.updateWorkoutSession(updatedSession)
        currentState = repository.workoutState.first()
        
        val savedSession = currentState.sessions[0]
        assertEquals(1, savedSession.exercises.size)
        
        // Tonnage Volume calculation: 80*10 + 85*8 = 800 + 680 = 1480kg. 90*5 is uncompleted and must be ignored.
        assertEquals(1480.0, savedSession.totalVolume, 0.001)
        
        // Delete session
        repository.deleteWorkoutSession(session.id)
        currentState = repository.workoutState.first()
        assertTrue(currentState.sessions.isEmpty())
    }
}

