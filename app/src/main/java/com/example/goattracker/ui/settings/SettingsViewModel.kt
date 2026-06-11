package com.example.goattracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.goattracker.data.DataRepository
import com.example.goattracker.domain.model.BodyWeightSource
import com.example.goattracker.domain.model.UserProfile
import com.example.goattracker.domain.model.WeightUnit
import com.example.goattracker.health.BodyWeightSyncer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Transient status of the Health Connect linking flow, surfaced under the toggle. */
enum class HealthConnectStatus {
    IDLE,
    SYNCING,
    SYNCED,
    PERMISSION_DENIED,
    NO_DATA,
}

class SettingsViewModel(
    private val dataRepository: DataRepository,
    private val syncer: BodyWeightSyncer,
) : ViewModel() {

    val userProfile: StateFlow<UserProfile> = dataRepository.workoutState
        .map { it.userProfile }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserProfile()
        )

    private val _healthConnectStatus = MutableStateFlow(HealthConnectStatus.IDLE)
    val healthConnectStatus: StateFlow<HealthConnectStatus> = _healthConnectStatus.asStateFlow()

    /** Manual entry from the settings dialog — value is in the user's display unit. */
    fun setBodyWeight(value: Double, unit: WeightUnit) {
        val kg = unit.toKg(value)
        if (kg <= 0.0 || kg > 500.0) return // garbage guard; the dialog also validates
        viewModelScope.launch {
            val current = dataRepository.getLatestState().userProfile
            dataRepository.saveUserProfile(
                current.copy(
                    bodyWeightKg = kg,
                    bodyWeightUpdatedAt = System.currentTimeMillis(),
                    bodyWeightSource = BodyWeightSource.MANUAL,
                )
            )
        }
    }

    fun setWeightUnit(unit: WeightUnit) {
        viewModelScope.launch {
            val current = dataRepository.getLatestState().userProfile
            if (current.weightUnit != unit) {
                dataRepository.saveUserProfile(current.copy(weightUnit = unit))
            }
        }
    }

    /**
     * Called once the system permission sheet comes back. Enabling is only persisted when the
     * permission was actually granted, so the flag can never be on without read access.
     */
    fun onHealthConnectPermissionResult(granted: Boolean) {
        viewModelScope.launch {
            val current = dataRepository.getLatestState().userProfile
            if (!granted) {
                _healthConnectStatus.value = HealthConnectStatus.PERMISSION_DENIED
                if (current.healthConnectSyncEnabled) {
                    dataRepository.saveUserProfile(current.copy(healthConnectSyncEnabled = false))
                }
                return@launch
            }
            dataRepository.saveUserProfile(current.copy(healthConnectSyncEnabled = true))
            _healthConnectStatus.value = HealthConnectStatus.SYNCING
            val synced = syncer.syncIfEnabled()
            _healthConnectStatus.value = if (synced) HealthConnectStatus.SYNCED else HealthConnectStatus.NO_DATA
        }
    }

    fun disableHealthConnect() {
        viewModelScope.launch {
            val current = dataRepository.getLatestState().userProfile
            if (current.healthConnectSyncEnabled) {
                dataRepository.saveUserProfile(current.copy(healthConnectSyncEnabled = false))
            }
            _healthConnectStatus.value = HealthConnectStatus.IDLE
        }
    }
}
