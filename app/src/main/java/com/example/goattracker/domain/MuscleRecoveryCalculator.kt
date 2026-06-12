package com.example.goattracker.domain

import com.example.goattracker.domain.model.MuscleGroup
import com.example.goattracker.domain.model.WorkoutSession

/**
 * Recovery snapshot for one [MuscleGroup].
 *
 * @property recovery 0f = just trained (fatigued / needs rest) … 1f = fully recovered (ready for a
 *   new session). [Float.NaN] when the muscle has no training history (→ render neutral, not green).
 */
data class MuscleStatus(
    val group: MuscleGroup,
    val lastWorkedAt: Long?,
    val recovery: Float,
    val recentSets: Int,
) {
    val hasData: Boolean get() = lastWorkedAt != null && !recovery.isNaN()
}

/**
 * Pure, deterministic recovery model — no Android dependencies, so it is unit-testable.
 *
 * v2 dose model. Each exercise trains its PRIMARY muscle at full intensity (1.0) and its
 * SECONDARY muscles at [SECONDARY_CONTRIBUTION]. For each muscle group, the most recent completed
 * session that trained it defines:
 *  - `weightedSets` = Σ completedSets × contribution (a secondary stimulus counts half),
 *  - `intensity`    = the strongest contribution that day (0.5 when only hit as a secondary).
 *
 * The recovery window is `base × intensity + hoursPerSet × weightedSets`, where `base` is the
 * engine default OR the user's per-muscle override (everyone recovers differently). A muscle hit
 * only as a secondary therefore recovers ~2× faster than a primary session — and with no
 * secondaries and no overrides the math reduces exactly to the v1 model.
 *
 * The mapping from free-form muscle strings to [MuscleGroup] is delegated to [MuscleGroupMapper].
 */
class MuscleRecoveryCalculator(
    private val mapper: MuscleGroupMapper = MuscleGroupMapper,
    private val baseRecoveryHours: Float = 48f,
    private val hoursPerSet: Float = 4f,
    private val maxRecoveryHours: Float = 96f,
) {
    fun compute(
        sessions: List<WorkoutSession>,
        now: Long,
        recoveryHoursOverrides: Map<MuscleGroup, Int> = emptyMap(),
    ): Map<MuscleGroup, MuscleStatus> {
        // Most recent completed session per group, with the dose that trained it.
        val lastEnd = HashMap<MuscleGroup, Long>()
        val doseAtLast = HashMap<MuscleGroup, Float>()
        val intensityAtLast = HashMap<MuscleGroup, Float>()

        for (session in sessions) {
            val end = session.endTime ?: continue // only finished sessions count toward recovery
            val dosePerGroup = HashMap<MuscleGroup, Float>()
            val intensityPerGroup = HashMap<MuscleGroup, Float>()
            for (es in session.exercises) {
                val completed = es.sets.count { it.isCompleted }
                if (completed == 0) continue
                for ((group, contribution) in contributionsOf(es.exercise.primaryMuscle, es.exercise.secondaryMuscles)) {
                    dosePerGroup[group] = (dosePerGroup[group] ?: 0f) + completed * contribution
                    intensityPerGroup[group] = maxOf(intensityPerGroup[group] ?: 0f, contribution)
                }
            }
            for ((group, dose) in dosePerGroup) {
                if (end >= (lastEnd[group] ?: Long.MIN_VALUE)) {
                    lastEnd[group] = end
                    doseAtLast[group] = dose
                    intensityAtLast[group] = intensityPerGroup[group] ?: 1f
                }
            }
        }

        return MuscleGroup.entries.associateWith { group ->
            val end = lastEnd[group]
            if (end == null) {
                MuscleStatus(group, lastWorkedAt = null, recovery = Float.NaN, recentSets = 0)
            } else {
                val dose = doseAtLast[group] ?: 0f
                val intensity = intensityAtLast[group] ?: 1f
                val base = recoveryHoursOverrides[group]?.toFloat() ?: baseRecoveryHours
                // The cap grows with a user override: someone declaring 96 h of base recovery must
                // not have the volume term silently clipped to nothing.
                val cap = maxOf(maxRecoveryHours, base * 2f)
                val windowHours = (base * intensity + hoursPerSet * dose).coerceAtMost(cap)
                val hoursSince = (now - end).coerceAtLeast(0L) / 3_600_000f
                val recovery = (hoursSince / windowHours).coerceIn(0f, 1f)
                MuscleStatus(
                    group,
                    lastWorkedAt = end,
                    recovery = recovery,
                    recentSets = kotlin.math.ceil(dose).toInt(),
                )
            }
        }
    }

    /** Per-group contribution of one exercise; a string mapping to the primary's group is ignored. */
    private fun contributionsOf(primary: String, secondaries: List<String>): Map<MuscleGroup, Float> {
        val result = HashMap<MuscleGroup, Float>()
        mapper.map(primary)?.let { result[it] = 1f }
        for (secondary in secondaries) {
            val group = mapper.map(secondary) ?: continue
            result[group] = maxOf(result[group] ?: 0f, SECONDARY_CONTRIBUTION)
        }
        return result
    }

    companion object {
        /** Weight of a secondary muscle relative to the primary, v1 of the dose model. */
        const val SECONDARY_CONTRIBUTION = 0.5f
    }
}
