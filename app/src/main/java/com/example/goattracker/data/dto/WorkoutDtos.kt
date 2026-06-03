package com.example.goattracker.data.dto

import com.example.goattracker.domain.model.Exercise
import com.example.goattracker.domain.model.ExerciseCategory
import com.example.goattracker.domain.model.ExerciseSession
import com.example.goattracker.domain.model.TrackingType
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
data class WorkoutStateDto(
    val exercises: List<ExerciseDto>,
    val sessions: List<WorkoutSessionDto>,
    // Defaulted so existing on-disk files (written before this field existed) still deserialize.
    val activeDraft: WorkoutSessionDto? = null
) {
    fun toDomain(): WorkoutState {
        return WorkoutState(
            exercises = exercises.map { it.toDomain() },
            sessions = sessions.map { it.toDomain() },
            activeDraft = activeDraft?.toDomain()
        )
    }
}

fun WorkoutState.toDto(): WorkoutStateDto {
    return WorkoutStateDto(
        exercises = exercises.map { it.toDto() },
        sessions = sessions.map { it.toDto() },
        activeDraft = activeDraft?.toDto()
    )
}
