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
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
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
    var actionCount       by remember { mutableStateOf(0) }

    // User's drawing strokes mapped to intrinsic image coordinates
    // We maintain a list of Paths representing strokes on the real 1:1 image.
    val drawnPaths = remember { mutableStateListOf<Path>() }

    if (sourceBitmap != null) {
        androidx.activity.compose.BackHandler {
            sourceBitmap   = null
            resultBitmap   = null
            state          = ProcessingState.IDLE
            showComparison = false
            drawnPaths.clear()
            errorMessage   = ""
            saveFeedback   = null
        }
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            sourceBitmap   = decodeBitmapFromUri(context, it)
            resultBitmap   = null
            state          = ProcessingState.IDLE
            showComparison = false
            drawnPaths.clear()
        }
    }

    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pickImage.launch("image/*")
    }

    fun onPickImage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            pickImage.launch("image/*")
        } else {
            requestPermission.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
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
                    adManager.showRewarded(activity, onReward = {
                        earned = true
                        performReEdit()
                    }, onClosed = {
                        if (!earned) {
                            Toast.makeText(context, "Watch the full ad to continue editing!", Toast.LENGTH_SHORT).show()
                        }
                    })
                } else {
                    performReEdit()
                }
            }
        }
    }

    fun onErase() {
        val src = sourceBitmap ?: return
        if (drawnPaths.isEmpty()) {
            Toast.makeText(context, "Please swipe over an object first!", Toast.LENGTH_SHORT).show()
            return
        }

        val processImage = {
            state = ProcessingState.PROCESSING
            coroutine.launch {
                runCatching {
                    // 1. Convert drawing Paths into a precise Boolean Mask
                    val mask = withContext(Dispatchers.Default) { renderPathsToMask(src.width, src.height, drawnPaths) }
                    // 2. Call FastAPI Backend for Generative Inpainting
                    withContext(Dispatchers.IO) { ObjectEraser.erase(src, mask, isPremium) }
                }.onSuccess { result ->
                    resultBitmap   = result
                    state          = ProcessingState.DONE
                    showComparison = true
                    drawnPaths.clear()
                }.onFailure { err ->
                    errorMessage = err.message ?: "Unknown error"
                    state        = ProcessingState.ERROR
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

    fun onSave() {
        val bmp = resultBitmap ?: return
        
        val saveImage = {
            coroutine.launch {
                val saved = withContext(Dispatchers.IO) { saveBitmapToGallery(context, bmp) }
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
                    adManager.showRewarded(activity, onReward = {
                        earned = true
                        saveImage()
                    }, onClosed = {
                        if (!earned) {
                            Toast.makeText(context, "Watch the full ad to save your image!", Toast.LENGTH_SHORT).show()
                        }
                    })
                } else {
                    saveImage() // Fallback if no activity found but shouldn't happen
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
                        sourceBitmap    = sourceBitmap,
                        resultBitmap    = resultBitmap,
                        showComparison  = showComparison,
                        processingState = state,
                        drawnPaths      = drawnPaths,
                        onAddPath       = { drawnPaths.add(it) },
                        onUndo          = { if (drawnPaths.isNotEmpty()) drawnPaths.removeAt(drawnPaths.lastIndex) },
                        onPickImage     = ::onPickImage,
                        onToggleView    = { showComparison = it }
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
                    sourceBitmap    = sourceBitmap,
                    resultBitmap    = resultBitmap,
                    showComparison  = showComparison,
                    processingState = state,
                    drawnPaths      = drawnPaths,
                    onAddPath       = { drawnPaths.add(it) },
                    onUndo          = { if (drawnPaths.isNotEmpty()) drawnPaths.removeAt(drawnPaths.lastIndex) },
                    onPickImage     = ::onPickImage,
                    onToggleView    = { showComparison = it },
                    modifier        = Modifier
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
                            EraseButton(isProcessing = false, onClick = ::onErase)
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
                                    onClick = ::onSave,
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

        if (showPaywallScreen) {
            FullScreenPaywall(
                onDismiss = { showPaywallScreen = false },
                onSubscribe = {
                    showPaywallScreen = false
                    context.findActivity()?.let { billingManager.launchBillingFlow(it) }
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
    drawnPaths: List<Path>,
    onAddPath: (Path) -> Unit,
    onUndo: () -> Unit,
    onPickImage: () -> Unit,
    onToggleView: (Boolean) -> Unit,
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

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { viewSize = it }
                        .pointerInput(showComparison) {
                            // Only allow drawing on the Original, not Result
                            if (showComparison) return@pointerInput

                            var currentPath: Path? = null
                            var scaleX = 1f
                            var scaleY = 1f
                            var offsetX = 0f
                            var offsetY = 0f

                            // Compute scale so we can map touch gestures linearly back to original coordinates
                            if (viewSize.width > 0 && viewSize.height > 0) {
                                val vAspect = viewSize.width.toFloat() / viewSize.height
                                val bAspect = sourceBitmap.width.toFloat() / sourceBitmap.height
                                if (vAspect > bAspect) {
                                    // Height constrained
                                    scaleY = viewSize.height.toFloat() / sourceBitmap.height
                                    scaleX = scaleY
                                    val renderedW = sourceBitmap.width * scaleX
                                    offsetX = (viewSize.width - renderedW) / 2f
                                } else {
                                    // Width constrained
                                    scaleX = viewSize.width.toFloat() / sourceBitmap.width
                                    scaleY = scaleX
                                    val renderedH = sourceBitmap.height * scaleY
                                    offsetY = (viewSize.height - renderedH) / 2f
                                }
                            }

                            // Inverse map func from screen (x,y) -> bitmap (x,y)
                            fun toBmpCoords(off: Offset): Offset {
                                return Offset((off.x - offsetX) / scaleX, (off.y - offsetY) / scaleY)
                            }

                            var previousX = 0f
                            var previousY = 0f

                            detectDragGestures(
                                onDragStart = { offset ->
                                    val bmpPos = toBmpCoords(offset)
                                    previousX = bmpPos.x
                                    previousY = bmpPos.y
                                    currentPath = Path().apply { moveTo(previousX, previousY) }
                                    activePath = currentPath
                                },
                                onDrag = { change, _ ->
                                    val bmpPos = toBmpCoords(change.position)
                                    val midX = (previousX + bmpPos.x) / 2f
                                    val midY = (previousY + bmpPos.y) / 2f
                                    
                                    currentPath?.quadraticBezierTo(previousX, previousY, midX, midY)
                                    
                                    previousX = bmpPos.x
                                    previousY = bmpPos.y
                                    
                                    // Create a new path wrapping the updated path to trigger Compose recomposition properly
                                    val livePath = Path().apply { addPath(currentPath!!) }
                                    activePath = livePath
                                },
                                onDragEnd = {
                                    currentPath?.lineTo(previousX, previousY)
                                    currentPath?.let { onAddPath(it) }
                                    currentPath = null
                                    activePath = null
                                }
                            )
                        }
                ) {
                    val sWidth = size.width
                    val sHeight = size.height

                    // 1. Draw the image
                    val bRatio = sourceBitmap.width.toFloat() / sourceBitmap.height
                    val vRatio = sWidth / sHeight

                    var renderW = sWidth
                    var renderH = sHeight
                    var renderX = 0f
                    var renderY = 0f

                    if (vRatio > bRatio) {
                        renderW = renderH * bRatio
                        renderX = (sWidth - renderW) / 2f
                    } else {
                        renderH = renderW / bRatio
                        renderY = (sHeight - renderH) / 2f
                    }

                    drawImage(
                        image = displayBmp.asImageBitmap(),
                        dstOffset = IntOffset(renderX.toInt(), renderY.toInt()),
                        dstSize = IntSize(renderW.toInt(), renderH.toInt())
                    )

                    // 2. Draw the strokes OVER the image if we are viewing the original
                    if (!showComparison) {
                        val strokeScale = renderW / sourceBitmap.width.toFloat()
                        
                        // We translate the canvas to the image's top-left so paths align
                        drawContext.canvas.save()
                        drawContext.canvas.translate(renderX, renderY)
                        drawContext.canvas.scale(strokeScale, strokeScale)

                        // Highlight strokes with neon cyan logic
                        val brushPathColor = Color(0xFF06B6D4).copy(alpha = 0.5f)
                        val strokeStyle = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 45f,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                        for (path in drawnPaths) {
                            drawPath(path = path, color = brushPathColor, style = strokeStyle)
                        }
                        activePath?.let {
                            drawPath(path = it, color = brushPathColor, style = strokeStyle)
                        }
                        
                        drawContext.canvas.restore()
                    }
                }

                // Header overlays (Undo, Toggle)
                Row(
                    modifier = Modifier.align(Alignment.TopCenter).padding(12.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (!showComparison && drawnPaths.isNotEmpty() && processingState == ProcessingState.IDLE) {
                        IconButton(onClick = onUndo, modifier = Modifier.background(Color.Black.copy(0.5f), CircleShape)) {
                            Icon(Icons.Default.Undo, "Undo brush", tint = Color.White)
                        }
                    } else Spacer(Modifier.width(1.dp))

                    if (resultBitmap != null) {
                        Surface(
                            shape = CircleShape, color = Color.Black.copy(0.7f), border = BorderStroke(1.dp, Color(0xFF8B5CF6))
                        ) {
                            Text(
                                if (showComparison) "Result" else "Original",
                                color = Color.White, modifier = Modifier.clickable { onToggleView(!showComparison) }.padding(horizontal = 14.dp, vertical = 8.dp),
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                            )
                        }
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
fun renderPathsToMask(width: Int, height: Int, paths: List<Path>): Bitmap {
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bmp)
    
    // Fill background with black
    canvas.drawColor(AndroidColor.BLACK)

    // Setup white Android paintbrush
    val paint = AndroidPaint().apply {
        color = AndroidColor.WHITE
        style = AndroidPaint.Style.STROKE
        strokeWidth = 45f // Base brush size, matching the display
        strokeCap = Cap.ROUND
        strokeJoin = Join.ROUND
        isAntiAlias = true
    }

    // Convert Compose Paths to Android Paths and draw them
    for (composePath in paths) {
        val androidPath = composePath.asAndroidPath()
        canvas.drawPath(androidPath, paint)
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
                    .size(56.dp)
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
                    modifier = Modifier.size(44.dp).scale(pulse)
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
                // Golden shimmer brush that sweeps right
                val goldShimmer = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFD97706),
                        Color(0xFFF59E0B),
                        Color(0xFFFFD60A),
                        Color(0xFFF59E0B),
                        Color(0xFFD97706)
                    ),
                    start = Offset(shimmerOffset - 100f, 0f),
                    end = Offset(shimmerOffset + 100f, 0f)
                )
                Button(
                    onClick = onGoPro,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent, contentColor = Color(0xFF1A0A00)
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    modifier = Modifier
                        .height(36.dp)
                        .background(brush = goldShimmer, shape = RoundedCornerShape(18.dp))
                        .drawWithContent {
                            // Golden glow halo
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFFF59E0B).copy(alpha = glowAlpha * 0.7f),
                                        Color.Transparent
                                    ),
                                    center = Offset(size.width / 2f, size.height / 2f),
                                    radius = size.maxDimension * 1.2f
                                ),
                                radius = size.maxDimension * 1.2f
                            )
                            drawContent()
                        },
                    shape = RoundedCornerShape(18.dp),
                    elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp)
                ) {
                    Icon(
                        Icons.Default.WorkspacePremium,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = Color(0xFF1A0A00)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Go PRO",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp,
                        color = Color(0xFF1A0A00),
                        letterSpacing = 0.5.sp
                    )
                }
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

        // ── Glowing PRO corner badge (full hero mode) ──────────────────
        if (!isPremium) {
            val goldShimmer = Brush.linearGradient(
                colors = listOf(
                    Color(0xFFD97706),
                    Color(0xFFF59E0B),
                    Color(0xFFFFD60A),
                    Color(0xFFF59E0B),
                    Color(0xFFD97706)
                ),
                start = Offset(shimmerOffset - 120f, 0f),
                end = Offset(shimmerOffset + 120f, 0f)
            )
            Button(
                onClick = onGoPro,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = Color(0xFF1A0A00)
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp)
                    .height(42.dp)
                    .background(brush = goldShimmer, shape = RoundedCornerShape(21.dp))
                    .drawWithContent {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF7C3AED).copy(alpha = glowAlpha * 0.8f),
                                    Color(0xFF06B6D4).copy(alpha = glowAlpha * 0.3f),
                                    Color.Transparent
                                ),
                                center = Offset(size.width / 2f, size.height / 2f),
                                radius = size.maxDimension * 1.1f
                            ),
                            radius = size.maxDimension * 1.1f
                        )
                        drawContent()
                    },
                shape = RoundedCornerShape(20.dp),
                elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo_icon),
                    contentDescription = null,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "PRO",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                    color = Color.White
                )
            }
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