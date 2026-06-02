package com.example.goattracker

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.example.goattracker.ui.live.RestTimerManager
import com.example.goattracker.ui.main.MainScreen
import com.example.goattracker.ui.create.CreateExerciseScreen
import com.example.goattracker.ui.live.LiveWorkoutScreen
import com.example.goattracker.ui.profile.ProfileScreen
import com.example.goattracker.ui.profile.SessionsListScreen
import com.example.goattracker.ui.exercise.ExerciseDetailScreen

@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(Main)

  // Observe pending navigation from notification intent
  val context = LocalContext.current
  val activity = context as? MainActivity
  val pendingNav by (activity?.pendingNavigation
      ?: kotlinx.coroutines.flow.MutableStateFlow<String?>(null)).collectAsState()

  LaunchedEffect(pendingNav) {
      if (pendingNav == RestTimerManager.NAV_LIVE_WORKOUT) {
          // Navigate to live workout if not already there
          val alreadyOnLive = backStack.any { it is LiveWorkout }
          if (!alreadyOnLive) {
              backStack.add(LiveWorkout())
          }
          activity?.consumeNavigation()
      }
  }

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    // Scope ViewModels to each nav entry (not the Activity). Without the ViewModelStore decorator
    // every viewModel{} resolves to the Activity store, so e.g. LiveWorkoutViewModel survived a
    // pop and was reused for the next session — leaving a stale session and a frozen timer until
    // the process was restarted. With per-entry scoping the VM is cleared on pop and recreated fresh.
    // (The scene-setup decorator is applied internally by NavDisplay; we only add the public
    // saved-state + view-model decorators here.)
    entryDecorators = listOf(
        rememberSaveableStateHolderNavEntryDecorator(),
        rememberViewModelStoreNavEntryDecorator(),
    ),
    entryProvider =
      entryProvider {
        entry<Main> {
          MainScreen(onItemClick = { navKey -> backStack.add(navKey) }, modifier = Modifier.safeDrawingPadding().padding(16.dp))
        }
        entry<CreateExercise> { key ->
          CreateExerciseScreen(exerciseId = key.exerciseId, onBackClick = { backStack.removeLastOrNull() })
        }
        entry<LiveWorkout> { key ->
          LiveWorkoutScreen(
              sessionId = key.sessionId,
              onSessionExit = { backStack.removeLastOrNull() },
              // Push the full create screen on top of the live session. The LiveWorkout entry stays
              // in the backstack, so the session (and its ViewModel) is preserved; on save the user
              // returns to the intact session and the new exercise is auto-added (see ViewModel).
              onCreateExercise = { backStack.add(CreateExercise()) }
          )
        }
        entry<Profile> {
          ProfileScreen(
            onBackClick = { backStack.removeLastOrNull() },
            onSessionsClick = { backStack.add(SessionsList) }
          )
        }
        entry<SessionsList> {
          SessionsListScreen(onBackClick = { backStack.removeLastOrNull() })
        }
        entry<ExerciseDetail> { key ->
          ExerciseDetailScreen(
            exerciseId = key.exerciseId,
            onBackClick = { backStack.removeLastOrNull() },
            onEditClick = { backStack.add(CreateExercise(key.exerciseId)) }
          )
        }
      },
  )
}
