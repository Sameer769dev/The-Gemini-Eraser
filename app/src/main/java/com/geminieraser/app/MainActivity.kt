package com.geminieraser.app

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint as AndroidPaint
import android.graphics.Paint.Cap
import android.graphics.Paint.Join
import android.app.Activity
import android.content.ContextWrapper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.geminieraser.app.billing.BillingManager
import com.geminieraser.app.ads.AdManager
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items

class MainActivity : ComponentActivity() {
    private lateinit var billingManager: BillingManager
    private lateinit var adManager: AdManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        MobileAds.initialize(this) {}
        billingManager = BillingManager(this)
        adManager = AdManager(this)

        setContent {
            GeminiEraserTheme {
                GeminiEraserApp(billingManager, adManager)
            }
        }
    }
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

// ────────────────────────────────────────────────────────────────────────────
// THEME
// ────────────────────────────────────────────────────────────────────────────
@Composable
fun GeminiEraserTheme(content: @Composable () -> Unit) {
    val colorScheme = darkColorScheme(
        primary          = Color(0xFF8B5CF6),
        onPrimary        = Color.White,
        primaryContainer = Color(0xFF4C1D95),
        secondary        = Color(0xFF06B6D4),
        background       = Color(0xFF09090B),
        surface          = Color(0xFF18181B),
        outline          = Color(0xFF3F3F46),
    )
    MaterialTheme(colorScheme = colorScheme, content = content)
}

enum class ProcessingState { IDLE, PROCESSING, DONE, ERROR }

enum class SelectionMode { AI_TAP, MANUAL_BRUSH }

/**
 * A single user brush stroke.
 * [isFilled] = true when the user drew a closed loop (lasso) →
 * the interior is flood-filled on the mask, just like Photoshop's lasso tool.
 */
data class DrawnStroke(val path: Path, val isFilled: Boolean)

