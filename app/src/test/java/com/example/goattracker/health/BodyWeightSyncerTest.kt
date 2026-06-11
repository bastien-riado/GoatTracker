package com.example.goattracker.health

import com.example.goattracker.data.DefaultDataRepository
import com.example.goattracker.domain.model.BodyWeightSource
import com.example.goattracker.domain.model.UserProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BodyWeightSyncerTest {

    private class FakeProvider(
        private val availableAndGranted: Boolean,
        private val reading: BodyWeightReading?,
    ) : BodyWeightProvider {
        override suspend fun isAvailableAndGranted(): Boolean = availableAndGranted
        override suspend fun readLatestWeight(): BodyWeightReading? = reading
    }

    private fun TestScope.repository(): DefaultDataRepository {
        val td = UnconfinedTestDispatcher(testScheduler)
        return DefaultDataRepository(storageDir = null, dispatcher = td, scope = TestScope(td))
    }

    @Test
    fun sync_disabled_doesNothing() = runTest {
        val repo = repository()
        val syncer = BodyWeightSyncer(FakeProvider(true, BodyWeightReading(80.0, 123L)), repo)

        assertFalse(syncer.syncIfEnabled())
        assertNull(repo.getLatestState().userProfile.bodyWeightKg)
    }

    @Test
    fun sync_enabledButProviderUnavailable_keepsManualWeight() = runTest {
        val repo = repository()
        repo.saveUserProfile(
            UserProfile(bodyWeightKg = 70.0, healthConnectSyncEnabled = true, bodyWeightSource = BodyWeightSource.MANUAL)
        )
        val syncer = BodyWeightSyncer(FakeProvider(availableAndGranted = false, reading = null), repo)

        assertFalse(syncer.syncIfEnabled())
        val profile = repo.getLatestState().userProfile
        assertEquals(70.0, profile.bodyWeightKg!!, 0.0)
        assertEquals(BodyWeightSource.MANUAL, profile.bodyWeightSource)
    }

    @Test
    fun sync_enabledAndGranted_updatesWeightFromProvider() = runTest {
        val repo = repository()
        repo.saveUserProfile(UserProfile(bodyWeightKg = 70.0, healthConnectSyncEnabled = true))
        val syncer = BodyWeightSyncer(FakeProvider(true, BodyWeightReading(72.5, 1718000000000L)), repo)

        assertTrue(syncer.syncIfEnabled())
        val profile = repo.getLatestState().userProfile
        assertEquals(72.5, profile.bodyWeightKg!!, 0.0)
        assertEquals(1718000000000L, profile.bodyWeightUpdatedAt)
        assertEquals(BodyWeightSource.HEALTH_CONNECT, profile.bodyWeightSource)
        // The opt-in flag must survive the update
        assertTrue(profile.healthConnectSyncEnabled)
    }

    @Test
    fun sync_noReadingOrGarbage_keepsExistingWeight() = runTest {
        val repo = repository()
        repo.saveUserProfile(UserProfile(bodyWeightKg = 70.0, healthConnectSyncEnabled = true))

        assertFalse(BodyWeightSyncer(FakeProvider(true, null), repo).syncIfEnabled())
        assertFalse(BodyWeightSyncer(FakeProvider(true, BodyWeightReading(0.0, 1L)), repo).syncIfEnabled())

        assertEquals(70.0, repo.getLatestState().userProfile.bodyWeightKg!!, 0.0)
    }
}
