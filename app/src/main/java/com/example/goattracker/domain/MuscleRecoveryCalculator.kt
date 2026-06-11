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
 * For each muscle it finds the most recent *completed* session that trained it, then estimates how
 * far along recovery is as `elapsed / window`, where the window grows with the session's volume
 * (more sets → longer to recover). The mapping from free-form exercise muscles to [MuscleGroup] is
 * delegated to [MuscleGroupMapper].
 */
class MuscleRecoveryCalculator(
    private val mapper: MuscleGroupMapper = MuscleGroupMapper,
    private val baseRecoveryHours: Float = 48f,
    private val hoursPerSet: Float = 4f,
    private val maxRecoveryHours: Float = 96f,
) {
    fun compute(sessions: List<WorkoutSession>, now: Long): Map<MuscleGroup, MuscleStatus> {
        // Most recent completed session per group, with the completed-set count that trained it.
        val lastEnd = HashMap<MuscleGroup, Long>()
        val setsAtLast = HashMap<MuscleGroup, Int>()

        for (session in sessions) {
            val end = session.endTime ?: continue // only finished sessions count toward recovery
            val setsPerGroup = HashMap<MuscleGroup, Int>()
            for (es in session.exercises) {
                val group = mapper.map(es.exercise.primaryMuscle) ?: continue
                val completed = es.sets.count { it.isCompleted }
                if (completed > 0) setsPerGroup[group] = (setsPerGroup[group] ?: 0) + completed
            }
            for ((group, sets) in setsPerGroup) {
                if (end >= (lastEnd[group] ?: Long.MIN_VALUE)) {
                    lastEnd[group] = end
                    setsAtLast[group] = sets
                }
            }
        }

        return MuscleGroup.entries.associateWith { group ->
            val end = lastEnd[group]
            if (end == null) {
                MuscleStatus(group, lastWorkedAt = null, recovery = Float.NaN, recentSets = 0)
            } else {
                val sets = setsAtLast[group] ?: 0
                val windowHours = (baseRecoveryHours + hoursPerSet * sets).coerceAtMost(maxRecoveryHours)
                val hoursSince = (now - end).coerceAtLeast(0L) / 3_600_000f
                val recovery = (hoursSince / windowHours).coerceIn(0f, 1f)
                MuscleStatus(group, lastWorkedAt = end, recovery = recovery, recentSets = sets)
            }
        }
    }
}
