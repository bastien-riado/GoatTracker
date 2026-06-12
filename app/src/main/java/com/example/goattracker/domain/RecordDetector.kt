package com.example.goattracker.domain

import com.example.goattracker.domain.model.TrackingType
import com.example.goattracker.domain.model.WorkoutSession
import com.example.goattracker.domain.model.WorkoutSet

enum class RecordKind(val displayName: String) {
    MAX_WEIGHT("Charge max"),
    EST_ONE_RM("1RM estimé"),
    MAX_REPS("Reps max"),
    MAX_DISTANCE("Distance max"),
    MAX_DURATION("Durée max"),
    BEST_PACE("Meilleure allure"),
    SESSION_VOLUME("Volume de séance"),
}

/**
 * One beaten record. Values are in the kind's natural unit (kg, reps, km, seconds, sec/km, kg of
 * tonnage) — display formatting goes through MetricFormatter at the UI layer.
 */
data class PersonalRecord(
    val kind: RecordKind,
    /** Null for session-level records ([RecordKind.SESSION_VOLUME]). */
    val exerciseName: String?,
    val value: Double,
    val previousBest: Double,
)

/**
 * Detects the records a finished session just beat, by comparing it against the PRIOR history
 * only (sessions started before it, itself excluded). Recomputed on the fly — no persisted
 * record table, so editing or deleting history can never leave stale trophies behind.
 *
 * Anti-noise rule: a record needs a previous best to beat. The first time an exercise is ever
 * performed, everything would technically be a "record" — that run stays quiet.
 *
 * e1RM detection uses Epley as the fixed reference (the user-facing formula switch on the
 * exercise page is a display choice; detection must not flip records when the user flips formula).
 */
object RecordDetector {

    fun detect(
        session: WorkoutSession,
        allSessions: List<WorkoutSession>,
        bodyWeightKg: Double?,
    ): List<PersonalRecord> {
        val prior = allSessions.filter { it.id != session.id && it.startTime < session.startTime }
        val records = mutableListOf<PersonalRecord>()

        session.exercises
            .groupBy { it.exercise.id }
            .forEach { (exerciseId, exerciseSessions) ->
                val exercise = exerciseSessions.first().exercise
                val current = exerciseSessions.flatMap { it.sets }.filter { it.isCompleted }
                if (current.isEmpty()) return@forEach
                val previous = prior.flatMap { s ->
                    s.exercises.filter { it.exercise.id == exerciseId }
                        .flatMap { it.sets }
                        .filter { it.isCompleted }
                }
                if (previous.isEmpty()) return@forEach // first ever: nothing to beat

                records += when (exercise.trackingType) {
                    TrackingType.WEIGHT_REPS -> weightRecords(exercise.name, current, previous)
                    TrackingType.BODYWEIGHT_REPS -> repsRecord(exercise.name, current, previous)
                    TrackingType.TIME -> durationRecord(exercise.name, current, previous)
                    TrackingType.DISTANCE -> cardioRecords(exercise.name, current, previous)
                }
            }

        sessionVolumeRecord(session, prior, bodyWeightKg)?.let { records += it }
        return records
    }

    private fun weightRecords(name: String, current: List<WorkoutSet>, previous: List<WorkoutSet>): List<PersonalRecord> {
        val records = mutableListOf<PersonalRecord>()
        val prevMaxWeight = previous.maxOf { it.weight }
        val curMaxWeight = current.maxOf { it.weight }
        if (prevMaxWeight > 0 && curMaxWeight > prevMaxWeight) {
            records += PersonalRecord(RecordKind.MAX_WEIGHT, name, curMaxWeight, prevMaxWeight)
        }
        val epley = OneRepMaxFormula.EPLEY.strategy
        val prevBest1Rm = previous.filter { it.weight > 0 && it.reps > 0 }
            .maxOfOrNull { epley.calculate(it.weight, it.reps) } ?: 0.0
        val curBest1Rm = current.filter { it.weight > 0 && it.reps > 0 }
            .maxOfOrNull { epley.calculate(it.weight, it.reps) } ?: 0.0
        if (prevBest1Rm > 0 && curBest1Rm > prevBest1Rm) {
            records += PersonalRecord(RecordKind.EST_ONE_RM, name, curBest1Rm, prevBest1Rm)
        }
        return records
    }

    private fun repsRecord(name: String, current: List<WorkoutSet>, previous: List<WorkoutSet>): List<PersonalRecord> {
        val prev = previous.maxOf { it.reps }
        val cur = current.maxOf { it.reps }
        return if (prev > 0 && cur > prev) {
            listOf(PersonalRecord(RecordKind.MAX_REPS, name, cur.toDouble(), prev.toDouble()))
        } else emptyList()
    }

    private fun durationRecord(name: String, current: List<WorkoutSet>, previous: List<WorkoutSet>): List<PersonalRecord> {
        val prev = previous.maxOf { it.durationSeconds }
        val cur = current.maxOf { it.durationSeconds }
        return if (prev > 0 && cur > prev) {
            listOf(PersonalRecord(RecordKind.MAX_DURATION, name, cur.toDouble(), prev.toDouble()))
        } else emptyList()
    }

    private fun cardioRecords(name: String, current: List<WorkoutSet>, previous: List<WorkoutSet>): List<PersonalRecord> {
        val records = mutableListOf<PersonalRecord>()
        val prevDistance = previous.maxOf { it.distanceKm }
        val curDistance = current.maxOf { it.distanceKm }
        if (prevDistance > 0 && curDistance > prevDistance) {
            records += PersonalRecord(RecordKind.MAX_DISTANCE, name, curDistance, prevDistance)
        }
        // Pace: lower is better.
        val prevPace = previous.mapNotNull { WorkoutMetrics.paceSecPerKm(it.durationSeconds, it.distanceKm) }
            .minOrNull()
        val curPace = current.mapNotNull { WorkoutMetrics.paceSecPerKm(it.durationSeconds, it.distanceKm) }
            .minOrNull()
        if (prevPace != null && curPace != null && curPace < prevPace) {
            records += PersonalRecord(RecordKind.BEST_PACE, name, curPace, prevPace)
        }
        return records
    }

    private fun sessionVolumeRecord(
        session: WorkoutSession,
        prior: List<WorkoutSession>,
        bodyWeightKg: Double?,
    ): PersonalRecord? {
        if (prior.isEmpty()) return null
        val current = WorkoutMetrics.sessionStrengthVolumeKg(session, bodyWeightKg)
        val previousBest = prior.maxOf { WorkoutMetrics.sessionStrengthVolumeKg(it, bodyWeightKg) }
        return if (previousBest > 0 && current > previousBest) {
            PersonalRecord(RecordKind.SESSION_VOLUME, null, current, previousBest)
        } else null
    }
}
