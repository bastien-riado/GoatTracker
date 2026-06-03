package com.example.goattracker.ui.live

import android.content.Context
import kotlinx.coroutines.flow.StateFlow

/**
 * Abstraction over the rest-timer subsystem ([RestTimerManager] + [RestTimerService]) so the
 * [LiveWorkoutViewModel] depends on an interface rather than Android globals. Production wires in
 * [AndroidRestTimer]; unit tests pass a fake. This is what lets the ViewModel drop its nullable
 * `Context` and the `*ForTesting` shortcuts that previously leaked the test strategy into prod.
 */
interface RestTimer {
    /** Hot stream of the current timer state (Idle / Counting / Finished). */
    val state: StateFlow<RestTimerState>

    /** Start (or restart) a rest countdown of [durationSeconds]. */
    fun start(durationSeconds: Int)

    /** User skipped/dismissed the rest: stop effects and return to Idle. */
    fun acknowledge()

    /** Session started/ended/discarded: cancel everything and return to Idle. */
    fun cancelAll()

    /** Seconds left on the current countdown (0 when not counting). */
    fun remainingSeconds(): Int
}

/**
 * Real implementation: delegates verbatim to the existing [RestTimerManager] + [RestTimerService]
 * so the carefully-tuned timer behaviour (idempotent finish, alarm fallback, vibration, channel
 * migration) is unchanged — this only moves it behind an interface.
 */
class AndroidRestTimer(context: Context) : RestTimer {
    private val appContext = context.applicationContext

    override val state: StateFlow<RestTimerState> = RestTimerManager.state

    override fun start(durationSeconds: Int) {
        RestTimerManager.startTimer(appContext, durationSeconds)
        RestTimerService.start(appContext)
    }

    override fun acknowledge() {
        RestTimerManager.acknowledge(appContext)
        RestTimerService.stop(appContext)
    }

    override fun cancelAll() {
        RestTimerManager.cancelAll(appContext)
        RestTimerService.stop(appContext)
    }

    override fun remainingSeconds(): Int = RestTimerManager.getRemainingSeconds()
}
