package com.example.goattracker.health

import com.example.goattracker.data.DataRepository
import com.example.goattracker.domain.model.BodyWeightSource
import kotlinx.coroutines.flow.first

/**
 * Pulls the latest body weight from the external provider into the user profile. Pure orchestration
 * (no Android imports): the provider and repository are injected, so the decision logic is
 * unit-testable.
 *
 * Called fire-and-forget on app start and right after the user enables the sync in settings.
 * Never throws: a sync failure must not break app startup — the manual weight stays in place.
 */
class BodyWeightSyncer(
    private val provider: BodyWeightProvider,
    private val repository: DataRepository,
) {

    /** @return true when a sync actually updated (or confirmed) the profile weight. */
    suspend fun syncIfEnabled(): Boolean {
        // The profile is loaded from disk asynchronously; don't read it before it exists.
        repository.isReady.first { it }

        val profile = repository.getLatestState().userProfile
        if (!profile.healthConnectSyncEnabled) return false
        if (!provider.isAvailableAndGranted()) return false

        val reading = provider.readLatestWeight() ?: return false
        if (reading.weightKg <= 0.0) return false

        val updated = profile.copy(
            bodyWeightKg = reading.weightKg,
            bodyWeightUpdatedAt = reading.recordedAt,
            bodyWeightSource = BodyWeightSource.HEALTH_CONNECT,
        )
        if (updated != profile) {
            repository.saveUserProfile(updated)
        }
        return true
    }
}
