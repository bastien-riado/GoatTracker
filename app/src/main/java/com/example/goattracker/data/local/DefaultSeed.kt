package com.example.goattracker.data.local

import com.example.goattracker.domain.model.Exercise
import com.example.goattracker.domain.model.ExerciseCategory
import com.example.goattracker.domain.model.TrackingType

/**
 * The preset catalog seeded on first launch (fresh install, or corrupt/absent legacy file).
 * Moved verbatim from the legacy JSON repository so the out-of-the-box experience is unchanged —
 * except the ids: presets get FIXED ids (the legacy repo randomized them) so a crash-interrupted
 * seed retries to the exact same rows instead of minting duplicates. Existing users keep their
 * random preset ids through the JSON import; fixed ids only ever apply to fresh databases.
 */
internal object DefaultSeed {
    fun exercises(): List<Exercise> = listOf(
        Exercise(
            id = "preset-developpe-couche",
            name = "Développé Couché",
            category = ExerciseCategory.PUSH,
            primaryMuscle = "Pectoraux",
            trackingType = TrackingType.WEIGHT_REPS,
            restTimeSeconds = 120,
        ),
        Exercise(
            id = "preset-tractions-pronation",
            name = "Tractions Pronation",
            category = ExerciseCategory.PULL,
            primaryMuscle = "Dos",
            trackingType = TrackingType.BODYWEIGHT_REPS,
            restTimeSeconds = 120,
        ),
        Exercise(
            id = "preset-squat-barre",
            name = "Squat Barre",
            category = ExerciseCategory.LEG,
            primaryMuscle = "Quadriceps",
            trackingType = TrackingType.WEIGHT_REPS,
            restTimeSeconds = 150,
        ),
        Exercise(
            id = "preset-course-a-pied",
            name = "Course à pied",
            category = ExerciseCategory.CARDIO,
            primaryMuscle = "Cardio",
            trackingType = TrackingType.DISTANCE,
            restTimeSeconds = 60,
        ),
    )

    /**
     * Inserts the preset catalog with strictly increasing `createdAt` so the catalog screen's
     * insertion order matches the legacy list order. Idempotent (upsert + IGNORE): first-launch
     * init retries after a mid-seed crash.
     */
    suspend fun seed(db: GoatTrackerDatabase, now: Long) {
        exercises().forEachIndexed { index, exercise ->
            val at = now + index
            db.exerciseDao().upsert(exercise.toEntity(isArchived = false, createdAt = at, updatedAt = at))
            db.exerciseDao().insertMusclesIgnore(exercise.toMuscleRows())
        }
    }
}
