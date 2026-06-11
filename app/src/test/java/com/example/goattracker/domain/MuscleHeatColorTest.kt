package com.example.goattracker.domain

import com.example.goattracker.domain.model.MuscleGroup
import org.junit.Assert.assertEquals
import org.junit.Test

class MuscleHeatColorTest {

    private fun status(recovery: Float, hasData: Boolean = true) =
        MuscleStatus(
            group = MuscleGroup.CHEST,
            lastWorkedAt = if (hasData) 1L else null,
            recovery = recovery,
            recentSets = if (hasData) 3 else 0,
        )

    @Test
    fun noStatusOrNoDataIsNeutral() {
        assertEquals(MuscleHeatColor.NEUTRAL, MuscleHeatColor.forStatus(null))
        assertEquals(MuscleHeatColor.NEUTRAL, MuscleHeatColor.forStatus(status(Float.NaN, hasData = false)))
    }

    @Test
    fun freshTrainedIsRed_recoveredIsGreen_midIsAmber() {
        assertEquals(MuscleHeatColor.FATIGUED, MuscleHeatColor.forStatus(status(0f)))
        assertEquals(MuscleHeatColor.READY, MuscleHeatColor.forStatus(status(1f)))
        assertEquals(MuscleHeatColor.MID, MuscleHeatColor.forStatus(status(0.5f)))
    }

    @Test
    fun gradientIsMonotonicFromRedToGreen() {
        // red channel should fall and green channel rise as recovery increases
        val low = MuscleHeatColor.forStatus(status(0.1f))
        val high = MuscleHeatColor.forStatus(status(0.9f))
        assertEquals(true, high.g > low.g)
        assertEquals(true, high.r < low.r)
    }

    @Test
    fun lerpClampsOutOfRange() {
        val a = Rgb(0f, 0f, 0f); val b = Rgb(1f, 1f, 1f)
        assertEquals(a, MuscleHeatColor.lerp(a, b, -1f))
        assertEquals(b, MuscleHeatColor.lerp(a, b, 2f))
    }
}
