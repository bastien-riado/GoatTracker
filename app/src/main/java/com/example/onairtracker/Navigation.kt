package com.example.onairtracker

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.onairtracker.ui.live.RestTimerManager
import com.example.onairtracker.ui.main.MainScreen
import com.example.onairtracker.ui.create.CreateExerciseScreen
import com.example.onairtracker.ui.live.LiveWorkoutScreen
import com.example.onairtracker.ui.profile.ProfileScreen
import com.example.onairtracker.ui.profile.SessionsListScreen
import com.example.onairtracker.ui.exercise.ExerciseDetailScreen

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
              onSessionExit = { backStack.removeLastOrNull() }
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
