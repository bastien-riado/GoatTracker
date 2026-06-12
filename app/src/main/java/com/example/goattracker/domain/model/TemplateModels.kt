package com.example.goattracker.domain.model

import java.util.UUID

enum class TemplateTargetMode(val displayName: String) {
    REPS("Répétitions"),

    /** As many reps as possible: no rep target, the set is planned to be taken to failure. */
    AMRAP("Max (à l'échec)")
}

/**
 * One exercise slot of a workout template. References the catalog by id (the exercise definition
 * stays live — editing an exercise updates every template using it); list order in
 * [WorkoutTemplate.entries] is the execution order.
 */
data class TemplateEntry(
    val id: String = UUID.randomUUID().toString(),
    val exerciseId: String,
    val targetSets: Int = 3,
    val targetReps: Int? = 10,
    val targetWeightKg: Double? = null,
    val targetMode: TemplateTargetMode = TemplateTargetMode.REPS,
    /** Per-template rest override; null = use the exercise's own restTimeSeconds. */
    val restOverrideSeconds: Int? = null,
)

/** A reusable workout (e.g. "Push") the user launches pre-filled sessions from. */
data class WorkoutTemplate(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val notes: String = "",
    val entries: List<TemplateEntry> = emptyList(),
)

/**
 * Instantiates the template as the pre-filled draft session for "lancer une séance" — the launch
 * path then persists it as the active draft and the live screen resumes it like any other draft.
 *
 * - Target sets are pre-created INCOMPLETE with the target weight/reps filled in, so the user
 *   checks sets off (adjusting values where reality differed) instead of building the session.
 * - AMRAP slots map to sets flagged [WorkoutSet.isToFailure] with no rep target.
 * - A rest override rides the session's embedded exercise copy ([Exercise.restTimeSeconds]), so
 *   the live rest timer honors it without knowing templates exist.
 * - Slots whose exercise cannot be resolved (defensive: RESTRICT keys + archiving should make
 *   that impossible) are skipped rather than failing the launch.
 * - [WorkoutSession.templateId] links the session back for future adherence stats.
 */
fun WorkoutTemplate.toDraftSession(
    resolveExercise: (String) -> Exercise?,
    now: Long = System.currentTimeMillis(),
): WorkoutSession {
    val exerciseSessions = entries.mapNotNull { entry ->
        val exercise = resolveExercise(entry.exerciseId) ?: return@mapNotNull null
        val effective = entry.restOverrideSeconds
            ?.let { exercise.copy(restTimeSeconds = it) } ?: exercise
        val isAmrap = entry.targetMode == TemplateTargetMode.AMRAP
        ExerciseSession(
            exercise = effective,
            sets = (1..entry.targetSets.coerceAtLeast(1)).map { number ->
                WorkoutSet(
                    setNumber = number,
                    weight = entry.targetWeightKg ?: 0.0,
                    reps = if (isAmrap) 0 else entry.targetReps ?: 0,
                    isCompleted = false,
                    isToFailure = isAmrap,
                )
            },
        )
    }
    return WorkoutSession(
        name = name,
        startTime = now,
        exercises = exerciseSessions,
        templateId = id,
    )
}
