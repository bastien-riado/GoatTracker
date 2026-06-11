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
    // Cardio: a set carries a distance AND an optional duration (pace/speed are derived) — the
    // enum NAME stays DISTANCE because it is persisted as a string in workouts.json.
    DISTANCE("Distance + Temps")
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
}

// Volume/tonnage and display strings deliberately do NOT live on these data classes: they depend on
// the user profile (body weight, unit) — see domain.WorkoutMetrics and domain.MetricFormatter.
data class WorkoutSession(
    val id: String = UUID.randomUUID().toString(),
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val name: String,
    val exercises: List<ExerciseSession> = emptyList()
)

data class WorkoutState(
    val exercises: List<Exercise> = emptyList(),
    val sessions: List<WorkoutSession> = emptyList(),
    // In-progress live session, persisted so it survives process death (audit P0-1).
    // Null when no session is active; cleared on save or discard.
    val activeDraft: WorkoutSession? = null,
    val userProfile: UserProfile = UserProfile()
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
