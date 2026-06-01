package com.example.onairtracker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.onairtracker.theme.OnAirTrackerTheme
import com.example.onairtracker.ui.live.RestTimerManager
import com.example.onairtracker.ui.live.RestTimerService
import com.example.onairtracker.ui.live.RestTimerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainActivity : ComponentActivity() {

    private val _pendingNavigation = MutableStateFlow<String?>(null)
    val pendingNavigation: StateFlow<String?> = _pendingNavigation.asStateFlow()

    fun consumeNavigation() {
        _pendingNavigation.value = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Restore timer state from SharedPreferences (survives process death)
        RestTimerManager.initialize(this)

        // If a timer was counting when the app was killed, restart the foreground service
        if (RestTimerManager.state.value is RestTimerState.Counting) {
            RestTimerService.start(this)
        }

        // Handle navigation from notification when app is cold-started
        handleNavigationIntent(intent)

        enableEdgeToEdge()
        setContent {
            OnAirTrackerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigation()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        RestTimerManager.isAppInForeground = true
    }

    override fun onStop() {
        super.onStop()
        RestTimerManager.isAppInForeground = false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle navigation from notification when app is already running (SINGLE_TOP)
        handleNavigationIntent(intent)
    }

    private fun handleNavigationIntent(intent: Intent?) {
        val navigateTo = intent?.getStringExtra(RestTimerManager.EXTRA_NAVIGATE_TO)
        if (navigateTo != null) {
            _pendingNavigation.value = navigateTo
            // Clear the extra so it doesn't re-trigger on config change
            intent.removeExtra(RestTimerManager.EXTRA_NAVIGATE_TO)
        }
    }
}
