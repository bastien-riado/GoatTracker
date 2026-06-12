package com.example.goattracker.data.local

import com.example.goattracker.data.dto.WorkoutStateDto
import com.example.goattracker.domain.model.WorkoutSession
import com.example.goattracker.domain.model.WorkoutState
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The entity batch a legacy [WorkoutState] turns into. Built purely by [planLegacyImport].
 * Public only because Room's generated (public) ImportDao implementation takes it as a parameter.
 */
data class LegacyImportPlan(
    val exercises: List<ExerciseEntity>,
    val muscles: List<ExerciseMuscleEntity>,
    val sessions: List<WorkoutSessionEntity>,
    val entries: List<ExerciseEntryEntity>,
    val sets: List<SetEntryEntity>,
    val profile: UserProfileEntity?,
    val bodyWeightLog: BodyWeightLogEntity?,
)

/**
 * Maps a parsed legacy state to entity rows. Pure — all the import's decisions live here, the
 * file/DB orchestration in [LegacyJsonImporter] stays thin.
 *
 * Decisions:
 * - catalog exercises keep their list order via strictly increasing `createdAt`;
 * - exercises that only exist EMBEDDED in history (deleted from the catalog before the migration)
 *   are inserted `isArchived = true`: history keeps rendering them, the catalog stays clean;
 * - entry snapshots come from the embedded copies, so renames that happened before the migration
 *   are preserved exactly as the legacy UI showed them;
 * - sessions land FINISHED, the activeDraft lands DRAFT (incomplete sets included);
 * - the profile's current weight becomes the first body_weight_log row.
 */
internal fun planLegacyImport(state: WorkoutState, now: Long): LegacyImportPlan {
    val exerciseRows = mutableListOf<ExerciseEntity>()
    val muscleRows = mutableListOf<ExerciseMuscleEntity>()
    val seenExerciseIds = mutableSetOf<String>()

    state.exercises.forEachIndexed { index, exercise ->
        if (!seenExerciseIds.add(exercise.id)) return@forEachIndexed
        val at = now + index
        exerciseRows += exercise.toEntity(isArchived = false, createdAt = at, updatedAt = at)
        muscleRows += exercise.toMuscleRows()
    }

    val allSessions: List<Pair<WorkoutSession, String>> =
        state.sessions.map { it to SessionStatus.FINISHED } +
            listOfNotNull(state.activeDraft?.let { it to SessionStatus.DRAFT })

    // Embedded copies of exercises missing from the catalog: archived, first occurrence wins.
    allSessions.forEach { (session, _) ->
        session.exercises.forEach { es ->
            if (seenExerciseIds.add(es.exercise.id)) {
                exerciseRows += es.exercise.toEntity(isArchived = true, createdAt = now, updatedAt = now)
                muscleRows += es.exercise.toMuscleRows()
            }
        }
    }

    val sessionRows = mutableListOf<WorkoutSessionEntity>()
    val entryRows = mutableListOf<ExerciseEntryEntity>()
    val setRows = mutableListOf<SetEntryEntity>()
    val seenSessionIds = mutableSetOf<String>()

    allSessions.forEach { (session, status) ->
        if (!seenSessionIds.add(session.id)) return@forEach
        sessionRows += session.toEntity(
            status = status,
            createdAt = session.startTime,
            updatedAt = session.endTime ?: session.startTime,
        )
        session.exercises.forEachIndexed { position, es ->
            entryRows += es.toEntryEntity(session.id, position)
            setRows += es.sets.map { it.toEntity(es.id) }
        }
    }

    val profileRow = state.userProfile.toEntity()
    val weightLogRow = state.userProfile.bodyWeightKg?.let { weightKg ->
        BodyWeightLogEntity(
            id = java.util.UUID.randomUUID().toString(),
            weightKg = weightKg,
            measuredAt = state.userProfile.bodyWeightUpdatedAt ?: now,
            source = state.userProfile.bodyWeightSource.name,
        )
    }

    return LegacyImportPlan(
        exercises = exerciseRows,
        muscles = muscleRows,
        sessions = sessionRows,
        entries = entryRows,
        sets = setRows,
        profile = profileRow,
        bodyWeightLog = weightLogRow,
    )
}

/**
 * One-shot migration of the legacy `workouts.json` into Room, plugged into
 * [RoomDataRepository]'s first-launch initializer slot.
 *
 * File lifecycle mirrors the legacy repository's guarantees:
 * - a successfully imported file is RENAMED to `workouts.imported-<ts>.json`, never deleted — it
 *   is the user's pre-migration backup;
 * - an unparseable file is preserved as `workouts.corrupt-<ts>.json` (the legacy corrupt-file
 *   path) and the default catalog is seeded instead;
 * - no file (fresh install) seeds the default catalog.
 *
 * Idempotent end to end: inserts are IGNOREs inside one transaction and the rename happens after
 * the commit, so a crash anywhere simply re-runs to convergence on the next launch (the repository
 * only writes its init marker once this returns).
 */
internal class LegacyJsonImporter(
    private val storageDir: File?,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun run(db: GoatTrackerDatabase) {
        val stateFile = storageDir?.let { File(it, LEGACY_FILE_NAME) }
        if (stateFile == null || !stateFile.exists()) {
            // An imported-backup without a workouts.json means a previous run crashed between the
            // file rename and the init marker: the data is already in the DB. Seeding here would
            // dump the presets on top of the user's imported catalog.
            val alreadyImported = storageDir?.listFiles()
                ?.any { it.name.startsWith(IMPORTED_PREFIX) } == true
            if (!alreadyImported) {
                DefaultSeed.seed(db, now())
            }
            return
        }

        val state = try {
            json.decodeFromString<WorkoutStateDto>(stateFile.readText()).toDomain()
        } catch (e: Exception) {
            e.printStackTrace()
            moveAside(stateFile, "workouts.corrupt-${now()}.json")
            DefaultSeed.seed(db, now())
            return
        }

        db.importDao().importAll(planLegacyImport(state, now()))
        moveAside(stateFile, "$IMPORTED_PREFIX${now()}.json")
    }

    /** Best-effort rename-with-copy-fallback, same approach as the legacy corrupt-file backup. */
    private fun moveAside(file: File, newName: String) {
        try {
            val target = File(file.parentFile, newName)
            if (!file.renameTo(target)) {
                file.copyTo(target, overwrite = true)
                file.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        const val LEGACY_FILE_NAME = "workouts.json"
        const val IMPORTED_PREFIX = "workouts.imported-"
    }
}
