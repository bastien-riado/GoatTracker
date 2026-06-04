package com.example.goattracker.data

import com.example.goattracker.data.dto.WorkoutStateDto
import com.example.goattracker.data.dto.toDto
import com.example.goattracker.domain.model.Exercise
import com.example.goattracker.domain.model.ExerciseCategory
import com.example.goattracker.domain.model.TrackingType
import com.example.goattracker.domain.model.WorkoutSession
import com.example.goattracker.domain.model.WorkoutState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

interface DataRepository {
    val workoutState: Flow<WorkoutState>

    /** Emits true once the initial load from disk has completed (used to gate the splash screen). */
    val isReady: StateFlow<Boolean>

    fun getLatestState(): WorkoutState
    suspend fun addExercise(exercise: Exercise)
    suspend fun deleteExercise(exerciseId: String)
    suspend fun addWorkoutSession(session: WorkoutSession)
    suspend fun updateWorkoutSession(session: WorkoutSession)
    suspend fun deleteWorkoutSession(sessionId: String)

    /** Persist (or clear, when null) the in-progress live session so it survives process death. */
    suspend fun saveActiveDraft(session: WorkoutSession?)
}

class DefaultDataRepository(
    private val storageDir: File?,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val scope: CoroutineScope = CoroutineScope(dispatcher)
) : DataRepository {

    companion object {
        private const val SAVE_DEBOUNCE_MS = 300L

        @Volatile
        private var INSTANCE: DefaultDataRepository? = null

        fun getInstance(storageDir: File): DefaultDataRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DefaultDataRepository(storageDir).also { INSTANCE = it }
            }
        }
    }

    private val json = Json { 
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val mutex = Mutex()
    @Volatile
    private var saveJob: Job? = null
    private val stateFile: File? = storageDir?.let { File(it, "workouts.json") }
    private val tempFile: File? = storageDir?.let { File(it, "workouts.json.tmp") }

    private val _workoutState = MutableStateFlow(WorkoutState())
    override val workoutState: Flow<WorkoutState> = _workoutState.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    override val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    init {
        // Asynchronously load from disk to prevent UI-blocking on Main thread
        scope.launch {
            loadFromDisk()
        }
    }

    private suspend fun loadFromDisk() = withContext(dispatcher) {
        try {
            if (stateFile == null || !stateFile.exists()) {
                val defaultPreset = WorkoutState(exercises = defaultExercises())
                _workoutState.value = defaultPreset
                saveToDiskInternal(defaultPreset)
                return@withContext
            }

            mutex.withLock {
                try {
                    val content = stateFile.readText()
                    val parsedDto = json.decodeFromString<WorkoutStateDto>(content)
                    _workoutState.value = parsedDto.toDomain()
                } catch (e: Exception) {
                    e.printStackTrace()
                    // Preserve the unreadable file instead of silently wiping the user's history
                    // (the old "recover" path WAS the data loss). Then fall back to defaults.
                    backupCorruptFile(stateFile)
                    _workoutState.value = WorkoutState(exercises = defaultExercises())
                }
            }
        } finally {
            // Signal readiness so the splash screen can be dismissed, even on the early
            // return / error paths above.
            _isReady.value = true
        }
    }

    private fun saveToDisk() {
        // Coalesce bursts of mutations (typing in a set field, or persisting the live draft on every
        // set edit): cancel the pending write and reschedule, turning once-per-keystroke full-file
        // rewrites into one write per quiet window. _workoutState is already updated synchronously by
        // callers, so only the DISK write is delayed (<= SAVE_DEBOUNCE_MS); a process kill inside that
        // window loses at most the last edit — an acceptable trade for the write reduction.
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(SAVE_DEBOUNCE_MS)
            saveToDiskInternal(_workoutState.value)
        }
    }

    // Thread-safe and atomic file write implementation
    private suspend fun saveToDiskInternal(state: WorkoutState) = withContext(dispatcher) {
        if (stateFile == null || tempFile == null) return@withContext
        mutex.withLock {
            try {
                val dto = state.toDto()
                val content = json.encodeToString(WorkoutStateDto.serializer(), dto)
                // 1. Write to temporary file
                tempFile.writeText(content)
                // 2. Perform atomic replacement to prevent corruption
                if (!tempFile.renameTo(stateFile)) {
                    // Fallback to normal write if renameTo fails (some OS boundaries)
                    stateFile.writeText(content)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Moves an unparseable state file aside (workouts.corrupt-<ts>.json) so the user's data can be
     * recovered or inspected rather than overwritten. Best-effort; never throws into the caller.
     * Called from within the load mutex but touches only the filesystem, so no re-entrant lock.
     */
    private fun backupCorruptFile(file: File) {
        try {
            if (!file.exists()) return
            val backup = File(file.parentFile, "workouts.corrupt-${System.currentTimeMillis()}.json")
            if (!file.renameTo(backup)) {
                file.copyTo(backup, overwrite = true)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun getLatestState(): WorkoutState = _workoutState.value

    override suspend fun addExercise(exercise: Exercise) {
        _workoutState.update { current ->
            current.copy(exercises = current.exercises.filter { it.id != exercise.id } + exercise)
        }
        saveToDisk()
    }

    override suspend fun deleteExercise(exerciseId: String) {
        _workoutState.update { current ->
            current.copy(exercises = current.exercises.filter { it.id != exerciseId })
        }
        saveToDisk()
    }

    override suspend fun addWorkoutSession(session: WorkoutSession) {
        _workoutState.update { current ->
            current.copy(sessions = current.sessions.filter { it.id != session.id } + session)
        }
        saveToDisk()
    }

    override suspend fun updateWorkoutSession(session: WorkoutSession) {
        _workoutState.update { current ->
            val updatedSessions = current.sessions.map { 
                if (it.id == session.id) session else it
            }
            current.copy(sessions = updatedSessions)
        }
        saveToDisk()
    }

    override suspend fun deleteWorkoutSession(sessionId: String) {
        _workoutState.update { current ->
            current.copy(sessions = current.sessions.filter { it.id != sessionId })
        }
        saveToDisk()
    }

    override suspend fun saveActiveDraft(session: WorkoutSession?) {
        _workoutState.update { it.copy(activeDraft = session) }
        saveToDisk()
    }

    private fun defaultExercises(): List<Exercise> {
        return listOf(
            Exercise(
                name = "Développé Couché",
                category = ExerciseCategory.PUSH,
                primaryMuscle = "Pectoraux",
                trackingType = TrackingType.WEIGHT_REPS,
                restTimeSeconds = 120
            ),
            Exercise(
                name = "Tractions Pronation",
                category = ExerciseCategory.PULL,
                primaryMuscle = "Dos",
                trackingType = TrackingType.BODYWEIGHT_REPS,
                restTimeSeconds = 120
            ),
            Exercise(
                name = "Squat Barre",
                category = ExerciseCategory.LEG,
                primaryMuscle = "Quadriceps",
                trackingType = TrackingType.WEIGHT_REPS,
                restTimeSeconds = 150
            )
        )
    }
}
