package com.example.goattracker.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room schema, version 1. Conventions shared by every table:
 * - ids are UUID strings (already the domain convention) so a future multi-device sync can merge
 *   rows without renumbering;
 * - enums are stored as their `.name` TEXT and converted in the mappers, so the schema has no
 *   hidden TypeConverter coupling to Kotlin types;
 * - weights are ALWAYS kilograms, distances kilometers (same rule as the domain — display units
 *   convert at the UI boundary only);
 * - `createdAt`/`updatedAt` are epoch millis maintained by the repository, present so a sync layer
 *   can do last-write-wins later without a schema change.
 *
 * Catalog rows ([ExerciseEntity], [WorkoutTemplateEntity]) are soft-deleted via `isArchived` when
 * history still references them: hard-deleting would break the FK joins the stats queries rely on.
 */
@Entity(tableName = "exercise")
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val trackingType: String,
    val notes: String,
    val restTimeSeconds: Int,
    val isArchived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * Muscles worked by an exercise, weighted. `contribution` is 1.0 for the primary muscle and a
 * fraction (typically 0.3–0.5) for secondaries, so per-muscle volume/fatigue is
 * Σ(set load × contribution). Phase 1 writes a single 1.0 row per exercise (today's
 * `primaryMuscle`); the secondary-muscles feature only adds rows.
 */
@Entity(
    tableName = "exercise_muscle",
    primaryKeys = ["exerciseId", "muscle"],
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
)
data class ExerciseMuscleEntity(
    val exerciseId: String,
    /** Same vocabulary as the legacy `Exercise.primaryMuscle` strings (see MuscleGroupMapper). */
    val muscle: String,
    val contribution: Double,
)

object SessionStatus {
    const val DRAFT = "DRAFT"
    const val FINISHED = "FINISHED"
}

/**
 * One workout. The in-progress live session is the (single) row with [SessionStatus.DRAFT] —
 * replacing the JSON blob's `activeDraft` field — so it survives process death exactly like before.
 */
@Entity(
    tableName = "workout_session",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            // A deleted template must not take the sessions launched from it down with it.
            onDelete = ForeignKey.SET_NULL,
        )
    ],
    indices = [Index("status"), Index("startedAt"), Index("templateId")],
)
data class WorkoutSessionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val startedAt: Long,
    val endedAt: Long?,
    val status: String,
    val notes: String,
    /** Body weight at session time; historical sessions imported from JSON have null (unknown). */
    val bodyWeightKg: Double?,
    /** Session-level perceived effort (1–10), input of the sRPE training-load model. */
    val sessionRpe: Double?,
    val templateId: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * One exercise performed within a session (ex `ExerciseSession`). `nameSnapshot` and
 * `trackingTypeSnapshot` freeze what the log looked like at the time, preserving the legacy
 * embedded-copy semantics: renaming an exercise (or changing how it is tracked) must not rewrite
 * how past sessions are displayed. Everything else (category, muscles) resolves live from the
 * exercise row, so correcting an exercise's muscle assignment deliberately fixes history stats.
 */
@Entity(
    tableName = "exercise_entry",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            // History protects its exercises: the repository archives referenced exercises instead
            // of deleting them, and the DB enforces it.
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("sessionId"), Index("exerciseId")],
)
data class ExerciseEntryEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val exerciseId: String,
    val position: Int,
    val nameSnapshot: String,
    val trackingTypeSnapshot: String,
)

/**
 * One set (ex `WorkoutSet`). `setType` stores the domain `SetType` enum name (WARMUP/WORKING/DROP).
 * Drop sets are chains of rows sharing a `dropGroupId` (each weight plateau is its own row with its
 * achieved reps); `isToFailure` is orthogonal to `setType` because a plain working set can also be
 * taken to failure.
 */
@Entity(
    tableName = "set_entry",
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("entryId")],
)
data class SetEntryEntity(
    @PrimaryKey val id: String,
    val entryId: String,
    val setNumber: Int,
    val weightKg: Double,
    val reps: Int,
    val durationSeconds: Int,
    val distanceKm: Double,
    val isCompleted: Boolean,
    /** When the set was checked off — feeds rest-time/density stats. Null for legacy data. */
    val completedAt: Long?,
    /** Per-set perceived effort (1–10), optional input. */
    val rpe: Double?,
    val setType: String,
    val isToFailure: Boolean,
    val dropGroupId: String?,
)

/**
 * Body-weight history (the profile only keeps the CURRENT value). Appended by the repository on
 * every weight change, whatever the source, so weight curves and at-the-time bodyweight tonnage
 * become possible.
 */
@Entity(tableName = "body_weight_log", indices = [Index("measuredAt")])
data class BodyWeightLogEntity(
    @PrimaryKey val id: String,
    val weightKg: Double,
    val measuredAt: Long,
    val source: String,
)

/** Single-row table (id fixed to [SINGLETON_ID]); mirrors the domain UserProfile. */
@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val bodyWeightKg: Double?,
    val weightUnit: String,
    val healthConnectSyncEnabled: Boolean,
    val bodyWeightUpdatedAt: Long?,
    val bodyWeightSource: String,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}

/** A reusable workout (e.g. "Push") the user launches a session from. Feature lands in phase 2. */
@Entity(tableName = "workout_template")
data class WorkoutTemplateEntity(
    @PrimaryKey val id: String,
    val name: String,
    val notes: String,
    val position: Int,
    val isArchived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

object TargetMode {
    const val REPS = "REPS"

    /** As many reps as possible — the "to failure" planning mode (no target rep count). */
    const val AMRAP = "AMRAP"
}

@Entity(
    tableName = "template_entry",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("templateId"), Index("exerciseId")],
)
data class TemplateEntryEntity(
    @PrimaryKey val id: String,
    val templateId: String,
    val exerciseId: String,
    val position: Int,
    val targetSets: Int,
    val targetReps: Int?,
    val targetWeightKg: Double?,
    val targetMode: String,
    val restOverrideSeconds: Int?,
)

/**
 * Per-user recovery time per muscle, overriding the calculator defaults (everyone recovers
 * differently). No row = use the built-in default; the table only stores explicit overrides so the
 * two default sources can never drift.
 */
@Entity(tableName = "muscle_recovery_setting")
data class MuscleRecoverySettingEntity(
    @PrimaryKey val muscle: String,
    val recoveryHours: Int,
)

/**
 * Internal key/value flags. Holds [KEY_DATA_INITIALIZED] so "user deleted every exercise" is
 * distinguishable from "fresh install" — the legacy JSON repo used file existence for this; a DB
 * needs an explicit marker or defaults would resurrect on every empty launch.
 */
@Entity(tableName = "app_meta")
data class AppMetaEntity(
    @PrimaryKey val key: String,
    val value: String,
) {
    companion object {
        const val KEY_DATA_INITIALIZED = "data_initialized"
    }
}
