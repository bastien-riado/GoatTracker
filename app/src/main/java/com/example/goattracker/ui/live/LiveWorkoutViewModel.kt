package com.example.goattracker.ui.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.goattracker.data.DataRepository
import com.example.goattracker.domain.model.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class LiveWorkoutUiState(
    val activeSession: WorkoutSession? = null,
    val elapsedSeconds: Int = 0,
    val timerRemainingSeconds: Int? = null,
    val isRestTimerVibrating: Boolean = false,
    val isFinishModalOpen: Boolean = false,
    val isExercisePickerOpen: Boolean = false,
    val availableExercises: List<Exercise> = emptyList(),
    val totalCompletedSets: Int = 0,
    val totalExercises: Int = 0,
    val plannedExercisesCount: Int = 0,
    val plannedSetsCount: Int = 0,
    val completedExercisesCount: Int = 0,
    val completedSetsCount: Int = 0,
    // Needed by the set rows: weight unit for inputs and body weight for the PDC cell.
    val userProfile: UserProfile = UserProfile()
)

class LiveWorkoutViewModel(
    private val dataRepository: DataRepository,
    private val restTimer: RestTimer,
    private val clock: () -> Long = System::currentTimeMillis,
    // Display tickers are injected so production uses a real repeating clock while tests pass a
    // finite flow (flowOf(Unit)) — the loops then emit once and complete instead of looping forever
    // in viewModelScope, which is what used to hang the unit-test suite.
    private val elapsedTicker: Flow<Unit> = flow { while (true) { emit(Unit); delay(1000) } },
    private val countdownTicker: Flow<Unit> = flow { while (true) { emit(Unit); delay(500) } },
) : ViewModel() {

    private val _uiState = MutableStateFlow(LiveWorkoutUiState())
    val uiState: StateFlow<LiveWorkoutUiState> = _uiState.asStateFlow()

    private var elapsedTimerJob: Job? = null
    private var timerObserverJob: Job? = null

    private var allSessions: List<WorkoutSession> = emptyList()

    // Exercise ids seen so far, used to detect an exercise that was just created.
    private var knownExerciseIds: Set<String>? = null
    // Set when the user leaves to create an exercise from inside the session: the next newly
    // created exercise is auto-added to the active session on return.
    private var pendingAutoAdd: Boolean = false

    init {
        viewModelScope.launch {
            dataRepository.workoutState.collect { state ->
                allSessions = state.sessions

                // Auto-add an exercise created via the in-session "Créer" flow. On the first
                // emission we only seed the known set so pre-existing exercises are never added.
                val previousIds = knownExerciseIds
                if (previousIds != null && pendingAutoAdd) {
                    val createdExercise = state.exercises.firstOrNull { it.id !in previousIds }
                    if (createdExercise != null) {
                        pendingAutoAdd = false
                        addExerciseToSession(createdExercise)
                    }
                }
                knownExerciseIds = state.exercises.mapTo(HashSet()) { it.id }

                _uiState.update { it.copy(availableExercises = state.exercises, userProfile = state.userProfile) }
            }
        }

        // Observe the rest-timer state and reflect it in the UI
        timerObserverJob = viewModelScope.launch {
            restTimer.state.collect { timerState ->
                when (timerState) {
                    is RestTimerState.Counting -> {
                        // Start a local countdown display loop
                        launchCountdownLoop()
                    }
                    is RestTimerState.Finished -> {
                        _uiState.update {
                            it.copy(timerRemainingSeconds = 0, isRestTimerVibrating = true)
                        }
                    }
                    is RestTimerState.Idle -> {
                        _uiState.update {
                            it.copy(timerRemainingSeconds = null, isRestTimerVibrating = false)
                        }
                        countdownDisplayJob?.cancel()
                    }
                }
            }
        }
    }

    private var countdownDisplayJob: Job? = null

    private fun launchCountdownLoop() {
        countdownDisplayJob?.cancel()
        countdownDisplayJob = viewModelScope.launch {
            // Refresh the displayed remaining time on each tick. The Counting -> Finished transition
            // is owned by the timer (service/alarm), not this display loop; here we only render.
            countdownTicker.collect {
                val remaining = restTimer.remainingSeconds()
                if (remaining <= 0) return@collect
                _uiState.update {
                    it.copy(timerRemainingSeconds = remaining, isRestTimerVibrating = false)
                }
            }
        }
    }

    /**
     * Entry point from the screen. Resumes a persisted in-progress session if one exists (e.g. the
     * process was killed mid-workout), otherwise starts a fresh one. The in-memory guard keeps a
     * recomposition / config change from clobbering a session that's already loaded.
     */
    /**
     * Re-adopt the persisted draft when the screen returns to the foreground: out-of-screen edits
     * (the watch/notification "Valider la série") land in the draft while this ViewModel's state
     * is frozen in the background. Same-id guard keeps it from clobbering an unrelated session.
     */
    fun resyncFromDraft() {
        val current = _uiState.value.activeSession ?: return
        val draft = dataRepository.getLatestState().activeDraft ?: return
        if (draft.id == current.id && draft != current) {
            updateSessionState(draft)
        }
    }

    fun startOrResumeSession() {
        if (_uiState.value.activeSession != null) return
        viewModelScope.launch {
            val draft = dataRepository.getLatestState().activeDraft
            if (draft != null) {
                updateSessionState(draft) // restore activeSession + counters from the persisted draft
                startElapsedTimer()
            } else {
                startNewSession()
            }
        }
    }

    fun startNewSession() {
        // Cancel any existing timers from a previous session
        elapsedTimerJob?.cancel()
        restTimer.cancelAll()

        val newSession = WorkoutSession(
            name = "Séance du ${getCurrentDateString()}",
            exercises = emptyList()
        )
        _uiState.update {
            LiveWorkoutUiState(
                activeSession = newSession,
                availableExercises = it.availableExercises
            )
        }
        persistDraft(newSession)
        startElapsedTimer()
    }

    // --- Elapsed Timer (counts UP from 0) ---

    private fun startElapsedTimer() {
        elapsedTimerJob?.cancel()
        elapsedTimerJob = viewModelScope.launch {
            // Derive elapsed time from the session's wall-clock startTime rather than accumulating
            // ticks: delay() is uptime-based and pauses while the process is suspended (screen off /
            // Doze), which used to freeze the counter. Reading the wall clock self-corrects on
            // resume; the ticker only paces the display refresh.
            elapsedTicker.collect {
                val startTime = _uiState.value.activeSession?.startTime ?: return@collect
                val elapsed = ((clock() - startTime) / 1000L).toInt()
                _uiState.update { it.copy(elapsedSeconds = elapsed) }
            }
        }
    }

    // --- Exercise Management ---

    fun addExerciseToSession(exercise: Exercise) {
        val currentSession = _uiState.value.activeSession ?: return

        // Don't add duplicate exercises to the session
        if (currentSession.exercises.any { it.exercise.id == exercise.id }) return

        // Check if exercise has already been performed in previous sessions
        val lastSessionWithExercise = allSessions
            .filter { session -> session.exercises.any { it.exercise.id == exercise.id } }
            .maxByOrNull { it.startTime }

        val previousExerciseSession = lastSessionWithExercise?.exercises
            ?.firstOrNull { it.exercise.id == exercise.id }

        val previousCompletedSets = previousExerciseSession?.sets?.filter { it.isCompleted } ?: emptyList()

        val newExerciseSession = if (previousCompletedSets.isNotEmpty()) {
            // Prefill with previous session completed sets
            val prefilledSets = previousCompletedSets.mapIndexed { index, oldSet ->
                WorkoutSet(
                    id = UUID.randomUUID().toString(),
                    setNumber = index + 1,
                    weight = oldSet.weight,
                    reps = oldSet.reps,
                    durationSeconds = oldSet.durationSeconds,
                    distanceKm = oldSet.distanceKm,
                    isCompleted = false
                )
            }
            ExerciseSession(
                exercise = exercise,
                sets = prefilledSets
            )
        } else {
            // Fallback defaults: 2 sets for strength work, a single open set for cardio/time
            // (a run is one effort, not "2 sets of 1 km"; intervals are added via "Ajouter une série").
            val defaultSets = when (exercise.trackingType) {
                TrackingType.WEIGHT_REPS -> listOf(
                    WorkoutSet(setNumber = 1, weight = 20.0, reps = 10),
                    WorkoutSet(setNumber = 2, weight = 20.0, reps = 10)
                )
                TrackingType.BODYWEIGHT_REPS -> listOf(
                    WorkoutSet(setNumber = 1, reps = 8),
                    WorkoutSet(setNumber = 2, reps = 8)
                )
                TrackingType.TIME -> listOf(WorkoutSet(setNumber = 1, durationSeconds = 60))
                TrackingType.DISTANCE -> listOf(WorkoutSet(setNumber = 1))
            }

            ExerciseSession(
                exercise = exercise,
                sets = defaultSets
            )
        }

        val updatedSession = currentSession.copy(
            exercises = currentSession.exercises + newExerciseSession
        )

        updateSessionState(updatedSession)
    }

    fun removeExerciseFromSession(exerciseId: String) {
        val currentSession = _uiState.value.activeSession ?: return
        val updatedSession = currentSession.copy(
            exercises = currentSession.exercises.filter { it.exercise.id != exerciseId }
        )
        updateSessionState(updatedSession)
    }

    fun addSetToExercise(exerciseId: String) {
        val currentSession = _uiState.value.activeSession ?: return
        val exerciseSession = currentSession.exercises.firstOrNull { it.exercise.id == exerciseId } ?: return

        val lastSet = exerciseSession.sets.lastOrNull()
        val nextSetNumber = (lastSet?.setNumber ?: 0) + 1

        // Pre-populate with previous set values for perfect UX
        val newSet = lastSet?.copy(
            id = UUID.randomUUID().toString(),
            setNumber = nextSetNumber,
            isCompleted = false
        ) ?: WorkoutSet(setNumber = nextSetNumber)

        val updatedExercises = currentSession.exercises.map {
            if (it.exercise.id == exerciseId) {
                it.copy(sets = it.sets + newSet)
            } else {
                it
            }
        }

        updateSessionState(currentSession.copy(exercises = updatedExercises))
    }

    fun deleteSetFromExercise(exerciseId: String, setId: String) {
        val currentSession = _uiState.value.activeSession ?: return

        val updatedSets = currentSession.exercises
            .firstOrNull { it.exercise.id == exerciseId }
            ?.sets
            ?.filter { it.id != setId }
            ?.mapIndexed { index, workoutSet ->
                workoutSet.copy(setNumber = index + 1)
            } ?: return

        val updatedExercises = currentSession.exercises.map {
            if (it.exercise.id == exerciseId) {
                it.copy(sets = updatedSets)
            } else {
                it
            }
        }

        updateSessionState(currentSession.copy(exercises = updatedExercises))
    }

    fun updateSetValues(
        exerciseId: String,
        setId: String,
        weight: Double? = null,
        reps: Int? = null,
        durationSeconds: Int? = null,
        distanceKm: Double? = null
    ) {
        val currentSession = _uiState.value.activeSession ?: return

        val updatedExercises = currentSession.exercises.map { exSession ->
            if (exSession.exercise.id == exerciseId) {
                val updatedSets = exSession.sets.map { set ->
                    if (set.id == setId) {
                        set.copy(
                            weight = weight ?: set.weight,
                            reps = reps ?: set.reps,
                            durationSeconds = durationSeconds ?: set.durationSeconds,
                            distanceKm = distanceKm ?: set.distanceKm
                        )
                    } else {
                        set
                    }
                }
                exSession.copy(sets = updatedSets)
            } else {
                exSession
            }
        }

        updateSessionState(currentSession.copy(exercises = updatedExercises))
    }

    fun toggleSetCompletion(exerciseId: String, setId: String) {
        val currentSession = _uiState.value.activeSession ?: return
        var startRestTimer = false
        var restTimeForExercise = 90 // default fallback

        val updatedExercises = currentSession.exercises.map { exSession ->
            if (exSession.exercise.id == exerciseId) {
                val updatedSets = exSession.sets.map { set ->
                    if (set.id == setId) {
                        val newCompletedState = !set.isCompleted
                        if (newCompletedState) {
                            startRestTimer = true
                            restTimeForExercise = exSession.exercise.restTimeSeconds
                        }
                        // completedAt feeds the future rest-time/density stats; unchecking clears
                        // it (the set was not actually done at that moment).
                        set.copy(
                            isCompleted = newCompletedState,
                            completedAt = if (newCompletedState) System.currentTimeMillis() else null,
                        )
                    } else {
                        set
                    }
                }
                exSession.copy(sets = updatedSets)
            } else {
                exSession
            }
        }

        updateSessionState(currentSession.copy(exercises = updatedExercises))

        if (startRestTimer) {
            startRestTimer(restTimeForExercise)
        }
    }

    // --- Session Finish Flow ---

    fun requestFinishSession() {
        _uiState.update { it.copy(isFinishModalOpen = true) }
    }

    fun dismissFinishModal() {
        _uiState.update { it.copy(isFinishModalOpen = false) }
    }

    /**
     * Saves and ends the session. Returns the saved session's id (the celebration screen loads it
     * by id), or null when nothing was worth saving (no completed set) — caller just exits then.
     */
    fun confirmSaveSession(): String? {
        val currentSession = _uiState.value.activeSession ?: return null

        // Stop all timers
        elapsedTimerJob?.cancel()
        restTimer.cancelAll()

        // Same finish rule as the out-of-screen path (ActiveSessionController, notification
        // "Terminer"): drop incomplete sets / empty exercises and stamp the end time. Shared via
        // toFinishedOrNull so the two finish paths cannot drift apart.
        val finishedSession = currentSession.toFinishedOrNull(System.currentTimeMillis())
        viewModelScope.launch {
            // The screen pops right after this call and the per-entry ViewModel is cleared with
            // it: a plain viewModelScope write could be CANCELLED mid-flight and lose the session
            // (the legacy JSON repo updated its in-memory state synchronously; Room suspends all
            // the way to the commit). NonCancellable + save-then-clear order: if the process dies
            // between the two, the draft resurrects and re-finishing de-dupes by id.
            withContext(NonCancellable) {
                finishedSession?.let { dataRepository.addWorkoutSession(it) }
                dataRepository.saveActiveDraft(null)
            }
        }
        // Reset to a clean slate so re-entering the screen starts a brand-new session. Primary fix
        // is per-entry VM scoping (Navigation.kt); this is defense-in-depth if the VM is ever reused.
        _uiState.update { LiveWorkoutUiState(availableExercises = it.availableExercises) }
        return finishedSession?.id
    }

    fun discardSession() {
        // Stop all timers, don't save anything
        elapsedTimerJob?.cancel()
        restTimer.cancelAll()
        // Discarding must NOT leave a resumable draft behind — and must survive the screen pop
        // (same cancellation window as the finish path).
        viewModelScope.launch {
            withContext(NonCancellable) { dataRepository.saveActiveDraft(null) }
        }
        // Reset to a clean slate so re-entering the screen starts a brand-new session.
        _uiState.update { LiveWorkoutUiState(availableExercises = it.availableExercises) }
    }

    // --- Exercise Picker ---

    fun setExercisePickerOpen(isOpen: Boolean) {
        _uiState.update { it.copy(isExercisePickerOpen = isOpen) }
    }

    /**
     * Called right before navigating to the create-exercise screen from inside the session, so the
     * next exercise created is auto-added to the active session when we return (see the
     * workoutState collector in init).
     */
    fun prepareAutoAddOnReturn() {
        pendingAutoAdd = true
    }

    private fun startRestTimer(durationSeconds: Int) {
        restTimer.start(durationSeconds)
    }

    fun acknowledgeRestTimer() {
        restTimer.acknowledge()
    }

    override fun onCleared() {
        super.onCleared()
        elapsedTimerJob?.cancel()
        countdownDisplayJob?.cancel()
        timerObserverJob?.cancel()
        // Do NOT cancel the timer/alarm/service here — it should persist beyond ViewModel lifecycle
    }

    // --- Private Helper Functions ---

    private fun updateSessionState(updatedSession: WorkoutSession) {
        val plannedExercises = updatedSession.exercises.size
        val plannedSets = updatedSession.exercises.sumOf { it.sets.size }
        val completedExercises = updatedSession.exercises.count { it.sets.any { set -> set.isCompleted } }
        val completedSets = updatedSession.exercises.sumOf { it.sets.count { it.isCompleted } }

        _uiState.update {
            it.copy(
                activeSession = updatedSession,
                totalExercises = plannedExercises,
                totalCompletedSets = completedSets,
                plannedExercisesCount = plannedExercises,
                plannedSetsCount = plannedSets,
                completedExercisesCount = completedExercises,
                completedSetsCount = completedSets
            )
        }
        persistDraft(updatedSession)
    }

    /**
     * Persist the in-progress session (or clear it with null) so it survives process death.
     * Fire-and-forget on viewModelScope; the data layer serialises. NOTE: today this rewrites the
     * whole store on every set edit — lot 6 (debounced writes) will smooth that out.
     */
    private fun persistDraft(session: WorkoutSession?) {
        viewModelScope.launch { dataRepository.saveActiveDraft(session) }
    }

    private fun getCurrentDateString(): String {
        val sdf = java.text.SimpleDateFormat("d MMMM", java.util.Locale.FRENCH)
        return sdf.format(java.util.Date())
    }
}
