package com.example.goattracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * DAOs are deliberately single-statement: multi-step writes (replace a session's entries, archive
 * vs delete an exercise, the JSON import) are composed in the repository under
 * `database.useWriterConnection`/`withTransaction` so the transaction boundary lives in ONE layer.
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
}

@Dao
interface ProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = ${UserProfileEntity.SINGLETON_ID}")
    fun observe(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile WHERE id = ${UserProfileEntity.SINGLETON_ID}")
    suspend fun get(): UserProfileEntity?

    @Upsert
    suspend fun upsert(profile: UserProfileEntity)
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
