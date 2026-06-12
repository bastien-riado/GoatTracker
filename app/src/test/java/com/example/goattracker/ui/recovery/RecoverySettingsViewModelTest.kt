package com.example.goattracker.ui.recovery

import com.example.goattracker.MainDispatcherRule
import com.example.goattracker.data.FakeDataRepository
import com.example.goattracker.domain.model.MuscleGroup
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RecoverySettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun rows_showDefaultsUntilOverridden() = runTest {
        val viewModel = RecoverySettingsViewModel(FakeDataRepository())

        val rows = viewModel.rows.first { it.isNotEmpty() }

        assertEquals(MuscleGroup.entries.size, rows.size)
        assertTrue(rows.all { it.hours == RecoverySettingsViewModel.DEFAULT_HOURS && !it.isOverridden })
    }

    @Test
    fun adjust_persistsTheOverride_andClampsAtBounds() = runTest {
        val repository = FakeDataRepository()
        val viewModel = RecoverySettingsViewModel(repository)
        viewModel.rows.first { it.isNotEmpty() }

        viewModel.adjust(MuscleGroup.QUADS, +6)
        var quads = viewModel.rows.first { rows -> rows.any { it.group == MuscleGroup.QUADS && it.isOverridden } }
            .first { it.group == MuscleGroup.QUADS }
        assertEquals(54, quads.hours)

        // Clamp at MAX (120): big repeated increments stop there.
        repeat(20) { viewModel.adjust(MuscleGroup.QUADS, +6) }
        quads = viewModel.rows.first { rows ->
            rows.first { it.group == MuscleGroup.QUADS }.hours == RecoverySettingsViewModel.MAX_HOURS
        }.first { it.group == MuscleGroup.QUADS }
        assertEquals(RecoverySettingsViewModel.MAX_HOURS, quads.hours)
    }

    @Test
    fun adjustingBackToTheDefault_removesTheOverrideRow() = runTest {
        val repository = FakeDataRepository()
        val viewModel = RecoverySettingsViewModel(repository)
        viewModel.rows.first { it.isNotEmpty() }

        viewModel.adjust(MuscleGroup.CHEST, +6)  // 54, overridden
        viewModel.rows.first { rows -> rows.first { it.group == MuscleGroup.CHEST }.isOverridden }
        viewModel.adjust(MuscleGroup.CHEST, -6)  // back to 48 = default => row deleted

        val chest = viewModel.rows.first { rows -> !rows.first { it.group == MuscleGroup.CHEST }.isOverridden }
            .first { it.group == MuscleGroup.CHEST }
        assertEquals(RecoverySettingsViewModel.DEFAULT_HOURS, chest.hours)
        assertTrue(repository.muscleRecoveryOverrides.first().isEmpty())
    }

    @Test
    fun reset_clearsTheOverride() = runTest {
        val repository = FakeDataRepository()
        val viewModel = RecoverySettingsViewModel(repository)
        viewModel.rows.first { it.isNotEmpty() }
        viewModel.adjust(MuscleGroup.LATS, +12)
        viewModel.rows.first { rows -> rows.first { it.group == MuscleGroup.LATS }.isOverridden }

        viewModel.reset(MuscleGroup.LATS)

        val lats = viewModel.rows.first { rows -> !rows.first { it.group == MuscleGroup.LATS }.isOverridden }
            .first { it.group == MuscleGroup.LATS }
        assertFalse(lats.isOverridden)
    }
}
