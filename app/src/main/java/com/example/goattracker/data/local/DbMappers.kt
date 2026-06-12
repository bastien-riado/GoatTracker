package com.example.goattracker.data.local

import com.example.goattracker.domain.model.BodyWeightSource
import com.example.goattracker.domain.model.Exercise
import com.example.goattracker.domain.model.ExerciseCategory
import com.example.goattracker.domain.model.ExerciseSession
import com.example.goattracker.domain.model.SetType
import com.example.goattracker.domain.model.TemplateEntry
import com.example.goattracker.domain.model.TemplateTargetMode
import com.example.goattracker.domain.model.TrackingType
import com.example.goattracker.domain.model.WorkoutTemplate
import com.example.goattracker.domain.model.UserProfile
import com.example.goattracker.domain.model.WeightUnit
import com.example.goattracker.domain.model.WorkoutSession
import com.example.goattracker.domain.model.WorkoutSet

/**
 * Pure entity↔domain conversions; timestamps and transaction orchestration belong to the
 * repository. Enum TEXT columns parse tolerantly (unknown name → default) so a future rename or a
 * hand-edited DB degrades a field instead of failing the whole load — same policy as the legacy
 * JSON DTOs.
 */
private inline fun <reified E : Enum<E>> parseOr(name: String?, fallback: E): E =
    enumValues<E>().firstOrNull { it.name == name } ?: fallback

// ---------- Exercise ----------

/** The primary muscle is the highest-contribution row; ties resolve by name for determinism. */
fun ExerciseWithMuscles.toDomain(): Exercise = Exercise(
    id = exercise.id,
    name = exercise.name,
    category = parseOr(exercise.category, ExerciseCategory.PUSH),
    primaryMuscle = muscles.maxWithOrNull(
        compareBy({ it.contribution }, { it.muscle })
    )?.muscle ?: "",
    trackingType = parseOr(exercise.trackingType, TrackingType.WEIGHT_REPS),
    notes = exercise.notes,
    restTimeSeconds = exercise.restTimeSeconds,
)

