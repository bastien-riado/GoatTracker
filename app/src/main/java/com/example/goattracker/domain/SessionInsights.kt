package com.example.goattracker.domain

import com.example.goattracker.domain.model.ExerciseSession
import com.example.goattracker.domain.model.TrackingType
import com.example.goattracker.domain.model.WorkoutSession

/**
 * Everything the session recap screen displays, computed once from a session (and its history for
 * the comparison). Pure — same testability rule as [WorkoutMetrics], which provides the underlying
 * per-set/per-exercise math.
 */
data class SessionSummary(
    val durationSeconds: Int?,
    val strengthVolumeKg: Double,
    val completedSets: Int,
    val exerciseCount: Int,
    val totalDistanceKm: Double,
    val totalCardioSeconds: Int,
    /** Completed sets per muscle (primary muscle of each exercise), insertion-ordered by volume. */
    val setsPerMuscle: Map<String, Int>,
    /** Per exercise: completed-set count and strength volume (kg), in session order. */
    val perExercise: List<ExerciseBreakdown>,
    /**
     * Volume delta vs the previous comparable session (same templateId when set, else same name),
     * as a ratio (+0.12 = +12%). Null when there is no comparable previous session or either
     * volume is zero (cardio-only sessions compare to nothing meaningful).
     */
    val volumeDeltaVsPrevious: Double?,
)

data class ExerciseBreakdown(
    val exerciseSession: ExerciseSession,
    val completedSets: Int,
    val strengthVolumeKg: Double,
)

object SessionInsights {

    fun summarize(
        session: WorkoutSession,
        allSessions: List<WorkoutSession>,
        bodyWeightKg: Double?,
    ): SessionSummary {
        val completed = session.exercises.flatMap { it.sets }.filter { it.isCompleted }
        val volume = WorkoutMetrics.sessionStrengthVolumeKg(session, bodyWeightKg)

        val setsPerMuscle = linkedMapOf<String, Int>()
        session.exercises.forEach { es ->
            val muscle = es.exercise.primaryMuscle.ifBlank { "Autre" }
            val sets = es.sets.count { it.isCompleted }
            if (sets > 0) setsPerMuscle[muscle] = (setsPerMuscle[muscle] ?: 0) + sets
        }

        val cardioSets = session.exercises
            .filter { it.exercise.trackingType == TrackingType.DISTANCE || it.exercise.trackingType == TrackingType.TIME }
            .flatMap { it.sets }
            .filter { it.isCompleted }

        return SessionSummary(
            durationSeconds = session.endTime?.let { ((it - session.startTime) / 1000L).toInt().coerceAtLeast(0) },
            strengthVolumeKg = volume,
            completedSets = completed.size,
            exerciseCount = session.exercises.count { es -> es.sets.any { it.isCompleted } },
            totalDistanceKm = cardioSets.sumOf { it.distanceKm },
            totalCardioSeconds = cardioSets.sumOf { it.durationSeconds },
            setsPerMuscle = setsPerMuscle,
            perExercise = session.exercises.map { es ->
                ExerciseBreakdown(
                    exerciseSession = es,
                    completedSets = es.sets.count { it.isCompleted },
                    strengthVolumeKg = WorkoutMetrics.strengthVolumeKg(es, bodyWeightKg),
                )
            },
            volumeDeltaVsPrevious = volumeDelta(session, allSessions, volume, bodyWeightKg),
        )
    }

    private fun volumeDelta(
        session: WorkoutSession,
        allSessions: List<WorkoutSession>,
        volume: Double,
        bodyWeightKg: Double?,
    ): Double? {
        val previous = allSessions
            .filter { it.id != session.id && it.startTime < session.startTime }
            .filter {
                if (session.templateId != null) it.templateId == session.templateId
                else it.name == session.name
            }
            .maxByOrNull { it.startTime } ?: return null
        val previousVolume = WorkoutMetrics.sessionStrengthVolumeKg(previous, bodyWeightKg)
        if (previousVolume <= 0.0 || volume <= 0.0) return null
        return (volume - previousVolume) / previousVolume
    }
}
