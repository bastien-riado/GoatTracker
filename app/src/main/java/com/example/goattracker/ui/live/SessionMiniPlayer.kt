package com.example.goattracker.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.goattracker.theme.Accent
import com.example.goattracker.theme.AccentSecondary
import com.example.goattracker.theme.Border
import com.example.goattracker.theme.Danger
import com.example.goattracker.theme.Fg
import com.example.goattracker.theme.Muted
import com.example.goattracker.theme.SurfaceElevated

/**
 * Persistent, docked mini-player for the active session — the Deezer-style "réduit" bar. Tapping it
 * anywhere re-opens the full live screen; while a rest is running it surfaces the countdown and a
 * "Passer" control. Stateless: it renders a [MiniSessionState] and forwards intent through [onOpen]
 * and [onAction], so new controls are a button + a [SessionAction] branch, nothing more.
 */
@Composable
fun SessionMiniPlayer(
    state: MiniSessionState,
    onOpen: () -> Unit,
    onAction: (SessionAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val restRemaining = state.restRemainingSeconds
    val isResting = restRemaining != null
    val accent = if (state.isRestVibrating) Danger else if (isResting) AccentSecondary else Accent

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(SurfaceElevated)
            .border(
                width = 1.dp,
                color = if (isResting) accent.copy(alpha = 0.6f) else Border,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            )
            .clickable(onClickLabel = "Ouvrir la séance") { onOpen() }
            .semantics { contentDescription = "Séance en cours, appuyez pour rouvrir" }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Live indicator
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(accent),
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = state.name,
                color = Fg,
                maxLines = 1,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            )
            val subtitle = if (restRemaining != null) {
                "Repos ${formatClock(restRemaining)} • ${formatClock(state.elapsedSeconds)}"
            } else {
                "${formatClock(state.elapsedSeconds)} • ${state.completedExercises} ex • ${state.completedSets} séries"
            }
            Text(
                text = subtitle,
                color = if (state.isRestVibrating) Danger else Muted,
                maxLines = 1,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            )
        }

        if (isResting) {
            Text(
                text = "Passer",
                color = accent,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClickLabel = "Passer le repos") { onAction(SessionAction.SkipRest) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

/** mm:ss, or h:mm:ss once past an hour. */
private fun formatClock(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    val h = safe / 3600
    val m = (safe % 3600) / 60
    val s = safe % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
}
