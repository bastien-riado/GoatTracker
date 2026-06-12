package com.example.goattracker.domain

import com.example.goattracker.domain.model.ExerciseSession
import com.example.goattracker.domain.model.TrackingType
import com.example.goattracker.domain.model.WorkoutSession
import com.example.goattracker.domain.model.WorkoutSet

/**
 * Single source of truth for every metric computation. Pure Kotlin (no Android imports) so the
 * whole engine is unit-testable on the JVM.
 *
 * Two distinct metric domains, never mixed:
 *  - STRENGTH (WEIGHT_REPS, BODYWEIGHT_REPS): tonnage in kg. Bodyweight exercises use the user's
 *    body weight as the load; while it is unknown (null) they contribute 0 to tonnage and the
 *    progression metric falls back to rep count.
 *  - ENDURANCE (TIME, DISTANCE): seconds / kilometres. These NEVER enter a tonnage sum — the old
 *    `totalVolume` added seconds and metres to kilograms, which made every mixed-session total
 *    meaningless.
 */
object WorkoutMetrics {

    fun isStrength(type: TrackingType): Boolean =
        type == TrackingType.WEIGHT_REPS || type == TrackingType.BODYWEIGHT_REPS

    /**
     * Load actually moved on one set, in kg — the value every tonnage/1RM computation must use.
     * Null when the tracking type has no meaningful load (endurance) or the bodyweight is unknown.
     */
    fun effectiveLoadKg(type: TrackingType, set: WorkoutSet, bodyWeightKg: Double?): Double? =
        when (type) {
            TrackingType.WEIGHT_REPS -> set.weight
            TrackingType.BODYWEIGHT_REPS -> bodyWeightKg
            TrackingType.TIME, TrackingType.DISTANCE -> null
        }

    /** Tonnage (kg) of the completed sets of one exercise. 0 for endurance types. */
    fun strengthVolumeKg(exerciseSession: ExerciseSession, bodyWeightKg: Double?): Double {
        val type = exerciseSession.exercise.trackingType
        if (!isStrength(type)) return 0.0
        return exerciseSession.sets
            .filter { it.isCompleted }
            .sumOf { set -> (effectiveLoadKg(type, set, bodyWeightKg) ?: 0.0) * set.reps }
    }

    /** Tonnage (kg) of a whole session — strength exercises only, completed sets only. */
    fun sessionStrengthVolumeKg(session: WorkoutSession, bodyWeightKg: Double?): Double =
        session.exercises.sumOf { strengthVolumeKg(it, bodyWeightKg) }

    /**
     * Per-exercise progression value used by history charts. The unit depends on the type — charts
     * label it through [MetricFormatter.progressionPoint]:
     *  - WEIGHT_REPS: tonnage kg
     *  - BODYWEIGHT_REPS: tonnage kg when the body weight is known, else total reps
     *  - TIME: total seconds
     *  - DISTANCE: total km
     */
    fun progressionValue(exerciseSession: ExerciseSession, bodyWeightKg: Double?): Double {
        val completed = exerciseSession.sets.filter { it.isCompleted }
        return when (exerciseSession.exercise.trackingType) {
            TrackingType.WEIGHT_REPS -> strengthVolumeKg(exerciseSession, bodyWeightKg)
            TrackingType.BODYWEIGHT_REPS ->
                if (bodyWeightKg != null) strengthVolumeKg(exerciseSession, bodyWeightKg)
                else completed.sumOf { it.reps }.toDouble()
            TrackingType.TIME -> completed.sumOf { it.durationSeconds }.toDouble()
            TrackingType.DISTANCE -> completed.sumOf { it.distanceKm }
        }
    }

    /**
     * Rep records: for each weight ever lifted (completed sets), the best rep count achieved —
     * the heaviest [limit] tiers, descending. "8 reps @ 100 kg" is a record the e1RM hides; this
     * is the raw material for the exercise page's "records de répétitions".
     */
    fun repRecords(sets: List<WorkoutSet>, limit: Int = 3): List<Pair<Double, Int>> =
        sets.asSequence()
            .filter { it.isCompleted && it.weight > 0.0 && it.reps > 0 }
            .groupBy { it.weight }
            .map { (weight, group) -> weight to group.maxOf { it.reps } }
            .sortedByDescending { it.first }
            .take(limit)

    /** Average pace over one set, in seconds per km. Null when distance or duration is missing. */
    fun paceSecPerKm(durationSeconds: Int, distanceKm: Double): Double? =
        if (durationSeconds <= 0 || distanceKm <= 0.0) null
        else durationSeconds / distanceKm

    /** Average speed over one set, in km/h. Null when distance or duration is missing. */
    fun speedKmh(durationSeconds: Int, distanceKm: Double): Double? =
        if (durationSeconds <= 0 || distanceKm <= 0.0) null
        else distanceKm / (durationSeconds / 3600.0)
}
