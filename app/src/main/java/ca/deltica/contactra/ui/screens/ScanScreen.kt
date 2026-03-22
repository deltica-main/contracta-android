package ca.deltica.contactra.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.Size
import android.util.Log
import android.view.OrientationEventListener
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import ca.deltica.contactra.data.ocr.TextRecognitionManager
import ca.deltica.contactra.ui.components.AppBackground
import ca.deltica.contactra.ui.components.AppCard
import ca.deltica.contactra.ui.components.AppTopBar
import ca.deltica.contactra.ui.components.BottomActionBar
import ca.deltica.contactra.ui.components.EmptyState
import ca.deltica.contactra.ui.components.LoadingState
import ca.deltica.contactra.ui.components.PrimaryButton
import ca.deltica.contactra.ui.components.SecondaryButton
import ca.deltica.contactra.ui.components.StatusPill
import ca.deltica.contactra.ui.components.StatusPillTone
import ca.deltica.contactra.ui.components.StepIndicator
import ca.deltica.contactra.ui.navigation.Screen
import ca.deltica.contactra.ui.theme.AppDimens
import ca.deltica.contactra.ui.theme.AppTheme
import ca.deltica.contactra.ui.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private enum class CameraUnavailableType {
    PERMISSION_DENIED,
    HARDWARE_UNAVAILABLE,
    TEMPORARY_ERROR
}

