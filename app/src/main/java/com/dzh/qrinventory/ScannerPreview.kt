// #3.1
package com.dzh.qrinventory

import android.graphics.Bitmap
import android.graphics.Matrix
import android.view.ViewGroup
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp


@androidx.annotation.OptIn(ExperimentalGetImage::class)
private fun scanBarcode(
    imageProxy: ImageProxy,
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    onScan: (String) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        return
    }

    val rotationDegrees = imageProxy.imageInfo.rotationDegrees
    val bitmap = imageProxy.toBitmap()

    val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
    val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

    val size = (rotatedBitmap.width * 0.6f).toInt()
    val x = (rotatedBitmap.width - size) / 2
    val y = ((rotatedBitmap.height - size) / 2) + (rotatedBitmap.height * 0.05f).toInt()

    if (size > 0 && x >= 0 && y >= 0 && x + size <= rotatedBitmap.width && y + size <= rotatedBitmap.height) {
        val croppedBitmap = Bitmap.createBitmap(rotatedBitmap, x, y, size, size)
        val image = InputImage.fromBitmap(croppedBitmap, 0)

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                barcodes.firstOrNull()?.rawValue?.let { onScan(it) }
            }
            .addOnCompleteListener {
                croppedBitmap.recycle()
                rotatedBitmap.recycle()
                bitmap.recycle()
                imageProxy.close()
            }
    } else {
        rotatedBitmap.recycle()
        bitmap.recycle()
        imageProxy.close()
    }
}

// #3.2
@Composable
fun ScannerPreview(
    modifier: Modifier = Modifier,
    torchOn: Boolean,
    onTorchToggle: (Boolean) -> Unit,
    onCreateNewClick: () -> Unit,
    onOpenFileClick: () -> Unit,
    onShareClick: () -> Unit,
    onScan: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = context as LifecycleOwner

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    val barcodeScanner = remember {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_QR_CODE,
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_CODE_39
            )
            .build()
        BarcodeScanning.getClient(options)
    }

    // Отслеживаем сворачивание приложения для сброса кнопки фонарика
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            // Когда приложение уходит в фон (или закрывается камера), сбрасываем кнопку
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                onTorchToggle(false)
            }
        }
        lifecycle.addObserver(observer)

        onDispose {
            lifecycle.removeObserver(observer)
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

        // СИНХРОНИЗАЦИЯ: вызываем стандартный scanBarcode, где зона анализа находится строго по центру кадра,
        // так как рамка на экране теперь тоже центрирована идеально
        scanBarcode(imageProxy, barcodeScanner, onScan)
    }

    var camera by remember { mutableStateOf<Camera?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var currentZoom by remember { mutableStateOf(1f) }

    LaunchedEffect(camera) {
        camera?.cameraInfo?.zoomState?.observe(lifecycleOwner) { state ->
            if (state != null) {
                currentZoom = state.zoomRatio
            }
        }
    }

    LaunchedEffect(cameraProviderFuture) {
        val provider = try { cameraProviderFuture.get() } catch (e: Exception) { null } ?: return@LaunchedEffect
        val cam = try {
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis)
        } catch (e: Exception) { null }
        camera = cam
        previewView?.let { preview.setSurfaceProvider(it.surfaceProvider) }
    }

    LaunchedEffect(torchOn, camera) {
        camera?.cameraControl?.enableTorch(torchOn)
    }

    BoxWithConstraints(
        modifier = modifier
            .clipToBounds()

            .pointerInput(camera) {
                detectTransformGestures(panZoomLock = true) { _, _, zoom, _ ->
                    val cam = camera ?: return@detectTransformGestures
                    val zoomState = cam.cameraInfo.zoomState.value ?: return@detectTransformGestures

                    // Вычисляем новый зум на основе текущего масштаба камеры
                    val minZoom = zoomState.minZoomRatio
                    val maxZoom = zoomState.maxZoomRatio
                    val newZoomRatio = (zoomState.zoomRatio * zoom).coerceIn(minZoom, maxZoom)

                    cam.cameraControl.setZoomRatio(newZoomRatio)
                }
            }
            .pointerInput(camera, previewView) {
                detectTapGestures { tapOffset ->
                    val camControl = camera?.cameraControl ?: return@detectTapGestures
                    val pView = previewView ?: return@detectTapGestures
                    val factory = pView.meteringPointFactory
                    val point = factory.createPoint(tapOffset.x, tapOffset.y)
                    val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                        .setAutoCancelDuration(4, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                    camControl.startFocusAndMetering(action)
                }
            }
    ) {
        val boxWidth = maxWidth * 0.6f

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
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

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(boxWidth)
                .border(BorderStroke(2.dp, Color(0xFF33BBEE)), shape = RoundedCornerShape(12.dp))
                .background(Color(0x1A000000), shape = RoundedCornerShape(12.dp))
        )
        if (currentZoom > 1.01f) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    // Смещаем текст чуть ниже центральной рамки сканера, чтобы он не перекрывал прицел
                    .offset(y = (boxWidth / 2) + 24.dp)
                    .background(Color(0x99000000), shape = RoundedCornerShape(16.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    // Форматируем до одной десятой, например "2.4x"
                    text = String.format(java.util.Locale.US, "%.1fx", currentZoom),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = { onTorchToggle(!torchOn) },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color(0x66000000),
                    contentColor = if (torchOn) Color(0xFF33BBEE) else Color.White
                ),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(imageVector = Icons.Filled.FlashOn, contentDescription = "Фонарик", modifier = Modifier.size(24.dp))
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = onCreateNewClick,
                colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0x66000000), contentColor = Color.White),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(imageVector = Icons.Filled.NoteAdd, contentDescription = "Новый", modifier = Modifier.size(24.dp))
            }

            IconButton(
                onClick = onOpenFileClick,
                colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0x66000000), contentColor = Color.White),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(imageVector = Icons.Filled.FolderOpen, contentDescription = "Открыть", modifier = Modifier.size(24.dp))
            }

            IconButton(
                onClick = onShareClick,
                colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0x66000000), contentColor = Color.White),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(imageVector = Icons.Filled.Share, contentDescription = "Поделиться", modifier = Modifier.size(24.dp))
            }
        }
    }
}
