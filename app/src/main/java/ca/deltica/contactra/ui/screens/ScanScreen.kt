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
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
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
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max
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
    var captureInFlight by remember { mutableStateOf(false) }
    var cameraBindAttempt by remember { mutableStateOf(0) }

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
    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }

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

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) {
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
    }

    LaunchedEffect(scanState.lastCapturedImage, scanState.isProcessing, scanState.errorMessage) {
        val isFreshScanSession = !scanState.isProcessing &&
            scanState.lastCapturedImage == null &&
            scanState.errorMessage == null
        if (isFreshScanSession) {
            captureInFlight = false
            captureError = null
        }
    }

    val captureNowState = rememberUpdatedState<() -> Unit> {
        if (!hasCameraPermission || !isCameraReady) {
            cameraError = "Camera is not ready yet."
            cameraUnavailableType = CameraUnavailableType.TEMPORARY_ERROR
            return@rememberUpdatedState
        }
        if (captureInFlight) return@rememberUpdatedState

        captureInFlight = true
        val resolvedRotation = resolveDisplayRotation(context, previewView)
        currentDisplayRotation = resolvedRotation
        boundPreview?.targetRotation = resolvedRotation
        imageCapture.targetRotation = resolvedRotation

        val outputFile = File(context.cacheDir, "captured_${System.currentTimeMillis()}.jpg")
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
                            captureInFlight = false
                            if (bitmap == null) {
                                captureError = "Captured image could not be decoded."
                                return@execute
                            }
                            captureError = null
                            viewModel.onImageCaptured(bitmap)
                            navController.navigate(Screen.Review.route)
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        mainExecutor.execute {
                            captureInFlight = false
                            captureError = exception.message ?: "Image capture failed."
                        }
                        Log.e("ScanScreen", "Image capture failed: ${exception.message}", exception)
                    }
                }
            )
        } catch (t: Throwable) {
            captureInFlight = false
            captureError = t.message ?: "Camera capture unavailable."
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
            galleryLauncher.launch("image/*")
        } else if (!launchGallery && hasLaunchedGallery) {
            hasLaunchedGallery = false
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
                    imageCapture
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
        captureInFlight -> "Capturing..."
        isCameraReady -> "Tap preview or Capture."
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

                if (captureError != null && unavailableType == null) {
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(AppDimens.sm)) {
                            Text(
                                text = captureError.orEmpty(),
                                color = MaterialTheme.colorScheme.error
                            )
                            SecondaryButton(
                                text = "Dismiss",
                                onClick = { captureError = null },
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
                                    captureInFlight -> StatusPillTone.Brand
                                    isCameraReady -> StatusPillTone.Success
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
                                        enabled = hasCameraPermission && isCameraReady && !captureInFlight,
                                        onClick = { captureNowState.value() }
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
                        onClick = { captureNowState.value() },
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Filled.CameraAlt,
                        enabled = hasCameraPermission && isCameraReady && !captureInFlight
                    )
                }
                Spacer(modifier = Modifier.height(AppDimens.sm))
            }
        }
    }
}

private const val MAX_IMAGE_DIMENSION = 2000

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
