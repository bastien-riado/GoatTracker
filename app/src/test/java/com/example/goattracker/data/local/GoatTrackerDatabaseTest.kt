package com.example.goattracker.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

/**
 * Schema-level smoke tests: FK cascades/restrictions, the DRAFT/FINISHED split and the relation
 * projections — the SQL behaviors the repository builds on. Runs on the JVM: Robolectric provides
 * the Context, [BundledSQLiteDriver] provides a real SQLite without any emulator.
 */
@RunWith(RobolectricTestRunner::class)
class GoatTrackerDatabaseTest {

    private lateinit var db: GoatTrackerDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, GoatTrackerDatabase::class.java)
            .setDriver(BundledSQLiteDriver())
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun exercise(
        id: String = UUID.randomUUID().toString(),
        name: String = "Développé Couché",
        isArchived: Boolean = false,
    ) = ExerciseEntity(
        id = id,
        name = name,
        category = "PUSH",
        trackingType = "WEIGHT_REPS",
        notes = "",
        restTimeSeconds = 90,
        isArchived = isArchived,
        createdAt = 1L,
        updatedAt = 1L,
    )

    private fun session(
        id: String = UUID.randomUUID().toString(),
        status: String = SessionStatus.FINISHED,
        startedAt: Long = 1_000L,
    ) = WorkoutSessionEntity(
        id = id,
        name = "Push A",
        startedAt = startedAt,
        endedAt = null,
        status = status,
        notes = "",
        bodyWeightKg = null,
        sessionRpe = null,
        templateId = null,
        createdAt = startedAt,
        updatedAt = startedAt,
    )

    private fun entry(sessionId: String, exerciseId: String, id: String = UUID.randomUUID().toString()) =
        ExerciseEntryEntity(
            id = id,
            sessionId = sessionId,
            exerciseId = exerciseId,
            position = 0,
            nameSnapshot = "Développé Couché",
            trackingTypeSnapshot = "WEIGHT_REPS",
        )

    private fun set(entryId: String, number: Int = 1) = SetEntryEntity(
        id = UUID.randomUUID().toString(),
        entryId = entryId,
        setNumber = number,
        weightKg = 80.0,
        reps = 10,
        durationSeconds = 0,
        distanceKm = 0.0,
        isCompleted = true,
        completedAt = null,
        rpe = null,
        setType = "WORKING",
        isToFailure = false,
        dropGroupId = null,
    )

    @Test
    fun exerciseWithMuscles_roundTrips_andArchivedAreFilteredOut() = runTest {
        val active = exercise(name = "Squat")
        val archived = exercise(name = "Vieux mouvement", isArchived = true)
        db.exerciseDao().upsert(active)
        db.exerciseDao().upsert(archived)
        db.exerciseDao().insertMuscles(
            listOf(ExerciseMuscleEntity(active.id, "Quadriceps", 1.0))
        )

        val visible = db.exerciseDao().observeActive().first()

        assertEquals(listOf("Squat"), visible.map { it.exercise.name })
        assertEquals("Quadriceps", visible.single().muscles.single().muscle)
        assertEquals(1.0, visible.single().muscles.single().contribution, 0.0)
    }

    @Test
    fun deletingSession_cascadesToEntriesAndSets() = runTest {
        val ex = exercise()
        val s = session()
        val e = entry(s.id, ex.id)
        db.exerciseDao().upsert(ex)
        db.sessionDao().upsertSession(s)
        db.sessionDao().insertEntries(listOf(e))
        db.sessionDao().insertSets(listOf(set(e.id), set(e.id, 2)))

        db.sessionDao().deleteById(s.id)

        assertTrue(db.sessionDao().observeFinished().first().isEmpty())
        // The exercise itself must survive: only the session subtree cascades.
        assertNotNull(db.exerciseDao().getById(ex.id))
        // Re-inserting an entry with a fresh session proves the orphaned sets are really gone
        // (an insert under a lingering child row would violate nothing — count via relation).
        val s2 = session()
        db.sessionDao().upsertSession(s2)
        val e2 = entry(s2.id, ex.id, id = e.id) // same entry id reusable => old row was deleted
        db.sessionDao().insertEntries(listOf(e2))
        assertEquals(0, db.sessionDao().observeFinished().first().single().entries.single().sets.size)
    }

    @Test
    fun draftAndFinished_liveInSeparateQueries() = runTest {
        db.sessionDao().upsertSession(session(status = SessionStatus.FINISHED, startedAt = 1L))
        val draft = session(status = SessionStatus.DRAFT, startedAt = 2L)
        db.sessionDao().upsertSession(draft)

        assertEquals(1, db.sessionDao().observeFinished().first().size)
        assertEquals(draft.id, db.sessionDao().observeDraft().first()?.session?.id)

        db.sessionDao().deleteDrafts()
        assertNull(db.sessionDao().observeDraft().first())
        assertEquals(1, db.sessionDao().observeFinished().first().size)
    }

    @Test
    fun deletingExerciseReferencedByHistory_isRejectedByTheSchema() = runTest {
        val ex = exercise()
        val s = session()
        db.exerciseDao().upsert(ex)
        db.sessionDao().upsertSession(s)
        db.sessionDao().insertEntries(listOf(entry(s.id, ex.id)))

        try {
            db.exerciseDao().deleteById(ex.id)
            fail("expected the RESTRICT foreign key to reject the delete")
        } catch (expected: Exception) {
            assertTrue(
                "expected a foreign key failure, got: $expected",
                expected.message.orEmpty().contains("FOREIGN KEY", ignoreCase = true),
            )
        }
        assertNotNull(db.exerciseDao().getById(ex.id))
        assertEquals(1, db.exerciseDao().historyReferenceCount(ex.id))
    }

    @Test
    fun userProfile_isSingleRowUpsert() = runTest {
        val dao = db.profileDao()
        dao.upsert(
            UserProfileEntity(
                bodyWeightKg = 72.5,
                weightUnit = "KG",
                healthConnectSyncEnabled = false,
                bodyWeightUpdatedAt = null,
                bodyWeightSource = "MANUAL",
            )
        )
        dao.upsert(
            UserProfileEntity(
                bodyWeightKg = 73.0,
                weightUnit = "LBS",
                healthConnectSyncEnabled = true,
                bodyWeightUpdatedAt = 42L,
                bodyWeightSource = "HEALTH_CONNECT",
            )
        )

        val profile = dao.observe().first()
        assertEquals(73.0, profile?.bodyWeightKg!!, 0.0)
        assertEquals("LBS", profile.weightUnit)
    }
}
