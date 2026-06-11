package com.example.goattracker.ui.main

import com.example.goattracker.data.DefaultDataRepository
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainScreenViewModelTest {
  @Test
  fun uiState_emitsSuccessWithExercisesAndStats() = runTest {
    val testDispatcher = UnconfinedTestDispatcher(testScheduler)
    val testScope = TestScope(testDispatcher)
    
    val repository = DefaultDataRepository(
        storageDir = null,
        dispatcher = testDispatcher,
        scope = testScope
    )
    
    val viewModel = MainScreenViewModel(repository)
    val state = viewModel.uiState.first { it is MainScreenUiState.Success }
    
    val successState = state as MainScreenUiState.Success
    assertEquals(4, successState.exercises.size)
    assertEquals("Développé Couché", successState.exercises[0].name)
    assertEquals("Tractions Pronation", successState.exercises[1].name)
    assertEquals("Squat Barre", successState.exercises[2].name)
    assertEquals("Course à pied", successState.exercises[3].name)

    // Check that stats maps were populated
    assertEquals(4, successState.exerciseStats.size)
  }
}




