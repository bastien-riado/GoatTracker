package com.example.goattracker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.goattracker.data.DefaultDataRepository
import com.example.goattracker.theme.GoatTrackerTheme
import com.example.goattracker.ui.SplashScreen
import com.example.goattracker.ui.live.RestTimerManager
import com.example.goattracker.ui.live.RestTimerService
import com.example.goattracker.ui.live.RestTimerState
import kotlinx.coroutines.delay
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
        // Install the system splash screen (core-splashscreen) for an instant branded window at
        // cold start. Must run before super.onCreate(). It hands off quickly to the branded
        // Compose SplashScreen below, which shows the app name while the data layer finishes loading.
        installSplashScreen()
        val repository = DefaultDataRepository.getInstance(filesDir)

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
            GoatTrackerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val isReady by repository.isReady.collectAsStateWithLifecycle()
                    // Keep the branded splash up for a brief minimum so the name is actually seen,
                    // even when the data loads near-instantly.
                    var minTimePassed by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        delay(1400)
                        minTimePassed = true
                    }

                    Crossfade(
                        targetState = isReady && minTimePassed,
                        animationSpec = tween(450),
                        label = "splashToHome",
                    ) { showHome ->
                        if (showHome) MainNavigation() else SplashScreen()
                    }
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