fun Exercise.toEntity(isArchived: Boolean, createdAt: Long, updatedAt: Long): ExerciseEntity =
    ExerciseEntity(
        id = id,
        name = name,
        category = category.name,
        trackingType = trackingType.name,
        notes = notes,
        restTimeSeconds = restTimeSeconds,
        isArchived = isArchived,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

/** Phase 1 writes the single primary row (contribution 1.0); secondaries are a later feature. */
fun Exercise.toMuscleRows(): List<ExerciseMuscleEntity> =
    if (primaryMuscle.isBlank()) emptyList()
    else listOf(ExerciseMuscleEntity(exerciseId = id, muscle = primaryMuscle, contribution = 1.0))

// ---------- Session content ----------

fun SetEntryEntity.toDomain(): WorkoutSet = WorkoutSet(
    id = id,
    setNumber = setNumber,
    weight = weightKg,
    reps = reps,
    durationSeconds = durationSeconds,
    distanceKm = distanceKm,
    isCompleted = isCompleted,
    completedAt = completedAt,
    rpe = rpe,
    setType = parseOr(setType, SetType.WORKING),
    isToFailure = isToFailure,
    dropGroupId = dropGroupId,
)

fun WorkoutSet.toEntity(entryId: String): SetEntryEntity = SetEntryEntity(
    id = id,
    entryId = entryId,
    setNumber = setNumber,
    weightKg = weight,
    reps = reps,
    durationSeconds = durationSeconds,
    distanceKm = distanceKm,
    isCompleted = isCompleted,
    completedAt = completedAt,
    rpe = rpe,
    setType = setType.name,
    isToFailure = isToFailure,
    dropGroupId = dropGroupId,
)

/**
 * Rebuilds the embedded-exercise view the domain expects: live row for everything except
 * name/trackingType, which come from the at-the-time snapshots (legacy embedded-copy semantics —
 * renaming an exercise must not rewrite history). A missing live row (hand-edited DB) degrades to
 * a snapshot-only placeholder instead of crashing.
 */
fun EntryWithSets.toDomain(): ExerciseSession {
    val snapshotTrackingType = parseOr(entry.trackingTypeSnapshot, TrackingType.WEIGHT_REPS)
    val base = exercise?.toDomain() ?: Exercise(
        id = entry.exerciseId,
        name = entry.nameSnapshot,
        category = ExerciseCategory.PUSH,
        primaryMuscle = "",
        trackingType = snapshotTrackingType,
    )
    return ExerciseSession(
        id = entry.id,
        exercise = base.copy(name = entry.nameSnapshot, trackingType = snapshotTrackingType),
        sets = sets.sortedBy { it.setNumber }.map { it.toDomain() },
    )
}

fun ExerciseSession.toEntryEntity(sessionId: String, position: Int): ExerciseEntryEntity =
    ExerciseEntryEntity(
        id = id,
        sessionId = sessionId,
        exerciseId = exercise.id,
        position = position,
        nameSnapshot = exercise.name,
        trackingTypeSnapshot = exercise.trackingType.name,
    )

// ---------- Session ----------

fun SessionWithContent.toDomain(): WorkoutSession = WorkoutSession(
    id = session.id,
    startTime = session.startedAt,
    endTime = session.endedAt,
    name = session.name,
    exercises = entries.sortedBy { it.entry.position }.map { it.toDomain() },
    notes = session.notes,
    bodyWeightKg = session.bodyWeightKg,
    sessionRpe = session.sessionRpe,
    templateId = session.templateId,
)

fun WorkoutSession.toEntity(status: String, createdAt: Long, updatedAt: Long): WorkoutSessionEntity =
    WorkoutSessionEntity(
        id = id,
        name = name,
        startedAt = startTime,
        endedAt = endTime,
        status = status,
        notes = notes,
        bodyWeightKg = bodyWeightKg,
        sessionRpe = sessionRpe,
        templateId = templateId,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

// ---------- Templates ----------

fun TemplateEntryEntity.toDomain(): TemplateEntry = TemplateEntry(
    id = id,
    exerciseId = exerciseId,
    targetSets = targetSets,
    targetReps = targetReps,
    targetWeightKg = targetWeightKg,
    targetMode = parseOr(targetMode, TemplateTargetMode.REPS),
    restOverrideSeconds = restOverrideSeconds,
)

fun TemplateWithEntries.toDomain(): WorkoutTemplate = WorkoutTemplate(
    id = template.id,
    name = template.name,
    notes = template.notes,
    entries = entries.sortedBy { it.position }.map { it.toDomain() },
)

fun WorkoutTemplate.toEntity(createdAt: Long, updatedAt: Long): WorkoutTemplateEntity =
    WorkoutTemplateEntity(
        id = id,
        name = name,
        notes = notes,
        position = 0, // template list ordering is createdAt-based until a reorder feature exists
        isArchived = false,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun WorkoutTemplate.entryEntities(): List<TemplateEntryEntity> =
    entries.mapIndexed { index, entry ->
        TemplateEntryEntity(
            id = entry.id,
            templateId = id,
            exerciseId = entry.exerciseId,
            position = index,
            targetSets = entry.targetSets,
            targetReps = entry.targetReps,
            targetWeightKg = entry.targetWeightKg,
            targetMode = entry.targetMode.name,
            restOverrideSeconds = entry.restOverrideSeconds,
        )
    }

// ---------- Profile ----------

fun UserProfileEntity.toDomain(): UserProfile = UserProfile(
    bodyWeightKg = bodyWeightKg,
    weightUnit = parseOr(weightUnit, WeightUnit.KG),
    healthConnectSyncEnabled = healthConnectSyncEnabled,
    bodyWeightUpdatedAt = bodyWeightUpdatedAt,
    bodyWeightSource = parseOr(bodyWeightSource, BodyWeightSource.MANUAL),
)

fun UserProfile.toEntity(): UserProfileEntity = UserProfileEntity(
    bodyWeightKg = bodyWeightKg,
    weightUnit = weightUnit.name,
    healthConnectSyncEnabled = healthConnectSyncEnabled,
    bodyWeightUpdatedAt = bodyWeightUpdatedAt,
    bodyWeightSource = bodyWeightSource.name,
)
