package com.example.goattracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Multi-step writes are default interface methods annotated @Transaction: Room generates the
 * transactional wrapper for them, which is the driver-compatible path — the legacy
 * `RoomDatabase.withTransaction` extension targets the SupportSQLite executor machinery and is
 * not safe with `setDriver` (it hangs under the bundled JVM driver).
 */
@Dao
interface ExerciseDao {
    @Transaction
    @Query("SELECT * FROM exercise WHERE isArchived = 0 ORDER BY createdAt ASC, name ASC")
    fun observeActive(): Flow<List<ExerciseWithMuscles>>

    @Query("SELECT * FROM exercise WHERE id = :id")
    suspend fun getById(id: String): ExerciseEntity?

    @Upsert
    suspend fun upsert(exercise: ExerciseEntity)

    /** Import path: embedded historical copies must not overwrite the live catalog row. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(exercise: ExerciseEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMusclesIgnore(rows: List<ExerciseMuscleEntity>)

    @Query("DELETE FROM exercise_muscle WHERE exerciseId = :exerciseId")
    suspend fun deleteMuscles(exerciseId: String)

    @Insert
    suspend fun insertMuscles(rows: List<ExerciseMuscleEntity>)

    @Query("SELECT COUNT(*) FROM exercise_entry WHERE exerciseId = :exerciseId")
    suspend fun historyReferenceCount(exerciseId: String): Int

    @Query("SELECT COUNT(*) FROM template_entry WHERE exerciseId = :exerciseId")
    suspend fun templateReferenceCount(exerciseId: String): Int

    @Query("UPDATE exercise SET isArchived = 1, updatedAt = :now WHERE id = :id")
    suspend fun archive(id: String, now: Long)

    @Query("DELETE FROM exercise WHERE id = :id")
    suspend fun deleteById(id: String)

    @Transaction
    suspend fun save(exercise: ExerciseEntity, muscles: List<ExerciseMuscleEntity>) {
        upsert(exercise)
        deleteMuscles(exercise.id)
        insertMuscles(muscles)
    }

    /**
     * Archives instead of deleting when history (or a template) references the exercise: the rows
     * must survive for the stats joins and the RESTRICT keys enforce it. The user-visible result
     * is identical — the exercise leaves the catalog list.
     */
    @Transaction
    suspend fun deleteOrArchive(id: String, now: Long) {
        if (historyReferenceCount(id) > 0 || templateReferenceCount(id) > 0) {
            archive(id, now)
        } else {
            deleteById(id)
        }
    }
}

@Dao
interface SessionDao {
    @Transaction
    @Query("SELECT * FROM workout_session WHERE status = 'FINISHED' ORDER BY startedAt ASC")
    fun observeFinished(): Flow<List<SessionWithContent>>

    @Transaction
    @Query("SELECT * FROM workout_session WHERE status = 'DRAFT' LIMIT 1")
    fun observeDraft(): Flow<SessionWithContent?>

    @Query("SELECT EXISTS(SELECT 1 FROM workout_session WHERE id = :id)")
    suspend fun exists(id: String): Boolean

    @Upsert
    suspend fun upsertSession(session: WorkoutSessionEntity)

    /** CASCADE wipes the entries' sets too: one call resets a session's whole content subtree. */
    @Query("DELETE FROM exercise_entry WHERE sessionId = :sessionId")
    suspend fun deleteEntriesFor(sessionId: String)

    @Insert
    suspend fun insertEntries(entries: List<ExerciseEntryEntity>)

    @Insert
    suspend fun insertSets(sets: List<SetEntryEntity>)

    @Query("DELETE FROM workout_session WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM workout_session WHERE status = 'DRAFT'")
    suspend fun deleteDrafts()

    @Query("DELETE FROM workout_session WHERE status = 'DRAFT' AND id != :exceptId")
    suspend fun deleteOtherDrafts(exceptId: String)

    /** Replaces the whole content subtree (deleting entries CASCADE-deletes their sets). */
    @Transaction
    suspend fun replaceContent(
        session: WorkoutSessionEntity,
        entries: List<ExerciseEntryEntity>,
        sets: List<SetEntryEntity>,
    ) {
        upsertSession(session)
        deleteEntriesFor(session.id)
        insertEntries(entries)
        insertSets(sets)
    }

    /** Legacy contract: updating an unknown id is a no-op. */
    @Transaction
    suspend fun replaceContentIfExists(
        session: WorkoutSessionEntity,
        entries: List<ExerciseEntryEntity>,
        sets: List<SetEntryEntity>,
    ) {
        if (exists(session.id)) replaceContent(session, entries, sets)
    }