@Composable
fun ScanScreen(
    navController: NavController,
    viewModel: MainViewModel,
    launchGallery: Boolean,
    requestCameraPermissionOnLaunch: Boolean = true
) {
    val scanState by viewModel.scanUiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val scope = rememberCoroutineScope()
    val isDebugBuild = remember(context) {
        (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }
    val autoCaptureTraceRecorder = remember(context, isDebugBuild) {
        if (!isDebugBuild) {
            DISABLED_TRACE_RECORDER
        } else {
            val createdAtMs = System.currentTimeMillis()
            val sessionId = "session_${createdAtMs}_${SystemClock.elapsedRealtime()}"
            AutoCaptureTraceRecorderFactory.create(
                context = context,
                manifest = AutoCaptureTraceManifest(
                    appVersionName = resolveAppVersionName(context),
                    appVersionCode = resolveAppVersionCode(context),
                    parserVersion = AUTO_CAPTURE_DECISION_PARSER_VERSION,
                    deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                    sessionId = sessionId,
                    createdAtEpochMs = createdAtMs,
                    constantsSnapshot = AutoCaptureTraceConstantsSnapshot(
                        analysisIntervalMs = AUTO_CAPTURE_ANALYSIS_INTERVAL_MS,
                        stableFramesRequired = AUTO_CAPTURE_REQUIRED_STABLE_FRAMES,
                        ocrStableFramesRequired = AUTO_CAPTURE_REQUIRED_OCR_STABLE_FRAMES,
                        readyChecksRequired = AUTO_CAPTURE_REQUIRED_READY_CHECKS,
                        minCaptureIntervalMs = AUTO_CAPTURE_MIN_CAPTURE_INTERVAL_MS,
                        firstCaptureMinIntervalMs = AUTO_CAPTURE_FIRST_CAPTURE_INTERVAL_MS,
                        ocrIntervalMs = AUTO_CAPTURE_OCR_CHECK_INTERVAL_MS,
                        fastOcrIntervalMs = AUTO_CAPTURE_FAST_OCR_CHECK_INTERVAL_MS,
                        stageAStabilityGraceMs = AUTO_CAPTURE_STAGE_A_GRACE_WINDOW_MS,
                        refocusAfterMs = Long.MAX_VALUE,
                        refocusMinIntervalMs = Long.MAX_VALUE,
                        minOcrReadyScore = AUTO_CAPTURE_MIN_OCR_READY_SCORE,
                        allowSoftFailRelaxedCapture = AUTO_CAPTURE_ALLOW_SOFT_FAIL_RELAXED_CAPTURE,
                        softFailMinOcrReadyScore = AUTO_CAPTURE_SOFT_FAIL_MIN_OCR_READY_SCORE,
                        softFailReadyHitsRequired = AUTO_CAPTURE_SOFT_FAIL_READY_HITS_REQUIRED,
                        softFailReadyHitWindowMs = AUTO_CAPTURE_SOFT_FAIL_READY_HIT_WINDOW_MS,
                        relaxedNearReadyScoreMargin = AUTO_CAPTURE_RELAXED_NEAR_READY_SCORE_MARGIN,
                        relaxedNearReadyStrikesRequired = AUTO_CAPTURE_RELAXED_NEAR_READY_STRIKES,
                        relaxedNearReadyStrikeWindowMs = AUTO_CAPTURE_RELAXED_NEAR_READY_WINDOW_MS,
                        maxRelaxedCaptureMotionScore = DEFAULT_AUTO_CAPTURE_MAX_MOTION_SCORE_OCR,
                        minCaptureSharpness = DEFAULT_AUTO_CAPTURE_MIN_SHARPNESS_CAPTURE,
                        minOcrBlocks = AUTO_CAPTURE_MIN_OCR_BLOCKS,
                        minLumaMean = DEFAULT_AUTO_CAPTURE_MIN_LUMA_MEAN,
                        maxLumaMean = DEFAULT_AUTO_CAPTURE_MAX_LUMA_MEAN,
                        maxHighlightPct = DEFAULT_AUTO_CAPTURE_MAX_HIGHLIGHT_PCT,
                        minCenterEdgeDensity = DEFAULT_AUTO_CAPTURE_MIN_CENTER_EDGE_DENSITY,
                        maxOuterToCenterEdgeRatio = DEFAULT_AUTO_CAPTURE_MAX_OUTER_TO_CENTER_EDGE_RATIO
                    )
                )
            )
        }
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var isCameraReady by remember { mutableStateOf(false) }
    var isBindingCamera by remember { mutableStateOf(false) }
    var cameraUnavailableType by remember { mutableStateOf<CameraUnavailableType?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var captureError by remember { mutableStateOf<String?>(null) }
    var cameraBindAttempt by remember { mutableStateOf(0) }
    var autoCaptureEnabled by remember { mutableStateOf(true) }
    var autoCaptureInFlight by remember { mutableStateOf(false) }
    var autoCaptureFailureCount by rememberSaveable { mutableStateOf(0) }
    var autoCaptureDisabledReason by rememberSaveable { mutableStateOf<String?>(null) }
    val lastAnalysisTime = remember { AtomicLong(0L) }
    val ocrCheckInFlight = remember { AtomicBoolean(false) }
    val lastOcrReadiness = remember {
        AtomicReference(
            OcrReadiness(
                ready = false,
                score = 0.0,
                blocks = 0,
                reason = "ocr_not_started"
            )
        )
    }
    val autoCaptureStateMachine = remember {
        AutoCaptureStateMachine(
            stableFramesRequired = AUTO_CAPTURE_REQUIRED_STABLE_FRAMES,
            ocrStableFramesRequired = AUTO_CAPTURE_REQUIRED_OCR_STABLE_FRAMES,
            readyChecksRequired = AUTO_CAPTURE_REQUIRED_READY_CHECKS,
            ocrIntervalMs = AUTO_CAPTURE_OCR_CHECK_INTERVAL_MS,
            fastOcrIntervalMs = AUTO_CAPTURE_FAST_OCR_CHECK_INTERVAL_MS,
            minCaptureIntervalMs = AUTO_CAPTURE_MIN_CAPTURE_INTERVAL_MS,
            firstCaptureMinIntervalMs = AUTO_CAPTURE_FIRST_CAPTURE_INTERVAL_MS,
            stageAStabilityGraceMs = AUTO_CAPTURE_STAGE_A_GRACE_WINDOW_MS,
            refocusAfterMs = Long.MAX_VALUE,
            refocusMinIntervalMs = Long.MAX_VALUE
        ).apply { reset(SystemClock.elapsedRealtime()) }
    }
    val autoCaptureDecisionEngine = remember {
        AutoCaptureDecisionEngine(
            policy = AutoCaptureDecisionPolicy(
                minOcrReadyScore = AUTO_CAPTURE_MIN_OCR_READY_SCORE,
                allowSoftFailRelaxedCapture = AUTO_CAPTURE_ALLOW_SOFT_FAIL_RELAXED_CAPTURE,
                softFailMinOcrReadyScore = AUTO_CAPTURE_SOFT_FAIL_MIN_OCR_READY_SCORE,
                softFailReadyHitsRequired = AUTO_CAPTURE_SOFT_FAIL_READY_HITS_REQUIRED,
                softFailReadyHitWindowMs = AUTO_CAPTURE_SOFT_FAIL_READY_HIT_WINDOW_MS,
                relaxedNearReadyScoreMargin = AUTO_CAPTURE_RELAXED_NEAR_READY_SCORE_MARGIN,
                relaxedNearReadyStrikesRequired = AUTO_CAPTURE_RELAXED_NEAR_READY_STRIKES,
                relaxedNearReadyStrikeWindowMs = AUTO_CAPTURE_RELAXED_NEAR_READY_WINDOW_MS,
                maxRelaxedCaptureMotionScore = DEFAULT_AUTO_CAPTURE_MAX_MOTION_SCORE_OCR,
                minCaptureSharpness = DEFAULT_AUTO_CAPTURE_MIN_SHARPNESS_CAPTURE
            )
        )
    }
    val lumaFrameAnalyzer = remember {
        LumaFrameAnalyzer(
            thresholds = LumaGateThresholds(
                minSharpnessVarCapture = DEFAULT_AUTO_CAPTURE_MIN_SHARPNESS_CAPTURE,
                minSharpnessVarOcr = DEFAULT_AUTO_CAPTURE_MIN_SHARPNESS_OCR,
                maxMotionScoreCapture = DEFAULT_AUTO_CAPTURE_MAX_MOTION_SCORE_CAPTURE,
                maxMotionScoreOcr = DEFAULT_AUTO_CAPTURE_MAX_MOTION_SCORE_OCR,
                minLumaMean = DEFAULT_AUTO_CAPTURE_MIN_LUMA_MEAN,
                maxLumaMean = DEFAULT_AUTO_CAPTURE_MAX_LUMA_MEAN,
                maxHighlightPct = DEFAULT_AUTO_CAPTURE_MAX_HIGHLIGHT_PCT,
                minCenterEdgeDensity = DEFAULT_AUTO_CAPTURE_MIN_CENTER_EDGE_DENSITY,
                maxOuterToCenterEdgeRatio = DEFAULT_AUTO_CAPTURE_MAX_OUTER_TO_CENTER_EDGE_RATIO,
                edgeThreshold = DEFAULT_AUTO_CAPTURE_EDGE_THRESHOLD,
                gridWidth = AUTO_CAPTURE_GRID_WIDTH,
                gridHeight = AUTO_CAPTURE_GRID_HEIGHT,
                centerWindowRatio = AUTO_CAPTURE_CENTER_WINDOW_RATIO,
                motionWindowSize = AUTO_CAPTURE_MOTION_SMOOTHING_WINDOW
            )
        )
    }
    val autoCaptureCountdownController = remember {
        AutoCaptureCountdownController(
            stableHoldMs = AUTO_CAPTURE_STABLE_HOLD_MS,
            countdownMs = AUTO_CAPTURE_COUNTDOWN_MS
        )
    }
    var autoCaptureCountdownUiState by remember { mutableStateOf(AutoCaptureCountdownUiState()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            isCameraReady = false
            cameraError = "Camera permission denied."
            cameraUnavailableType = CameraUnavailableType.PERMISSION_DENIED
        } else {
            cameraUnavailableType = null
            cameraError = null
        }
    }

    val registerAutoCaptureFailure: (String) -> Unit = remember {
        { reason ->
            autoCaptureFailureCount += 1
            captureError = reason
            Log.w(
                AUTO_CAPTURE_LOG_TAG,
                "auto_capture_failure count=$autoCaptureFailureCount reason=$reason"
            )
            if (autoCaptureFailureCount >= AUTO_CAPTURE_MAX_FAILURES) {
                autoCaptureEnabled = false
                autoCaptureDisabledReason =
                    "Auto-capture paused. Tap re-enable to try again."
                Log.w(
                    AUTO_CAPTURE_LOG_TAG,
                    "auto_capture_disabled reason=$autoCaptureDisabledReason"
                )
            }
        }
    }

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    var currentDisplayRotation by remember {
        mutableIntStateOf(resolveDisplayRotation(context, previewView))
    }
    var boundPreview by remember { mutableStateOf<Preview?>(null) }
    val imageCapture = remember { ImageCapture.Builder().build() }
    val imageAnalysis = remember {
        ImageAnalysis.Builder()
            .setTargetResolution(Size(1280, 720))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
    }
    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(previewView, context) {
        val onRotationMaybeChanged = {
            val resolvedRotation = resolveDisplayRotation(context, previewView)
            if (resolvedRotation != currentDisplayRotation) {
                currentDisplayRotation = resolvedRotation
            }
        }
        val orientationListener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                onRotationMaybeChanged()
            }
        }
        val layoutChangeListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            onRotationMaybeChanged()
        }
        previewView.addOnLayoutChangeListener(layoutChangeListener)
        previewView.post { onRotationMaybeChanged() }
        if (orientationListener.canDetectOrientation()) {
            orientationListener.enable()
        }
        onDispose {
            orientationListener.disable()
            previewView.removeOnLayoutChangeListener(layoutChangeListener)
        }
    }

    LaunchedEffect(currentDisplayRotation, boundPreview) {
        boundPreview?.targetRotation = currentDisplayRotation
        imageCapture.targetRotation = currentDisplayRotation
        imageAnalysis.targetRotation = currentDisplayRotation
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) {
            autoCaptureEnabled = autoCaptureDisabledReason == null
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                decodeBitmapFromUri(context, uri, MAX_IMAGE_DIMENSION)
            }
            if (bitmap == null) {
                captureError = "Unable to open the selected image."
                return@launch
            }
            viewModel.onImageCaptured(bitmap)
            mainExecutor.execute {
                navController.navigate(Screen.Review.route)
            }
        }
    }

    var hasLaunchedGallery by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(scanState.lastCapturedImage, scanState.isProcessing, scanState.errorMessage) {
        val isFreshScanSession = !scanState.isProcessing &&
            scanState.lastCapturedImage == null &&
            scanState.errorMessage == null
        if (isFreshScanSession) {
            autoCaptureInFlight = false
            ocrCheckInFlight.set(false)
            lastAnalysisTime.set(0L)
            lastOcrReadiness.set(
                OcrReadiness(
                    ready = false,
                    score = 0.0,
                    blocks = 0,
                    reason = "ocr_not_started"
                )
            )
            autoCaptureStateMachine.reset(SystemClock.elapsedRealtime())
            autoCaptureDecisionEngine.reset()
            lumaFrameAnalyzer.reset()
            autoCaptureCountdownController.reset()
            autoCaptureCountdownUiState = AutoCaptureCountdownUiState()
            autoCaptureFailureCount = 0
            autoCaptureDisabledReason = null
            captureError = null
            autoCaptureEnabled = true
        }
    }

    val captureNowState = rememberUpdatedState<(Boolean) -> Unit> { fromAutoCapture ->
        if (fromAutoCapture && !autoCaptureEnabled) return@rememberUpdatedState
        if (fromAutoCapture && autoCaptureDisabledReason != null) return@rememberUpdatedState
        if (!hasCameraPermission || !isCameraReady) {
            cameraError = "Camera is not ready yet."
            cameraUnavailableType = CameraUnavailableType.TEMPORARY_ERROR
            return@rememberUpdatedState
        }
        if (autoCaptureInFlight) return@rememberUpdatedState
        autoCaptureInFlight = true
        autoCaptureStateMachine.clearTransientState()
        autoCaptureDecisionEngine.reset()
        lumaFrameAnalyzer.reset()
        autoCaptureCountdownController.reset()
        autoCaptureCountdownUiState = AutoCaptureCountdownUiState()
        ocrCheckInFlight.set(false)
        val resolvedRotation = resolveDisplayRotation(context, previewView)
        currentDisplayRotation = resolvedRotation
        boundPreview?.targetRotation = resolvedRotation
        imageCapture.targetRotation = resolvedRotation
        imageAnalysis.targetRotation = resolvedRotation
        val outputDir = context.cacheDir
        val outputFile = File(
            outputDir,
            "captured_${System.currentTimeMillis()}.jpg"
        )
        val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
        try {
            imageCapture.takePicture(
                outputOptions,
                cameraExecutor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        val savedFile = outputFileResults.savedUri?.path?.let { File(it) } ?: outputFile
                        val bitmap = decodeBitmapFromFile(savedFile, MAX_IMAGE_DIMENSION)
                        mainExecutor.execute {
                            autoCaptureInFlight = false
                            if (bitmap == null) {
                                if (fromAutoCapture) {
                                    registerAutoCaptureFailure(
                                        "Auto-capture found a frame, but the image could not be decoded."
                                    )
                                } else {
                                    captureError = "Captured image could not be decoded."
                                }
                                return@execute
                            }
                            autoCaptureFailureCount = 0
                            autoCaptureDisabledReason = null
                            captureError = null
                            autoCaptureStateMachine.reset(SystemClock.elapsedRealtime())
                            autoCaptureDecisionEngine.reset()
                            autoCaptureCountdownController.reset()
                            autoCaptureCountdownUiState = AutoCaptureCountdownUiState()
                            viewModel.onImageCaptured(bitmap)
                            autoCaptureEnabled = false
                            navController.navigate(Screen.Review.route)
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        mainExecutor.execute {
                            autoCaptureInFlight = false
                            autoCaptureCountdownController.reset()
                            autoCaptureCountdownUiState = AutoCaptureCountdownUiState()
                            val reason = exception.message ?: "Image capture failed."
                            if (fromAutoCapture) {
                                registerAutoCaptureFailure("Auto-capture failed: $reason")
                            } else {
                                captureError = reason
                            }
                        }
                        Log.e("ScanScreen", "Image capture failed: ${exception.message}", exception)
                    }
                }
            )
        } catch (t: Throwable) {
            autoCaptureInFlight = false
            autoCaptureCountdownController.reset()
            autoCaptureCountdownUiState = AutoCaptureCountdownUiState()
            val reason = t.message ?: "Camera capture unavailable."
            if (fromAutoCapture) {
                registerAutoCaptureFailure("Auto-capture stopped: $reason")
            } else {
                captureError = reason
            }
        }
    }

    val autoCaptureEnabledState = rememberUpdatedState(autoCaptureEnabled)
    val autoCaptureInFlightState = rememberUpdatedState(autoCaptureInFlight)
    val cameraReadyState = rememberUpdatedState(isCameraReady)
    val cameraPermissionState = rememberUpdatedState(hasCameraPermission)
    val cameraErrorState = rememberUpdatedState(cameraError)
    val autoCaptureDisabledReasonState = rememberUpdatedState(autoCaptureDisabledReason)

    DisposableEffect(imageAnalysis) {
        var tickCount = 0
        var lastTickLogAt = 0L
        var lastGateSnapshot: String? = null
        var lastBlockReason: String? = null
        var lastStageAStable = false
        var lastStageBReady = false
        var lastCountdownUiState = AutoCaptureCountdownUiState()
        val traceEnabled = autoCaptureTraceRecorder.isEnabled

        imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
            try {
                val now = SystemClock.elapsedRealtime()
                val last = lastAnalysisTime.get()
                if (now - last < AUTO_CAPTURE_ANALYSIS_INTERVAL_MS) {
                    return@setAnalyzer
                }
                lastAnalysisTime.set(now)
                val gateEnabled = autoCaptureEnabledState.value
                val gateInFlight = autoCaptureInFlightState.value
                val gateCameraReady = cameraReadyState.value
                val gatePermission = cameraPermissionState.value
                val gateCameraError = cameraErrorState.value != null
                val gateDisabledReason = autoCaptureDisabledReasonState.value != null
                val gateSnapshot =
                    "enabled=$gateEnabled,inFlight=$gateInFlight,cameraReady=$gateCameraReady," +
                        "permission=$gatePermission,cameraError=$gateCameraError,disabled=$gateDisabledReason"
                if (gateSnapshot != lastGateSnapshot) {
                    Log.i(AUTO_CAPTURE_LOG_TAG, "gate_change $gateSnapshot")
                    lastGateSnapshot = gateSnapshot
                }

                tickCount += 1
                if (lastTickLogAt == 0L || (now - lastTickLogAt) >= 1000L) {
                    Log.i(
                        AUTO_CAPTURE_LOG_TAG,
                        "analyzer_tps=$tickCount frame=${imageProxy.width}x${imageProxy.height} " +
                            "rotation=${imageProxy.imageInfo.rotationDegrees} format=${imageProxy.format} " +
                            "stageA=${autoCaptureStateMachine.isStageAStable()} " +
                            "stageAOcr=${autoCaptureStateMachine.isStageAOcrStable()} " +
                            "stageB=${autoCaptureStateMachine.isStageBReady()}"
                    )
                    tickCount = 0
                    lastTickLogAt = now
                }

                if (!gateEnabled || gateInFlight || !gateCameraReady || !gatePermission ||
                    gateCameraError || gateDisabledReason
                ) {
                    autoCaptureStateMachine.clearTransientState()
                    autoCaptureDecisionEngine.reset()
                    lumaFrameAnalyzer.reset()
                    autoCaptureCountdownController.reset()
                    val idleUi = AutoCaptureCountdownUiState()
                    if (lastCountdownUiState != idleUi) {
                        lastCountdownUiState = idleUi
                        mainExecutor.execute {
                            autoCaptureCountdownUiState = idleUi
                        }
                    }
                    ocrCheckInFlight.set(false)
                    val blockReason = when {
                        !gateEnabled -> "auto_capture_off"
                        gateInFlight -> "capture_in_flight"
                        !gateCameraReady -> "camera_not_ready"
                        !gatePermission -> "camera_permission_missing"
                        gateCameraError -> "camera_error_active"
                        else -> "auto_capture_disabled"
                    }
                    if (blockReason != lastBlockReason) {
                        Log.i(AUTO_CAPTURE_LOG_TAG, "capture_blocked reason=$blockReason")
                        lastBlockReason = blockReason
                    }
                    if (traceEnabled) {
                        val readinessSnapshot = lastOcrReadiness.get()
                        autoCaptureTraceRecorder.record(
                            AutoCaptureTraceRecord(
                                timestampMs = now,
                                stageAPass = false,
                                stageABand = StageABand.BLOCKED,
                                stageAFailReason = blockReason,
                                motionRaw = 0.0,
                                motionSmoothed = 0.0,
                                sharpness = 0.0,
                                exposureOk = false,
                                highlightOk = false,
                                edgeCenterScore = 0.0,
                                edgeOuterScore = 0.0,
                                framingOk = false,
                                ocrAttempted = false,
                                ocrReadyScore = readinessSnapshot.score,
                                ocrReady = readinessSnapshot.ready,
                                ocrInFlight = ocrCheckInFlight.get(),
                                cooldownRemainingMs = 0L,
                                captureFired = false
                            )
                        )
                    }
                    return@setAnalyzer
                }

                val yPlane = imageProxy.planes.firstOrNull()
                if (yPlane == null) {
                    autoCaptureCountdownController.reset()
                    val idleUi = AutoCaptureCountdownUiState()
                    if (lastCountdownUiState != idleUi) {
                        lastCountdownUiState = idleUi
                        mainExecutor.execute {
                            autoCaptureCountdownUiState = idleUi
                        }
                    }
                    if ("missing_luma_plane" != lastBlockReason) {
                        Log.w(AUTO_CAPTURE_LOG_TAG, "capture_blocked reason=missing_luma_plane")
                        lastBlockReason = "missing_luma_plane"
                    }
                    if (traceEnabled) {
                        val readinessSnapshot = lastOcrReadiness.get()
                        autoCaptureTraceRecorder.record(
                            AutoCaptureTraceRecord(
                                timestampMs = now,
                                stageAPass = false,
                                stageABand = StageABand.BLOCKED,
                                stageAFailReason = "missing_luma_plane",
                                motionRaw = 0.0,
                                motionSmoothed = 0.0,
                                sharpness = 0.0,
                                exposureOk = false,
                                highlightOk = false,
                                edgeCenterScore = 0.0,
                                edgeOuterScore = 0.0,
                                framingOk = false,
                                ocrAttempted = false,
                                ocrReadyScore = readinessSnapshot.score,
                                ocrReady = readinessSnapshot.ready,
                                ocrInFlight = ocrCheckInFlight.get(),
                                cooldownRemainingMs = 0L,
                                captureFired = false
                            )
                        )
                    }
                    return@setAnalyzer
                }

                val rowStride = yPlane.rowStride
                val pixelStride = yPlane.pixelStride
                val buffer = yPlane.buffer
                val limit = buffer.limit()
                val stageAEvaluation = lumaFrameAnalyzer.evaluate(
                    frameWidth = imageProxy.width,
                    frameHeight = imageProxy.height
                ) { x, y ->
                    getLuma(buffer, rowStride, pixelStride, x, y, limit)
                }

                val tick = autoCaptureStateMachine.onStageA(now, stageAEvaluation)
                if (tick.stageAStable != lastStageAStable) {
                    Log.i(
                        AUTO_CAPTURE_LOG_TAG,
                        "stageA_change stable=${tick.stageAStable} sharpness=${stageAEvaluation.metrics.sharpness.format2()} " +
                            "motionRaw=${stageAEvaluation.metrics.motionRaw.format3()} " +
                            "motionSmoothed=${stageAEvaluation.metrics.motion.format3()} " +
                            "band=${tick.stageABand.name.lowercase()} " +
                            "luma=${stageAEvaluation.metrics.lumaMean.format1()} " +
                            "highlight=${(stageAEvaluation.metrics.highlightPct * 100.0).format1()}% " +
                            "reason=${stageAEvaluation.reason}"
                    )
                    lastStageAStable = tick.stageAStable
                }

                var ocrAttemptedThisCycle = false
                if (tick.shouldRunOcr && ocrCheckInFlight.compareAndSet(false, true)) {
                    ocrAttemptedThisCycle = true
                    val previewBitmap = buildPreviewBitmapForOcr(
                        imageProxy = imageProxy,
                        maxDimension = AUTO_CAPTURE_OCR_PREVIEW_MAX_DIMENSION,
                        centerRatio = AUTO_CAPTURE_OCR_CENTER_RATIO
                    )
                    if (previewBitmap == null) {
                        lastOcrReadiness.set(
                            OcrReadiness(
                                ready = false,
                                score = 0.0,
                                blocks = 0,
                                reason = "ocr_preview_unavailable"
                            )
                        )
                        autoCaptureStateMachine.onOcrReadiness(false)
                        ocrCheckInFlight.set(false)
                    } else {
                        val rotation = imageProxy.imageInfo.rotationDegrees
                        val previousReadiness = lastOcrReadiness.get()
                        scope.launch(Dispatchers.Default) {
                            val ocrReadiness = evaluateFrameOcrReadiness(
                                bitmap = previewBitmap,
                                rotationDegrees = rotation,
                                stageAEvaluation = stageAEvaluation,
                                previousReadiness = previousReadiness
                            )
                            lastOcrReadiness.set(ocrReadiness)
                            val stageBChanged = autoCaptureStateMachine.onOcrReadiness(ocrReadiness.ready)
                            if (stageBChanged) {
                                Log.i(
                                    AUTO_CAPTURE_LOG_TAG,
                                    "stageB_change ready=${autoCaptureStateMachine.isStageBReady()} " +
                                        "score=${ocrReadiness.score.format2()} blocks=${ocrReadiness.blocks} " +
                                        "reason=${ocrReadiness.reason}"
                                )
                            }
                            ocrCheckInFlight.set(false)
                        }
                    }
                }

                val stageBReady = autoCaptureStateMachine.isStageBReady()
                if (stageBReady != lastStageBReady) {
                    lastStageBReady = stageBReady
                    val ocrReadiness = lastOcrReadiness.get()
                    Log.i(
                        AUTO_CAPTURE_LOG_TAG,
                        "stageB_state ready=$stageBReady score=${ocrReadiness.score.format2()} " +
                            "blocks=${ocrReadiness.blocks} checks=${tick.ocrChecks}"
                    )
                }

                val ocrReadiness = lastOcrReadiness.get()
                val captureDecision = autoCaptureDecisionEngine.evaluate(
                    nowMs = now,
                    tick = tick,
                    stageAEvaluation = stageAEvaluation,
                    ocrReadiness = ocrReadiness
                )
                val countdownTick = autoCaptureCountdownController.onReadiness(
                    nowMs = now,
                    ready = captureDecision.shouldCapture
                )
                val countdownUiState = countdownTick.toUiState()
                if (countdownUiState != lastCountdownUiState) {
                    lastCountdownUiState = countdownUiState
                    mainExecutor.execute {
                        autoCaptureCountdownUiState = countdownUiState
                    }
                }
                val shouldCaptureNow = countdownTick.shouldCapture

                if (isDebugBuild) {
                    val stageAFailReason = if (stageAEvaluation.strictPassed || stageAEvaluation.relaxedPassed) {
                        "none"
                    } else {
                        stageAEvaluation.reason
                    }
                    Log.d(
                        AUTO_CAPTURE_LOG_TAG,
                        "diag stageA_fail_reason=$stageAFailReason " +
                            "stageA_band_strict_or_relaxed=${tick.stageABand.name.lowercase()} " +
                            "motion_raw=${stageAEvaluation.metrics.motionRaw.format3()} " +
                            "motion_smoothed=${stageAEvaluation.metrics.motion.format3()} " +
                            "sharpness_score=${stageAEvaluation.metrics.sharpness.format2()} " +
                            "stageB_ready_score=${ocrReadiness.score.format2()} " +
                            "stageB_ready_boolean=${tick.stageBReady} " +
                            "near_ready_strikes=${captureDecision.relaxedNearReadyStrikes} " +
                            "countdown_phase=${countdownTick.phase.name.lowercase()} " +
                            "countdown_remaining_ms=${countdownTick.countdownRemainingMs} " +
                            "hold_remaining_ms=${countdownTick.holdRemainingMs} " +
                            "ocr_in_flight=${ocrCheckInFlight.get()} " +
                            "cooldown_remaining_ms=${tick.cooldownRemainingMs}"
                    )
                }

                if (shouldCaptureNow) {
                    Log.i(
                        AUTO_CAPTURE_LOG_TAG,
                        "capture_trigger sharpness=${stageAEvaluation.metrics.sharpness.format2()} " +
                            "motionRaw=${stageAEvaluation.metrics.motionRaw.format3()} " +
                            "motionSmoothed=${stageAEvaluation.metrics.motion.format3()} " +
                            "ocrScore=${ocrReadiness.score.format2()} " +
                            "mode=${when {
                                captureDecision.usedRelaxedNearReadyPath -> "relaxed_near_ready"
                                captureDecision.usedRelaxedGraceReadyPath -> "relaxed_grace"
                                else -> "strict"
                            }}"
                    )
                    lastBlockReason = "capture_trigger"
                    autoCaptureStateMachine.onCaptureTriggered(now)
                    mainExecutor.execute {
                        captureNowState.value(true)
                    }
                } else {
                    val blockReason = if (!captureDecision.shouldCapture) {
                        if (captureDecision.blockReason == "stage_b_not_ready") {
                            val ocrReason = lastOcrReadiness.get().reason
                            "stage_b_not_ready:$ocrReason"
                        } else {
                            captureDecision.blockReason
                        }
                    } else if (countdownTick.phase == AutoCaptureCountdownPhase.HOLDING) {
                        "stable_hold_pending"
                    } else if (countdownTick.phase == AutoCaptureCountdownPhase.COUNTDOWN) {
                        "countdown_active"
                    } else {
                        "countdown_pending"
                    }
                    if (blockReason != lastBlockReason) {
                        Log.i(
                            AUTO_CAPTURE_LOG_TAG,
                            "capture_blocked reason=$blockReason sharpness=${stageAEvaluation.metrics.sharpness.format2()} " +
                                "motionRaw=${stageAEvaluation.metrics.motionRaw.format3()} " +
                                "motionSmoothed=${stageAEvaluation.metrics.motion.format3()} " +
                                "luma=${stageAEvaluation.metrics.lumaMean.format1()} " +
                                "highlight=${(stageAEvaluation.metrics.highlightPct * 100.0).format1()}% " +
                                "holdRemainingMs=${countdownTick.holdRemainingMs} " +
                                "countdownRemainingMs=${countdownTick.countdownRemainingMs}"
                        )
                        lastBlockReason = blockReason
                    }
                }

                val exposureOk = stageAEvaluation.metrics.lumaMean in
                    DEFAULT_AUTO_CAPTURE_MIN_LUMA_MEAN..DEFAULT_AUTO_CAPTURE_MAX_LUMA_MEAN
                val highlightOk = stageAEvaluation.metrics.highlightPct <= DEFAULT_AUTO_CAPTURE_MAX_HIGHLIGHT_PCT
                val framingOk = exposureOk &&
                    highlightOk &&
                    stageAEvaluation.metrics.centerEdgeDensity >= DEFAULT_AUTO_CAPTURE_MIN_CENTER_EDGE_DENSITY &&
                    stageAEvaluation.metrics.outerEdgeDensity <=
                    stageAEvaluation.metrics.centerEdgeDensity * DEFAULT_AUTO_CAPTURE_MAX_OUTER_TO_CENTER_EDGE_RATIO
                val stageAFailReason = if (stageAEvaluation.strictPassed) {
                    "none"
                } else {
                    stageAEvaluation.reason
                }
                if (traceEnabled) {
                    autoCaptureTraceRecorder.record(
                        AutoCaptureTraceRecord(
                            timestampMs = now,
                            stageAPass = stageAEvaluation.strictPassed,
                            stageABand = tick.stageABand,
                            stageAFailReason = stageAFailReason,
                            motionRaw = stageAEvaluation.metrics.motionRaw,
                            motionSmoothed = stageAEvaluation.metrics.motion,
                            sharpness = stageAEvaluation.metrics.sharpness,
                            exposureOk = exposureOk,
                            highlightOk = highlightOk,
                            edgeCenterScore = stageAEvaluation.metrics.centerEdgeDensity,
                            edgeOuterScore = stageAEvaluation.metrics.outerEdgeDensity,
                            framingOk = framingOk,
                            ocrAttempted = ocrAttemptedThisCycle,
                            ocrReadyScore = ocrReadiness.score,
                            ocrReady = ocrReadiness.ready,
                            ocrInFlight = ocrCheckInFlight.get(),
                            cooldownRemainingMs = tick.cooldownRemainingMs,
                            captureFired = shouldCaptureNow
                        )
                    )
                }
            } catch (t: Throwable) {
                autoCaptureStateMachine.clearTransientState()
                autoCaptureDecisionEngine.reset()
                lumaFrameAnalyzer.reset()
                autoCaptureCountdownController.reset()
                ocrCheckInFlight.set(false)
                val idleUi = AutoCaptureCountdownUiState()
                if (lastCountdownUiState != idleUi) {
                    lastCountdownUiState = idleUi
                    mainExecutor.execute {
                        autoCaptureCountdownUiState = idleUi
                    }
                }
                if (traceEnabled) {
                    autoCaptureTraceRecorder.record(
                        AutoCaptureTraceRecord(
                            timestampMs = SystemClock.elapsedRealtime(),
                            stageAPass = false,
                            stageABand = StageABand.BLOCKED,
                            stageAFailReason = "analyzer_exception",
                            motionRaw = 0.0,
                            motionSmoothed = 0.0,
                            sharpness = 0.0,
                            exposureOk = false,
                            highlightOk = false,
                            edgeCenterScore = 0.0,
                            edgeOuterScore = 0.0,
                            framingOk = false,
                            ocrAttempted = false,
                            ocrReadyScore = 0.0,
                            ocrReady = false,
                            ocrInFlight = false,
                            cooldownRemainingMs = 0L,
                            captureFired = false
                        )
                    )
                }
                mainExecutor.execute {
                    autoCaptureEnabled = false
                    autoCaptureDisabledReason =
                        "Auto-capture paused after analyzer instability."
                    captureError = "Auto-capture paused. Tap re-enable."
                }
                Log.e(AUTO_CAPTURE_LOG_TAG, "analyzer_failure", t)
            } finally {
                imageProxy.close()
            }
        }
        onDispose {
            imageAnalysis.clearAnalyzer()
        }
    }

    DisposableEffect(autoCaptureTraceRecorder) {
        onDispose {
            autoCaptureTraceRecorder.close()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
                if (hasCameraPermission != granted) {
                    hasCameraPermission = granted
                    if (!granted) {
                        isCameraReady = false
                        cameraUnavailableType = CameraUnavailableType.PERMISSION_DENIED
                    } else {
                        cameraUnavailableType = null
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            imageAnalysis.clearAnalyzer()
            autoCaptureStateMachine.clearTransientState()
            autoCaptureDecisionEngine.reset()
            lumaFrameAnalyzer.reset()
            autoCaptureCountdownController.reset()
            autoCaptureCountdownUiState = AutoCaptureCountdownUiState()
            boundPreview = null
            runCatching { cameraProvider?.unbindAll() }
            runCatching {
                if (!cameraExecutor.isShutdown) {
                    cameraExecutor.shutdown()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (requestCameraPermissionOnLaunch && !hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(launchGallery) {
        if (launchGallery && !hasLaunchedGallery) {
            hasLaunchedGallery = true
            autoCaptureEnabled = false
            autoCaptureStateMachine.clearTransientState()
            autoCaptureDecisionEngine.reset()
            lumaFrameAnalyzer.reset()
            autoCaptureCountdownController.reset()
            autoCaptureCountdownUiState = AutoCaptureCountdownUiState()
            galleryLauncher.launch("image/*")
        } else if (!launchGallery && hasLaunchedGallery) {
            autoCaptureEnabled = autoCaptureDisabledReason == null
        }
    }

    LaunchedEffect(hasCameraPermission, cameraBindAttempt) {
        if (hasCameraPermission) {
            isBindingCamera = true
            try {
                val provider = withContext(Dispatchers.IO) {
                    cameraProviderFuture.get()
                }
                cameraProvider = provider
                val resolvedRotation = resolveDisplayRotation(context, previewView)
                currentDisplayRotation = resolvedRotation
                imageCapture.targetRotation = resolvedRotation
                imageAnalysis.targetRotation = resolvedRotation
                val preview = Preview.Builder()
                    .setTargetRotation(resolvedRotation)
                    .build()
                    .apply {
                    setSurfaceProvider(previewView.surfaceProvider)
                }
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                if (!provider.hasCamera(cameraSelector)) {
                    boundPreview = null
                    isCameraReady = false
                    cameraError = "No back camera available on this device."
                    cameraUnavailableType = CameraUnavailableType.HARDWARE_UNAVAILABLE
                    isBindingCamera = false
                    return@LaunchedEffect
                }
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture,
                    imageAnalysis
                )
                boundPreview = preview
                isCameraReady = true
                cameraError = null
                cameraUnavailableType = null
            } catch (e: Exception) {
                boundPreview = null
                isCameraReady = false
                cameraError = e.message ?: "Camera temporarily unavailable."
                cameraUnavailableType = CameraUnavailableType.TEMPORARY_ERROR
                Log.e("ScanScreen", "Camera binding failed", e)
            } finally {
                isBindingCamera = false
            }
        } else {
            boundPreview = null
            isCameraReady = false
            isBindingCamera = false
            cameraUnavailableType = CameraUnavailableType.PERMISSION_DENIED
        }
    }

    val unavailableType = when {
        !hasCameraPermission -> CameraUnavailableType.PERMISSION_DENIED
        cameraUnavailableType == CameraUnavailableType.HARDWARE_UNAVAILABLE -> CameraUnavailableType.HARDWARE_UNAVAILABLE
        cameraUnavailableType == CameraUnavailableType.TEMPORARY_ERROR -> CameraUnavailableType.TEMPORARY_ERROR
        else -> null
    }
    val cameraStatusHint = when {
        autoCaptureInFlight -> "Capturing..."
        autoCaptureCountdownUiState.phase == AutoCaptureCountdownPhase.COUNTDOWN ->
            "Capturing in ${autoCaptureCountdownUiState.countdownSecondsRemaining}s..."
        autoCaptureCountdownUiState.phase == AutoCaptureCountdownPhase.HOLDING ->
            "Hold steady..."
        autoCaptureEnabled && isCameraReady -> "Auto on. Hold still."
        else -> "Tap Capture."
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Scan",
                subtitle = "Step 1 of 3",
                navController = navController,
                showBack = true,
                showHome = true,
                showContacts = true
            )
        }
    ) { paddingValues ->
        AppBackground(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(AppDimens.lg),
                verticalArrangement = Arrangement.spacedBy(AppDimens.lg)
            ) {
                StepIndicator(steps = listOf("Scan", "Review", "Context"), currentIndex = 0)

                if (autoCaptureDisabledReason != null) {
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(AppDimens.sm)) {
                            Text(
                                text = autoCaptureDisabledReason.orEmpty(),
                                color = MaterialTheme.colorScheme.error
                            )
                            SecondaryButton(
                                text = "Re-enable auto-capture",
                                onClick = {
                                    autoCaptureFailureCount = 0
                                    autoCaptureDisabledReason = null
                                    captureError = null
                                    autoCaptureEnabled = true
                                    autoCaptureStateMachine.reset(SystemClock.elapsedRealtime())
                                    autoCaptureDecisionEngine.reset()
                                    lumaFrameAnalyzer.reset()
                                    autoCaptureCountdownController.reset()
                                    autoCaptureCountdownUiState = AutoCaptureCountdownUiState()
                                    ocrCheckInFlight.set(false)
                                    lastOcrReadiness.set(
                                        OcrReadiness(
                                            ready = false,
                                            score = 0.0,
                                            blocks = 0,
                                            reason = "ocr_not_started"
                                        )
                                    )
                                    Log.i(AUTO_CAPTURE_LOG_TAG, "auto_capture_reenabled")
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = hasCameraPermission && unavailableType == null
                            )
                        }
                    }
                }

                if (captureError != null && unavailableType == null) {
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(AppDimens.sm)) {
                            Text(
                                text = captureError.orEmpty(),
                                color = MaterialTheme.colorScheme.error
                            )
                            SecondaryButton(
                                text = "Dismiss",
                                onClick = {
                                    captureError = null
                                    autoCaptureEnabled = autoCaptureDisabledReason == null
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }


                if (isBindingCamera && unavailableType == null) {
                    AppCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        LoadingState(
                            message = "Starting camera...",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else if (unavailableType == null) {
                    AppCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surface)
                        ) {
                            AndroidView(
                                factory = { previewView },
                                modifier = Modifier
                                    .matchParentSize()
                                    .border(
                                        width = AppDimens.divider,
                                        color = AppTheme.colors.border,
                                        shape = MaterialTheme.shapes.large
                                    )
                                    .background(AppTheme.colors.surfaceMuted)
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .fillMaxWidth(0.9f)
                                    .aspectRatio(1.75f)
                                    .border(
                                        width = AppDimens.outline,
                                        color = AppTheme.colors.primary.copy(alpha = 0.92f),
                                        shape = MaterialTheme.shapes.large
                                    )
                            )
                            StatusPill(
                                label = cameraStatusHint,
                                tone = when {
                                    autoCaptureInFlight -> StatusPillTone.Brand
                                    autoCaptureEnabled && isCameraReady -> StatusPillTone.Success
                                    else -> StatusPillTone.Neutral
                                },
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = AppDimens.sm)
                            )
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .padding(AppDimens.sm)
                                    .semantics {
                                        contentDescription = "Camera preview. Tap to capture."
                                    }
                                    .clickable(
                                        enabled = hasCameraPermission && isCameraReady,
                                        onClick = { captureNowState.value(false) }
                                    )
                            )
                        }
                    }
                } else {
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(AppDimens.md)) {
                            when (unavailableType) {
                                CameraUnavailableType.PERMISSION_DENIED -> {
                                    EmptyState(
                                        title = "Camera permission is off",
                                        subtitle = "Allow camera access or import a photo.",
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    PrimaryButton(
                                        text = "Grant permission",
                                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                CameraUnavailableType.HARDWARE_UNAVAILABLE -> {
                                    EmptyState(
                                        title = "Camera hardware unavailable",
                                        subtitle = "No back camera found. Import a photo instead.",
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    SecondaryButton(
                                        text = "Retry camera detection",
                                        onClick = {
                                            cameraError = null
                                            cameraUnavailableType = null
                                            cameraBindAttempt += 1
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                CameraUnavailableType.TEMPORARY_ERROR -> {
                                    EmptyState(
                                        title = "Camera temporarily unavailable",
                                        subtitle = cameraError ?: "Camera failed to start. Retry or import a photo.",
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    PrimaryButton(
                                        text = "Retry camera",
                                        onClick = {
                                            cameraError = null
                                            cameraUnavailableType = null
                                            isCameraReady = false
                                            cameraBindAttempt += 1
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                null -> Unit
                            }
                        }
                    }
                }

                BottomActionBar(
                    supportingContent = {
                        StatusPill(
                            label = if (unavailableType == null) {
                                "On-device processing"
                            } else {
                                "Photo import available"
                            },
                            tone = StatusPillTone.Brand
                        )
                    },
                    secondaryAction = {
                        SecondaryButton(
                            text = "Import",
                            onClick = { galleryLauncher.launch("image/*") },
                            modifier = Modifier.fillMaxWidth(),
                            icon = Icons.Filled.PhotoLibrary
                        )
                    }
                ) {
                    PrimaryButton(
                        text = "Capture",
                        onClick = {
                            captureNowState.value(false)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Filled.CameraAlt,
                        enabled = hasCameraPermission && isCameraReady
                    )
                }
                Spacer(modifier = Modifier.height(AppDimens.sm))
            }
        }
    }
}

private const val AUTO_CAPTURE_LOG_TAG = "AutoCapture"
private const val MAX_IMAGE_DIMENSION = 2000
private const val AUTO_CAPTURE_MAX_FAILURES = 3
private const val AUTO_CAPTURE_ANALYSIS_INTERVAL_MS = 100L
private const val AUTO_CAPTURE_REQUIRED_STABLE_FRAMES = 3
private const val AUTO_CAPTURE_REQUIRED_OCR_STABLE_FRAMES = 1
private const val AUTO_CAPTURE_REQUIRED_READY_CHECKS = 2
private const val AUTO_CAPTURE_MIN_CAPTURE_INTERVAL_MS = 900L
private const val AUTO_CAPTURE_FIRST_CAPTURE_INTERVAL_MS = 400L
private const val AUTO_CAPTURE_STABLE_HOLD_MS = 700L
private const val AUTO_CAPTURE_COUNTDOWN_MS = 1200L
private const val AUTO_CAPTURE_OCR_CHECK_INTERVAL_MS = 250L
private const val AUTO_CAPTURE_FAST_OCR_CHECK_INTERVAL_MS = 150L
private const val AUTO_CAPTURE_OCR_TIMEOUT_MS = 700L
private const val AUTO_CAPTURE_OCR_TIMEOUT_FAST_FAIL_MS = 450L
private const val AUTO_CAPTURE_OCR_TIMEOUT_NEAR_READY_MS = 900L
private const val AUTO_CAPTURE_STAGE_A_GRACE_WINDOW_MS = 300L
private const val AUTO_CAPTURE_OCR_PREVIEW_MAX_DIMENSION = 420
private const val AUTO_CAPTURE_OCR_CENTER_RATIO = 0.6
private const val AUTO_CAPTURE_MIN_OCR_BLOCKS = 2
private const val AUTO_CAPTURE_MIN_OCR_READY_SCORE = 0.72
private const val AUTO_CAPTURE_ALLOW_SOFT_FAIL_RELAXED_CAPTURE = true
private const val AUTO_CAPTURE_SOFT_FAIL_MIN_OCR_READY_SCORE = 0.90
private const val AUTO_CAPTURE_SOFT_FAIL_READY_HITS_REQUIRED = 3
private const val AUTO_CAPTURE_SOFT_FAIL_READY_HIT_WINDOW_MS = 1500L
private const val AUTO_CAPTURE_RELAXED_NEAR_READY_SCORE_MARGIN = 0.03
private const val AUTO_CAPTURE_RELAXED_NEAR_READY_STRIKES = 3
private const val AUTO_CAPTURE_RELAXED_NEAR_READY_WINDOW_MS = 1800L
private const val AUTO_CAPTURE_GRID_WIDTH = 48
private const val AUTO_CAPTURE_GRID_HEIGHT = 32
private const val AUTO_CAPTURE_CENTER_WINDOW_RATIO = 0.6
private const val AUTO_CAPTURE_MOTION_SMOOTHING_WINDOW = 5
private const val DEFAULT_AUTO_CAPTURE_MIN_SHARPNESS_CAPTURE = 24.0
private const val DEFAULT_AUTO_CAPTURE_MIN_SHARPNESS_OCR = 20.0
private const val DEFAULT_AUTO_CAPTURE_MAX_MOTION_SCORE_CAPTURE = 0.055
private const val DEFAULT_AUTO_CAPTURE_MAX_MOTION_SCORE_OCR = 0.075
private const val DEFAULT_AUTO_CAPTURE_MIN_LUMA_MEAN = 48.0
private const val DEFAULT_AUTO_CAPTURE_MAX_LUMA_MEAN = 220.0
private const val DEFAULT_AUTO_CAPTURE_MAX_HIGHLIGHT_PCT = 0.20
private const val DEFAULT_AUTO_CAPTURE_MIN_CENTER_EDGE_DENSITY = 0.055
private const val DEFAULT_AUTO_CAPTURE_MAX_OUTER_TO_CENTER_EDGE_RATIO = 0.92
private const val DEFAULT_AUTO_CAPTURE_EDGE_THRESHOLD = 20

private val DISABLED_TRACE_RECORDER = object : AutoCaptureTraceRecorder {
    override val isEnabled: Boolean = false
    override fun record(record: AutoCaptureTraceRecord) = Unit
    override fun close() = Unit
}

private data class AutoCaptureCountdownUiState(
    val phase: AutoCaptureCountdownPhase = AutoCaptureCountdownPhase.IDLE,
    val countdownSecondsRemaining: Int = 0
)

private fun AutoCaptureCountdownTick.toUiState(): AutoCaptureCountdownUiState {
    if (phase != AutoCaptureCountdownPhase.COUNTDOWN) {
        return AutoCaptureCountdownUiState(phase = phase, countdownSecondsRemaining = 0)
    }
    val secondsRemaining = if (countdownRemainingMs <= 0L) {
        0
    } else {
        ((countdownRemainingMs + 999L) / 1000L).toInt().coerceAtLeast(1)
    }
    return AutoCaptureCountdownUiState(
        phase = phase,
        countdownSecondsRemaining = secondsRemaining
    )
}

private fun resolveDisplayRotation(context: Context, previewView: PreviewView): Int {
    val resolved = previewView.display?.rotation ?: run {
        if (Build.VERSION.SDK_INT >= 30) {
            context.display?.rotation
        } else {
            null
        }
    } ?: Surface.ROTATION_0
    return when (resolved) {
        Surface.ROTATION_0,
        Surface.ROTATION_90,
        Surface.ROTATION_180,
        Surface.ROTATION_270 -> resolved
        else -> Surface.ROTATION_0
    }
}

private fun resolveAppVersionName(context: Context): String {
    return runCatching {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        packageInfo.versionName ?: "unknown"
    }.getOrDefault("unknown")
}

private fun resolveAppVersionCode(context: Context): Long {
    return runCatching {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
    }.getOrDefault(0L)
}

private fun decodeBitmapFromFile(file: File, maxDimension: Int): Bitmap? {
    return runCatching {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        val sampleSize = calculateInSampleSize(options.outWidth, options.outHeight, maxDimension)
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(file.path, decodeOptions) ?: return null
        val exifOrientation = readExifOrientation(file)
        normalizeBitmapOrientation(decoded, exifOrientation)
    }.getOrNull()
}

private fun decodeBitmapFromUri(context: Context, uri: Uri, maxDimension: Int): Bitmap? {
    return runCatching {
        val decoded = if (Build.VERSION.SDK_INT >= 28) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val size = info.size
                val largest = max(size.width, size.height)
                if (largest > maxDimension) {
                    val scale = maxDimension.toFloat() / largest.toFloat()
                    val targetWidth = (size.width * scale).roundToInt().coerceAtLeast(1)
                    val targetHeight = (size.height * scale).roundToInt().coerceAtLeast(1)
                    decoder.setTargetSize(targetWidth, targetHeight)
                }
                decoder.isMutableRequired = true
            }
        } else {
            @Suppress("DEPRECATION")
            val resolver = context.contentResolver
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, boundsOptions)
            }
            if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) return null
            val sampleSize = calculateInSampleSize(boundsOptions.outWidth, boundsOptions.outHeight, maxDimension)
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            resolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, decodeOptions)
            }
        } ?: return null
        val exifOrientation = readExifOrientation(context, uri)
        normalizeBitmapOrientation(decoded, exifOrientation)
    }.getOrNull()
}

internal data class ExifOrientationTransform(
    val rotationDegrees: Int = 0,
    val mirrorHorizontally: Boolean = false
)

internal fun exifOrientationTransform(orientation: Int): ExifOrientationTransform {
    return when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL ->
            ExifOrientationTransform(rotationDegrees = 0, mirrorHorizontally = true)
        ExifInterface.ORIENTATION_ROTATE_180 ->
            ExifOrientationTransform(rotationDegrees = 180, mirrorHorizontally = false)
        ExifInterface.ORIENTATION_FLIP_VERTICAL ->
            ExifOrientationTransform(rotationDegrees = 180, mirrorHorizontally = true)
        ExifInterface.ORIENTATION_TRANSPOSE ->
            ExifOrientationTransform(rotationDegrees = 90, mirrorHorizontally = true)
        ExifInterface.ORIENTATION_ROTATE_90 ->
            ExifOrientationTransform(rotationDegrees = 90, mirrorHorizontally = false)
        ExifInterface.ORIENTATION_TRANSVERSE ->
            ExifOrientationTransform(rotationDegrees = 270, mirrorHorizontally = true)
        ExifInterface.ORIENTATION_ROTATE_270 ->
            ExifOrientationTransform(rotationDegrees = 270, mirrorHorizontally = false)
        else ->
            ExifOrientationTransform(rotationDegrees = 0, mirrorHorizontally = false)
    }
}

private fun normalizeBitmapOrientation(bitmap: Bitmap, exifOrientation: Int): Bitmap {
    val transform = exifOrientationTransform(exifOrientation)
    if (transform.rotationDegrees == 0 && !transform.mirrorHorizontally) {
        return bitmap
    }
    if (bitmap.width <= 0 || bitmap.height <= 0) {
        return bitmap
    }

    val matrix = Matrix().apply {
        if (transform.rotationDegrees != 0) {
            postRotate(transform.rotationDegrees.toFloat())
        }
        if (transform.mirrorHorizontally) {
            postScale(-1f, 1f)
        }
    }
    return runCatching {
        Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true
        )
    }.getOrElse {
        bitmap
    }
}

private fun readExifOrientation(file: File): Int {
    return runCatching {
        ExifInterface(file.absolutePath)
            .getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
}

private fun readExifOrientation(context: Context, uri: Uri): Int {
    return runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            ExifInterface(input).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } ?: ExifInterface.ORIENTATION_NORMAL
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
}

private fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
    if (maxDimension <= 0) return 1
    val largest = max(width, height)
    var sampleSize = 1
    while (largest / sampleSize > maxDimension) {
        sampleSize *= 2
    }
    return sampleSize.coerceAtLeast(1)
}

