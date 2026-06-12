package com.example.goattracker.domain

import com.example.goattracker.domain.model.Exercise
import com.example.goattracker.domain.model.ExerciseCategory
import com.example.goattracker.domain.model.ExerciseSession
import com.example.goattracker.domain.model.TrackingType
import com.example.goattracker.domain.model.WorkoutSession
import com.example.goattracker.domain.model.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.Locale

class ProfileInsightsTest {

    // A fixed "now": Wednesday 2026-06-10 12:00 local time.
    private val now: Long = Calendar.getInstance(Locale.FRANCE).apply {
        set(2026, Calendar.JUNE, 10, 12, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun daysAgo(days: Int): Long = now - days * 24L * 3_600_000L

    private fun session(startTime: Long, durationMin: Int = 60) = WorkoutSession(
        startTime = startTime,
        endTime = startTime + durationMin * 60_000L,
        name = "Séance",
    )

    @Test
    fun cadence_countsThisWeek_average_andTotals() {
        val sessions = listOf(
            session(daysAgo(0)),   // this week (wednesday)
            session(daysAgo(2)),   // this week (monday)
            session(daysAgo(7)),   // last week
            session(daysAgo(14)),  // two weeks ago
            session(daysAgo(21)),  // three weeks ago
        )

        val cadence = ProfileInsights.cadence(sessions, now)

        assertEquals(2, cadence.sessionsThisWeek)
        assertEquals(5 / 4.0, cadence.averagePerWeekLast4, 0.0001)
        assertEquals(4, cadence.streakWeeks)
        assertEquals(5L * 3_600L, cadence.totalDurationSeconds)
    }

    @Test
    fun streak_brokenByAnEmptyPastWeek_butNotByTheCurrentOne() {
        // Nothing yet this week; trained last week and the week before; gap 3 weeks ago.
        val sessions = listOf(
            session(daysAgo(7)),
            session(daysAgo(14)),
            session(daysAgo(28)),
        )

        val cadence = ProfileInsights.cadence(sessions, now)

        assertEquals(0, cadence.sessionsThisWeek)
        assertEquals(2, cadence.streakWeeks) // current empty week doesn't break it
    }

    @Test
    fun totalDistance_sumsCompletedCardioSets() {
        val run = Exercise(
            name = "Course", category = ExerciseCategory.CARDIO,
            primaryMuscle = "Cardio", trackingType = TrackingType.DISTANCE,
        )
        val sessions = listOf(
            session(daysAgo(1)).copy(
                exercises = listOf(
                    ExerciseSession(
                        exercise = run,
                        sets = listOf(
                            WorkoutSet(setNumber = 1, distanceKm = 5.0, isCompleted = true),
                            WorkoutSet(setNumber = 2, distanceKm = 3.0, isCompleted = false),
                        ),
                    )
                )
            ),
        )

        assertEquals(5.0, ProfileInsights.cadence(sessions, now).totalDistanceKm, 0.001)
    }

    @Test
    fun repRecords_bestRepsPerWeightTier_heaviestFirst() {
        val sets = listOf(
            WorkoutSet(setNumber = 1, weight = 100.0, reps = 5, isCompleted = true),
            WorkoutSet(setNumber = 2, weight = 100.0, reps = 8, isCompleted = true),
            WorkoutSet(setNumber = 3, weight = 90.0, reps = 10, isCompleted = true),
            WorkoutSet(setNumber = 4, weight = 110.0, reps = 2, isCompleted = false), // ignored
            WorkoutSet(setNumber = 5, weight = 80.0, reps = 12, isCompleted = true),
            WorkoutSet(setNumber = 6, weight = 70.0, reps = 15, isCompleted = true),
        )

        val records = WorkoutMetrics.repRecords(sets, limit = 3)

        assertEquals(listOf(100.0 to 8, 90.0 to 10, 80.0 to 12), records)
    }
}
