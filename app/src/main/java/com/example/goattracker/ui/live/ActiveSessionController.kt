package com.example.goattracker.ui.live

import android.content.Context
import com.example.goattracker.data.DataRepository
import com.example.goattracker.data.DefaultDataRepository
import com.example.goattracker.domain.model.WorkoutSession
import com.example.goattracker.domain.model.toFinishedOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Compact projection of the active session for the surfaces that live OUTSIDE the full live screen:
 * the in-app mini-player bar and the ongoing "Séance en cours" notification. Everything here is
 * derived from the persisted draft + the rest timer, so it is a read model — never a second copy of
 * the session that could disagree with [DataRepository].activeDraft.
 */
data class MiniSessionState(
    val sessionId: String,
    val name: String,
    val elapsedSeconds: Int,
    val completedExercises: Int,
    val plannedExercises: Int,
    val completedSets: Int,
    val plannedSets: Int,
    /** Seconds left on the current rest, or null when no rest is running. */
    val restRemainingSeconds: Int?,
    /** True while the rest timer is in its finished/buzzing state. */
    val isRestVibrating: Boolean,
)

/**
 * The small, explicit set of session controls reachable from outside the live screen. Routing every
 * out-of-screen control through one sealed type is what makes adding a new one (e.g. "Passer la
 * série", "Mettre en pause") a one-line change here + one button in the UI, instead of new plumbing
 * across the service, the receiver and the bar.
 */
sealed interface SessionAction {
    /** Bring the full live screen back to the foreground. Handled by the navigation layer. */
    data object Open : SessionAction
    /** Save & end the session (same rule as the in-screen "Terminer"). */
    data object Finish : SessionAction
    /** Skip the running rest timer. */
    data object SkipRest : SessionAction
    /** Open the exercise picker on the live screen (the bottom slot's action while it is on top). */
    data object AddExercise : SessionAction
}

/** Seam over the foreground service so the controller core stays testable without Android. */
interface SessionServiceController {
    fun ensureRunning()
    fun stop()
}

/**
 * Process-scoped owner of the **active session presence** across the whole app.
 *
 * The live screen's [LiveWorkoutViewModel] still owns the rich editing experience and writes the
 * draft; this controller does NOT duplicate that. It only:
 *  - projects the persisted draft (+ rest timer) into [miniState] for the bar & notification,
 *  - keeps the [WorkoutSessionService] running exactly while a session exists,
 *  - provides the finish/discard path for when there is no live screen on screen (the notification).
 *
 * Because the source of truth is [DataRepository.activeDraft] (already process-wide and
 * process-death-proof), leaving the live screen can simply pop it: the session keeps living here and
 * resurfaces in the mini-player. Constructor deps are injected so it unit-tests like the ViewModel
 * (fake repo / fake [RestTimer] / [TestScope] / finite ticker); production wires the Android impls in
 * [initialize].
 */
