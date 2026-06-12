package com.example.goattracker.data.local

import android.content.Context
import com.example.goattracker.data.DataRepository
import com.example.goattracker.domain.model.BodyWeightEntry
import com.example.goattracker.domain.model.BodyWeightSource
import com.example.goattracker.domain.model.Exercise
import com.example.goattracker.domain.model.UserProfile
import com.example.goattracker.domain.model.WorkoutSession
import com.example.goattracker.domain.model.WorkoutState
import com.example.goattracker.domain.model.WorkoutTemplate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Room-backed [DataRepository]. The observable state is assembled by combining the four reactive
 * queries (active catalog, finished sessions, the single DRAFT row, profile) and mirrored into a
 * [MutableStateFlow] so [getLatestState] stays synchronous for out-of-screen callers
 * (ActiveSessionController's notification actions), exactly like the legacy JSON repository.
 *
 * Unlike the legacy repository there is NO write debounce: the JSON file forced a full-state
 * rewrite per mutation (hence its 300 ms coalescing), whereas Room writes only the touched rows
 * inside a transaction — so every mutation, draft edits included, is durable the moment it returns.
 *
 * [isReady] flips true only after first-launch initialization (seed or legacy import) AND the
 * first post-init emission has landed in the mirror, so the splash screen never reveals an empty
 * flash of state.
 */
class RoomDataRepository(
    private val db: GoatTrackerDatabase,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val initializer: suspend (GoatTrackerDatabase) -> Unit = { DefaultSeed.seed(it, System.currentTimeMillis()) },
    private val now: () -> Long = System::currentTimeMillis,
) : DataRepository {

    private val _workoutState = MutableStateFlow(WorkoutState())
    override val workoutState: Flow<WorkoutState> = _workoutState.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    override val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val combinedState: Flow<WorkoutState> = combine(
        db.exerciseDao().observeActive(),
        db.sessionDao().observeFinished(),
        db.sessionDao().observeDraft(),
        db.profileDao().observe(),
    ) { exercises, sessions, draft, profile ->
        WorkoutState(
            exercises = exercises.map { it.toDomain() },
            sessions = sessions.map { it.toDomain() },
            activeDraft = draft?.toDomain(),
            userProfile = profile?.toDomain() ?: UserProfile(),
        )
    }

    init {
        scope.launch {
            initializeIfNeeded()
            var firstEmission = true
            combinedState.collect { state ->
                _workoutState.value = state
                if (firstEmission) {
                    firstEmission = false
                    _isReady.value = true
                }
            }
        }
    }

    /**
     * First-launch only (marker absent). The initializer must be idempotent: it runs without a
     * wrapping transaction and the marker is written LAST, so a crash mid-init simply retries on
     * the next launch instead of leaving a half-initialized DB behind a "done" flag.
     */
    private suspend fun initializeIfNeeded() {
        try {
            if (db.appMetaDao().get(AppMetaEntity.KEY_DATA_INITIALIZED) != null) return
            initializer(db)
            db.appMetaDao().put(AppMetaEntity(AppMetaEntity.KEY_DATA_INITIALIZED, "true"))
        } catch (e: Exception) {
            // Never block readiness on a failed first-run init: the app starts (empty) rather than
            // hanging on the splash screen, and the absent marker retries next launch.
            e.printStackTrace()
        }
    }

    override fun getLatestState(): WorkoutState = _workoutState.value

    override suspend fun addExercise(exercise: Exercise) {
        val existing = db.exerciseDao().getById(exercise.id)
        val timestamp = now()
        db.exerciseDao().save(
            exercise.toEntity(
                isArchived = existing?.isArchived ?: false,
                createdAt = existing?.createdAt ?: timestamp,
                updatedAt = timestamp,
            ),
            exercise.toMuscleRows(),
        )
    }

    override suspend fun deleteExercise(exerciseId: String) {
        db.exerciseDao().deleteOrArchive(exerciseId, now())
    }

    override suspend fun addWorkoutSession(session: WorkoutSession) {
        db.sessionDao().replaceContent(
            session.toEntity(SessionStatus.FINISHED, createdAt = session.startTime, updatedAt = now()),
            session.entryEntities(),
            session.setEntities(),
        )
    }

    override suspend fun updateWorkoutSession(session: WorkoutSession) {
        // Status is fixed to FINISHED: only finished sessions are edited through this path, and a
        // stray id collision with the draft must not resurrect it.
        db.sessionDao().replaceContentIfExists(
            session.toEntity(SessionStatus.FINISHED, createdAt = session.startTime, updatedAt = now()),
            session.entryEntities(),
            session.setEntities(),
        )
    }

    override suspend fun deleteWorkoutSession(sessionId: String) {
        db.sessionDao().deleteById(sessionId)
    }

    override suspend fun saveActiveDraft(session: WorkoutSession?) {
        if (session == null) {
            db.sessionDao().deleteDrafts()
        } else {
            db.sessionDao().saveDraft(
                session.toEntity(SessionStatus.DRAFT, createdAt = session.startTime, updatedAt = now()),
                session.entryEntities(),
                session.setEntities(),
            )
        }
    }

    override suspend fun saveUserProfile(profile: UserProfile) {
        val candidate = profile.bodyWeightKg?.let { weightKg ->
            BodyWeightLogEntity(
                id = UUID.randomUUID().toString(),
                weightKg = weightKg,
                measuredAt = profile.bodyWeightUpdatedAt ?: now(),
                source = profile.bodyWeightSource.name,
            )
        }
        db.profileDao().saveWithBodyWeightLog(profile.toEntity(), candidate)
    }

    override val templates: Flow<List<WorkoutTemplate>> =
        db.templateDao().observeActive().map { rows -> rows.map { it.toDomain() } }

    override val bodyWeightHistory: Flow<List<BodyWeightEntry>> =
        db.bodyWeightDao().observeAll().map { rows ->
            rows.map { row ->
                BodyWeightEntry(
                    weightKg = row.weightKg,
                    measuredAt = row.measuredAt,
                    source = BodyWeightSource.entries.firstOrNull { it.name == row.source }
                        ?: BodyWeightSource.MANUAL,
                )
            }
        }

    override suspend fun getExercise(exerciseId: String): Exercise? =
        db.exerciseDao().getWithMusclesById(exerciseId)?.toDomain()

    override suspend fun saveWorkoutTemplate(template: WorkoutTemplate) {
        val existing = db.templateDao().getById(template.id)
        val timestamp = now()
        db.templateDao().save(
            template.toEntity(createdAt = existing?.createdAt ?: timestamp, updatedAt = timestamp),
            template.entryEntities(),
        )
    }

    override suspend fun deleteWorkoutTemplate(templateId: String) {
        db.templateDao().deleteById(templateId)
    }

    private fun WorkoutSession.entryEntities() =
        exercises.mapIndexed { index, es -> es.toEntryEntity(id, index) }

    private fun WorkoutSession.setEntities() =
        exercises.flatMap { es -> es.sets.map { it.toEntity(es.id) } }

    companion object {
        @Volatile
        private var INSTANCE: RoomDataRepository? = null

        fun getInstance(context: Context): RoomDataRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RoomDataRepository(
                    db = GoatTrackerDatabase.build(context.applicationContext),
                    // First launch after the app update: migrate the legacy workouts.json (or seed
                    // the defaults when there is none). filesDir is where the legacy repo wrote.
                    initializer = LegacyJsonImporter(context.applicationContext.filesDir)::run,
                ).also { INSTANCE = it }
            }
        }
    }
}