private fun buildPreviewBitmapForOcr(
    imageProxy: ImageProxy,
    maxDimension: Int,
    centerRatio: Double
): Bitmap? {
    val plane = imageProxy.planes.firstOrNull() ?: return null
    val width = imageProxy.width.coerceAtLeast(1)
    val height = imageProxy.height.coerceAtLeast(1)
    if (width <= 0 || height <= 0) return null

    val rowStride = plane.rowStride
    val pixelStride = plane.pixelStride
    if (rowStride <= 0 || pixelStride <= 0) return null

    val clippedCenterRatio = centerRatio.coerceIn(0.35, 1.0)
    val cropWidth = (width * clippedCenterRatio).toInt().coerceAtLeast(1)
    val cropHeight = (height * clippedCenterRatio).toInt().coerceAtLeast(1)
    val cropStartX = ((width - cropWidth) / 2).coerceAtLeast(0)
    val cropStartY = ((height - cropHeight) / 2).coerceAtLeast(0)

    val maxSide = max(cropWidth, cropHeight)
    val step = max(1, maxSide / maxDimension.coerceAtLeast(1))
    val outWidth = max(1, cropWidth / step)
    val outHeight = max(1, cropHeight / step)
    val pixels = IntArray(outWidth * outHeight)
    val buffer = plane.buffer
    val limit = buffer.limit()

    var outY = 0
    while (outY < outHeight) {
        val srcY = min(height - 1, cropStartY + outY * step)
        var outX = 0
        while (outX < outWidth) {
            val srcX = min(width - 1, cropStartX + outX * step)
            val luma = getLuma(buffer, rowStride, pixelStride, srcX, srcY, limit) ?: return null
            pixels[outY * outWidth + outX] = android.graphics.Color.argb(255, luma, luma, luma)
            outX++
        }
        outY++
    }

    return Bitmap.createBitmap(pixels, outWidth, outHeight, Bitmap.Config.ARGB_8888)
}

