package com.example.goattracker.domain

import com.example.goattracker.domain.model.TrackingType
import com.example.goattracker.domain.model.WorkoutSession
import java.util.Calendar
import java.util.Locale

/**
 * Training-cadence and lifetime aggregates for the profile screen. Pure; "now" is injected so the
 * week math is testable. Weeks are ISO-style Monday-based (gym weeks), in the device timezone.
 */
data class ProfileCadence(
    val sessionsThisWeek: Int,
    /** Average sessions/week over the last 4 full-or-current weeks (current week included). */
    val averagePerWeekLast4: Double,
    /** Consecutive weeks (current included) with at least one session. */
    val streakWeeks: Int,
    val totalDurationSeconds: Long,
    val totalDistanceKm: Double,
)

object ProfileInsights {

    fun cadence(sessions: List<WorkoutSession>, now: Long): ProfileCadence {
        val weekStarts = sessions.map { weekStartMillis(it.startTime) }.toSet()
        val currentWeekStart = weekStartMillis(now)

        val sessionsThisWeek = sessions.count { weekStartMillis(it.startTime) == currentWeekStart }

        val last4Starts = (0 until 4).map { offset -> shiftWeeks(currentWeekStart, -offset) }
        val last4Count = sessions.count { weekStartMillis(it.startTime) in last4Starts.toSet() }

        var streak = 0
        var cursor = currentWeekStart
        // An empty CURRENT week does not break a streak (the week isn't over) — start counting
        // from the current week if trained, else from the previous one.
        if (cursor !in weekStarts) cursor = shiftWeeks(cursor, -1)
        while (cursor in weekStarts) {
            streak++
            cursor = shiftWeeks(cursor, -1)
        }

        val cardio = sessions.asSequence()
            .flatMap { it.exercises }
            .filter { it.exercise.trackingType == TrackingType.DISTANCE || it.exercise.trackingType == TrackingType.TIME }
            .flatMap { it.sets }
            .filter { it.isCompleted }
            .toList()

        return ProfileCadence(
            sessionsThisWeek = sessionsThisWeek,
            averagePerWeekLast4 = last4Count / 4.0,
            streakWeeks = streak,
            totalDurationSeconds = sessions.sumOf { s ->
                s.endTime?.let { ((it - s.startTime) / 1000L).coerceAtLeast(0L) } ?: 0L
            },
            totalDistanceKm = cardio.sumOf { it.distanceKm },
        )
    }

    private fun weekStartMillis(timestamp: Long): Long {
        val cal = Calendar.getInstance(Locale.FRANCE).apply {
            timeInMillis = timestamp
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    private fun shiftWeeks(weekStart: Long, weeks: Int): Long {
        val cal = Calendar.getInstance(Locale.FRANCE).apply {
            timeInMillis = weekStart
            add(Calendar.WEEK_OF_YEAR, weeks)
        }
        return cal.timeInMillis
    }
}
