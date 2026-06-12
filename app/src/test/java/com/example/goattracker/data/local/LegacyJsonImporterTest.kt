package com.example.goattracker.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.example.goattracker.data.dto.WorkoutStateDto
import com.example.goattracker.data.dto.toDto
import com.example.goattracker.domain.model.BodyWeightSource
import com.example.goattracker.domain.model.Exercise
import com.example.goattracker.domain.model.ExerciseCategory
import com.example.goattracker.domain.model.ExerciseSession
import com.example.goattracker.domain.model.TrackingType
import com.example.goattracker.domain.model.UserProfile
import com.example.goattracker.domain.model.WeightUnit
import com.example.goattracker.domain.model.WorkoutSession
import com.example.goattracker.domain.model.WorkoutSet
import com.example.goattracker.domain.model.WorkoutState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class LegacyJsonImporterTest {

    private lateinit var db: GoatTrackerDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var dir: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, GoatTrackerDatabase::class.java)
            .setDriver(BundledSQLiteDriver())
            .build()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        dir = File(System.getProperty("java.io.tmpdir"), "gt-import-" + System.nanoTime()).apply { mkdirs() }
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
        dir.deleteRecursively()
    }

    /** A repository wired exactly like production: importer as the first-launch initializer. */
    private suspend fun repository(): RoomDataRepository {
        val repo = RoomDataRepository(
            db = db,
            scope = scope,
            initializer = LegacyJsonImporter(dir)::run,
        )
        repo.isReady.first { it }
        return repo
    }

    private val bench = Exercise(
        id = "ex-bench",
        name = "Développé Couché",
        category = ExerciseCategory.PUSH,
        primaryMuscle = "Pectoraux",
        trackingType = TrackingType.WEIGHT_REPS,
        restTimeSeconds = 120,
    )

    private val deletedCurl = Exercise(
        id = "ex-curl",
        name = "Curl Supprimé",
        category = ExerciseCategory.PULL,
        primaryMuscle = "Biceps",
        trackingType = TrackingType.WEIGHT_REPS,
    )

    private fun writeLegacyFile(state: WorkoutState) {
        val json = Json { prettyPrint = true }
        File(dir, "workouts.json")
            .writeText(json.encodeToString(WorkoutStateDto.serializer(), state.toDto()))
    }

    @Test
    fun fullLegacyState_importsAndFileIsRenamedAsBackup() = runTest {
        val session = WorkoutSession(
            id = "s-1",
            startTime = 1_000L,
            endTime = 2_000L,
            name = "Push A",
            exercises = listOf(
                ExerciseSession(
                    id = "es-1",
                    exercise = bench,
                    sets = listOf(
                        WorkoutSet(id = "set-1", setNumber = 1, weight = 80.0, reps = 10, isCompleted = true),
                        WorkoutSet(id = "set-2", setNumber = 2, weight = 85.0, reps = 8, isCompleted = true),
                    ),
                ),
                // An exercise the user deleted from the catalog before migrating:
                ExerciseSession(
                    id = "es-2",
                    exercise = deletedCurl,
                    sets = listOf(WorkoutSet(id = "set-3", setNumber = 1, weight = 20.0, reps = 12, isCompleted = true)),
                ),
            ),
        )
        val draft = WorkoutSession(
            id = "s-draft",
            startTime = 3_000L,
            name = "En cours",
            exercises = listOf(
                ExerciseSession(
                    id = "es-3",
                    exercise = bench,
                    sets = listOf(WorkoutSet(id = "set-4", setNumber = 1, weight = 60.0, reps = 0, isCompleted = false)),
                )
            ),
        )
        writeLegacyFile(
            WorkoutState(
                exercises = listOf(bench),
                sessions = listOf(session),
                activeDraft = draft,
                userProfile = UserProfile(
                    bodyWeightKg = 74.0,
                    weightUnit = WeightUnit.KG,
                    bodyWeightUpdatedAt = 42L,
                    bodyWeightSource = BodyWeightSource.MANUAL,
                ),
            )
        )

        val state = repository().workoutState.first()

        // Catalog: only the non-deleted exercise; NOT the default presets.
        assertEquals(listOf("Développé Couché"), state.exercises.map { it.name })
        // History: intact, including the deleted exercise rendered from its archived row.
        val imported = state.sessions.single()
        assertEquals("Push A", imported.name)
        assertEquals(2_000L, imported.endTime)
        assertEquals(listOf("Développé Couché", "Curl Supprimé"), imported.exercises.map { it.exercise.name })
        assertEquals(
            listOf(80.0 to 10, 85.0 to 8),
            imported.exercises[0].sets.map { it.weight to it.reps },
        )
        // Draft: revived with its incomplete set.
        assertEquals("s-draft", state.activeDraft?.id)
        assertEquals(1, state.activeDraft?.exercises?.single()?.sets?.size)
        // Profile + first body-weight observation.
        assertEquals(74.0, state.userProfile.bodyWeightKg!!, 0.0)
        assertEquals(42L, db.bodyWeightDao().latest()?.measuredAt)
        // The legacy file became a backup, not garbage.
        assertFalse(File(dir, "workouts.json").exists())
        assertTrue(dir.listFiles().orEmpty().any { it.name.startsWith("workouts.imported-") })
    }

    @Test
    fun legacyFileWithoutSchemaVersionOrDraft_stillImports() = runTest {
        // Pre-versioning, pre-draft, pre-profile format (legacy contract test, verbatim fixture).
        File(dir, "workouts.json").writeText(
            """{"exercises":[{"id":"x1","name":"Legacy","category":"PUSH","primaryMuscle":"Pecs","trackingType":"WEIGHT_REPS"}],"sessions":[]}"""
        )

        val state = repository().workoutState.first()

        assertEquals(listOf("Legacy"), state.exercises.map { it.name })
        assertNull(state.activeDraft)
        assertNull(state.userProfile.bodyWeightKg)
        assertEquals(WeightUnit.KG, state.userProfile.weightUnit)
    }

    @Test
    fun corruptFile_isPreservedAndDefaultsSeeded() = runTest {
        File(dir, "workouts.json").writeText("{ not valid json ]]")

        val state = repository().workoutState.first()

        assertEquals(4, state.exercises.size) // default presets
        assertTrue(dir.listFiles().orEmpty().any { it.name.startsWith("workouts.corrupt-") })
        assertFalse(File(dir, "workouts.json").exists())
    }

    @Test
    fun noLegacyFile_seedsDefaults() = runTest {
        val state = repository().workoutState.first()
        assertEquals(4, state.exercises.size)
    }

    @Test
    fun importRunTwice_convergesWithoutDuplicates() = runTest {
        writeLegacyFile(
            WorkoutState(
                exercises = listOf(bench),
                sessions = listOf(
                    WorkoutSession(
                        id = "s-1",
                        startTime = 1L,
                        endTime = 2L,
                        name = "Push",
                        exercises = listOf(
                            ExerciseSession(
                                id = "es-1",
                                exercise = bench,
                                sets = listOf(WorkoutSet(id = "set-1", setNumber = 1, weight = 80.0, reps = 10, isCompleted = true)),
                            )
                        ),
                    )
                ),
            )
        )
        // Covers both crash windows. Run #2 with a re-written file = crash BEFORE the rename
        // (re-import must converge via IGNORE). The repository() below runs the importer a third
        // time with no file but an imported-backup present = crash AFTER the rename, BEFORE the
        // init marker (must NOT seed the presets on top of the imported data).
        val importer = LegacyJsonImporter(dir)
        importer.run(db)
        File(dir, "workouts.json").writeText(
            Json.encodeToString(
                WorkoutStateDto.serializer(),
                WorkoutState(exercises = listOf(bench), sessions = emptyList()).toDto(),
            )
        )
        importer.run(db)

        val state = repository().workoutState.first()
        assertEquals(1, state.exercises.size)
        assertEquals(1, state.sessions.size)
        assertEquals(1, state.sessions.single().exercises.single().sets.size)
        assertNotNull(db.exerciseDao().getById("ex-bench"))
    }
}