    /** Single-draft invariant, kept by construction. */
    @Transaction
    suspend fun saveDraft(
        session: WorkoutSessionEntity,
        entries: List<ExerciseEntryEntity>,
        sets: List<SetEntryEntity>,
    ) {
        deleteOtherDrafts(session.id)
        replaceContent(session, entries, sets)
    }
}

@Dao
interface ProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = ${UserProfileEntity.SINGLETON_ID}")
    fun observe(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile WHERE id = ${UserProfileEntity.SINGLETON_ID}")
    suspend fun get(): UserProfileEntity?

    @Upsert
    suspend fun upsert(profile: UserProfileEntity)

    @Query("SELECT * FROM body_weight_log ORDER BY measuredAt DESC, id DESC LIMIT 1")
    suspend fun latestBodyWeight(): BodyWeightLogEntity?

    @Insert
    suspend fun insertBodyWeight(row: BodyWeightLogEntity)

    /**
     * Every distinct weight observation lands in body_weight_log, whatever the source — that
     * history is what weight curves and at-the-time bodyweight tonnage are built on. Saves that
     * don't change the observation (unit toggle, Health Connect opt-in...) must not spam the log,
     * hence the value+timestamp dedup against the latest row.
     */
    @Transaction
    suspend fun saveWithBodyWeightLog(profile: UserProfileEntity, candidate: BodyWeightLogEntity?) {
        upsert(profile)
        if (candidate == null) return
        val latest = latestBodyWeight()
        if (latest != null &&
            latest.weightKg == candidate.weightKg &&
            latest.measuredAt == candidate.measuredAt
        ) {
            return
        }
        insertBodyWeight(candidate)
    }
}

@Dao
interface BodyWeightDao {
    @Insert
    suspend fun insert(row: BodyWeightLogEntity)

    @Query("SELECT * FROM body_weight_log ORDER BY measuredAt DESC, id DESC LIMIT 1")
    suspend fun latest(): BodyWeightLogEntity?

    @Query("SELECT * FROM body_weight_log ORDER BY measuredAt ASC, id ASC")
    fun observeAll(): Flow<List<BodyWeightLogEntity>>
}

@Dao
interface TemplateDao {
    @Transaction
    @Query("SELECT * FROM workout_template WHERE isArchived = 0 ORDER BY position ASC, createdAt ASC")
    fun observeActive(): Flow<List<TemplateWithEntries>>

    @Upsert
    suspend fun upsertTemplate(template: WorkoutTemplateEntity)

    @Query("DELETE FROM template_entry WHERE templateId = :templateId")
    suspend fun deleteEntriesFor(templateId: String)

    @Insert
    suspend fun insertEntries(entries: List<TemplateEntryEntity>)

    @Query("DELETE FROM workout_template WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface MuscleRecoveryDao {
    @Query("SELECT * FROM muscle_recovery_setting")
    fun observeAll(): Flow<List<MuscleRecoverySettingEntity>>

    @Upsert
    suspend fun upsert(setting: MuscleRecoverySettingEntity)

    @Query("DELETE FROM muscle_recovery_setting WHERE muscle = :muscle")
    suspend fun delete(muscle: String)
}

@Dao
interface AppMetaDao {
    @Query("SELECT value FROM app_meta WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Upsert
    suspend fun put(entry: AppMetaEntity)
}

/**
 * First-run import of the legacy workouts.json, as one atomic batch. Everything inserts with
 * IGNORE so a crash before the init marker lands simply re-runs the import to convergence —
 * existing rows (catalog wins over embedded historical copies, first occurrence wins among
 * duplicates) are never overwritten.
 */
@Dao
interface ImportDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertExercises(rows: List<ExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMuscles(rows: List<ExerciseMuscleEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSessions(rows: List<WorkoutSessionEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEntries(rows: List<ExerciseEntryEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSets(rows: List<SetEntryEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertProfile(row: UserProfileEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBodyWeightLog(row: BodyWeightLogEntity)

    @Transaction
    suspend fun importAll(plan: LegacyImportPlan) {
        insertExercises(plan.exercises)
        insertMuscles(plan.muscles)
        insertSessions(plan.sessions)
        insertEntries(plan.entries)
        insertSets(plan.sets)
        plan.profile?.let { insertProfile(it) }
        plan.bodyWeightLog?.let { insertBodyWeightLog(it) }
    }
}
