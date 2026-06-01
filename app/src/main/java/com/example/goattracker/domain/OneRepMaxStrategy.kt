package com.example.goattracker.domain

import kotlin.math.roundToInt

interface OneRepMaxStrategy {
    fun calculate(weight: Double, reps: Int): Double
}

class EpleyStrategy : OneRepMaxStrategy {
    override fun calculate(weight: Double, reps: Int): Double {
        if (reps <= 0) return 0.0
        if (reps == 1) return weight
        return weight * (1.0 + reps / 30.0)
    }
}

class BrzyckiStrategy : OneRepMaxStrategy {
    override fun calculate(weight: Double, reps: Int): Double {
        if (reps <= 0) return 0.0
        if (reps == 1) return weight
        val denominator = 1.0278 - (0.0278 * reps)
        if (denominator <= 0.0) return weight * 1.5 // Fallback if reps are too high for formula bounds
        return weight / denominator
    }
}

class LanderStrategy : OneRepMaxStrategy {
    override fun calculate(weight: Double, reps: Int): Double {
        if (reps <= 0) return 0.0
        if (reps == 1) return weight
        val denominator = 101.3 - (1.09 * reps)
        if (denominator <= 0.0) return weight * 1.5
        return (100.0 * weight) / denominator
    }
}

enum class OneRepMaxFormula(val displayName: String, val strategy: OneRepMaxStrategy) {
    EPLEY("Epley", EpleyStrategy()),
    BRZYCKI("Brzycki", BrzyckiStrategy()),
    LANDER("Lander", LanderStrategy());

    fun calculateFormatted(weight: Double, reps: Int): Int {
        return strategy.calculate(weight, reps).roundToInt()
    }
}
