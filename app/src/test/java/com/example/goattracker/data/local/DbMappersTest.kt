package com.example.goattracker.data.local

import com.example.goattracker.domain.model.Exercise
import com.example.goattracker.domain.model.ExerciseCategory
import com.example.goattracker.domain.model.ExerciseSession
import com.example.goattracker.domain.model.SetType
import com.example.goattracker.domain.model.TrackingType
import com.example.goattracker.domain.model.UserProfile
import com.example.goattracker.domain.model.WeightUnit
import com.example.goattracker.domain.model.WorkoutSession
import com.example.goattracker.domain.model.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DbMappersTest {

    private val benchPress = Exercise(
        id = "ex-1",
        name = "Développé Couché",
        category = ExerciseCategory.PUSH,
        primaryMuscle = "Pectoraux",
        trackingType = TrackingType.WEIGHT_REPS,
        notes = "coudes rentrés",
        restTimeSeconds = 120,
    )

    private fun ExerciseEntity.withMuscles(vararg rows: ExerciseMuscleEntity) =
        ExerciseWithMuscles(this, rows.toList())

    @Test
    fun exercise_roundTrips_throughEntityAndMuscleRows() {
        val entity = benchPress.toEntity(isArchived = false, createdAt = 10L, updatedAt = 20L)
        val muscles = benchPress.toMuscleRows()

        assertEquals(listOf(ExerciseMuscleEntity("ex-1", "Pectoraux", 1.0)), muscles)
        assertEquals(benchPress, entity.withMuscles(*muscles.toTypedArray()).toDomain())
    }

    @Test
    fun secondaryMuscles_roundTrip_atHalfContribution() {
        val withSecondaries = benchPress.copy(secondaryMuscles = listOf("Triceps", "Épaules"))

        val rows = withSecondaries.toMuscleRows()

        assertEquals(3, rows.size)
        assertEquals(1.0, rows.first { it.muscle == "Pectoraux" }.contribution, 0.0)
        assertEquals(0.5, rows.first { it.muscle == "Triceps" }.contribution, 0.0)

        val entity = withSecondaries.toEntity(isArchived = false, createdAt = 0L, updatedAt = 0L)
        val rebuilt = entity.withMuscles(*rows.toTypedArray()).toDomain()
        assertEquals("Pectoraux", rebuilt.primaryMuscle)
        assertEquals(listOf("Triceps", "Épaules").sorted(), rebuilt.secondaryMuscles.sorted())
    }

    @Test
    fun secondaryDuplicatingThePrimary_isDropped() {
        val rows = benchPress.copy(secondaryMuscles = listOf("Pectoraux", "Triceps")).toMuscleRows()

        assertEquals(listOf("Pectoraux", "Triceps"), rows.map { it.muscle })
    }

    @Test
    fun primaryMuscle_isTheHighestContributionRow() {
        val entity = benchPress.toEntity(isArchived = false, createdAt = 0L, updatedAt = 0L)
        val withSecondaries = entity.withMuscles(
            ExerciseMuscleEntity("ex-1", "Triceps", 0.4),
            ExerciseMuscleEntity("ex-1", "Pectoraux", 1.0),
            ExerciseMuscleEntity("ex-1", "Épaules", 0.3),
        )

        assertEquals("Pectoraux", withSecondaries.toDomain().primaryMuscle)
    }

    @Test
    fun unknownEnumNamesInDb_degradeToDefaultsInsteadOfCrashing() {
        val entity = ExerciseEntity(
            id = "x", name = "Mystère", category = "FUTURE_CATEGORY", trackingType = "FUTURE_TYPE",
            notes = "", restTimeSeconds = 90, isArchived = false, createdAt = 0L, updatedAt = 0L,
        )

        val domain = entity.withMuscles().toDomain()

        assertEquals(ExerciseCategory.PUSH, domain.category)
        assertEquals(TrackingType.WEIGHT_REPS, domain.trackingType)
        assertEquals("", domain.primaryMuscle)
    }

    @Test
    fun workoutSet_roundTrips_withStatsFields() {
        val set = WorkoutSet(
            setNumber = 3,
            weight = 92.5,
            reps = 6,
            isCompleted = true,
            completedAt = 123L,
            rpe = 8.5,
            setType = SetType.DROP,
            isToFailure = true,
            dropGroupId = "grp-1",
        )

        assertEquals(set, set.toEntity(entryId = "entry-1").toDomain())
    }

    @Test
    fun entrySnapshots_overrideLiveRow_soHistorySurvivesRenames() {
        val renamedLiveRow = benchPress.copy(name = "DC Haltères", trackingType = TrackingType.TIME)
            .toEntity(isArchived = false, createdAt = 0L, updatedAt = 0L)
        val entry = ExerciseSession(id = "es-1", exercise = benchPress)
            .toEntryEntity(sessionId = "s-1", position = 0)

        val rebuilt = EntryWithSets(
            entry = entry,
            sets = emptyList(),
            exercise = renamedLiveRow.withMuscles(ExerciseMuscleEntity("ex-1", "Pectoraux", 1.0)),
        ).toDomain()

        // Frozen at session time:
        assertEquals("Développé Couché", rebuilt.exercise.name)
        assertEquals(TrackingType.WEIGHT_REPS, rebuilt.exercise.trackingType)
        // Resolved live (deliberate: correcting an exercise fixes history stats):
        assertEquals("Pectoraux", rebuilt.exercise.primaryMuscle)
    }

    @Test
    fun missingLiveExerciseRow_degradesToSnapshotPlaceholder() {
        val entry = ExerciseSession(id = "es-1", exercise = benchPress)
            .toEntryEntity(sessionId = "s-1", position = 0)

        val rebuilt = EntryWithSets(entry = entry, sets = emptyList(), exercise = null).toDomain()

        assertEquals("Développé Couché", rebuilt.exercise.name)
        assertEquals(TrackingType.WEIGHT_REPS, rebuilt.exercise.trackingType)
        assertEquals("ex-1", rebuilt.exercise.id)
    }

    @Test
    fun session_roundTrips_andSortsEntriesAndSetsByPosition() {
        val squat = benchPress.copy(id = "ex-2", name = "Squat", primaryMuscle = "Quadriceps")
        val session = WorkoutSession(
            id = "s-1",
            startTime = 1_000L,
            endTime = 2_000L,
            name = "Push A",
            exercises = listOf(
                ExerciseSession(
                    id = "es-1",
                    exercise = benchPress,
                    sets = listOf(
                        WorkoutSet(id = "set-1", setNumber = 1, weight = 80.0, reps = 10, isCompleted = true),
                        WorkoutSet(id = "set-2", setNumber = 2, weight = 85.0, reps = 8, isCompleted = true),
                    ),
                ),
                ExerciseSession(id = "es-2", exercise = squat),
            ),
            notes = "bonne séance",
            bodyWeightKg = 74.0,
            sessionRpe = 7.5,
        )

        val sessionEntity = session.toEntity(SessionStatus.FINISHED, createdAt = 1_000L, updatedAt = 2_000L)
        val entryEntities = session.exercises.mapIndexed { i, es -> es.toEntryEntity(session.id, i) }
        val setEntities = session.exercises.flatMap { es -> es.sets.map { it.toEntity(es.id) } }

        // Rebuild with shuffled relation lists: Room does not guarantee @Relation order.
        val rebuilt = SessionWithContent(
            session = sessionEntity,
            entries = entryEntities.reversed().map { entry ->
                EntryWithSets(
                    entry = entry,
                    sets = setEntities.filter { it.entryId == entry.id }.reversed(),
                    exercise = (if (entry.exerciseId == "ex-1") benchPress else squat)
                        .toEntity(isArchived = false, createdAt = 0L, updatedAt = 0L)
                        .withMuscles(
                            ExerciseMuscleEntity(entry.exerciseId, if (entry.exerciseId == "ex-1") "Pectoraux" else "Quadriceps", 1.0)
                        ),
                )
            },
        ).toDomain()

        assertEquals(session, rebuilt)
    }

    @Test
    fun draftSession_roundTrips_keepingIncompleteSets() {
        val draft = WorkoutSession(
            name = "En cours",
            exercises = listOf(
                ExerciseSession(
                    exercise = benchPress,
                    sets = listOf(WorkoutSet(setNumber = 1, weight = 60.0, reps = 0, isCompleted = false)),
                )
            ),
        )
        val entity = draft.toEntity(SessionStatus.DRAFT, createdAt = 0L, updatedAt = 0L)

        assertEquals(SessionStatus.DRAFT, entity.status)
        assertNull(entity.endedAt)
        val sets = draft.exercises.single().sets.map { it.toEntity("e") }
        assertTrue(sets.none { it.isCompleted })
    }

    @Test
    fun userProfile_roundTrips() {
        val profile = UserProfile(
            bodyWeightKg = 72.5,
            weightUnit = WeightUnit.LBS,
            healthConnectSyncEnabled = true,
            bodyWeightUpdatedAt = 42L,
            bodyWeightSource = com.example.goattracker.domain.model.BodyWeightSource.HEALTH_CONNECT,
        )

        assertEquals(profile, profile.toEntity().toDomain())
        assertEquals(UserProfileEntity.SINGLETON_ID, profile.toEntity().id)
    }
}
