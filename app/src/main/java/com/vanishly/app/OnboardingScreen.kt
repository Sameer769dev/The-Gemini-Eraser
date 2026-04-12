package com.vanishly.app

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import kotlinx.coroutines.launch

// ── Persistence ──────────────────────────────────────────────────────────────
private const val PREFS_OB = "onboarding"
private const val KEY_SEEN = "has_seen"

fun hasSeenOnboarding(ctx: Context) =
    ctx.getSharedPreferences(PREFS_OB, Context.MODE_PRIVATE).getBoolean(KEY_SEEN, false)

fun markOnboardingSeen(ctx: Context) =
    ctx.getSharedPreferences(PREFS_OB, Context.MODE_PRIVATE)
        .edit().putBoolean(KEY_SEEN, true).apply()

// ── Slide data ────────────────────────────────────────────────────────────────
private data class LuxSlide(
    val emoji:         String,
    val title:         String,
    val body:          String,
    val accent:        Color,
    // Aurora tint for this slide's background (shifts per slide)
    val auroraShift:   Color,
)

private val SLIDES
    @Composable get() = listOf(
    LuxSlide(
        emoji      = "✦",
        title      = "Vanishly",
        body       = "AI-powered object & blemish removal.\nPremium results in seconds.",
        accent     = MaterialTheme.colorScheme.primary,
        auroraShift = Color(0xFF3A1800),
    ),
    LuxSlide(
        emoji      = "🖌",
        title      = "Brush with Precision",
        body       = "Pinch to zoom in with 2 fingers.\nBrush exactly what you want removed.",
        accent     = MaterialTheme.colorScheme.secondary,
        auroraShift = Color(0xFF2A1000),
    ),
    LuxSlide(
        emoji      = "⚡",
        title      = "Seamless AI Results",
        body       = "Our AI fills the gap as if it\nwas never there. Then drag to compare.",
        accent     = MaterialTheme.colorScheme.tertiary,
        auroraShift = Color(0xFF300A15),
    ),
    LuxSlide(
        emoji      = "✨",
        title      = "Save in Stunning Quality",
        body       = "PRO members save at 2× or 4× higher\nresolution using on-server AI upscaling —\nlike Upscayl, built right into your saves.",
        accent     = Color(0xFF8B5CF6),
        auroraShift = Color(0xFF0D0820),
    ),
)

// ── Screen ────────────────────────────────────────────────────────────────────
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val ctx        = LocalContext.current
    val scope      = rememberCoroutineScope()
    val slidesList = SLIDES
    val pagerState = rememberPagerState { slidesList.size }
    val page       = pagerState.currentPage
    val slide      = slidesList[page]
    val isLast     = page == slidesList.lastIndex

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Per-slide tint overlay for colour temperature shift
        val tintAlpha by animateFloatAsState(1f, label = "tint")
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        listOf(slide.auroraShift.copy(0.25f), Color.Transparent),
                        center = Offset.Zero, radius = 1200f
                    )
                )
        )

        HorizontalPager(
            state    = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { i ->
            SlideContent(slide = SLIDES[i])
        }

        // ── Bottom controls ───────────────────────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 44.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Liquid pill page indicators
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(SLIDES.size) { i ->
                    val isCurrent = i == page
                    val w by animateDpAsState(
                        if (isCurrent) 32.dp else 8.dp,
                        spring(Spring.DampingRatioMediumBouncy),
                        label = "dot_w"
                    )
                    val dotAlpha by animateFloatAsState(
                        if (isCurrent) 1f else 0.35f, label = "dot_a"
                    )
                    Box(
                        Modifier
                            .height(8.dp)
                            .width(w)
                            .clip(CircleShape)
                            .background(SLIDES[i].accent.copy(alpha = dotAlpha))
                    )
                }
            }

            // CTA button — M3 Button with slide accent color
            Button(
                onClick = {
                    if (isLast) { markOnboardingSeen(ctx); onFinished() }
                    else scope.launch { pagerState.animateScrollToPage(page + 1) }
                },
                modifier = Modifier
                    .fillMaxWidth(0.70f)
                    .height(56.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = slide.accent)
            ) {
                Text(
                    if (isLast) "Get Started  →" else "Next  →",
                    fontWeight = FontWeight.Bold,
                    fontSize   = 16.sp
                )
            }

            // Skip
            TextButton(
                onClick = { markOnboardingSeen(ctx); onFinished() }
            ) {
                Text("Skip", color = MaterialTheme.colorScheme.outline, fontSize = 13.sp)
            }
        }
    }
}

// ── Single slide ──────────────────────────────────────────────────────────────
@Composable
private fun SlideContent(slide: LuxSlide) {
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        0.88f, 1.04f,
        infiniteRepeatable(tween(1800, easing = EaseInOut), RepeatMode.Reverse),
        label = "pulse"
    )

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // M3 Surface card
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // Emoji icon — pulsing scale
                Text(
                    slide.emoji,
                    fontSize = 64.sp,
                    modifier = Modifier.scale(pulse)
                )

                Spacer(Modifier.height(24.dp))

                // Accent line
                Box(
                    Modifier
                        .width(40.dp)
                        .height(2.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, slide.accent, Color.Transparent)
                            )
                        )
                )

                Spacer(Modifier.height(20.dp))

                Text(
                    slide.title,
                    fontSize   = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onBackground,
                    textAlign  = TextAlign.Center
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    slide.body,
                    fontSize   = 15.sp,
                    color      = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign  = TextAlign.Center,
                    lineHeight = 22.sp
                )

                Spacer(Modifier.height(4.dp))
            }
        }
    }
}