class ActiveSessionController(
    private val repository: DataRepository,
    private val restTimer: RestTimer,
    private val service: SessionServiceController,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    // Injected so tests pass a finite flow (flowOf(Unit)) instead of looping forever in [scope] —
    // same pattern that stopped the ViewModel display loops from hanging the unit-test suite.
    private val ticker: Flow<Unit> = flow { while (true) { emit(Unit); delay(1000) } },
) {

    /** The active session, mirrored from the persisted draft (null when none is active). */
    val session: StateFlow<WorkoutSession?> =
        repository.workoutState
            .map { it.activeDraft }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, repository.getLatestState().activeDraft)

    /**
     * Read model for the bar & notification. [SharingStarted.WhileSubscribed] tears the 1 Hz ticker
     * down 5 s after the last collector leaves (e.g. while the full live screen is showing and the
     * bar is hidden) and revives it across a config change — so the elapsed clock only ticks when
     * something is actually rendering it.
     */
    val miniState: StateFlow<MiniSessionState?> =
        combine(session, restTimer.state, ticker) { current, rest, _ ->
            current?.let { project(it, rest) }
        }.stateIn(scope, SharingStarted.WhileSubscribed(5_000L), null)

    init {
        // Own the foreground-service lifecycle: present whenever a session exists, gone the instant
        // it ends. Eagerly stateIn means this starts immediately and also fires after the async disk
        // load restores a draft on a cold start (process-death resume).
        scope.launch {
            session
                .map { it != null }
                .distinctUntilChanged()
                .collect { hasSession -> if (hasSession) service.ensureRunning() else service.stop() }
        }
    }

    /**
     * Actions the controller cannot execute itself because their handler lives in a screen (e.g.
     * [SessionAction.AddExercise] opens the live screen's exercise picker). Screens collect this to
     * react; buffered so a tap is never lost to a missing collector during recomposition.
     */
    private val _uiEvents = MutableSharedFlow<SessionAction>(extraBufferCapacity = 4)
    val uiEvents: SharedFlow<SessionAction> = _uiEvents.asSharedFlow()

    /** Single entry point for the out-of-screen control surface (notification / mini-player). */
    fun dispatch(action: SessionAction) {
        when (action) {
            SessionAction.Finish -> finishActiveSession()
            SessionAction.SkipRest -> restTimer.acknowledge()
            SessionAction.AddExercise -> _uiEvents.tryEmit(action)
            SessionAction.Open -> Unit // navigation is inherently a UI-layer concern
        }
    }

    /**
     * Save & end the active session from outside the live screen (the notification "Terminer").
     * Uses the SAME [toFinishedOrNull] rule as the in-screen finish, then clears the draft — which
     * makes [session] emit null and the service self-stop. Idempotent with the in-screen path:
     * both clear the draft and cancel the rest timer, and [DataRepository.addWorkoutSession]
     * de-dupes by id, so a double finish cannot double-save.
     */
    fun finishActiveSession() {
        scope.launch {
            val draft = repository.getLatestState().activeDraft ?: return@launch
            restTimer.cancelAll()
            draft.toFinishedOrNull(clock())?.let { repository.addWorkoutSession(it) }
            repository.saveActiveDraft(null)
        }
    }

    /** Discard the active session without saving (no log written). */
    fun discardActiveSession() {
        scope.launch {
            restTimer.cancelAll()
            repository.saveActiveDraft(null)
        }
    }

    private fun project(s: WorkoutSession, rest: RestTimerState): MiniSessionState {
        // Elapsed is derived from the wall-clock start time (not accumulated ticks), so it self-
        // corrects after Doze / process death exactly like the in-screen header.
        val elapsed = ((clock() - s.startTime) / 1000L).toInt().coerceAtLeast(0)
        val restRemaining = when (rest) {
            is RestTimerState.Counting -> restTimer.remainingSeconds()
            is RestTimerState.Finished -> 0
            is RestTimerState.Idle -> null
        }
        return MiniSessionState(
            sessionId = s.id,
            name = s.name,
            elapsedSeconds = elapsed,
            completedExercises = s.exercises.count { ex -> ex.sets.any { it.isCompleted } },
            plannedExercises = s.exercises.size,
            completedSets = s.exercises.sumOf { ex -> ex.sets.count { it.isCompleted } },
            plannedSets = s.exercises.sumOf { it.sets.size },
            restRemainingSeconds = restRemaining,
            isRestVibrating = rest is RestTimerState.Finished,
        )
    }

    companion object {
        @Volatile
        private var INSTANCE: ActiveSessionController? = null

        /**
         * Build (once) and start the process-wide controller. Safe to call repeatedly — e.g. from
         * MainActivity.onCreate and lazily from the navigation/service layers; the first call wins.
         */
        fun initialize(context: Context): ActiveSessionController {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun getInstance(context: Context): ActiveSessionController = initialize(context)

        private fun build(appContext: Context): ActiveSessionController =
            ActiveSessionController(
                repository = DefaultDataRepository.getInstance(appContext.filesDir),
                restTimer = AndroidRestTimer(appContext),
                service = AndroidSessionServiceController(appContext),
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            )
    }
}

/** Production [SessionServiceController]: starts/stops the real foreground service. */
private class AndroidSessionServiceController(private val appContext: Context) : SessionServiceController {
    override fun ensureRunning() {
        // Starting an FGS is only legal from the foreground; a session can only begin from on-screen
        // UI, so this is always reached in the foreground. Guard anyway so a pathological cold-start-
        // into-background can never crash the process.
        runCatching { WorkoutSessionService.start(appContext) }
    }

    override fun stop() {
        runCatching { WorkoutSessionService.stop(appContext) }
    }
}