// ────────────────────────────────────────────────────────────────────────────
// ROOT APP
// ────────────────────────────────────────────────────────────────────────────
@Composable
fun GeminiEraserApp(billingManager: BillingManager, adManager: AdManager) {
    val context     = LocalContext.current
    val coroutine   = rememberCoroutineScope()
    val isPremium   by billingManager.isPremium.collectAsState()

    var sourceBitmap    by remember { mutableStateOf<Bitmap?>(null) }
    var resultBitmap    by remember { mutableStateOf<Bitmap?>(null) }
    var state           by remember { mutableStateOf(ProcessingState.IDLE) }
    var errorMessage    by remember { mutableStateOf("") }
    var showComparison  by remember { mutableStateOf(false) }
    var saveFeedback    by remember { mutableStateOf<Boolean?>(null) } // null = hidden, true = success, false = error
    var showPaywallScreen by remember { mutableStateOf(false) }
    var showResolutionPicker by remember { mutableStateOf(false) }
    var actionCount       by remember { mutableStateOf(0) }

    // User's drawing strokes mapped to intrinsic image coordinates
    // We maintain a list of Paths representing strokes on the real 1:1 image.
    val drawnPaths = remember { mutableStateListOf<DrawnStroke>() }
    var selectionMode       by remember { mutableStateOf(SelectionMode.MANUAL_BRUSH) }
    var segmentedMaskBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isSegmenting        by remember { mutableStateOf(false) }
    // Holds the running segmentation Job so it can be cancelled mid-flight
    var segmentationJob: kotlinx.coroutines.Job? = null

    // (MediaPipe initialization removed since we use FastSAM backend)

    if (sourceBitmap != null) {
        androidx.activity.compose.BackHandler {
            sourceBitmap        = null
            resultBitmap        = null
            state               = ProcessingState.IDLE
            showComparison      = false
            drawnPaths.clear()
            errorMessage        = ""
            saveFeedback        = null
            segmentedMaskBitmap = null
            isSegmenting        = false
        }
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        uri?.let {
            sourceBitmap        = decodeBitmapFromUri(context, it)
            resultBitmap        = null
            state               = ProcessingState.IDLE
            showComparison      = false
            drawnPaths.clear()
            segmentedMaskBitmap = null
            isSegmenting        = false
        }
    }

    fun onPickImage() {
        pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    fun onCancelSegmentation() {
        segmentationJob?.cancel()
        segmentationJob = null
        isSegmenting = false
        segmentedMaskBitmap = null
    }

    fun onAiTap(normX: Float, normY: Float) {
        val src = sourceBitmap ?: return
        // Cancel any in-flight request (re-tap on a different object)
        segmentationJob?.cancel()
        if (!isPremium) {
            showPaywallScreen = true
            return
        }
        isSegmenting = true
        segmentedMaskBitmap = null
        segmentationJob = coroutine.launch {
            try {
                val mask = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    ObjectEraser.segmentFromCloud(src, normX, normY, isPremium)
                }
                // This line only runs if not cancelled
                segmentedMaskBitmap = mask
                isSegmenting = false
                if (mask == null) {
                    Toast.makeText(context, "Couldn't detect an object — try tapping its center", Toast.LENGTH_SHORT).show()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Cancelled by user — UI was already reset in onCancelSegmentation()
            } catch (e: Exception) {
                // Network error, offline, server error etc.
                isSegmenting = false
                segmentedMaskBitmap = null
                Toast.makeText(context, e.message ?: "Couldn't reach server. Check your connection.", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun onReEdit() {
        val result = resultBitmap ?: return

        val performReEdit = {
            sourceBitmap = result      // promote result → new source
            resultBitmap = null
            drawnPaths.clear()
            showComparison = false
            state = ProcessingState.IDLE
        }

        if (isPremium) {
            performReEdit()
        } else {
            actionCount++
            if (actionCount % 3 == 0) {
                showPaywallScreen = true
            } else {
                val activity = context.findActivity()
                if (activity != null) {
                    var earned = false
                    adManager.showRewarded(
                        activity = activity,
                        onReward = {
                            earned = true
                            performReEdit()
                        },
                        onClosed = {
                            if (!earned) {
                                Toast.makeText(context, "Watch the full ad to continue editing!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onAdFailed = {
                            showPaywallScreen = true
                        }
                    )
                } else {
                    performReEdit()
                }
            }
        }
    }

    fun onErase() {
        val src = sourceBitmap ?: return

        if (segmentedMaskBitmap == null && drawnPaths.isEmpty()) {
            Toast.makeText(context, "Select an object with AI or draw over it first!", Toast.LENGTH_SHORT).show()
            return
        }

        val processImage = {
            state = ProcessingState.PROCESSING
            coroutine.launch {
                runCatching {
                    // 1. Build mask — combining AI segmentation AND manual brush strokes
                    val mask = withContext(Dispatchers.Default) {
                        renderPathsToMask(src.width, src.height, drawnPaths.toList(), segmentedMaskBitmap)
                    }
                    // 2. Call FastAPI Backend for Generative Inpainting
                    withContext(Dispatchers.IO) { ObjectEraser.erase(src, mask, isPremium) }
                }.onSuccess { result ->
                    resultBitmap        = result
                    state               = ProcessingState.DONE
                    showComparison      = true
                    drawnPaths.clear()
                    segmentedMaskBitmap = null
                }.onFailure { err ->
                    state = ProcessingState.IDLE  // Reset to IDLE so the Erase button re-enables
                    val msg = err.message ?: "Something went wrong. Please try again."
                    errorMessage = msg
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            }
        }

        if (isPremium) {
            processImage()
        } else {
            actionCount++
            if (actionCount % 3 == 0) {
                // Interval Paywall Trigger
                showPaywallScreen = true
            } else {
                val activity = context.findActivity()
                if (activity != null) {
                    adManager.showInterstitial(
                        activity,
                        onFinish = { processImage() },
                        onAdFailed = { showPaywallScreen = true } // Ad fallback trigger
                    )
                } else {
                    processImage()
                }
            }
        }
    }

    fun onSave(targetMaxPx: Int = Int.MAX_VALUE) {
        val bmp = resultBitmap ?: return

        // Scale bitmap exactly to the requested target dimension if it differs
        val origMax = maxOf(bmp.width, bmp.height)
        val finalBmp = if (targetMaxPx == Int.MAX_VALUE || targetMaxPx == origMax) {
            bmp
        } else {
            val ratio = targetMaxPx.toFloat() / origMax
            Bitmap.createScaledBitmap(bmp, (bmp.width * ratio).toInt(), (bmp.height * ratio).toInt(), true)
        }

        val saveImage = {
            coroutine.launch {
                val saved = withContext(Dispatchers.IO) { saveBitmapToGallery(context, finalBmp) }
                saveFeedback = saved
            }
        }

        if (isPremium) {
            saveImage()
        } else {
            actionCount++
            if (actionCount % 3 == 0) {
                showPaywallScreen = true
            } else {
                val activity = context.findActivity()
                if (activity != null) {
                    var earned = false
                    adManager.showRewarded(
                        activity = activity,
                        onReward = {
                            earned = true
                            saveImage()
                        },
                        onClosed = {
                            if (!earned) {
                                Toast.makeText(context, "Watch the full ad to save your image!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onAdFailed = {
                            showPaywallScreen = true
                        }
                    )
                } else {
                    saveImage()
                }
            }
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AmbientBackground()

        if (sourceBitmap == null) {
            // ── Empty state: scrollable with full hero ──────────────────────
            Column(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AppHeader(
                        isPremium = isPremium,
                        compact = false,
                        onGoPro = {
                            showPaywallScreen = true
                        }
                    )
                    Spacer(Modifier.height(16.dp))
                    InteractiveImageZone(
                        sourceBitmap          = sourceBitmap,
                        resultBitmap          = resultBitmap,
                        showComparison        = showComparison,
                        processingState       = state,
                        drawnPaths            = drawnPaths,
                        selectionMode         = selectionMode,
                        segmentedMask         = segmentedMaskBitmap,
                        isSegmenting          = isSegmenting,
                        isPremium             = isPremium,
                        onAddPath             = { path, filled -> drawnPaths.add(DrawnStroke(path, filled)) },
                        onUndo                = { if (drawnPaths.isNotEmpty()) drawnPaths.removeAt(drawnPaths.lastIndex) },
                        onPickImage           = ::onPickImage,
                        onToggleView          = { showComparison = it },
                        onAiTap               = ::onAiTap,
                        onCancelSegmentation  = ::onCancelSegmentation,
                        onModeChange          = { mode ->
                            if (mode == SelectionMode.AI_TAP && !isPremium) {
                                showPaywallScreen = true
                            } else {
                                selectionMode = mode
                                when (mode) {
                                    SelectionMode.AI_TAP       -> drawnPaths.clear()
                                    SelectionMode.MANUAL_BRUSH -> { segmentedMaskBitmap = null; isSegmenting = false }
                                }
                            }
                        }
                    )
                    Spacer(Modifier.height(32.dp))
                    HowItWorksSection()
                    Spacer(Modifier.height(24.dp))
                }
                if (!isPremium) {
                    Box(Modifier.navigationBarsPadding().fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
                        BannerAdView()
                    }
                } else {
                    Spacer(Modifier.navigationBarsPadding())
                }
            }
        } else {
            // ── Image loaded: fixed layout, image fills space, no blank gap ─
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Compact header
                AppHeader(
                    isPremium = isPremium,
                    compact = true,
                    onGoPro = { showPaywallScreen = true },
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )

                // Image zone expands to fill all remaining space
                InteractiveImageZone(
                    sourceBitmap          = sourceBitmap,
                    resultBitmap          = resultBitmap,
                    showComparison        = showComparison,
                    processingState       = state,
                    drawnPaths            = drawnPaths,
                    selectionMode         = selectionMode,
                    segmentedMask         = segmentedMaskBitmap,
                    isSegmenting          = isSegmenting,
                    isPremium             = isPremium,
                    onAddPath             = { path, filled -> drawnPaths.add(DrawnStroke(path, filled)) },
                    onUndo                = {
                        when (selectionMode) {
                            SelectionMode.AI_TAP       -> segmentedMaskBitmap = null
                            SelectionMode.MANUAL_BRUSH -> if (drawnPaths.isNotEmpty()) drawnPaths.removeAt(drawnPaths.lastIndex)
                        }
                    },
                    onPickImage           = ::onPickImage,
                    onToggleView          = { showComparison = it },
                    onAiTap               = ::onAiTap,
                    onCancelSegmentation  = ::onCancelSegmentation,
                    onModeChange          = { mode ->
                        if (mode == SelectionMode.AI_TAP && !isPremium) {
                            showPaywallScreen = true
                        } else {
                            selectionMode = mode
                            when (mode) {
                                SelectionMode.AI_TAP       -> drawnPaths.clear()
                                SelectionMode.MANUAL_BRUSH -> { segmentedMaskBitmap = null; isSegmenting = false }
                            }
                        }
                    },
                    modifier              = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                )

                // Controls pinned above banner, no blank space
                Spacer(Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when {
                        state == ProcessingState.IDLE || state == ProcessingState.ERROR -> {
                            EraseButton(isProcessing = isSegmenting, onClick = { if (!isSegmenting) onErase() })
                            if (state == ProcessingState.ERROR) {
                                Text("Error: $errorMessage", color = Color(0xFFEF4444), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                            }
                        }
                        state == ProcessingState.PROCESSING -> {
                            EraseButton(isProcessing = true, onClick = {})
                        }
                        else -> {
                            // ── Re-edit: full width on its own row ─────────
                            OutlinedButton(
                                onClick = ::onReEdit,
                                modifier = Modifier.fillMaxWidth(),
                                border = BorderStroke(
                                    1.5.dp,
                                    Brush.linearGradient(
                                        listOf(Color(0xFF7C3AED), Color(0xFF06B6D4))
                                    )
                                ),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = Color(0xFF7C3AED)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Re-edit — draw more to erase again",
                                    color = Color(0xFF7C3AED),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            // ── New + Save row ─────────────────────────────
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(onClick = ::onPickImage, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("New")
                                }
                                Button(
                                    onClick = { showResolutionPicker = true },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                                ) {
                                    Icon(Icons.Default.Download, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Save")
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))

                // Bottom Banner Ad
                if (!isPremium) {
                    Box(Modifier.navigationBarsPadding().fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
                        BannerAdView()
                    }
                } else {
                    Spacer(Modifier.navigationBarsPadding())
                }
            }
        }

        // Persistent flag to prevent text changing during exit animation
        var displaySuccess by remember { mutableStateOf(true) }
        LaunchedEffect(saveFeedback) {
            if (saveFeedback != null) {
                displaySuccess = saveFeedback == true
            }
        }

        // Beautiful Save Feedback Overlay
        AnimatedVisibility(
            visible = saveFeedback != null,
            enter = fadeIn(tween(300)) + scaleIn(tween(300, easing = Easing { android.view.animation.OvershootInterpolator().getInterpolation(it) }), initialScale = 0.8f),
            exit = fadeOut(tween(300)) + scaleOut(tween(300), targetScale = 0.8f),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                tonalElevation = 8.dp,
                modifier = Modifier.padding(32.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(if (displaySuccess) Color(0xFF10B981).copy(alpha=0.2f) else Color(0xFFEF4444).copy(alpha=0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (displaySuccess) Icons.Default.Check else Icons.Default.Close,
                            contentDescription = null,
                            tint = if (displaySuccess) Color(0xFF10B981) else Color(0xFFEF4444),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Text(
                        if (displaySuccess) "Saved to GeminiEraser" else "Failed to save",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }

        // Auto-dismiss the feedback
        LaunchedEffect(saveFeedback) {
            if (saveFeedback != null) {
                kotlinx.coroutines.delay(2500)
                saveFeedback = null
            }
        }

        // Resolution Picker Sheet
        if (showResolutionPicker && resultBitmap != null) {
            ResolutionPickerSheet(
                bitmap    = resultBitmap!!,
                isPremium = isPremium,
                onDismiss = { showResolutionPicker = false },
                onSelect  = { maxPx, requiresPro ->
                    showResolutionPicker = false
                    if (requiresPro && !isPremium) {
                        showPaywallScreen = true
                    } else {
                        onSave(maxPx)
                    }
                },
                onGoPro   = {
                    showResolutionPicker = false
                    showPaywallScreen    = true
                }
            )
        }

        if (showPaywallScreen) {
            FullScreenPaywall(
                onDismiss = { showPaywallScreen = false },
                onSubscribe = { productId ->
                    showPaywallScreen = false
                    context.findActivity()?.let { billingManager.launchBillingFlow(it, productId) }
                }
            )
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// INTERACTIVE DRAW ZONE
// ────────────────────────────────────────────────────────────────────────────
@Composable
fun InteractiveImageZone(
    sourceBitmap: Bitmap?,
    resultBitmap: Bitmap?,
    showComparison: Boolean,
    processingState: ProcessingState,
    drawnPaths: List<DrawnStroke>,
    selectionMode: SelectionMode,
    segmentedMask: Bitmap?,
    isSegmenting: Boolean,
    isPremium: Boolean,
    onAddPath: (Path, Boolean) -> Unit,
    onUndo: () -> Unit,
    onPickImage: () -> Unit,
    onToggleView: (Boolean) -> Unit,
    onAiTap: (Float, Float) -> Unit,
    onCancelSegmentation: () -> Unit,
    onModeChange: (SelectionMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val defaultHeight = if (sourceBitmap != null) 420.dp else 240.dp

    Surface(
        shape  = RoundedCornerShape(24.dp),
        color  = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, if (sourceBitmap == null) MaterialTheme.colorScheme.outline else Color(0xFF8B5CF6).copy(alpha = 0.5f)),
        modifier = if (modifier == Modifier) Modifier.fillMaxWidth().height(defaultHeight) else modifier
    ) {
        if (sourceBitmap == null) {
            // Empty state
            Box(Modifier.fillMaxSize().clickable { onPickImage() }, contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.AddPhotoAlternate, null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(52.dp))
                    Spacer(Modifier.height(14.dp))
                    Text("Select an image to start cleaning", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            // Interactive UI
            Box(Modifier.fillMaxSize()) {
                val displayBmp = if (showComparison && resultBitmap != null) resultBitmap else sourceBitmap
                
                var viewSize by remember { mutableStateOf(IntSize.Zero) }
                var activePath by remember { mutableStateOf<Path?>(null) }
                var activePathIsFilled by remember { mutableStateOf(false) }

                // ── Zoom / pan state ─────────────────────────────────────────
                var zoomScale by remember { mutableStateOf(1f) }
                var panOffset by remember { mutableStateOf(Offset.Zero) }

                // Clamp pan so the image doesn't fly off-screen
                fun clampPan(rawPan: Offset, scale: Float): Offset {
                    if (viewSize == IntSize.Zero || sourceBitmap == null) return rawPan
                    val vAspect = viewSize.width.toFloat() / viewSize.height
                    val bAspect = sourceBitmap.width.toFloat() / sourceBitmap.height
                    val (imgW, imgH) = if (vAspect > bAspect)
                        Pair(viewSize.height * bAspect * scale, viewSize.height.toFloat() * scale)
                    else
                        Pair(viewSize.width.toFloat() * scale, viewSize.width / bAspect * scale)
                    val maxX = ((imgW - viewSize.width) / 2f).coerceAtLeast(0f)
                    val maxY = ((imgH - viewSize.height) / 2f).coerceAtLeast(0f)
                    return Offset(rawPan.x.coerceIn(-maxX, maxX), rawPan.y.coerceIn(-maxY, maxY))
                }

                // Reset zoom/pan when image is swapped
                LaunchedEffect(sourceBitmap) {
                    zoomScale = 1f
                    panOffset = Offset.Zero
                }
                // Also reset zoom/pan when the result is revealed
                // so the user always sees the full result, not a zoomed-in crop
                LaunchedEffect(showComparison) {
                    if (showComparison) {
                        zoomScale = 1f
                        panOffset = Offset.Zero
                    }
                }

                // Derive a tinted cyan overlay from the AI segmentation mask (white=object, black=bg)
                val maskOverlay: Bitmap? = remember(segmentedMask) {
                    segmentedMask?.let { mask ->
                        val w = mask.width; val h = mask.height
                        val pixels = IntArray(w * h)
                        mask.getPixels(pixels, 0, w, 0, 0, w, h)
                        for (i in pixels.indices) {
                            val r = (pixels[i] shr 16) and 0xFF
                            // Foreground white pixels → semi-transparent cyan; background → fully transparent
                            pixels[i] = if (r >= 128) AndroidColor.argb(160, 6, 182, 212) else AndroidColor.TRANSPARENT
                        }
                        val overlay = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        overlay.setPixels(pixels, 0, w, 0, 0, w, h)
                        overlay
                    }
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { viewSize = it }
                        // ── Single unified gesture handler ─────────────────────────────
                        // 1 pointer → brush stroke (MANUAL_BRUSH) or ai tap (AI_TAP)
                        // 2+ pointers → pinch-to-zoom + pan
                        // zoomScale + panOffset intentionally NOT in key — they're snapshot state
                        // read fresh each event. Having them as keys restarts the coroutine
                        // on every zoom/pan frame, causing the frozen/sluggish feeling.
                        .pointerInput(showComparison, selectionMode, processingState, isSegmenting, sourceBitmap) {
                            // Block ALL input during active processing (spinner showing)
                            if (processingState == ProcessingState.PROCESSING) return@pointerInput
                            // In comparison/result view: allow pinch-to-zoom but block drawing

                            // Compute fit-inside geometry (recalculated inside each gesture)
                            fun computeBase(): FloatArray {
                                var scaleX = 1f; var scaleY = 1f; var offX = 0f; var offY = 0f
                                if (viewSize.width > 0 && viewSize.height > 0 && sourceBitmap != null) {
                                    val vA = viewSize.width.toFloat() / viewSize.height
                                    val bA = sourceBitmap.width.toFloat() / sourceBitmap.height
                                    if (vA > bA) {
                                        scaleY = viewSize.height.toFloat() / sourceBitmap.height
                                        scaleX = scaleY
                                        offX = (viewSize.width - sourceBitmap.width * scaleX) / 2f
                                    } else {
                                        scaleX = viewSize.width.toFloat() / sourceBitmap.width
                                        scaleY = scaleX
                                        offY = (viewSize.height - sourceBitmap.height * scaleY) / 2f
                                    }
                                }
                                return floatArrayOf(scaleX, scaleY, offX, offY)
                            }

                            fun toBmp(screen: Offset, base: FloatArray): Offset {
                                val cx = viewSize.width / 2f
                                val cy = viewSize.height / 2f
                                val cx2 = (screen.x - cx - panOffset.x) / zoomScale + cx
                                val cy2 = (screen.y - cy - panOffset.y) / zoomScale + cy
                                return Offset((cx2 - base[2]) / base[0], (cy2 - base[3]) / base[1])
                            }

                            awaitEachGesture {
                                val firstDown = awaitFirstDown(requireUnconsumed = false)

                                var isMultiTouch = false
                                var brushPath: Path? = null
                                var prevX = 0f
                                var prevY = 0f
                                // Track start position for lasso-fill detection
                                var startBmpX = 0f
                                var startBmpY = 0f
                                val base = computeBase()

                                if (selectionMode == SelectionMode.MANUAL_BRUSH && !isSegmenting) {
                                    val bmp = toBmp(firstDown.position, base)
                                    prevX = bmp.x; prevY = bmp.y
                                    startBmpX = prevX; startBmpY = prevY
                                    brushPath = Path().apply { moveTo(prevX, prevY) }
                                    activePath = brushPath
                                    activePathIsFilled = false
                                }

                                do {
                                    val event = awaitPointerEvent()
                                    val activePointers = event.changes.count { it.pressed }

                                    if (activePointers >= 2) {
                                        // ── Pinch / pan: cancel any active brush path ──
                                        if (brushPath != null) {
                                            brushPath = null
                                            activePath = null
                                        }
                                        isMultiTouch = true
                                        val zoom = event.calculateZoom()
                                        val pan  = event.calculatePan()
                                        val newScale = (zoomScale * zoom).coerceIn(1f, 5f)
                                        // Pan: raw screen delta (no * zoomScale — that caused sluggish
                                        // panning because at 3× zoom it tripled the pan displacement)
                                        val newPan   = clampPan(panOffset + pan, newScale)
                                        zoomScale = newScale
                                        panOffset = newPan
                                        event.changes.forEach { it.consume() }
                                    } else if (activePointers == 1 && !isMultiTouch && !isSegmenting && !showComparison) {
                                        // ── Single finger: draw or tap ──
                                        val change = event.changes.firstOrNull { it.pressed } ?: continue
                                        when (selectionMode) {
                                            SelectionMode.MANUAL_BRUSH -> {
                                                if (brushPath != null) {
                                                    val bmp = toBmp(change.position, base)
                                                    val midX = (prevX + bmp.x) / 2f
                                                    val midY = (prevY + bmp.y) / 2f
                                                    brushPath!!.quadraticBezierTo(prevX, prevY, midX, midY)
                                                    prevX = bmp.x; prevY = bmp.y
                                                    // Live preview: hint closure if near start
                                                    val dx = prevX - startBmpX
                                                    val dy = prevY - startBmpY
                                                    val nearStart = (dx * dx + dy * dy) < (80f * 80f)
                                                    activePathIsFilled = nearStart
                                                    activePath = Path().apply { addPath(brushPath!!) }
                                                }
                                                change.consume()
                                            }
                                            SelectionMode.AI_TAP -> { /* tap handled on lift */ }
                                        }
                                    }
                                } while (event.changes.any { it.pressed })

                                // ── Gesture ended ──────────────────────────────────────
                                if (!isMultiTouch && !isSegmenting) {
                                    when (selectionMode) {
                                        SelectionMode.MANUAL_BRUSH -> {
                                            if (brushPath != null) {
                                                val dx = prevX - startBmpX
                                                val dy = prevY - startBmpY
                                                // Lasso threshold: end within 60 bitmap-px of start
                                                val isClosed = (dx * dx + dy * dy) < (60f * 60f)
                                                if (isClosed) {
                                                    brushPath!!.close() // snap & fill interior
                                                } else {
                                                    brushPath!!.lineTo(prevX, prevY)
                                                }
                                                onAddPath(brushPath!!, isClosed)
                                            }
                                            activePath = null
                                            activePathIsFilled = false
                                        }
                                        SelectionMode.AI_TAP -> {
                                            // Lift after single-finger press = AI tap
                                            val liftPos = firstDown.position
                                            val base2 = computeBase()
                                            if (sourceBitmap != null) {
                                                val bmp = toBmp(liftPos, base2)
                                                val normX = (bmp.x / sourceBitmap.width).coerceIn(0f, 1f)
                                                val normY = (bmp.y / sourceBitmap.height).coerceIn(0f, 1f)
                                                onAiTap(normX, normY)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                ) {
                    val sWidth = size.width
                    val sHeight = size.height
                    val cx = sWidth / 2f
                    val cy = sHeight / 2f

                    // Base fit-inside geometry (zoom = 1)
                    val bRatio = sourceBitmap.width.toFloat() / sourceBitmap.height
                    val vRatio = sWidth / sHeight

                    var baseRenderW = sWidth
                    var baseRenderH = sHeight
                    var baseRenderX = 0f
                    var baseRenderY = 0f

                    if (vRatio > bRatio) {
                        baseRenderW = baseRenderH * bRatio
                        baseRenderX = (sWidth - baseRenderW) / 2f
                    } else {
                        baseRenderH = baseRenderW / bRatio
                        baseRenderY = (sHeight - baseRenderH) / 2f
                    }

                    // Apply zoom + pan transform around canvas centre
                    drawContext.canvas.save()
                    drawContext.canvas.translate(cx + panOffset.x, cy + panOffset.y)
                    drawContext.canvas.scale(zoomScale, zoomScale)
                    drawContext.canvas.translate(-cx, -cy)

                    val renderX = baseRenderX
                    val renderY = baseRenderY
                    val renderW = baseRenderW
                    val renderH = baseRenderH

                    // 1. Draw the image
                    drawImage(
                        image = displayBmp.asImageBitmap(),
                        dstOffset = IntOffset(renderX.toInt(), renderY.toInt()),
                        dstSize = IntSize(renderW.toInt(), renderH.toInt())
                    )

                    // 2. Draw strokes in Manual Brush mode
                    if (!showComparison && selectionMode == SelectionMode.MANUAL_BRUSH) {
                        val strokeScale = renderW / sourceBitmap.width.toFloat()
                        drawContext.canvas.save()
                        drawContext.canvas.translate(renderX, renderY)
                        drawContext.canvas.scale(strokeScale, strokeScale)

                        val cyanFill   = Color(0xFF06B6D4).copy(alpha = 0.35f)
                        val cyanStroke = Color(0xFF06B6D4).copy(alpha = 0.6f)
                        val fillStyle  = androidx.compose.ui.graphics.drawscope.Fill
                        val strokeStyle = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 45f, cap = StrokeCap.Round, join = StrokeJoin.Round
                        )
                        val thinStroke = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round
                        )

                        // Committed strokes
                        for (stroke in drawnPaths) {
                            if (stroke.isFilled) {
                                // Filled lasso: semi-transparent interior + thin outline
                                drawPath(path = stroke.path, color = cyanFill, style = fillStyle)
                                drawPath(path = stroke.path, color = cyanStroke, style = thinStroke)
                            } else {
                                drawPath(path = stroke.path, color = cyanStroke, style = strokeStyle)
                            }
                        }

                        // Live active path preview
                        activePath?.let { ap ->
                            if (activePathIsFilled) {
                                // Closing soon: show filled preview with dashed border hint
                                drawPath(path = ap, color = cyanFill, style = fillStyle)
                                drawPath(path = ap, color = Color(0xFFFFD700).copy(alpha = 0.9f), style = thinStroke)
                            } else {
                                drawPath(path = ap, color = cyanStroke, style = strokeStyle)
                            }
                        }

                        drawContext.canvas.restore()
                    }

                    // 3. Draw AI segmentation cyan mask overlay
                    if (!showComparison && maskOverlay != null) {
                        drawImage(
                            image = maskOverlay.asImageBitmap(),
                            dstOffset = IntOffset(renderX.toInt(), renderY.toInt()),
                            dstSize = IntSize(renderW.toInt(), renderH.toInt())
                        )
                    }

                    drawContext.canvas.restore()
                } // ← closes Canvas { } lambda

                // Header overlays: [Undo | Change Image]  [✦ AI | ✏ Brush]  [Result/Original]
                Row(
                    modifier = Modifier.align(Alignment.TopCenter).padding(12.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left cluster: Cancel (segmenting) | Undo | Deselect | Change Image
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // While AI is fetching: show Cancel button instead of Undo
                        if (isSegmenting) {
                            // Cancel in-progress segmentation
                            IconButton(
                                onClick = onCancelSegmentation,
                                modifier = Modifier.background(Color(0xFFEF4444).copy(0.85f), CircleShape)
                            ) { Icon(Icons.Default.Close, "Cancel segmentation", tint = Color.White) }
                        } else {
                            val canUndo = !showComparison && processingState == ProcessingState.IDLE &&
                                when (selectionMode) {
                                    SelectionMode.AI_TAP       -> false // use Deselect chip instead
                                    SelectionMode.MANUAL_BRUSH -> drawnPaths.isNotEmpty()
                                }
                            if (canUndo) {
                                IconButton(
                                    onClick = onUndo,
                                    modifier = Modifier.background(Color.Black.copy(0.5f), CircleShape)
                                ) { Icon(Icons.Default.Undo, "Undo", tint = Color.White) }
                            } else {
                                Spacer(Modifier.size(48.dp))
                            }
                        }

                        // Change Image button — always visible while not erasing
                        if (processingState != ProcessingState.PROCESSING && !isSegmenting) {
                            IconButton(
                                onClick = onPickImage,
                                modifier = Modifier.background(Color.Black.copy(0.5f), CircleShape)
                            ) { Icon(Icons.Default.Image, "Change Image", tint = Color(0xFF8B5CF6)) }
                        } else {
                            Spacer(Modifier.size(48.dp))
                        }
                    }

                    // Centre: AI / Brush mode toggle pill (hidden while viewing result)
                    if (!showComparison && processingState == ProcessingState.IDLE) {
                        Surface(shape = RoundedCornerShape(20.dp), color = Color.Black.copy(0.65f)) {
                            Row(modifier = Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                // AI Tap tab
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (selectionMode == SelectionMode.AI_TAP) Color(0xFF06B6D4) else Color.Transparent,
                                            RoundedCornerShape(16.dp)
                                        )
                                        .clickable { onModeChange(SelectionMode.AI_TAP) }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "✦ AI",
                                            color = if (selectionMode == SelectionMode.AI_TAP) Color.Black else Color.White.copy(0.7f),
                                            fontSize = 12.sp, fontWeight = FontWeight.Bold
                                        )
                                        if (!isPremium) {
                                            Spacer(Modifier.width(4.dp))
                                            Icon(Icons.Default.Star, contentDescription = "Pro Feature", modifier = Modifier.size(10.dp), tint = Color(0xFFFFD700))
                                        }
                                    }
                                }
                                // Manual Brush tab
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (selectionMode == SelectionMode.MANUAL_BRUSH) Color(0xFF8B5CF6) else Color.Transparent,
                                            RoundedCornerShape(16.dp)
                                        )
                                        .clickable { onModeChange(SelectionMode.MANUAL_BRUSH) }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "✏ Brush",
                                        color = Color.White.copy(if (selectionMode == SelectionMode.MANUAL_BRUSH) 1f else 0.7f),
                                        fontSize = 12.sp, fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    } else {
                        Spacer(Modifier.size(1.dp))
                    }

                    // Right: Result / Original toggle
                    if (resultBitmap != null) {
                        Surface(shape = CircleShape, color = Color.Black.copy(0.7f), border = BorderStroke(1.dp, Color(0xFF8B5CF6))) {
                            Text(
                                if (showComparison) "Result" else "Original",
                                color = Color.White,
                                modifier = Modifier.clickable { onToggleView(!showComparison) }.padding(horizontal = 14.dp, vertical = 8.dp),
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else {
                        Spacer(Modifier.size(1.dp))
                    }
                }

                // AI segmenting in-progress indicator + inline Cancel
                if (isSegmenting) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .background(Color.Black.copy(0.72f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color(0xFF06B6D4),
                                strokeWidth = 2.dp
                            )
                            Text(
                                "Selecting object…",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            // Tap to cancel the in-flight request
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFFEF4444).copy(0.85f), RoundedCornerShape(12.dp))
                                    .clickable { onCancelSegmentation() }
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("✕ Cancel", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Deselect chip — when AI mask is showing and user wants to re-tap
                if (!isSegmenting && selectionMode == SelectionMode.AI_TAP &&
                    segmentedMask != null && processingState == ProcessingState.IDLE && !showComparison
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Deselect
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFFEF4444).copy(0.85f), RoundedCornerShape(12.dp))
                                    .clickable { onUndo() }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("✕ Deselect", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            // Re-tap hint
                            Box(
                                modifier = Modifier
                                    .background(Color.Black.copy(0.6f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("👆 Tap a different object", color = Color.White.copy(0.9f), fontSize = 13.sp)
                            }
                        }
                    }
                }

                // Tap hint nudge (AI mode only, before first selection)
                if (!showComparison && selectionMode == SelectionMode.AI_TAP &&
                    segmentedMask == null && !isSegmenting && processingState == ProcessingState.IDLE
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp)
                            .background(Color.Black.copy(0.55f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            "👆 Tap the object you want to remove",
                            color = Color.White.copy(0.9f), fontSize = 13.sp, fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Processing Cover
                if (processingState == ProcessingState.PROCESSING) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.6f)), contentAlignment = Alignment.Center) {
                        val infiniteTransition = rememberInfiniteTransition(label = "logoSpin")
                        val rotation by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1200, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "rotation"
                        )
                        Image(
                            painter = painterResource(id = R.drawable.logo_icon),
                            contentDescription = "Processing...",
                            modifier = Modifier.size(196.dp).rotate(rotation)
                        )
                    }
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// BITMAP TOOLS
// ────────────────────────────────────────────────────────────────────────────

// Creates a pure black-and-white bitmap mapping exactly the drawn paths to the image.
// Filled (lasso) strokes use Fill style; open strokes use Stroke style.
fun renderPathsToMask(width: Int, height: Int, strokes: List<DrawnStroke>, baseMask: Bitmap? = null): Bitmap {
    val bmp = baseMask?.copy(Bitmap.Config.ARGB_8888, true) ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bmp)

    if (baseMask == null) {
        // Background = black
        canvas.drawColor(AndroidColor.BLACK)
    }

    val strokePaint = AndroidPaint().apply {
        color = AndroidColor.WHITE
        style = AndroidPaint.Style.STROKE
        strokeWidth = 45f
        strokeCap = Cap.ROUND
        strokeJoin = Join.ROUND
        isAntiAlias = true
    }
    val fillPaint = AndroidPaint().apply {
        color = AndroidColor.WHITE
        style = AndroidPaint.Style.FILL_AND_STROKE  // fill interior + anti-alias border
        strokeWidth = 8f
        isAntiAlias = true
    }

    for (stroke in strokes) {
        val androidPath = stroke.path.asAndroidPath()
        canvas.drawPath(androidPath, if (stroke.isFilled) fillPaint else strokePaint)
    }

    return bmp
}

// Below follows UI components similar to original layout
@Composable
fun BannerAdView() {
    AndroidView(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = "ca-app-pub-3940256099942544/6300978111" // Test Banner ID
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}

@Composable
fun AppHeader(isPremium: Boolean, compact: Boolean, onGoPro: () -> Unit, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "header")

    // Breathing pulse for logo
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    // Glow halo alpha throb
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow"
    )

    // Shimmer sweep for PRO button
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -300f,
        targetValue = 300f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer"
    )

    // ── Compact mode: small horizontal header when image is loaded ───────────
    if (compact) {
        Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF7C3AED).copy(alpha = glowAlpha * 0.7f),
                                    Color.Transparent
                                ),
                                center = center,
                                radius = size.minDimension * 0.7f
                            ),
                            radius = size.minDimension * 0.7f
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo_icon),
                    contentDescription = "Gemini Eraser Logo",
                    modifier = Modifier.size(101.dp).scale(pulse)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Gemini Eraser",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    "AI-powered object removal",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )
            }
            if (!isPremium) {
                GoPROButton(
                    onClick       = onGoPro,
                    shimmerOffset = shimmerOffset,
                    glowAlpha     = glowAlpha,
                    compact       = true
                )
            }
        }
        return
    }

    // ── Full hero header (no image loaded) ───────────────────────────────────
    Box(modifier = Modifier.fillMaxWidth()) {

        // ── Ambient glow layer behind logo ──────────────────────────────────
        Box(
            modifier = Modifier
                .size(340.dp)
                .align(Alignment.TopCenter)
                .drawBehind {
                    // Outer soft purple halo
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF7C3AED).copy(alpha = glowAlpha * 0.6f),
                                Color(0xFF4F46E5).copy(alpha = glowAlpha * 0.3f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = size.minDimension * 0.75f
                        ),
                        radius = size.minDimension * 0.75f
                    )
                    // Inner bright cyan core
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF06B6D4).copy(alpha = glowAlpha * 0.5f),
                                Color(0xFFEC4899).copy(alpha = glowAlpha * 0.2f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = size.minDimension * 0.45f
                        ),
                        radius = size.minDimension * 0.45f
                    )
                }
        )

        // ── Logo + text centered ────────────────────────────────────────────
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo_icon),
                contentDescription = "Gemini Eraser Logo",
                modifier = Modifier.size(200.dp).scale(pulse)
            )
            // Gradient shimmer title
            Text(
                "Gemini Eraser",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                "AI-powered object removal",
                color = Color(0xFF94A3B8),
                fontSize = 13.sp
            )
        }

        if (!isPremium) {
            GoPROButton(
                onClick       = onGoPro,
                shimmerOffset = shimmerOffset,
                glowAlpha     = glowAlpha,
                compact       = false,
                modifier      = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp)
            )
        }
    }
}


@Composable
fun EraseButton(isProcessing: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick, modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6))
    ) {
        Text("Erase Highlights", fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun HowItWorksSection() {
    Text("1. Load image\n2. Swipe precisely over the object or blemish\n3. Tap Erase to remove flawlessly.", color = Color.Gray, textAlign = TextAlign.Center)
}

@Composable
fun AmbientBackground() {
    // App background handled by MaterialTheme
}

fun decodeBitmapFromUri(context: Context, uri: Uri): Bitmap? {
    return runCatching { android.graphics.BitmapFactory.decodeStream(context.contentResolver.openInputStream(uri)) }.getOrNull()
}

// ────────────────────────────────────────────────────────────────────────────
// RESOLUTION PICKER — minimal 3-option sheet
// ────────────────────────────────────────────────────────────────────────────
private data class ResolutionTier(
    val label: String,
    val hint: String,
    val maxPx: Int,       // Int.MAX_VALUE = save at original size
    val isPro: Boolean,
)

private val RESOLUTION_TIERS = listOf(
    ResolutionTier("Low",      "Smaller file, quick share", 720,           isPro = false),
    ResolutionTier("High",     "Sharp & detailed",          1080,          isPro = true),
    ResolutionTier("Max",      "Maximum possible quality",  Int.MAX_VALUE, isPro = true),
)

@Composable
fun ResolutionPickerSheet(
    bitmap: Bitmap,
    isPremium: Boolean,
    onDismiss: () -> Unit,
    onSelect: (Int, Boolean) -> Unit,
    onGoPro: () -> Unit,
) {
    val origW = bitmap.width
    val origH = bitmap.height

    // Dim scrim — tap outside to dismiss
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Sheet — consume taps so they don't dismiss
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = false, onClick = {}),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = Color(0xFF141420),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {

                // Handle
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .width(36.dp).height(3.dp)
                            .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(2.dp))
                    )
                }

                Spacer(Modifier.height(22.dp))

                // Header & Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column {
                        Text(
                            "Save as",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "${origW} × ${origH}",
                            color = Color.White.copy(alpha = 0.35f),
                            fontSize = 12.sp,
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .offset(x = 12.dp, y = (-8).dp) // Nudge towards the corner for better padding balance
                            .size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Options
                val origMax = maxOf(origW, origH)
                RESOLUTION_TIERS.forEach { tier ->
                    val locked = tier.isPro && !isPremium
                    val targetDim = when (tier.label) {
                        "Low" -> minOf(origMax, 720)
                        "High" -> maxOf(1080, minOf(origMax, 1920))
                        else -> maxOf(origMax, 2160) // "Max" gives an upscaled premium feel if small
                    }
                    val r = targetDim.toFloat() / origMax
                    val outW = (origW * r).toInt()
                    val outH = (origH * r).toInt()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (locked) onGoPro() else onSelect(targetDim, tier.isPro)
                            }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "${outW} × ${outH}",
                                    color = if (locked) Color.White.copy(alpha = 0.35f) else Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                if (locked) {
                                    // Minimal PRO pill
                                    Text(
                                        "PRO",
                                        color = Color(0xFFFFAB00),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp,
                                        modifier = Modifier
                                            .background(
                                                Color(0xFFFFAB00).copy(alpha = 0.12f),
                                                RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                "${tier.label}  ·  ${tier.hint}",
                                color = Color.White.copy(alpha = if (locked) 0.2f else 0.4f),
                                fontSize = 12.sp,
                            )
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = if (locked) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Divider between items (not after last)
                    if (tier != RESOLUTION_TIERS.last()) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(0.5.dp)
                                .background(Color.White.copy(alpha = 0.07f))
                        )
                    }
                }

                // Pro upsell — only shown to free users, clean single line
                if (!isPremium) {
                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFFAB00).copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                            .clickable(onClick = onGoPro)
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("✦", color = Color(0xFFFFAB00), fontSize = 14.sp)
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Upgrade to PRO",
                                color = Color(0xFFFFAB00),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "Unlock High & Max quality saves",
                                color = Color(0xFFFFAB00).copy(alpha = 0.6f),
                                fontSize = 11.sp,
                            )
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = Color(0xFFFFAB00).copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}



fun saveBitmapToGallery(context: Context, bitmap: Bitmap): Boolean {
    val cv = ContentValues().apply { 
        put(MediaStore.Images.Media.DISPLAY_NAME, "Eraser_${System.currentTimeMillis()}.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/GeminiEraser")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    return runCatching {
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv) ?: return false
        context.contentResolver.openOutputStream(uri)?.use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            cv.clear()
            cv.put(MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, cv, null, null)
        }
        true
    }.getOrDefault(false)
}
