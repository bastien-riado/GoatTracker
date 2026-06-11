package com.example.goattracker.health

/** A body-weight measurement coming from an external source (Health Connect). */
data class BodyWeightReading(
    val weightKg: Double,
    /** Epoch millis at which the measurement was taken (not when we read it). */
    val recordedAt: Long,
)

/**
 * Abstraction over the external weight source so [BodyWeightSyncer] is unit-testable on the JVM —
 * the real implementation ([HealthConnectWeightProvider]) needs a device with Health Connect.
 */
interface BodyWeightProvider {
    /** True when the source exists on this device AND the user granted read access. */
    suspend fun isAvailableAndGranted(): Boolean

    /** Most recent weight measurement, or null when none exists / the source is unreachable. */
    suspend fun readLatestWeight(): BodyWeightReading?
}
