package com.example.goattracker.domain

import com.example.goattracker.domain.model.ExerciseSession
import com.example.goattracker.domain.model.TrackingType
import com.example.goattracker.domain.model.UserProfile
import com.example.goattracker.domain.model.WeightUnit
import com.example.goattracker.domain.model.WorkoutSet
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Single place that turns metric values into user-facing strings. Every screen goes through here so
 * units (kg/lbs), French number style and per-tracking-type wording stay consistent — the previous
 * code repeated `"${weight.toInt()}kg"`-style formatting in 7 places, truncating decimal loads
 * (22.5 kg displayed as "22") and hardcoding the unit.
 *
 * Weights/tonnage take values in KG and convert to the profile's [WeightUnit] for display.
 * Pure Kotlin: unit-testable on the JVM.
 */
object MetricFormatter {

    private val locale = Locale.FRENCH

    /** "72,5" / "80" — converted to [unit], one decimal max, trailing ",0" trimmed. */
    fun weightValue(kg: Double, unit: WeightUnit): String = trimDecimal(unit.fromKg(kg), 1)

    /** "72,5 kg" / "160 lbs". */
    fun weight(kg: Double, unit: WeightUnit): String = "${weightValue(kg, unit)} ${unit.suffix}"

    /**
     * Session/cumulative tonnage: kg switches to tonnes past 1000 ("2,40 t"); lbs stays in grouped
     * pounds (tonnes are not an imperial-lifting notion).
     */
    fun tonnage(kg: Double, unit: WeightUnit): String = when (unit) {
        WeightUnit.KG ->
            if (kg >= 1000.0) "${String.format(locale, "%.2f", kg / 1000.0)} t"
            else "${kg.roundToInt()} kg"
        WeightUnit.LBS -> {
            val lbs = unit.fromKg(kg).roundToInt()
            // Group via US locale (always a comma) then swap for a space: the French grouping
            // separator varies across JDK/CLDR versions (NBSP vs narrow NBSP), which is untestable.
            "${String.format(Locale.US, "%,d", lbs).replace(",", " ")} lbs"
        }
    }

    /** "45 s" under a minute, "12:30" under an hour, "1h05" beyond. */
    fun duration(totalSeconds: Int): String {
        val s = totalSeconds.coerceAtLeast(0)
        return when {
            s < 60 -> "$s s"
            s < 3600 -> String.format(locale, "%d:%02d", s / 60, s % 60)
            else -> String.format(locale, "%dh%02d", s / 3600, (s % 3600) / 60)
        }
    }

    /** "5,00 km", "800 m" under a kilometre. */
    fun distance(km: Double): String =
        if (km < 1.0) "${(km * 1000).roundToInt()} m"
        else "${String.format(locale, "%.2f", km)} km"

    /** "5:24 /km". */
    fun pace(secPerKm: Double): String {
        val total = secPerKm.roundToInt()
        return String.format(locale, "%d:%02d /km", total / 60, total % 60)
    }

    /** "11,1 km/h". */
    fun speed(kmh: Double): String = "${trimDecimal(kmh, 1)} km/h"

    /** Compact per-set line, e.g. history rows: "5 reps @ 100 kg", "12 reps (PDC)", "5,00 km • 25:00 • 5:00 /km". */
    fun setLineCompact(type: TrackingType, set: WorkoutSet, profile: UserProfile): String = when (type) {
        TrackingType.WEIGHT_REPS -> "${set.reps} reps @ ${weight(set.weight, profile.weightUnit)}"
        TrackingType.BODYWEIGHT_REPS -> "${set.reps} reps (PDC)"
        TrackingType.TIME -> duration(set.durationSeconds)
        TrackingType.DISTANCE -> cardioLine(set)
    }

    /** Verbose per-set line (last-workout panel): "5 répétitions à 100 kg", "12 répétitions au poids de corps". */
    fun setLineVerbose(type: TrackingType, set: WorkoutSet, profile: UserProfile): String = when (type) {
        TrackingType.WEIGHT_REPS -> "${set.reps} répétitions à ${weight(set.weight, profile.weightUnit)}"
        TrackingType.BODYWEIGHT_REPS -> "${set.reps} répétitions au poids de corps"
        TrackingType.TIME -> duration(set.durationSeconds)
        TrackingType.DISTANCE -> cardioLine(set)
    }

