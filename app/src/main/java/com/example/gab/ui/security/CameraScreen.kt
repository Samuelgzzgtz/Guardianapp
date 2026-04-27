package com.example.gab.ui.security

import android.content.Context
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MlKitCameraScreen(
    hint: String = "Apunta la cámara al texto",
    onTextRecognized: (String) -> Unit,
    onCancel: () -> Unit
) {
    val cameraPermission = rememberPermissionState(android.Manifest.permission.CAMERA)

    when {
        cameraPermission.status.isGranted -> {
            CameraPreviewWithOcr(hint = hint, onTextRecognized = onTextRecognized, onCancel = onCancel)
        }
        cameraPermission.status.shouldShowRationale -> {
            PermissionRationale(
                message = "Se necesita acceso a la cámara para escanear documentos.",
                onRequest = { cameraPermission.launchPermissionRequest() },
                onCancel = onCancel
            )
        }
        else -> {
            LaunchedEffect(Unit) { cameraPermission.launchPermissionRequest() }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun CameraPreviewWithOcr(
    hint: String,
    onTextRecognized: (String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    var scanning by remember { mutableStateOf(false) }
    var lastCapture by remember { mutableStateOf(0L) }

    val imageCapture = remember { ImageCapture.Builder().build() }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also { previewView ->
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageCapture
                            )
                        } catch (e: Exception) {
                            Log.e("CameraScreen", "Camera bind failed", e)
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                hint,
                color = androidx.compose.ui.graphics.Color.White,
                style = MaterialTheme.typography.bodyMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = {
                        val now = System.currentTimeMillis()
                        if (scanning || now - lastCapture < 2000) return@Button
                        scanning = true
                        lastCapture = now
                        captureAndRecognize(context, imageCapture, executor) { text ->
                            scanning = false
                            if (text.isNotBlank()) onTextRecognized(text)
                        }
                    },
                    enabled = !scanning,
                    colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color.White)
                ) {
                    if (scanning) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Capturar", color = androidx.compose.ui.graphics.Color.Black)
                    }
                }
                OutlinedButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = androidx.compose.ui.graphics.Color.White)
                ) {
                    Text("Cancelar")
                }
            }
        }
    }
}

private fun captureAndRecognize(
    context: Context,
    imageCapture: ImageCapture,
    executor: java.util.concurrent.Executor,
    onResult: (String) -> Unit
) {
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    imageCapture.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
        @androidx.camera.core.ExperimentalGetImage
        override fun onCaptureSuccess(proxy: ImageProxy) {
            val mediaImage = proxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)
                recognizer.process(image)
                    .addOnSuccessListener { result -> onResult(result.text) }
                    .addOnFailureListener { onResult("") }
                    .addOnCompleteListener { proxy.close() }
            } else {
                proxy.close()
                onResult("")
            }
        }
        override fun onError(exception: ImageCaptureException) {
            Log.e("CameraScreen", "Capture error", exception)
            onResult("")
        }
    })
}

@Composable
private fun PermissionRationale(message: String, onRequest: () -> Unit, onCancel: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(message, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onRequest) { Text("Conceder permiso") }
            TextButton(onClick = onCancel) { Text("Cancelar") }
        }
    }
}
