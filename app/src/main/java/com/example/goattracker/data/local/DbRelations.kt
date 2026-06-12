package com.example.goattracker.data.local

import androidx.room.Embedded
import androidx.room.Relation

/**
 * Read projections. NOTE: Room does NOT guarantee the order of @Relation lists — the mappers sort
 * by position/setNumber, never rely on these lists' order directly.
 */
data class ExerciseWithMuscles(
    @Embedded val exercise: ExerciseEntity,
    @Relation(parentColumn = "id", entityColumn = "exerciseId")
    val muscles: List<ExerciseMuscleEntity>,
)

data class EntryWithSets(
    @Embedded val entry: ExerciseEntryEntity,
    @Relation(parentColumn = "id", entityColumn = "entryId")
    val sets: List<SetEntryEntity>,
    /**
     * The live exercise row (never null in practice: RESTRICT + archiving guarantee the row
     * outlives its references; nullable because Room models a missing parent as null, and a
     * defensive mapper beats a crash on a hand-edited DB).
     */
    @Relation(entity = ExerciseEntity::class, parentColumn = "exerciseId", entityColumn = "id")
    val exercise: ExerciseWithMuscles?,
)

data class SessionWithContent(
    @Embedded val session: WorkoutSessionEntity,
    @Relation(entity = ExerciseEntryEntity::class, parentColumn = "id", entityColumn = "sessionId")
    val entries: List<EntryWithSets>,
)

data class TemplateWithEntries(
    @Embedded val template: WorkoutTemplateEntity,
    @Relation(parentColumn = "id", entityColumn = "templateId")
    val entries: List<TemplateEntryEntity>,
)
