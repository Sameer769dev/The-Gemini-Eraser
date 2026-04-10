package com.geminieraser.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun FullScreenPaywall(
    onDismiss: () -> Unit,
    onSubscribe: () -> Unit
) {
    var showCloseButton by remember { mutableStateOf(false) }

    // Delay showing the close button by 2 seconds to ensure users read the prompt
    LaunchedEffect(Unit) {
        delay(2000)
        showCloseButton = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF09090B)) // Dark sleek background
    ) {
        // Gradient overlay for premium feel
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF4C1D95).copy(alpha = 0.4f), Color.Transparent),
                        radius = 1200f
                    )
                )
        )

        // Close Button
        AnimatedVisibility(
            visible = showCloseButton,
            enter = fadeIn(animationSpec = tween(500)),
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(16.dp)
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f))
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // Premium Badge
            Box(
                modifier = Modifier
                    .background(Brush.linearGradient(listOf(Color(0xFF7C3AED), Color(0xFF06B6D4))), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("GEMINI ERASER PRO", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 2.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "Unlock Unlimited\nPerfect Removals",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                lineHeight = 34.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Features List
            Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(horizontal = 8.dp)) {
                PaywallFeatureRow("Unlimited HD Object Removals")
                PaywallFeatureRow("Zero Ads & No Interruptions")
                PaywallFeatureRow("Faster Priority Processing")
                PaywallFeatureRow("Weekly feature updates")
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Pricing Tiers (Mocked display)
            var selectedTier by remember { mutableStateOf(1) } // 0 = Weekly, 1 = Yearly

            PricingCard(
                title = "Yearly Access",
                price = "$29.99 / year",
                subtitle = "Just $0.57 per week",
                tag = "BEST VALUE",
                isSelected = selectedTier == 1,
                onClick = { selectedTier = 1 }
            )

            Spacer(modifier = Modifier.height(12.dp))

            PricingCard(
                title = "Weekly Access",
                price = "$4.99 / week",
                subtitle = "Cancel anytime",
                tag = null,
                isSelected = selectedTier == 0,
                onClick = { selectedTier = 0 }
            )

            Spacer(modifier = Modifier.weight(1f))

            // CTA Button
            Button(
                onClick = onSubscribe,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Continue", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Legal Footer
            Text(
                text = "By continuing you agree to our Terms of Service and Privacy Policy.",
                color = Color.Gray,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun PaywallFeatureRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(Color(0xFF8B5CF6).copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(16.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun PricingCard(
    title: String,
    price: String,
    subtitle: String,
    tag: String?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) Color(0xFF8B5CF6) else Color(0xFF27272A)
    val bgColor = if (isSelected) Color(0xFF8B5CF6).copy(alpha = 0.1f) else Color(0xFF18181B)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, borderColor, RoundedCornerShape(16.dp))
            .background(bgColor, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Radio button placeholder
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .border(2.dp, if (isSelected) Color(0xFF8B5CF6) else Color.Gray, CircleShape)
                    .background(if (isSelected) Color(0xFF8B5CF6) else Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    if (tag != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF10B981), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(tag, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(subtitle, color = Color(0xFFA1A1AA), fontSize = 12.sp)
            }

            Text(price, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}
