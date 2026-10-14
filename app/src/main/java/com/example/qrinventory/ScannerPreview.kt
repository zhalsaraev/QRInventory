package com.example.qrinventory

import android.view.ViewGroup
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

@androidx.annotation.OptIn(ExperimentalGetImage::class)
private fun scanBarcode(
    imageProxy: ImageProxy,
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    onScan: (String) -> Unit
) {
    val mediaImage = imageProxy.image ?: return
    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    scanner.process(image)
        .addOnSuccessListener { barcodes ->
            barcodes.firstOrNull()?.rawValue?.let { onScan(it) }
        }
        .addOnCompleteListener { imageProxy.close() }
}

/**
 * Верхняя половина: превью камеры (PreviewView) + кнопка подсветки.
 * Анализ QR-кодов — MLKit Barcode Scanning.
 */
@OptIn(ExperimentalGetImage::class)
@Composable
fun ScannerPreview(
    modifier: Modifier = Modifier,
    torchOn: Boolean,
    onTorchToggle: (Boolean) -> Unit,
    onScan: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = context as LifecycleOwner

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    // Инициализируем Executor и Scanner
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val barcodeScanner = remember {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE) // Ищем только QR-коды для производительности
            .build()
        BarcodeScanning.getClient(options)
    }

    // Правильно закрываем ресурсы при уходе с экрана, чтобы избежать утечек памяти
    DisposableEffect(Unit) {
        onDispose {
            barcodeScanner.close()
            analysisExecutor.shutdown()
        }
    }

    val preview = remember { Preview.Builder().build() }
    val imageAnalysis = remember {
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setBackgroundExecutor(analysisExecutor)
            .build()
    }

    imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
        if (imageProxy == null) return@setAnalyzer
        scanBarcode(imageProxy, barcodeScanner, onScan)
    }

    var camera by remember { mutableStateOf<Camera?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    LaunchedEffect(cameraProviderFuture) {
        val provider = try {
            cameraProviderFuture.get()
        } catch (e: Exception) {
            null
        } ?: return@LaunchedEffect
        val cam = try {
            provider.unbindAll() // На всякий случай отвязываем старые сессии камеры
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalysis
            )
        } catch (e: Exception) {
            null
        }
        camera = cam
        previewView?.let { preview.setSurfaceProvider(it.surfaceProvider) }
    }

    LaunchedEffect(torchOn, camera) {
        camera?.cameraControl?.enableTorch(torchOn)
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PreviewView(ctx).apply {
                    layoutParams =
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            },
            update = { view ->
                previewView = view
                preview.setSurfaceProvider(view.surfaceProvider)
            }
        )

        FloatingActionButton(
            onClick = { onTorchToggle(!torchOn) },
            containerColor = if (torchOn) Color(0xFF33BBEE) else Color(0x99000000),
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
        ) {
            Text(if (torchOn) "💡 Вкл" else "💡 Выкл")
        }
    }
}
