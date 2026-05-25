package com.example.onairtracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class OneRepMaxStrategyTest {

    @Test
    fun testEpleyStrategy_standardValues() {
        val strategy = EpleyStrategy()
        // 100kg x 10 reps -> 100 * (1 + 10/30) = 133.333
        val result = strategy.calculate(100.0, 10)
        assertEquals(133.333, result, 0.001)
    }

    @Test
    fun testEpleyStrategy_edgeCases() {
        val strategy = EpleyStrategy()
        assertEquals(0.0, strategy.calculate(100.0, 0), 0.001)
        assertEquals(100.0, strategy.calculate(100.0, 1), 0.001)
        assertEquals(0.0, strategy.calculate(0.0, 5), 0.001)
    }

    @Test
    fun testBrzyckiStrategy_standardValues() {
        val strategy = BrzyckiStrategy()
        // 100kg x 5 reps -> 100 / (1.0278 - 0.0278 * 5) = 100 / 0.8888 = 112.511
        val result = strategy.calculate(100.0, 5)
        assertEquals(112.511, result, 0.001)
    }

    @Test
    fun testBrzyckiStrategy_edgeCases() {
        val strategy = BrzyckiStrategy()
        assertEquals(0.0, strategy.calculate(100.0, 0), 0.001)
        assertEquals(100.0, strategy.calculate(100.0, 1), 0.001)
    }

    @Test
    fun testLanderStrategy_standardValues() {
        val strategy = LanderStrategy()
        // 100kg x 8 reps -> (100 * 100) / (101.3 - 1.09 * 8) = 10000 / 92.58 = 108.014
        val result = strategy.calculate(100.0, 8)
        assertEquals(108.014, result, 0.001)
    }

    @Test
    fun testOneRepMaxFormula_enumFormatting() {
        // Epley 80kg x 8 reps -> 80 * (1 + 8/30) = 80 * 1.26667 = 101.33 -> round to 101
        val result = OneRepMaxFormula.EPLEY.calculateFormatted(80.0, 8)
        assertEquals(101, result)
    }
}
