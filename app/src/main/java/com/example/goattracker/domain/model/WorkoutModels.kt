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
    val restTimeSeconds: Int = 90,
    /**
     * Muscles also worked, beyond [primaryMuscle] (same free-form vocabulary). They weigh half a
     * primary in the recovery engine (see MuscleRecoveryCalculator); the DB schema stores
     * arbitrary per-muscle contributions for a finer future model.
     */
    val secondaryMuscles: List<String> = emptyList()
)

enum class SetType(val displayName: String) {
    WARMUP("Échauffement"),
    WORKING("Travail"),
    /** One weight plateau of a drop set — chained plateaus share a [WorkoutSet.dropGroupId]. */
    DROP("Dégressive")
}

data class WorkoutSet(
    val id: String = UUID.randomUUID().toString(),
    val setNumber: Int,
    val weight: Double = 0.0,
    val reps: Int = 0,
    val durationSeconds: Int = 0,
    val distanceKm: Double = 0.0,
    val isCompleted: Boolean = false,
    // Stats-oriented fields, persisted since the Room migration. No UI writes them yet (each lands
    // with its feature); they default so existing call sites and tests stay untouched.
    /** When the set was checked off — rest-time/density stats. Null until the capture feature. */
    val completedAt: Long? = null,
    /** Per-set perceived effort (1–10), optional. */
    val rpe: Double? = null,
    val setType: SetType = SetType.WORKING,
    /** Orthogonal to [setType]: a plain working set can also be taken to failure. */
    val isToFailure: Boolean = false,
    val dropGroupId: String? = null
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
    val exercises: List<ExerciseSession> = emptyList(),
    // Stats-oriented fields, persisted since the Room migration; defaulted, no UI writes them yet.
    val notes: String = "",
    /** Body weight at session time (kg). Sessions predating the capture have null. */
    val bodyWeightKg: Double? = null,
    /** Session-level perceived effort (1–10), input of the sRPE training-load model. */
    val sessionRpe: Double? = null,
    /** The workout template this session was launched from, when any (PPL feature). */
    val templateId: String? = null
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
