package com.example.goattracker.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.example.goattracker.domain.WorkoutMetrics
import com.example.goattracker.domain.model.BodyWeightSource
import com.example.goattracker.domain.model.Exercise
import com.example.goattracker.domain.model.ExerciseCategory
import com.example.goattracker.domain.model.ExerciseSession
import com.example.goattracker.domain.model.TemplateEntry
import com.example.goattracker.domain.model.TemplateTargetMode
import com.example.goattracker.domain.model.TrackingType
import com.example.goattracker.domain.model.UserProfile
import com.example.goattracker.domain.model.WeightUnit
import com.example.goattracker.domain.model.WorkoutSession
import com.example.goattracker.domain.model.WorkoutSet
import com.example.goattracker.domain.model.WorkoutTemplate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomDataRepositoryTest {

    private lateinit var db: GoatTrackerDatabase
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, GoatTrackerDatabase::class.java)
            .setDriver(BundledSQLiteDriver())
            .build()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @After
    fun tearDown() {
        // cancel() alone is non-blocking: an in-flight Room query on the repository's collector
        // could still be unwinding when db.close() yanks the connection ("connection is closed"
        // uncaught exceptions polluting the NEXT test). Join before closing.
        runBlocking {
            scope.cancel()
            scope.coroutineContext[Job]?.join()
        }
        db.close()
    }

    /**
     * A ready repository over [db]; tests can spin up a second one to simulate an app restart.
     * Clocks are injected and disjoint (seed @1000+, mutations @1h+) because catalog order is
     * createdAt-based: with wall clocks, a fast test could add an exercise within the seed's
     * 4-millisecond createdAt spread and land in the middle of the presets.
     */
    private suspend fun repository(): RoomDataRepository {
        val clock = java.util.concurrent.atomic.AtomicLong(3_600_000L)
        val repo = RoomDataRepository(
            db = db,
            scope = scope,
            initializer = { DefaultSeed.seed(it, 1_000L) },
            now = { clock.incrementAndGet() },
        )
        repo.isReady.first { it }
        return repo
    }

    @Test
    fun freshDatabase_seedsTheDefaultCatalog_inLegacyOrder() = runTest {
        val state = repository().workoutState.first()

        assertEquals(
            listOf("Développé Couché", "Tractions Pronation", "Squat Barre", "Course à pied"),
            state.exercises.map { it.name },
        )
        assertNull(state.activeDraft)
        assertNull(state.userProfile.bodyWeightKg)
    }

    @Test
    fun emptiedCatalog_doesNotReseedOnRestart() = runTest {
        val first = repository()
        first.workoutState.first { it.exercises.size == 4 }.exercises.forEach {
            first.deleteExercise(it.id)
        }
        first.workoutState.first { it.exercises.isEmpty() }

        val second = repository() // same db: the init marker must prevent a reseed

        assertTrue(second.workoutState.first().exercises.isEmpty())
    }

    @Test
    fun addExercise_isAnUpsert_keepingCatalogPosition() = runTest {
        val repo = repository()
        val custom = Exercise(
            name = "Curl Biceps",
            category = ExerciseCategory.PULL,
            primaryMuscle = "Biceps",
            trackingType = TrackingType.WEIGHT_REPS,
        )
        repo.addExercise(custom)
        repo.workoutState.first { it.exercises.size == 5 }

        repo.addExercise(custom.copy(name = "Curl Marteau", restTimeSeconds = 60))

        val state = repo.workoutState.first { st -> st.exercises.any { it.name == "Curl Marteau" } }
        assertEquals(5, state.exercises.size) // replaced, not duplicated
        assertEquals("Curl Marteau", state.exercises.last().name) // createdAt preserved => position stable
        assertEquals(60, state.exercises.last().restTimeSeconds)
    }

    @Test
    fun deleteExercise_unreferenced_hardDeletes() = runTest {
        val repo = repository()
        val target = repo.workoutState.first().exercises.first()

        repo.deleteExercise(target.id)

        val state = repo.workoutState.first { it.exercises.size == 3 }
        assertTrue(state.exercises.none { it.id == target.id })
        assertNull(db.exerciseDao().getById(target.id)) // really gone, not archived
    }

    @Test
    fun deleteExercise_referencedByHistory_archivesAndHistorySurvives() = runTest {
        val repo = repository()
        val bench = repo.workoutState.first().exercises.first()
        val session = WorkoutSession(
            name = "Push A",
            exercises = listOf(
                ExerciseSession(
                    exercise = bench,
                    sets = listOf(WorkoutSet(setNumber = 1, weight = 80.0, reps = 10, isCompleted = true)),
                )
            ),
        )
        repo.addWorkoutSession(session)
        repo.workoutState.first { it.sessions.size == 1 }

        repo.deleteExercise(bench.id)

        val state = repo.workoutState.first { it.exercises.size == 3 }
        assertTrue(state.exercises.none { it.id == bench.id }) // left the catalog…
        val history = state.sessions.single().exercises.single().exercise
        assertEquals(bench.name, history.name) // …but history still renders it
        assertEquals(bench.primaryMuscle, history.primaryMuscle)
        assertNotNull(db.exerciseDao().getById(bench.id)) // archived row kept for the joins
    }

    @Test
    fun sessionCrud_matchesLegacyContract() = runTest {
        val repo = repository()
        val bench = repo.workoutState.first().exercises.first()
        val session = WorkoutSession(name = "Push A Force")

        repo.addWorkoutSession(session)
        repo.workoutState.first { it.sessions.size == 1 }

        val sets = listOf(
            WorkoutSet(setNumber = 1, weight = 80.0, reps = 10, isCompleted = true),
            WorkoutSet(setNumber = 2, weight = 85.0, reps = 8, isCompleted = true),
            WorkoutSet(setNumber = 3, weight = 90.0, reps = 5, isCompleted = false),
        )
        val updated = session.copy(exercises = listOf(ExerciseSession(exercise = bench, sets = sets)))
        repo.updateWorkoutSession(updated)

        val saved = repo.workoutState.first { st ->
            st.sessions.singleOrNull()?.exercises?.isNotEmpty() == true
        }.sessions.single()
        assertEquals(1480.0, WorkoutMetrics.sessionStrengthVolumeKg(saved, bodyWeightKg = null), 0.001)

        // Updating an unknown id is a no-op (legacy contract).
        repo.updateWorkoutSession(WorkoutSession(name = "Fantôme"))
        assertEquals(1, repo.workoutState.first().sessions.size)

        repo.deleteWorkoutSession(session.id)
        assertTrue(repo.workoutState.first { it.sessions.isEmpty() }.sessions.isEmpty())
    }

    @Test
    fun activeDraft_persistsAcrossRestart_andFinishingClearsIt() = runTest {
        val repo = repository()
        val bench = repo.workoutState.first().exercises.first()
        val draft = WorkoutSession(
            name = "Séance en cours",
            exercises = listOf(
                ExerciseSession(
                    exercise = bench,
                    sets = listOf(
                        WorkoutSet(setNumber = 1, weight = 80.0, reps = 10, isCompleted = true),
                        WorkoutSet(setNumber = 2, weight = 0.0, reps = 0, isCompleted = false),
                    ),
                )
            ),
        )
        repo.saveActiveDraft(draft)
        repo.workoutState.first { it.activeDraft != null }

        // "Process death": a fresh repository over the same db must restore the draft, incomplete
        // sets included.
        val revived = repository().workoutState.first { it.activeDraft != null }.activeDraft!!
        assertEquals(draft.id, revived.id)
        assertEquals(2, revived.exercises.single().sets.size)

        // Finish: same id flips to a finished session, the draft slot empties.
        val finished = draft.copy(
            endTime = 123L,
            exercises = draft.exercises.map { es -> es.copy(sets = es.sets.filter { it.isCompleted }) },
        )
        repo.addWorkoutSession(finished)
        repo.saveActiveDraft(null)

        val state = repo.workoutState.first { it.activeDraft == null && it.sessions.size == 1 }
        assertEquals(draft.id, state.sessions.single().id)
        assertEquals(1, state.sessions.single().exercises.single().sets.size)
    }

    @Test
    fun templates_crudRoundTrip_preservingListOrderAcrossEdits() = runTest {
        val repo = repository()
        val bench = repo.workoutState.first().exercises.first()
        val pull = repo.workoutState.first().exercises[1]
        val push = WorkoutTemplate(
            name = "Push",
            entries = listOf(
                TemplateEntry(exerciseId = bench.id, targetSets = 4, targetReps = 8, targetWeightKg = 80.0),
                TemplateEntry(exerciseId = pull.id, targetSets = 2, targetMode = TemplateTargetMode.AMRAP, targetReps = null),
            ),
        )
        repo.saveWorkoutTemplate(push)
        repo.saveWorkoutTemplate(WorkoutTemplate(name = "Leg"))

        // Edit the first template: createdAt is preserved so it keeps its list position.
        repo.saveWorkoutTemplate(push.copy(name = "Push lourd"))

        val templates = repo.templates.first { it.size == 2 }
        assertEquals(listOf("Push lourd", "Leg"), templates.map { it.name })
        val saved = templates.first()
        assertEquals(push.id, saved.id)
        assertEquals(2, saved.entries.size)
        assertEquals(TemplateTargetMode.AMRAP, saved.entries[1].targetMode)
        assertEquals(80.0, saved.entries[0].targetWeightKg!!, 0.0)

        repo.deleteWorkoutTemplate(push.id)
        assertEquals(listOf("Leg"), repo.templates.first { it.size == 1 }.map { it.name })
    }

    @Test
    fun deletingTemplate_nullsTheSessionLink_andSessionsSurvive() = runTest {
        val repo = repository()
        val bench = repo.workoutState.first().exercises.first()
        val template = WorkoutTemplate(
            name = "Push",
            entries = listOf(TemplateEntry(exerciseId = bench.id)),
        )
        repo.saveWorkoutTemplate(template)
        repo.addWorkoutSession(
            WorkoutSession(
                name = "Push",
                templateId = template.id,
                exercises = listOf(
                    ExerciseSession(
                        exercise = bench,
                        sets = listOf(WorkoutSet(setNumber = 1, weight = 80.0, reps = 8, isCompleted = true)),
                    )
                ),
            )
        )
        repo.workoutState.first { it.sessions.size == 1 }

        repo.deleteWorkoutTemplate(template.id)

        val session = repo.workoutState.first { st ->
            st.sessions.singleOrNull()?.templateId == null
        }.sessions.single()
        assertEquals("Push", session.name) // the session itself is intact, only the link is gone
    }

    @Test
    fun getExercise_resolvesArchivedExercises_unlikeTheCatalogFlow() = runTest {
        val repo = repository()
        val bench = repo.workoutState.first().exercises.first()
        repo.saveWorkoutTemplate(
            WorkoutTemplate(name = "Push", entries = listOf(TemplateEntry(exerciseId = bench.id)))
        )

        repo.deleteExercise(bench.id) // referenced by the template => archived

        repo.workoutState.first { st -> st.exercises.none { it.id == bench.id } }
        assertEquals(bench.name, repo.getExercise(bench.id)?.name)
        assertEquals(bench.primaryMuscle, repo.getExercise(bench.id)?.primaryMuscle)
    }

    @Test
    fun saveUserProfile_roundTrips_andFeedsTheBodyWeightLogWithoutSpam() = runTest {
        val repo = repository()
        val profile = UserProfile(
            bodyWeightKg = 72.5,
            weightUnit = WeightUnit.LBS,
            healthConnectSyncEnabled = true,
            bodyWeightUpdatedAt = 42L,
            bodyWeightSource = BodyWeightSource.HEALTH_CONNECT,
        )

        repo.saveUserProfile(profile)
        val saved = repo.workoutState.first { it.userProfile.bodyWeightKg != null }.userProfile
        assertEquals(profile, saved)

        // Same observation saved again (e.g. a settings toggle) must not append a second row…
        repo.saveUserProfile(profile.copy(healthConnectSyncEnabled = false))
        // …but a new weight must.
        repo.saveUserProfile(profile.copy(bodyWeightKg = 73.0, bodyWeightUpdatedAt = 43L))

        val log = db.bodyWeightDao().observeAll().first()
        assertEquals(listOf(72.5 to 42L, 73.0 to 43L), log.map { it.weightKg to it.measuredAt })
        assertEquals("HEALTH_CONNECT", log.first().source)
    }
}
