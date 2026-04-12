package com.vanishly.app

import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import coil.compose.AsyncImage

data class GalleryImage(val id: Int, val uri: Uri)

// ----------------------------------------------------
// GLASS LIMIT DIALOG
// ----------------------------------------------------
/**
 * Thin wrapper — forwards to the premium [PremiumLimitDialog] defined in
 * PaywallScreen.kt so that all existing call-sites continue to compile
 * without any changes.
 */
@Composable
fun DailyLimitDialog(limit: Int, onDismiss: () -> Unit) {
    PremiumLimitDialog(
        limit     = limit,
        onDismiss = onDismiss,
        onUpgrade = onDismiss   // MainActivity wires the real paywall via its own showPaywall flag
    )
}


// ----------------------------------------------------
// GALLERY SCREEN
// ----------------------------------------------------
@Composable
fun GalleryScreen(images: List<GalleryImage>, onPickImage: (Uri) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(top = 90.dp, bottom = 120.dp, start = 8.dp, end = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(images, key = { it.id }) { img ->
            Surface(
                modifier = Modifier
                    .aspectRatio(1f)
                    .clickable { onPickImage(img.uri) },
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                AsyncImage(
                    model = img.uri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(1.dp)
                        .clip(RoundedCornerShape(7.dp))
                )
            }
        }
    }
}

// ----------------------------------------------------
// EMPTY STATE (Fallback)
// ----------------------------------------------------
@Composable
fun EmptyState(onPickImage: () -> Unit) {
    val anim = rememberInfiniteTransition(label = "emptyHero")
    val glowScale by anim.animateFloat(
        0.80f, 1.0f,
        infiniteRepeatable(tween(2800, easing = EaseInOut), RepeatMode.Reverse),
        label = "gscale"
    )
    val glowAlpha by anim.animateFloat(
        0.20f, 0.55f,
        infiniteRepeatable(tween(2800, easing = EaseInOut), RepeatMode.Reverse),
        label = "galpha"
    )

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {

            // ----------------------------------------------------
            Box(
                Modifier.size(190.dp).clickable { onPickImage() },
                contentAlignment = Alignment.Center,
            ) {
                // ----------------------------------------------------
                Box(
                    Modifier
                        .size(170.dp)
                        .scale(glowScale)
                        .background(
                            Brush.radialGradient(
                                colorStops = arrayOf(
                                    0f   to MaterialTheme.colorScheme.primary.copy(alpha = glowAlpha * 0.50f),
                                    0.5f to MaterialTheme.colorScheme.primary.copy(alpha = glowAlpha * 0.08f),
                                    1f   to Color.Transparent,
                                )
                            )
                        )
                )
                // Mid definition ring
                Box(
                    Modifier
                        .size(112.dp)
                        .clip(CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f), CircleShape)
                )
                // Inner glass icon circle
                Surface(
                    modifier = Modifier.size(82.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.AddPhotoAlternate, null,
                        tint     = Color.White,
                        modifier = Modifier.size(38.dp),
                    )
                }
                    }
                }

            Spacer(Modifier.height(28.dp))

            // ----------------------------------------------------
            Text(
                "Access Needed",
                color         = Color.White,
                fontWeight    = FontWeight.Bold,
                fontSize      = 30.sp,
                letterSpacing = (-0.8f).sp,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Please grant photo access so you\ncan choose images to edit.",
                color      = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize   = 14.sp,
                textAlign  = TextAlign.Center,
                lineHeight = 21.sp,
            )
            
            Spacer(Modifier.height(36.dp))

            androidx.compose.material3.Button(
                onClick = onPickImage,
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(54.dp),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    "Grant Access",
                    fontWeight = FontWeight.Bold,
                    fontSize   = 15.sp,
                    textAlign  = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun HowItWorksStep(number: String, text: String) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Filled gold gradient badge
        Box(
            Modifier
                .size(27.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer))
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(number, color = Color(0xFF1A0600), fontSize = 11.sp, fontWeight = FontWeight.Black)
        }
        Text(
            text,
            color      = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize   = 14.sp,
            lineHeight = 19.sp,
            modifier   = Modifier.weight(1f),
        )
    }
}

// ── Shared Pro Brand Tokens ───────────────────────────────────────────────────
// Gold palette used consistently across every screen for Pro UI
val ProGoldDark   = Color(0xFFD97706)
val ProGoldMid    = Color(0xFFF59E0B)
val ProGoldLight  = Color(0xFFFFD60A)
val ProTextDark   = Color(0xFF1A0A00)

/**
 * The canonical "Go PRO" shimmer button. Use this in every header / toolbar.
 * [shimmerOffset] should come from an InfiniteTransition animating -300f → 300f.
 * [glowAlpha]    should come from an InfiniteTransition animating 0.35f → 0.75f.
 */
@Composable
fun GoPROButton(
    onClick: () -> Unit,
    shimmerOffset: Float,
    glowAlpha: Float,
    compact: Boolean = true,          // true = small pill (headers); false = larger (full hero)
    modifier: Modifier = Modifier
) {
    val height = if (compact) 36.dp else 44.dp
    val iconSize = if (compact) 15.dp else 18.dp
    val fontSize = if (compact) 12.sp else 14.sp
    val hPad    = if (compact) 14.dp else 18.dp
    val radius  = if (compact) 18.dp else 22.dp

    val goldBrush = Brush.linearGradient(
        colors = listOf(ProGoldDark, ProGoldMid, ProGoldLight, ProGoldMid, ProGoldDark),
        start  = Offset(shimmerOffset - 120f, 0f),
        end    = Offset(shimmerOffset + 120f, 0f)
    )

    androidx.compose.material3.Button(
        onClick      = onClick,
        colors       = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor   = ProTextDark
        ),
        contentPadding = PaddingValues(horizontal = hPad, vertical = 0.dp),
        modifier = modifier
            .height(height)
            .background(brush = goldBrush, shape = RoundedCornerShape(radius))
            .drawWithContent {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ProGoldMid.copy(alpha = glowAlpha * 0.7f),
                            Color.Transparent
                        ),
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = size.maxDimension * 1.2f
                    ),
                    radius = size.maxDimension * 1.2f
                )
                drawContent()
            },
        shape     = RoundedCornerShape(radius),
        elevation = androidx.compose.material3.ButtonDefaults.buttonElevation(0.dp, 0.dp)
    ) {
        androidx.compose.material3.Icon(
            Icons.Default.WorkspacePremium,
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            tint     = ProTextDark
        )
        Spacer(Modifier.width(4.dp))
        Text(
            "Go PRO",
            fontWeight    = FontWeight.ExtraBold,
            fontSize      = fontSize,
            color         = ProTextDark,
            letterSpacing = 0.5.sp
        )
    }
}

/**
 * Small gold-outlined "PRO" pill badge. Used in the Paywall header.
 */
@Composable
fun ProBadgePill(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(
                Brush.linearGradient(listOf(ProGoldDark, ProGoldMid, ProGoldLight)),
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("✦", color = ProTextDark, fontSize = 11.sp, fontWeight = FontWeight.Black)
            Text(
                "GEMINI ERASER PRO",
                color         = ProTextDark,
                fontWeight    = FontWeight.ExtraBold,
                fontSize      = 12.sp,
                letterSpacing = 2.sp
            )
        }
    }
}
