package com.example.goattracker.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ExerciseEntity::class,
        ExerciseMuscleEntity::class,
        WorkoutSessionEntity::class,
        ExerciseEntryEntity::class,
        SetEntryEntity::class,
        BodyWeightLogEntity::class,
        UserProfileEntity::class,
        WorkoutTemplateEntity::class,
        TemplateEntryEntity::class,
        MuscleRecoverySettingEntity::class,
        AppMetaEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class GoatTrackerDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun sessionDao(): SessionDao
    abstract fun profileDao(): ProfileDao
    abstract fun bodyWeightDao(): BodyWeightDao
    abstract fun templateDao(): TemplateDao
    abstract fun muscleRecoveryDao(): MuscleRecoveryDao
    abstract fun appMetaDao(): AppMetaDao
    abstract fun importDao(): ImportDao

    companion object {
        const val NAME = "goattracker.db"

        fun build(context: Context): GoatTrackerDatabase =
            Room.databaseBuilder(context.applicationContext, GoatTrackerDatabase::class.java, NAME)
                .build()
    }
}
