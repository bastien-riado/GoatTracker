package com.example.goattracker.data.dto

import com.example.goattracker.domain.model.BodyWeightSource
import com.example.goattracker.domain.model.Exercise
import com.example.goattracker.domain.model.ExerciseCategory
import com.example.goattracker.domain.model.ExerciseSession
import com.example.goattracker.domain.model.TrackingType
import com.example.goattracker.domain.model.UserProfile
import com.example.goattracker.domain.model.WeightUnit
import com.example.goattracker.domain.model.WorkoutSession
import com.example.goattracker.domain.model.WorkoutState
import com.example.goattracker.domain.model.WorkoutSet
import kotlinx.serialization.Serializable

@Serializable
data class ExerciseDto(
    val id: String,
    val name: String,
    val category: String,
    val primaryMuscle: String,
    val trackingType: String,
    val notes: String = "",
    val restTimeSeconds: Int = 90
) {
    fun toDomain(): Exercise {
        return Exercise(
            id = id,
            name = name,
            category = ExerciseCategory.valueOf(category),
            primaryMuscle = primaryMuscle,
            trackingType = TrackingType.valueOf(trackingType),
            notes = notes,
            restTimeSeconds = restTimeSeconds
        )
    }
}

fun Exercise.toDto(): ExerciseDto {
    return ExerciseDto(
        id = id,
        name = name,
        category = category.name,
        primaryMuscle = primaryMuscle,
        trackingType = trackingType.name,
        notes = notes,
        restTimeSeconds = restTimeSeconds
    )
}

@Serializable
data class WorkoutSetDto(
    val id: String,
    val setNumber: Int,
    val weight: Double,
    val reps: Int,
    val durationSeconds: Int,
    val distanceKm: Double,
    val isCompleted: Boolean
) {
    fun toDomain(): WorkoutSet {
        return WorkoutSet(
            id = id,
            setNumber = setNumber,
            weight = weight,
            reps = reps,
            durationSeconds = durationSeconds,
            distanceKm = distanceKm,
            isCompleted = isCompleted
        )
    }
}

fun WorkoutSet.toDto(): WorkoutSetDto {
    return WorkoutSetDto(
        id = id,
        setNumber = setNumber,
        weight = weight,
        reps = reps,
        durationSeconds = durationSeconds,
        distanceKm = distanceKm,
        isCompleted = isCompleted
    )
}

@Serializable
data class ExerciseSessionDto(
    val id: String,
    val exercise: ExerciseDto,
    val sets: List<WorkoutSetDto>
) {
    fun toDomain(): ExerciseSession {
        return ExerciseSession(
            id = id,
            exercise = exercise.toDomain(),
            sets = sets.map { it.toDomain() }
        )
    }
}

fun ExerciseSession.toDto(): ExerciseSessionDto {
    return ExerciseSessionDto(
        id = id,
        exercise = exercise.toDto(),
        sets = sets.map { it.toDto() }
    )
}

@Serializable
data class WorkoutSessionDto(
    val id: String,
    val startTime: Long,
    val endTime: Long?,
    val name: String,
    val exercises: List<ExerciseSessionDto>
) {
    fun toDomain(): WorkoutSession {
        return WorkoutSession(
            id = id,
            startTime = startTime,
            endTime = endTime,
            name = name,
            exercises = exercises.map { it.toDomain() }
        )
    }
}

fun WorkoutSession.toDto(): WorkoutSessionDto {
    return WorkoutSessionDto(
        id = id,
        startTime = startTime,
        endTime = endTime,
        name = name,
        exercises = exercises.map { it.toDto() }
    )
}

@Serializable
data class UserProfileDto(
    val bodyWeightKg: Double? = null,
    val weightUnit: String = WeightUnit.KG.name,
    val healthConnectSyncEnabled: Boolean = false,
    val bodyWeightUpdatedAt: Long? = null,
    val bodyWeightSource: String = BodyWeightSource.MANUAL.name
) {
    fun toDomain(): UserProfile {
        return UserProfile(
            bodyWeightKg = bodyWeightKg,
            // Tolerant parsing: an unknown enum string (edited file, future version) falls back to
            // the default instead of failing the whole load and triggering the corrupt-file path.
            weightUnit = WeightUnit.entries.firstOrNull { it.name == weightUnit } ?: WeightUnit.KG,
            healthConnectSyncEnabled = healthConnectSyncEnabled,
            bodyWeightUpdatedAt = bodyWeightUpdatedAt,
            bodyWeightSource = BodyWeightSource.entries.firstOrNull { it.name == bodyWeightSource }
                ?: BodyWeightSource.MANUAL
        )
    }
}

fun UserProfile.toDto(): UserProfileDto {
    return UserProfileDto(
        bodyWeightKg = bodyWeightKg,
        weightUnit = weightUnit.name,
        healthConnectSyncEnabled = healthConnectSyncEnabled,
        bodyWeightUpdatedAt = bodyWeightUpdatedAt,
        bodyWeightSource = bodyWeightSource.name
    )
}

/**
 * Bumped whenever the persisted schema changes in a way that needs a migration. [toDto] writes it
 * into every file; files written before versioning existed deserialize as 0, so a future migration
 * can tell them apart. The field default is 0 (not this constant) on purpose: kotlinx.serialization
 * omits values equal to the default, so defaulting to 0 while writing this constant guarantees the
 * version is actually serialised.
 *
 * Still 1: userProfile was added as an optional field with a default — old files deserialize
 * without migration, and older app versions ignore the new field (ignoreUnknownKeys).
 */
const val CURRENT_SCHEMA_VERSION = 1

@Serializable
data class WorkoutStateDto(
    val schemaVersion: Int = 0,
    val exercises: List<ExerciseDto>,
    val sessions: List<WorkoutSessionDto>,
    // Defaulted so existing on-disk files (written before this field existed) still deserialize.
    val activeDraft: WorkoutSessionDto? = null,
    val userProfile: UserProfileDto? = null
) {
    fun toDomain(): WorkoutState {
        return WorkoutState(
            exercises = exercises.map { it.toDomain() },
            sessions = sessions.map { it.toDomain() },
            activeDraft = activeDraft?.toDomain(),
            userProfile = userProfile?.toDomain() ?: UserProfile()
        )
    }
}

fun WorkoutState.toDto(): WorkoutStateDto {
    return WorkoutStateDto(
        schemaVersion = CURRENT_SCHEMA_VERSION,
        exercises = exercises.map { it.toDto() },
        sessions = sessions.map { it.toDto() },
        activeDraft = activeDraft?.toDto(),
        userProfile = userProfile.toDto()
    )
}