    /**
     * One-line summary of an exercise inside a finished session (sessions list). Mirrors the old
     * `volumeMetricDisplay`: best set for strength, totals for endurance.
     */
    fun exerciseSummary(exerciseSession: ExerciseSession, profile: UserProfile): String {
        val completed = exerciseSession.sets.filter { it.isCompleted }
        return when (exerciseSession.exercise.trackingType) {
            TrackingType.WEIGHT_REPS -> {
                val best = completed.maxByOrNull { it.weight }
                if (best != null && best.weight > 0) "${weight(best.weight, profile.weightUnit)} x ${best.reps}" else "—"
            }
            TrackingType.BODYWEIGHT_REPS -> {
                val maxReps = completed.maxOfOrNull { it.reps } ?: 0
                "PDC x $maxReps"
            }
            TrackingType.TIME -> duration(completed.sumOf { it.durationSeconds })
            TrackingType.DISTANCE -> {
                val totalKm = completed.sumOf { it.distanceKm }
                val totalSec = completed.sumOf { it.durationSeconds }
                val paceSec = WorkoutMetrics.paceSecPerKm(totalSec, totalKm)
                if (paceSec != null) "${distance(totalKm)} • ${pace(paceSec)}" else distance(totalKm)
            }
        }
    }

    /** "Dernier: …" line on exercise cards (main screen / exercise detail). */
    fun lastWorkoutLine(type: TrackingType, completedSets: List<WorkoutSet>, profile: UserProfile): String {
        if (completedSets.isEmpty()) return "Aucune série"
        return when (type) {
            TrackingType.WEIGHT_REPS -> {
                val sample = completedSets.first()
                "${completedSets.size}x${sample.reps} • ${weight(sample.weight, profile.weightUnit)}"
            }
            TrackingType.BODYWEIGHT_REPS -> {
                val sample = completedSets.first()
                "${completedSets.size}x${sample.reps} • PDC"
            }
            TrackingType.TIME -> duration(completedSets.sumOf { it.durationSeconds })
            TrackingType.DISTANCE -> {
                val totalKm = completedSets.sumOf { it.distanceKm }
                val totalSec = completedSets.sumOf { it.durationSeconds }
                val paceSec = WorkoutMetrics.paceSecPerKm(totalSec, totalKm)
                if (paceSec != null) "${distance(totalKm)} • ${pace(paceSec)}" else distance(totalKm)
            }
        }
    }

    /**
     * Label of one point on a per-exercise progression chart, matching
     * [WorkoutMetrics.progressionValue] semantics (kg / reps / seconds / km depending on type).
     */
    fun progressionPoint(type: TrackingType, value: Double, profile: UserProfile): String = when (type) {
        TrackingType.WEIGHT_REPS -> tonnage(value, profile.weightUnit)
        TrackingType.BODYWEIGHT_REPS ->
            if (profile.bodyWeightKg != null) tonnage(value, profile.weightUnit)
            else "${value.roundToInt()} reps"
        TrackingType.TIME -> duration(value.roundToInt())
        TrackingType.DISTANCE -> distance(value)
    }

    /** "5,00 km • 25:00 • 5:00 /km" — degrades gracefully when duration is missing. */
    private fun cardioLine(set: WorkoutSet): String {
        val paceSec = WorkoutMetrics.paceSecPerKm(set.durationSeconds, set.distanceKm)
        return buildString {
            append(distance(set.distanceKm))
            if (set.durationSeconds > 0) append(" • ").append(duration(set.durationSeconds))
            if (paceSec != null) append(" • ").append(pace(paceSec))
        }
    }

    private fun trimDecimal(value: Double, decimals: Int): String {
        val formatted = String.format(locale, "%.${decimals}f", value)
        return formatted.trimEnd('0').trimEnd(',', '.')
    }
}
