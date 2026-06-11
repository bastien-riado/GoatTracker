package com.example.goattracker.data

import com.example.goattracker.domain.WorkoutMetrics
import com.example.goattracker.domain.model.BodyWeightSource
import com.example.goattracker.domain.model.Exercise
import com.example.goattracker.domain.model.ExerciseCategory
import com.example.goattracker.domain.model.ExerciseSession
import com.example.goattracker.domain.model.TrackingType
import com.example.goattracker.domain.model.UserProfile
import com.example.goattracker.domain.model.WeightUnit
import com.example.goattracker.domain.model.WorkoutSession
import com.example.goattracker.domain.model.WorkoutSet
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

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
        
        assertEquals(4, state.exercises.size)
        assertEquals("Développé Couché", state.exercises[0].name)
        assertEquals("Tractions Pronation", state.exercises[1].name)
        assertEquals("Squat Barre", state.exercises[2].name)
        assertEquals("Course à pied", state.exercises[3].name)
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
        assertEquals(5, state.exercises.size)
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
        assertEquals(3, stateAfter.exercises.size)
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
        assertEquals(1480.0, WorkoutMetrics.sessionStrengthVolumeKg(savedSession, bodyWeightKg = null), 0.001)
        
        // Delete session
        repository.deleteWorkoutSession(session.id)
        currentState = repository.workoutState.first()
        assertTrue(currentState.sessions.isEmpty())
    }

    @Test
    fun repository_corruptFile_isBackedUpAndFallsBackToDefaults() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val dir = File(System.getProperty("java.io.tmpdir"), "gt-corrupt-" + System.nanoTime()).apply { mkdirs() }
        File(dir, "workouts.json").writeText("{ not valid json ]]")

        val repository = DefaultDataRepository(storageDir = dir, dispatcher = testDispatcher, scope = TestScope(testDispatcher))
        repository.isReady.first { it }

        // Fell back to the default preset instead of crashing...
        val state = repository.workoutState.first()
        assertEquals(4, state.exercises.size)
        // ...and the unreadable file was preserved rather than silently wiped.
        val backups = dir.listFiles { f -> f.name.startsWith("workouts.corrupt-") } ?: emptyArray()
        assertTrue("expected a corrupt-file backup to be created", backups.isNotEmpty())
    }

    @Test
    fun repository_legacyFileWithoutSchemaVersionOrDraft_stillLoads() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val dir = File(System.getProperty("java.io.tmpdir"), "gt-legacy-" + System.nanoTime()).apply { mkdirs() }
        // Pre-versioning, pre-draft format: must still deserialize via the defaulted fields.
        File(dir, "workouts.json").writeText(
            """{"exercises":[{"id":"x1","name":"Legacy","category":"PUSH","primaryMuscle":"Pecs","trackingType":"WEIGHT_REPS"}],"sessions":[]}"""
        )

        val repository = DefaultDataRepository(storageDir = dir, dispatcher = testDispatcher, scope = TestScope(testDispatcher))
        repository.isReady.first { it }

        val state = repository.workoutState.first()
        assertEquals(1, state.exercises.size)
        assertEquals("Legacy", state.exercises[0].name)
        assertNull(state.activeDraft)
        // Files written before the user profile existed load with the default (empty) profile.
        assertNull(state.userProfile.bodyWeightKg)
        assertEquals(WeightUnit.KG, state.userProfile.weightUnit)
    }

    @Test
    fun repository_userProfile_roundTripsThroughDisk() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val dir = File(System.getProperty("java.io.tmpdir"), "gt-profile-" + System.nanoTime()).apply { mkdirs() }

        val first = DefaultDataRepository(storageDir = dir, dispatcher = testDispatcher, scope = TestScope(testDispatcher))
        first.isReady.first { it }
        first.saveUserProfile(
            UserProfile(
                bodyWeightKg = 72.5,
                weightUnit = WeightUnit.LBS,
                healthConnectSyncEnabled = true,
                bodyWeightUpdatedAt = 42L,
                bodyWeightSource = BodyWeightSource.HEALTH_CONNECT,
            )
        )
        // The disk write is debounced; the repo scope shares this test's scheduler, so advancing
        // virtual time past the debounce window executes the write synchronously (unconfined).
        testScheduler.advanceTimeBy(1_000)
        testScheduler.runCurrent()
        assertTrue(File(dir, "workouts.json").readText().contains("bodyWeightKg"))

        val second = DefaultDataRepository(storageDir = dir, dispatcher = testDispatcher, scope = TestScope(testDispatcher))
        second.isReady.first { it }
        val profile = second.workoutState.first().userProfile
        assertEquals(72.5, profile.bodyWeightKg!!, 0.0)
        assertEquals(WeightUnit.LBS, profile.weightUnit)
        assertTrue(profile.healthConnectSyncEnabled)
        assertEquals(42L, profile.bodyWeightUpdatedAt)
        assertEquals(BodyWeightSource.HEALTH_CONNECT, profile.bodyWeightSource)
    }
}

