package com.example.goattracker.domain

import com.example.goattracker.domain.model.MuscleGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MuscleGroupMapperTest {

    @Test
    fun mapsRealWorldFrenchMuscleStrings() {
        assertEquals(MuscleGroup.CHEST, MuscleGroupMapper.map("Pectoraux"))
        assertEquals(MuscleGroup.LATS, MuscleGroupMapper.map("Dos"))
        assertEquals(MuscleGroup.QUADS, MuscleGroupMapper.map("Quadriceps"))
        assertEquals(MuscleGroup.HAMSTRINGS, MuscleGroupMapper.map("Ischio-jambiers"))
        assertEquals(MuscleGroup.BICEPS, MuscleGroupMapper.map("Biceps"))
        assertEquals(MuscleGroup.TRICEPS, MuscleGroupMapper.map("Triceps"))
        assertEquals(MuscleGroup.ABS, MuscleGroupMapper.map("Abdominaux"))
        assertEquals(MuscleGroup.CALVES, MuscleGroupMapper.map("Mollets"))
    }

    @Test
    fun isAccentAndCaseInsensitiveAndTrimmed() {
        assertEquals(MuscleGroup.FRONT_DELTS, MuscleGroupMapper.map("Épaules"))
        assertEquals(MuscleGroup.FRONT_DELTS, MuscleGroupMapper.map("EPAULES"))
        assertEquals(MuscleGroup.CHEST, MuscleGroupMapper.map("  pectoraux  "))
        assertEquals(MuscleGroup.HAMSTRINGS, MuscleGroupMapper.map("ischio jambiers"))
    }

    @Test
    fun distinguishesFrontAndRearDelts() {
        assertEquals(MuscleGroup.REAR_DELTS, MuscleGroupMapper.map("Épaules arrière"))
        assertEquals(MuscleGroup.FRONT_DELTS, MuscleGroupMapper.map("Épaules avant"))
    }

    @Test
    fun longerSynonymWinsOverSubstring() {
        // "triceps sural" is a calf muscle and must NOT be mistaken for the triceps brachial.
        assertEquals(MuscleGroup.CALVES, MuscleGroupMapper.map("triceps sural"))
    }

    @Test
    fun matchesWhenMuscleAppearsInsideALongerLabel() {
        assertEquals(MuscleGroup.CHEST, MuscleGroupMapper.map("Développé couché (pectoraux)"))
    }

    @Test
    fun unknownOrBlankReturnsNull() {
        assertNull(MuscleGroupMapper.map(null))
        assertNull(MuscleGroupMapper.map(""))
        assertNull(MuscleGroupMapper.map("   "))
        assertNull(MuscleGroupMapper.map("Cardio"))
    }

    @Test
    fun everyMuscleGroupIdRoundTrips() {
        // Guards the model<->code contract: each glTF material id resolves back to its enum.
        MuscleGroup.entries.forEach { group ->
            assertEquals(group, MuscleGroup.fromId(group.id))
        }
    }
}
