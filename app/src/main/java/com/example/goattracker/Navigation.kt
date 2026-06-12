package com.example.goattracker

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.goattracker.theme.Bg
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.example.goattracker.ui.live.ActiveSessionController
import com.example.goattracker.ui.live.GradientPillButton
import com.example.goattracker.ui.live.RestTimerManager
import com.example.goattracker.ui.live.SessionAction
import com.example.goattracker.ui.live.SessionMiniPlayer
import com.example.goattracker.ui.main.MainScreen
import com.example.goattracker.ui.create.CreateExerciseScreen
import com.example.goattracker.ui.live.LiveWorkoutScreen
import com.example.goattracker.ui.profile.ProfileScreen
import com.example.goattracker.ui.profile.SessionsListScreen
import com.example.goattracker.ui.bodyheatmap.BodyHeatmapScreen
import com.example.goattracker.ui.exercise.ExerciseDetailScreen
import com.example.goattracker.ui.settings.SettingsScreen
import com.example.goattracker.ui.settings.PatchNotesScreen
import com.example.goattracker.ui.sessiondetail.SessionDetailScreen
import com.example.goattracker.ui.templates.TemplateEditorScreen
import com.example.goattracker.ui.templates.TemplatesScreen

@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(Main)

  // Observe pending navigation from notification intent
  val context = LocalContext.current
  val activity = context as? MainActivity
  val pendingNav by (activity?.pendingNavigation
      ?: kotlinx.coroutines.flow.MutableStateFlow<String?>(null)).collectAsStateWithLifecycle()

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

  // App-scoped active-session presence. On the home screen the mini-player takes the place of the
  // "Démarrer une séance" button (passed into MainScreen below); on every other screen except the
  // live one it is docked at the bottom with the same skin. Leaving the live screen just pops it —
  // the session keeps living in the persisted draft and resurfaces here.
  val controller = remember(context) { ActiveSessionController.getInstance(context) }
  val miniState by controller.miniState.collectAsStateWithLifecycle()
  val topKey = backStack.lastOrNull()

  // Bring the live screen back: pop anything stacked above an existing LiveWorkout entry (e.g. the
  // in-session create-exercise flow), or push a fresh entry when none is in the stack.
  val openSession: () -> Unit = {
      if (backStack.any { it is LiveWorkout }) {
          while (backStack.isNotEmpty() && backStack.lastOrNull() !is LiveWorkout) {
              backStack.removeLastOrNull()
          }
      } else {
          backStack.add(LiveWorkout())
      }
  }

  Box(modifier = Modifier.fillMaxSize()) {
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
          MainScreen(
              onItemClick = { navKey -> backStack.add(navKey) },
              modifier = Modifier.safeDrawingPadding().padding(16.dp),
              // During a session the nav-root bottom slot (below) takes over the button's spot with
              // the mini-player — MainScreen only hides its own "Démarrer une séance".
              hasActiveSession = miniState != null
          )
        }
        entry<CreateExercise> { key ->
          CreateExerciseScreen(exerciseId = key.exerciseId, onBackClick = { backStack.removeLastOrNull() })
        }
        entry<LiveWorkout>(
          // "Re-enter the session": the live screen slides up from the bottom — out of the
          // mini-player — and slides back down into it when minimized (z-order keeps it on top
          // during both transitions).
          metadata = NavDisplay.transitionSpec {
              ContentTransform(
                  targetContentEnter = slideInVertically(tween(360)) { it } + fadeIn(tween(180)),
                  initialContentExit = fadeOut(tween(360)),
                  targetContentZIndex = 1f
              )
          } + NavDisplay.popTransitionSpec {
              ContentTransform(
                  targetContentEnter = fadeIn(tween(220)),
                  initialContentExit = slideOutVertically(tween(320)) { it } + fadeOut(tween(320)),
                  targetContentZIndex = -1f
              )
          }
        ) { key ->
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
            onSessionsClick = { backStack.add(SessionsList) },
            onBodyHeatmapClick = { backStack.add(BodyHeatmap) },
            onSettingsClick = { backStack.add(Settings) },
            onTemplatesClick = { backStack.add(Templates) }
          )
        }
        entry<Templates> {
          TemplatesScreen(
            onBackClick = { backStack.removeLastOrNull() },
            onCreateClick = { backStack.add(TemplateEditor()) },
            onEditClick = { id -> backStack.add(TemplateEditor(id)) },
            // The launcher has already persisted (and awaited) the pre-filled draft; opening the
            // live screen makes startOrResumeSession adopt it like any process-death draft.
            onLaunched = openSession,
            hasActiveSession = miniState != null,
          )
        }
        entry<TemplateEditor> { key ->
          TemplateEditorScreen(
            templateId = key.templateId,
            onBackClick = { backStack.removeLastOrNull() }
          )
        }
        entry<SessionsList> {
          SessionsListScreen(
            onBackClick = { backStack.removeLastOrNull() },
            onSessionClick = { id -> backStack.add(SessionDetail(id)) }
          )
        }
        entry<SessionDetail> { key ->
          SessionDetailScreen(
            sessionId = key.sessionId,
            onBackClick = { backStack.removeLastOrNull() },
            onExerciseClick = { id -> backStack.add(ExerciseDetail(id)) }
          )
        }
        entry<BodyHeatmap> {
          BodyHeatmapScreen(onBackClick = { backStack.removeLastOrNull() })
        }
        entry<ExerciseDetail> { key ->
          ExerciseDetailScreen(
            exerciseId = key.exerciseId,
            onBackClick = { backStack.removeLastOrNull() },
            onEditClick = { backStack.add(CreateExercise(key.exerciseId)) }
          )
        }
        entry<Settings> {
          SettingsScreen(
            onBackClick = { backStack.removeLastOrNull() },
            onPatchNotesClick = { backStack.add(PatchNotes) }
          )
        }
        entry<PatchNotes> {
          PatchNotesScreen(onBackClick = { backStack.removeLastOrNull() })
        }
      },
  )

    // THE bottom slot: one persistent pill rendered once at the navigation root, so it never
    // flashes or reloads across screen changes. Geometry matches the home button slot exactly
    // (16dp screen padding + 16dp slot padding = 32dp insets). Its content morphs with the context:
    // chrono mini-player everywhere, "Ajouter un exercice" while the live screen itself is on top.
    val mini = miniState
    if (mini != null) {
      Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Bg.copy(alpha = 0.95f)),
                    startY = 0f
                )
            )
            .navigationBarsPadding()
            .padding(start = 32.dp, end = 32.dp, bottom = 32.dp, top = 16.dp)
      ) {
        AnimatedContent(
          targetState = topKey is LiveWorkout,
          transitionSpec = { fadeIn(tween(220)).togetherWith(fadeOut(tween(220))) },
          label = "bottomSlot"
        ) { onLiveScreen ->
          if (onLiveScreen) {
            GradientPillButton(
              text = "Ajouter un exercice",
              icon = Icons.Default.Add,
              onClick = { controller.dispatch(SessionAction.AddExercise) },
            )
          } else {
            SessionMiniPlayer(
              state = mini,
              onOpen = openSession,
              onAction = { controller.dispatch(it) },
            )
          }
        }
      }
    }
  }
}