private suspend fun evaluateFrameOcrReadiness(
    bitmap: Bitmap,
    rotationDegrees: Int,
    stageAEvaluation: StageAEvaluation,
    previousReadiness: OcrReadiness
): OcrReadiness {
    return try {
        val timeoutMs = adaptiveOcrTimeoutMs(previousReadiness)
        val result = withTimeoutOrNull(timeoutMs) {
            TextRecognitionManager.recognizeTextStructured(bitmap, rotationDegrees)
        } ?: return OcrReadiness(
            ready = false,
            score = 0.0,
            blocks = 0,
            reason = "ocr_timeout"
        )

        val readiness = BusinessCardReadinessEvaluator.evaluate(
            BusinessCardEvidenceInput(
                frameWidth = bitmap.width,
                frameHeight = bitmap.height,
                blockCount = result.blockCount,
                lineCount = result.lineCount,
                text = result.text,
                lines = result.lines.map { line ->
                    BusinessCardTextLine(
                        text = line.text,
                        left = line.left,
                        top = line.top,
                        right = line.right,
                        bottom = line.bottom
                    )
                },
                stageAEvaluation = stageAEvaluation
            )
        )

        val ready = result.blockCount >= AUTO_CAPTURE_MIN_OCR_BLOCKS &&
            readiness.ready &&
            readiness.score >= AUTO_CAPTURE_MIN_OCR_READY_SCORE

        OcrReadiness(
            ready = ready,
            score = readiness.score.coerceIn(0.0, 1.0),
            blocks = result.blockCount,
            reason = readiness.reason
        )
    } catch (t: Throwable) {
        OcrReadiness(
            ready = false,
            score = 0.0,
            blocks = 0,
            reason = "ocr_error_${t.javaClass.simpleName}"
        )
    }
}

private fun adaptiveOcrTimeoutMs(previousReadiness: OcrReadiness): Long {
    return when {
        previousReadiness.score >= AUTO_CAPTURE_MIN_OCR_READY_SCORE - 0.08 ->
            AUTO_CAPTURE_OCR_TIMEOUT_NEAR_READY_MS
        previousReadiness.score <= 0.30 ->
            AUTO_CAPTURE_OCR_TIMEOUT_FAST_FAIL_MS
        else -> AUTO_CAPTURE_OCR_TIMEOUT_MS
    }
}

private fun Double.format1(): String = String.format(java.util.Locale.US, "%.1f", this)

private fun Double.format2(): String = String.format(java.util.Locale.US, "%.2f", this)

private fun Double.format3(): String = String.format(java.util.Locale.US, "%.3f", this)

private fun getLuma(
    buffer: ByteBuffer,
    rowStride: Int,
    pixelStride: Int,
    x: Int,
    y: Int,
    bufferLimit: Int
): Int? {
    val index = y * rowStride + x * pixelStride
    if (index < 0 || index >= bufferLimit) return null
    return buffer.get(index).toInt() and 0xFF
}
