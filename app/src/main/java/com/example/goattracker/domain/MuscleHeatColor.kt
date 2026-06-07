package com.example.goattracker.domain

/** Plain RGB triple (channels 0f..1f) — UI-framework-agnostic so the color logic stays unit-testable. */
data class Rgb(val r: Float, val g: Float, val b: Float)

/**
 * Maps a muscle's [MuscleStatus] to a heatmap color: fresh-trained (red) → recovering (amber) →
 * recovered/ready (green); no training history → neutral grey. Pure business logic (no Compose),
 * consumed by both the 3D material tint and the Compose legend/chips.
 */
object MuscleHeatColor {
    val NEUTRAL = Rgb(0.431f, 0.431f, 0.431f)   // #6E6E6E
    val FATIGUED = Rgb(0.898f, 0.224f, 0.212f)  // #E53935
    val MID = Rgb(0.961f, 0.700f, 0.004f)       // #F5B301
    val READY = Rgb(0.180f, 0.800f, 0.443f)     // #2ECC71

    fun forStatus(status: MuscleStatus?): Rgb {
        if (status == null || !status.hasData) return NEUTRAL
        val r = status.recovery.coerceIn(0f, 1f)
        return if (r < 0.5f) lerp(FATIGUED, MID, r / 0.5f)
        else lerp(MID, READY, (r - 0.5f) / 0.5f)
    }

    fun lerp(a: Rgb, b: Rgb, t: Float): Rgb {
        val c = t.coerceIn(0f, 1f)
        return Rgb(a.r + (b.r - a.r) * c, a.g + (b.g - a.g) * c, a.b + (b.b - a.b) * c)
    }
}
