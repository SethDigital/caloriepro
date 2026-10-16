package com.sethdigital.caloriepro.scan

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.sethdigital.caloriepro.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max

/**
 * Full-screen camera for photographing a nutrition label. Calls [onResult]
 * once a photo yields at least one nutrition value.
 */
@Composable
fun ScannerScreen(onResult: (ScanResult) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var hasPermission by remember { mutableStateOf(context.hasCameraPermission()) }
    var askedForPermission by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
        askedForPermission = true
    }
    LaunchedEffect(Unit) {
        if (!hasPermission && !askedForPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    // Pick up a permission granted from system settings when the user comes back.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) hasPermission = context.hasCameraPermission()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val scanner = remember { LabelScanner() }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose {
            scanner.close()
            cameraExecutor.shutdown()
        }
    }

    val cameraController = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
            // Plenty of pixels for small label print without slowing recognition down.
            setImageCaptureResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(
                        ResolutionStrategy(Size(2560, 1920), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)
                    )
                    .build()
            )
        }
    }
    DisposableEffect(lifecycleOwner, hasPermission) {
        if (hasPermission) cameraController.bindToLifecycle(lifecycleOwner)
        onDispose { cameraController.unbind() }
    }

    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var torchOn by remember { mutableStateOf(false) }

    fun runScan(load: suspend () -> InputImage) {
        busy = true
        message = null
        scope.launch {
            val result = runCatching { scanner.scan(load()) }
            busy = false
            result
                .onSuccess {
                    if (it.facts.nutrientCount == 0) {
                        message = "Couldn't find nutrition facts. Fill the frame with the label, " +
                            "keep it flat and well lit, then try again."
                    } else {
                        onResult(it)
                    }
                }
                .onFailure { message = "Couldn't read that image. Please try again." }
        }
    }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) runScan { withContext(Dispatchers.IO) { InputImage.fromFilePath(context, uri) } }
    }
    val choosePhoto = {
        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (hasPermission) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        controller = cameraController
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            ViewfinderFrame()
        } else {
            PermissionNeeded(
                askedBefore = askedForPermission,
                onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onOpenSettings = { context.openAppSettings() },
                onChoosePhoto = choosePhoto,
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                RoundButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close scanner")
                }
                if (hasPermission) {
                    RoundButton(onClick = {
                        torchOn = !torchOn
                        cameraController.enableTorch(torchOn)
                    }) {
                        Icon(
                            painterResource(if (torchOn) R.drawable.ic_flash_on else R.drawable.ic_flash_off),
                            contentDescription = if (torchOn) "Turn flashlight off" else "Turn flashlight on",
                        )
                    }
                }
            }
            if (hasPermission) {
                Text(
                    "Fit the Nutrition Facts panel inside the frame",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            message?.let {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                ) {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
            if (hasPermission) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    RoundButton(onClick = choosePhoto, enabled = !busy, modifier = Modifier.align(Alignment.CenterStart)) {
                        Icon(painterResource(R.drawable.ic_photo_library), contentDescription = "Choose a photo")
                    }
                    ShutterButton(
                        enabled = !busy,
                        onClick = {
                            runScan {
                                val (bitmap, rotation) = cameraController.capture(cameraExecutor)
                                InputImage.fromBitmap(bitmap, rotation)
                            }
                        },
                    )
                }
            }
        }

        if (busy) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(Modifier.height(12.dp))
                    Text("Reading label…", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun ViewfinderFrame() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .fillMaxWidth(0.82f)
                .aspectRatio(0.72f)
        ) {
            drawRoundRect(
                color = Color.White.copy(alpha = 0.9f),
                cornerRadius = CornerRadius(24.dp.toPx()),
                style = Stroke(width = 3.dp.toPx()),
            )
        }
    }
}

@Composable
private fun RoundButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(52.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = Color.Black.copy(alpha = 0.55f),
            contentColor = Color.White,
        ),
    ) { content() }
}

@Composable
private fun ShutterButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (enabled) 0.35f else 0.15f))
            .clickable(enabled = enabled, onClickLabel = "Take photo", role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Take photo" }
            .padding(8.dp)
            .background(if (enabled) Color.White else Color.Gray, CircleShape)
    )
}

@Composable
private fun PermissionNeeded(
    askedBefore: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    onChoosePhoto: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Camera access is needed to scan labels",
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Photos are only used to read the label on your phone. They are never saved or sent anywhere.",
            color = Color.White.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequest) { Text("Allow camera") }
        if (askedBefore) {
            TextButton(onClick = onOpenSettings) { Text("Open app settings", color = Color.White) }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onChoosePhoto) { Text("Choose a photo instead", color = Color.White) }
    }
}

private fun Context.hasCameraPermission() =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

/** Takes a photo and returns it with the rotation needed to show it upright. */
private suspend fun LifecycleCameraController.capture(executor: Executor): Pair<Bitmap, Int> =
    suspendCancellableCoroutine { cont ->
        takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                try {
                    val rotation = image.imageInfo.rotationDegrees
                    cont.resume(image.toBitmap().limitSize(MAX_EDGE_PX) to rotation)
                } catch (e: Exception) {
                    cont.resumeWithException(e)
                } finally {
                    image.close()
                }
            }

            override fun onError(exception: ImageCaptureException) {
                cont.resumeWithException(exception)
            }
        })
    }

private const val MAX_EDGE_PX = 3000

private fun Bitmap.limitSize(maxEdge: Int): Bitmap {
    val longest = max(width, height)
    if (longest <= maxEdge) return this
    val scale = maxEdge.toFloat() / longest
    return Bitmap.createScaledBitmap(this, (width * scale).toInt(), (height * scale).toInt(), true)
}
