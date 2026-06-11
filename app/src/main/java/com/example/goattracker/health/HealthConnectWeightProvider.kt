package com.example.goattracker.health

import android.content.Context
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant

/**
 * Real [BodyWeightProvider] backed by Health Connect ("Santé Connect").
 *
 * Compatibility: the app's minSdk is 24 but the Health Connect provider app requires Android 9
 * (API 28), so EVERY entry point is gated on [isSupported]. This also keeps `java.time` usage
 * (API 26+) off pre-26 devices without core-library desugaring: ART only rejects a method
 * referencing missing classes when it executes, and these methods never execute below 28.
 * All failures degrade to "no reading" — the caller falls back to the manual weight.
 */
class HealthConnectWeightProvider(private val context: Context) : BodyWeightProvider {

    companion object {
        /** The only Health Connect permission this app asks for: read-only body weight. */
        val PERMISSIONS: Set<String> by lazy { setOf(HealthPermission.getReadPermission(WeightRecord::class)) }

        /** True when the device can host Health Connect AND the provider is installed/up to date. */
        fun isSupported(context: Context): Boolean =
            Build.VERSION.SDK_INT >= 28 &&
                runCatching { HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE }
                    .getOrDefault(false)
    }

    override suspend fun isAvailableAndGranted(): Boolean {
        if (!isSupported(context)) return false
        return runCatching {
            HealthConnectClient.getOrCreate(context)
                .permissionController.getGrantedPermissions()
                .containsAll(PERMISSIONS)
        }.getOrDefault(false)
    }

    override suspend fun readLatestWeight(): BodyWeightReading? {
        if (!isSupported(context)) return null
        return runCatching {
            val response = HealthConnectClient.getOrCreate(context).readRecords(
                ReadRecordsRequest(
                    recordType = WeightRecord::class,
                    timeRangeFilter = TimeRangeFilter.before(Instant.now()),
                    ascendingOrder = false, // newest first
                    pageSize = 1,
                )
            )
            response.records.firstOrNull()?.let { record ->
                BodyWeightReading(
                    weightKg = record.weight.inKilograms,
                    recordedAt = record.time.toEpochMilli(),
                )
            }
        }.getOrNull()
    }
}
