package com.example.goattracker.ui.live

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.goattracker.theme.Border
import com.example.goattracker.theme.Fg
import com.example.goattracker.theme.PremiumGradient

/**
 * Persistent mini-player for the active session, skinned EXACTLY like the home "Démarrer une
 * séance" button (56dp gradient pill, 12dp corners, thin border). It is rendered ONCE, at the
 * navigation root, so it visually persists across every screen change — the Deezer bar feel.
 *
 * One line, three zones: play icon pinned left, chrono + a small "Séance en cours" hint in the
 * middle, chevron pinned right. While a rest runs the middle swaps to "Repos mm:ss" and the chevron
 * to a "Passer" control, with a small vertical slide inside the pill (keyed on the BOOLEAN so the
 * per-second countdown recomposes the text without replaying the slide).
 */
@Composable
fun SessionMiniPlayer(
    state: MiniSessionState,
    onOpen: () -> Unit,
    onAction: (SessionAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onOpen,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .border(1.dp, Border, RoundedCornerShape(12.dp))
            .semantics { contentDescription = "Séance en cours, appuyez pour rouvrir" },
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        contentPadding = PaddingValues(0.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PremiumGradient)
        ) {
            // The play icon stays fixed while the rest of the line swaps states.
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Fg,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 16.dp)
                    .size(24.dp)
            )

            AnimatedContent(
                targetState = state.restRemainingSeconds != null,
                transitionSpec = {
                    (slideInVertically { it / 2 } + fadeIn())
                        .togetherWith(slideOutVertically { -it / 2 } + fadeOut())
                },
                label = "miniPlayerContent",
                modifier = Modifier.fillMaxSize()
            ) { resting ->
                Box(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when {
                                !resting -> formatClock(state.elapsedSeconds)
                                state.isRestVibrating -> "Repos terminé !"
                                else -> "Repos ${formatClock(state.restRemainingSeconds ?: 0)}"
                            },
                            color = Fg,
                            maxLines = 1,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        if (!resting) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Séance en cours",
                                color = Fg.copy(alpha = 0.85f),
                                maxLines = 1,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
                            )
                        }
                    }

                    if (!resting) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = null,
                            tint = Fg,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 16.dp)
                                .size(22.dp)
                        )
                    } else {
                        Text(
                            text = "Passer",
                            color = Fg,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 10.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, Fg.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                                .clickable(onClickLabel = "Passer le repos") {
                                    onAction(SessionAction.SkipRest)
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Same 56dp gradient pill as the mini-player / home start button, hosting a plain centered action.
 * Used by the navigation-root bottom slot when the live screen is on top: the mini-player morphs
 * into the "Ajouter un exercice" control (the inverse of the home-screen swap).
 */
@Composable
fun GradientPillButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .border(1.dp, Border, RoundedCornerShape(12.dp)),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        contentPadding = PaddingValues(0.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PremiumGradient),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Fg,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = text,
                    color = Fg,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
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
