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

        // Handle navigation from notification when app is cold-started
        handleNavigationIntent(intent)

        // Create notification channel early so it's always available
        RestTimerManager.ensureNotificationChannel(this)

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
