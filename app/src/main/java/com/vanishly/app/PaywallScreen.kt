package com.vanishly.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay

// ─────────────────────────────────────────────────────────────────────────────
// Full-Screen Paywall — Premium Conversion-Optimized Redesign
// Research principles applied:
//   • Loss aversion: emphasise cost of NOT upgrading
//   • Anchoring: show original price crossed-out → savings pop
//   • Social proof: user count in subtitle
//   • Scarcity: "Special Offer" chip to trigger FOMO
//   • Free-trial nudge: reduce perceived risk at the CTA
//   • Trust signals: security / cancel-anytime / rating strip
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun FullScreenPaywall(
    onDismiss: () -> Unit,
    onSubscribe: (productId: String) -> Unit
) {
    var showCloseButton by remember { mutableStateOf(false) }
    var contentVisible  by remember { mutableStateOf(false) }
    var selectedTier    by remember { mutableStateOf(1) }   // 1 = Yearly (default best-value)

    LaunchedEffect(Unit) {
        delay(400); contentVisible = true
        delay(1800); showCloseButton = true
    }

    // ── Persistent animations ──────────────────────────────────────────────
    val inf = rememberInfiniteTransition(label = "paywall")
    val shimmerOffset by inf.animateFloat(
        -300f, 300f,
        infiniteRepeatable(tween(2400, easing = LinearEasing)),
        label = "shimmer"
    )
    val glowPulse by inf.animateFloat(
        0.35f, 0.80f,
        infiniteRepeatable(tween(1900, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "glow"
    )
    val crownPulse by inf.animateFloat(
        0.94f, 1.06f,
        infiniteRepeatable(tween(2200, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "crown"
    )

    Box(Modifier.fillMaxSize().background(Color(0xFF09090B))) {

        // ── Multi-stop depth gradient background ──────────────────────────
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    listOf(Color(0xFFD97706).copy(alpha = 0.10f), Color.Transparent),
                    radius = 1100f
                )
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    listOf(Color(0xFF1A0A00).copy(alpha = 0.6f), Color.Transparent),
                    center = Offset(Float.MAX_VALUE, Float.MAX_VALUE),
                    radius = 900f
                )
            )
        )

        // ── Scrollable content ────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ── Top bar: close + "Special Offer" badge ──────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 4.dp)
            ) {
                // Close button (only rendered after delay; alpha fade avoids ColumnScope error)
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF27272A).copy(alpha = if (showCloseButton) 1f else 0f))
                        .clickable(enabled = showCloseButton) { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    if (showCloseButton) {
                        Icon(
                            Icons.Default.Close, null,
                            tint = Color(0xFFA1A1AA),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }

                // Scarcity / urgency chip
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color(0xFF0F1A0F), RoundedCornerShape(20.dp))
                        .border(1.dp, Color(0xFF16A34A).copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 5.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF22C55E))
                        )
                        Text(
                            "Special Offer · Limited Time",
                            color = Color(0xFF4ADE80),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.2.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Hero: animated crown + badge + headline + social proof ──
            AnimatedVisibility(
                visible = contentVisible,
                enter = fadeIn(tween(600)) + slideInVertically(tween(600)) { 48 }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {

                    // Crown icon with pulsing glow
                    Box(Modifier.size(108.dp), contentAlignment = Alignment.Center) {
                        // Outer glow
                        Box(
                            Modifier
                                .size(108.dp)
                                .scale(crownPulse)
                                .background(
                                    Brush.radialGradient(
                                        listOf(
                                            ProGoldMid.copy(alpha = glowPulse * 0.35f),
                                            Color.Transparent
                                        )
                                    ),
                                    CircleShape
                                )
                        )
                        // Mid ring
                        Box(
                            Modifier
                                .size(80.dp)
                                .border(
                                    1.dp,
                                    Brush.linearGradient(
                                        listOf(
                                            ProGoldLight.copy(alpha = 0.6f),
                                            ProGoldDark.copy(alpha = 0.2f)
                                        )
                                    ),
                                    CircleShape
                                )
                        )
                        // Inner filled circle
                        Box(
                            Modifier
                                .size(68.dp)
                                .background(
                                    Brush.linearGradient(listOf(Color(0xFF211900), Color(0xFF2E2000))),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("👑", fontSize = 30.sp)
                        }
                    }

                    Spacer(Modifier.height(18.dp))

                    // Animated gold shimmer badge
                    Box(
                        modifier = Modifier
                            .background(
                                Brush.linearGradient(
                                    listOf(ProGoldDark, ProGoldMid, ProGoldLight, ProGoldMid, ProGoldDark),
                                    start = Offset(shimmerOffset - 120f, 0f),
                                    end   = Offset(shimmerOffset + 120f, 0f)
                                ),
                                RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 5.dp)
                    ) {
                        Text(
                            "✦  VANISHLY PRO",
                            color         = ProTextDark,
                            fontSize      = 11.sp,
                            fontWeight    = FontWeight.ExtraBold,
                            letterSpacing = 1.8.sp
                        )
                    }

                    Spacer(Modifier.height(20.dp))

                    // Main headline — outcome-focused (not feature-list)
                    Text(
                        "Erase. Enhance.\nPerfect Results.",
                        color         = Color.White,
                        fontWeight    = FontWeight.ExtraBold,
                        fontSize      = 36.sp,
                        lineHeight    = 42.sp,
                        textAlign     = TextAlign.Center,
                        letterSpacing = (-0.5).sp
                    )

                    Spacer(Modifier.height(10.dp))

                    // Social proof — trust through numbers
                    Text(
                        "Join 50,000+ users getting studio-quality\nerases & AI-upscaled saves",
                        color     = Color(0xFF71717A),
                        fontSize  = 14.sp,
                        textAlign = TextAlign.Center,
                        lineHeight= 21.sp
                    )
                }
            }

            Spacer(Modifier.height(30.dp))

            // ── 2×2 Feature Grid ────────────────────────────────────────
            AnimatedVisibility(
                visible = contentVisible,
                enter = fadeIn(tween(700, delayMillis = 150))
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ProFeatureCard("🎯", "Precision AI",     "Object-aware removal",    Modifier.weight(1f))
                        ProFeatureCard("⚡", "Priority Speed",   "2× faster processing",    Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ProFeatureCard("🚫", "Zero Ads",         "Fully uninterrupted",     Modifier.weight(1f))
                        ProFeatureCard("♾️", "Unlimited",        "No daily limits ever",    Modifier.weight(1f))
                    }

                    // AI Upscaling highlight — full-width banner card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                brush = Brush.horizontalGradient(
                                    listOf(Color(0xFF1A0D2E), Color(0xFF0F1A2E))
                                )
                            )
                            .border(
                                1.dp,
                                Brush.linearGradient(listOf(Color(0xFF8B5CF6).copy(0.7f), Color(0xFF3B82F6).copy(0.5f))),
                                RoundedCornerShape(14.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Icon
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(
                                        Brush.radialGradient(
                                            listOf(Color(0xFF8B5CF6).copy(0.25f), Color.Transparent)
                                        ),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("✦", fontSize = 22.sp, color = Color(0xFF8B5CF6))
                            }
                            // Text
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        "AI Upscaling",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                Brush.horizontalGradient(listOf(Color(0xFF8B5CF6), Color(0xFF3B82F6))),
                                                RoundedCornerShape(percent = 50)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            "PRO EXCLUSIVE",
                                            color = Color.White,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            letterSpacing = 0.5.sp
                                        )
                                    }
                                }
                                Text(
                                    "Save in 2× or 4× sharper resolution — powered by FSRCNN neural AI. Like Upscayl, built into your saves.",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            // ── Pricing Cards ────────────────────────────────────────────
            AnimatedVisibility(
                visible = contentVisible,
                enter = fadeIn(tween(700, delayMillis = 300))
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

                    // Yearly — pre-selected, best-value
                    PremiumPricingCard(
                        planName      = "Yearly",
                        priceMain     = "$29.99",
                        pricePeriod   = "/ year",
                        pricePerWeek  = "Just $0.57/week",
                        savingsLabel  = "SAVE 77%",
                        originalPrice = "$129.99",
                        isSelected    = selectedTier == 1,
                        isBestValue   = true,
                        onClick       = { selectedTier = 1 },
                        shimmerOffset = shimmerOffset
                    )

                    // Weekly
                    PremiumPricingCard(
                        planName      = "Weekly",
                        priceMain     = "$4.99",
                        pricePeriod   = "/ week",
                        pricePerWeek  = "Billed weekly",
                        savingsLabel  = null,
                        originalPrice = null,
                        isSelected    = selectedTier == 0,
                        isBestValue   = false,
                        onClick       = { selectedTier = 0 },
                        shimmerOffset = shimmerOffset
                    )
                }
            }

            Spacer(Modifier.height(22.dp))

            // ── Trust strip ││──────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF111111), RoundedCornerShape(12.dp))
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TrustChip(icon = "🔒", label = "Secure Payment")
                // divider
                Box(Modifier.width(1.dp).height(28.dp).background(Color(0xFF27272A)))
                TrustChip(icon = "↩", label = "Cancel Anytime")
                Box(Modifier.width(1.dp).height(28.dp).background(Color(0xFF27272A)))
                TrustChip(icon = "⭐", label = "4.9 Rating")
            }

            Spacer(Modifier.height(22.dp))

            // ── CTA Button with shimmer + outer glow ────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp)
                    .drawWithContent {
                        // soft glow shadow beneath the button
                        drawRect(
                            brush = Brush.radialGradient(
                                listOf(
                                    ProGoldMid.copy(alpha = glowPulse * 0.40f),
                                    Color.Transparent
                                ),
                                center = Offset(size.width / 2f, size.height / 2f),
                                radius = size.width * 0.70f
                            )
                        )
                        drawContent()
                    }
                    .background(
                        Brush.linearGradient(
                            listOf(ProGoldDark, ProGoldMid, ProGoldLight, ProGoldMid, ProGoldDark),
                            start = Offset(shimmerOffset - 160f, 0f),
                            end   = Offset(shimmerOffset + 160f, 0f)
                        ),
                        RoundedCornerShape(18.dp)
                    )
                    .clickable {
                        // Pass the product ID that matches the user's tier selection:
                        // Tier 1 = Yearly (best value, 3-day free trial)
                        // Tier 0 = Weekly
                        val productId = if (selectedTier == 1)
                            com.vanishly.app.billing.BillingManager.SUB_YEARLY
                        else
                            com.vanishly.app.billing.BillingManager.SUB_WEEKLY
                        onSubscribe(productId)
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.WorkspacePremium, null,
                        tint = ProTextDark,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        "Unlock Pro Now",
                        color      = ProTextDark,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize   = 19.sp,
                        letterSpacing = 0.2.sp
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Risk-reduction copy directly below CTA
            Text(
                "3-day free trial • No charge until ${paywallTrialEndDate()}",
                color     = Color(0xFF71717A),
                fontSize  = 12.sp,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium
            )

            Spacer(Modifier.height(14.dp))

            Text(
                "Subscriptions auto-renew unless cancelled 24h before the end of the current period. " +
                "By continuing you agree to our Terms of Service and Privacy Policy.",
                color     = Color(0xFF3F3F46),
                fontSize  = 9.5.sp,
                textAlign = TextAlign.Center,
                lineHeight = 13.5.sp,
                modifier  = Modifier.padding(horizontal = 4.dp)
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Feature Grid Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ProFeatureCard(
    emoji: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF111111))
            .border(1.dp, Color(0xFF1F1F1F), RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(emoji, fontSize = 24.sp)
            Text(title,    color = Color.White,       fontWeight = FontWeight.Bold,    fontSize = 14.sp)
            Text(subtitle, color = Color(0xFF71717A), fontWeight = FontWeight.Normal,  fontSize = 11.sp, lineHeight = 14.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Premium Pricing Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PremiumPricingCard(
    planName:      String,
    priceMain:     String,
    pricePeriod:   String,
    pricePerWeek:  String,
    savingsLabel:  String?,
    originalPrice: String?,
    isSelected:    Boolean,
    isBestValue:   Boolean,
    onClick:       () -> Unit,
    shimmerOffset: Float
) {
    val borderBrush = if (isSelected) {
        Brush.linearGradient(
            listOf(ProGoldLight, ProGoldMid, ProGoldDark, ProGoldMid, ProGoldLight),
            start = Offset(shimmerOffset - 100f, 0f),
            end   = Offset(shimmerOffset + 100f, 0f)
        )
    } else {
        Brush.linearGradient(listOf(Color(0xFF232323), Color(0xFF232323)))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(if (isSelected) 2.dp else 1.dp, borderBrush, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) Color(0xFF1A1409) else Color(0xFF0F0F0F))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── Radio indicator ──
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .let {
                        if (isSelected)
                            it.background(Brush.linearGradient(listOf(ProGoldLight, ProGoldDark)))
                        else
                            it.border(1.5.dp, Color(0xFF4A4A4A), CircleShape)
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Default.Check, null,
                        tint     = ProTextDark,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            // ── Plan name + badge ──
            Column(modifier = Modifier.weight(1f)) {
                // Title — single line; badge stacked below prevents horizontal overflow
                Text(
                    "$planName Access",
                    color      = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 15.sp,
                    maxLines   = 1
                )
                if (isBestValue) {
                    Spacer(Modifier.height(3.dp))
                    Box(
                        modifier = Modifier
                            .background(
                                Brush.linearGradient(listOf(Color(0xFF16A34A), Color(0xFF15803D))),
                                RoundedCornerShape(percent = 50)
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            "BEST VALUE",
                            color         = Color.White,
                            fontSize      = 8.sp,
                            fontWeight    = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(pricePerWeek, color = Color(0xFF71717A), fontSize = 11.sp)
            }

            Spacer(Modifier.width(10.dp))

            // ── Price column ── (anchored: crossed-out original + price + savings)
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (originalPrice != null) {
                    Text(
                        originalPrice,
                        color           = Color(0xFF52525B),
                        fontSize        = 11.sp,
                        textDecoration  = TextDecoration.LineThrough
                    )
                }
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        priceMain,
                        color      = if (isSelected) ProGoldLight else Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize   = 19.sp
                    )
                    Text(
                        pricePeriod,
                        color    = Color(0xFF71717A),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }
                if (savingsLabel != null) {
                    Box(
                        modifier = Modifier
                            .background(ProGoldDark.copy(alpha = 0.18f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            savingsLabel,
                            color      = ProGoldLight,
                            fontSize   = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.3.sp
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Trust Chip
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun TrustChip(icon: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, fontSize = 17.sp)
        Spacer(Modifier.height(3.dp))
        Text(label, color = Color(0xFF71717A), fontSize = 10.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Premium Daily-Limit "Go Pro" popup dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PremiumLimitDialog(
    limit: Int,
    onDismiss: () -> Unit,
    onUpgrade: () -> Unit
) {
    val inf = rememberInfiniteTransition(label = "dlg")
    val shimmer by inf.animateFloat(
        -260f, 260f,
        infiniteRepeatable(tween(2000, easing = LinearEasing)),
        label = "s"
    )
    val glow by inf.animateFloat(
        0.3f, 0.75f,
        infiniteRepeatable(tween(1600, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "g"
    )

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF111111))
                .border(
                    1.dp,
                    Brush.linearGradient(
                        listOf(ProGoldLight.copy(alpha = 0.5f), ProGoldDark.copy(alpha = 0.2f), ProGoldLight.copy(alpha = 0.5f))
                    ),
                    RoundedCornerShape(24.dp)
                )
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Icon
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(ProGoldMid.copy(alpha = glow * 0.30f), Color.Transparent)
                            ),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                Brush.linearGradient(listOf(Color(0xFF211900), Color(0xFF302100))),
                                CircleShape
                            )
                            .border(
                                1.dp,
                                Brush.linearGradient(listOf(ProGoldLight, ProGoldDark)),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("⚡", fontSize = 26.sp)
                    }
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    "Daily Limit Reached",
                    color      = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize   = 20.sp
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    "You've used all $limit free erases today.\nGo Pro for unlimited erases, zero ads,\nand AI-powered upscaling on every save.",
                    color     = Color(0xFF71717A),
                    fontSize  = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 21.sp
                )

                Spacer(Modifier.height(24.dp))

                // ── Unlock Pro CTA ──────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .background(
                            Brush.linearGradient(
                                listOf(ProGoldDark, ProGoldMid, ProGoldLight, ProGoldMid, ProGoldDark),
                                start = Offset(shimmer - 130f, 0f),
                                end   = Offset(shimmer + 130f, 0f)
                            ),
                            RoundedCornerShape(14.dp)
                        )
                        .clickable { onUpgrade() },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Icon(Icons.Default.WorkspacePremium, null, tint = ProTextDark, modifier = Modifier.size(17.dp))
                        Text(
                            "Unlock Pro",
                            color      = ProTextDark,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize   = 16.sp
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Secondary: come back tomorrow
                Text(
                    "Or come back tomorrow for $limit free erases",
                    color          = Color(0xFF52525B),
                    fontSize       = 12.sp,
                    textAlign      = TextAlign.Center,
                    modifier       = Modifier.clickable { onDismiss() }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun paywallTrialEndDate(): String {
    val cal = java.util.Calendar.getInstance()
    cal.add(java.util.Calendar.DAY_OF_YEAR, 3)
    val months = arrayOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
    return "${months[cal.get(java.util.Calendar.MONTH)]} ${cal.get(java.util.Calendar.DAY_OF_MONTH)}"
}

// ─────────────────────────────────────────────────────────────────────────────
// Legacy stubs — kept so existing call-sites compile without changes
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PaywallFeatureRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(ProGoldMid.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Check, null, tint = ProGoldDark, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(text, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
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
    PremiumPricingCard(
        planName      = title,
        priceMain     = price,
        pricePeriod   = "",
        pricePerWeek  = subtitle,
        savingsLabel  = if (tag != null) tag else null,
        originalPrice = null,
        isSelected    = isSelected,
        isBestValue   = (tag != null),
        onClick       = onClick,
        shimmerOffset = 0f
    )
}
