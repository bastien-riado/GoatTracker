package com.example.goattracker.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.goattracker.R
import com.example.goattracker.theme.Bg
import com.example.goattracker.theme.Meta
import com.example.goattracker.theme.PremiumGradient
import androidx.compose.material3.Text

/**
 * Branded splash content shown while the app loads. Mirrors the design mockup:
 * the neon goat logo, the "GoatTracker" wordmark and a subtitle, on the dark
 * app background. Displayed by [com.example.goattracker.MainActivity] until the
 * data layer is ready, then replaced by the home screen.
 */
@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    // Entrance animation: fade in + slight upward scale (matches the mockup's fadeUp).
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        enter.animateTo(1f, animationSpec = tween(900, easing = LinearOutSlowInEasing))
    }

    // Subtle "breathing" pulse on the logo.
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Bg),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .alpha(enter.value)
                .graphicsLayer {
                    val s = 0.96f + 0.04f * enter.value
                    scaleX = s
                    scaleY = s
                    translationY = (1f - enter.value) * 48f
                },
        ) {
            Image(
                painter = painterResource(R.drawable.ic_splash),
                contentDescription = "GoatTracker",
                modifier = Modifier
                    .size(160.dp)
                    .scale(pulse),
            )
            Text(
                text = "GoatTracker",
                style = TextStyle(
                    brush = PremiumGradient,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 34.sp,
                    letterSpacing = (-0.5).sp,
                ),
                textAlign = TextAlign.Center,
            )
        }

        // Discreet attribution, kept low and unobtrusive at the bottom of the screen.
        Text(
            text = "Powered by DrPixel",
            color = Meta,
            fontSize = 10.sp,
            letterSpacing = 0.5.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
                .alpha(0.6f * enter.value),
        )
    }
}
