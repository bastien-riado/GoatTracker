package com.example.goattracker.domain.model

import java.util.UUID

enum class ExerciseCategory(val displayName: String) {
    PUSH("Push"),
    PULL("Pull"),
    LEG("Leg"),
    CORE("Core"),
    CARDIO("Cardio")
}

enum class TrackingType(val displayName: String) {
    WEIGHT_REPS("Poids + Répétitions"),
    BODYWEIGHT_REPS("Poids de corps + Répétitions"),
    TIME("Temps"),
    DISTANCE("Distance")
}

data class Exercise(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val category: ExerciseCategory,
    val primaryMuscle: String,
    val trackingType: TrackingType,
    val notes: String = "",
    val restTimeSeconds: Int = 90
)

data class WorkoutSet(
    val id: String = UUID.randomUUID().toString(),
    val setNumber: Int,
    val weight: Double = 0.0,
    val reps: Int = 0,
    val durationSeconds: Int = 0,
    val distanceKm: Double = 0.0,
    val isCompleted: Boolean = false
)

data class ExerciseSession(
    val id: String = UUID.randomUUID().toString(),
    val exercise: Exercise,
    val sets: List<WorkoutSet> = emptyList()
) {
    val completedSetsCount: Int
        get() = sets.count { it.isCompleted }

    val volumeMetricDisplay: String
        get() = when (exercise.trackingType) {
            TrackingType.WEIGHT_REPS -> {
                val maxWeight = sets.filter { it.isCompleted }.maxOfOrNull { it.weight } ?: 0.0
                val repsForMax = sets.firstOrNull { it.isCompleted && it.weight == maxWeight }?.reps ?: 0
                if (maxWeight > 0) "${maxWeight.toInt()}kg x $repsForMax" else "PDC"
            }
            TrackingType.BODYWEIGHT_REPS -> {
                val maxReps = sets.filter { it.isCompleted }.maxOfOrNull { it.reps } ?: 0
                "PDC x $maxReps"
            }
            TrackingType.TIME -> {
                val totalSeconds = sets.filter { it.isCompleted }.sumOf { it.durationSeconds }
                val minutes = totalSeconds / 60
                val seconds = totalSeconds % 60
                String.format("%02d:%02d", minutes, seconds)
            }
            TrackingType.DISTANCE -> {
                val totalDistance = sets.filter { it.isCompleted }.sumOf { it.distanceKm }
                String.format("%.2f km", totalDistance)
            }
        }
}

data class WorkoutSession(
    val id: String = UUID.randomUUID().toString(),
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val name: String,
    val exercises: List<ExerciseSession> = emptyList()
) {
    val totalVolume: Double
        get() = exercises.sumOf { exerciseSession ->
            exerciseSession.sets
                .filter { it.isCompleted }
                .sumOf { set ->
                    when (exerciseSession.exercise.trackingType) {
                        TrackingType.WEIGHT_REPS -> set.weight * set.reps
                        TrackingType.BODYWEIGHT_REPS -> set.reps.toDouble() // Treat each rep as 1 unit of volume
                        TrackingType.TIME -> set.durationSeconds.toDouble() // Treat seconds as volume units
                        TrackingType.DISTANCE -> set.distanceKm * 1000.0 // Treat meters as volume units
                    }
                }
        }
}

data class WorkoutState(
    val exercises: List<Exercise> = emptyList(),
    val sessions: List<WorkoutSession> = emptyList(),
    // In-progress live session, persisted so it survives process death (audit P0-1).
    // Null when no session is active; cleared on save or discard.
    val activeDraft: WorkoutSession? = null
)

/**
 * Turn an in-progress live session into the session to persist on "Terminer", or null when there is
 * nothing worth saving (no completed set). Drops incomplete sets and exercises with no completed set
 * so the saved log stays clean, and stamps [now] as the end time.
 *
 * Pure and side-effect free so the SAME finish rule is shared by the in-screen ViewModel and the
 * out-of-screen ActiveSessionController (the notification "Terminer" action) — one transform, so the
 * two finish paths can never drift apart.
 */
fun WorkoutSession.toFinishedOrNull(now: Long): WorkoutSession? {
    val completedExercises = exercises
        .map { it.copy(sets = it.sets.filter { set -> set.isCompleted }) }
        .filter { it.sets.isNotEmpty() }
    return if (completedExercises.isEmpty()) null
    else copy(endTime = now, exercises = completedExercises)
}
