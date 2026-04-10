package com.geminieraser.app

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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import coil.compose.AsyncImage

// ----------------------------------------------------
// GLASS LIMIT DIALOG
// ----------------------------------------------------
@Composable
fun DailyLimitDialog(limit: Int, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("✨", fontSize = 38.sp, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Daily Limit Reached",
                    color      = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 18.sp,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "You've used all $limit free erases today.\nCome back tomorrow for $limit more ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â completely free!",
                    color      = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize   = 14.sp,
                    textAlign  = TextAlign.Center,
                    lineHeight = 21.sp,
                )
                Spacer(Modifier.height(24.dp))
                androidx.compose.material3.Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Got it", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
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

// ----------------------------------------------------

