package com.example.demo

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.demo.domain.CardMapping
import com.example.demo.guidance.NormalizedBox
import com.example.demo.preprocessing.preprocessImageProxy
import com.example.demo.tflite.LiteRtCardClassifier
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

@Composable
actual fun CameraView(
    modifier: Modifier,
    onCardsDetected: (List<String>) -> Unit,
    onBoxesDetected: ((List<NormalizedBox>) -> Unit)?
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Dynamically update callback references on every recomposition without rebuilding CameraX
    val currentOnCardsDetected by rememberUpdatedState(onCardsDetected)
    val currentOnBoxesDetected by rememberUpdatedState(onBoxesDetected)

    val previewView = remember { PreviewView(context) }
    val analysisExecutor = remember {
        ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS,
            LinkedBlockingQueue(1),
            ThreadPoolExecutor.DiscardPolicy()
        )
    }
    val classifier = remember { LiteRtCardClassifier(context) }

    // Camera binding must happen after the view is attached
    DisposableEffect(lifecycleOwner) {
        var activeCameraProvider: ProcessCameraProvider? = null
        var activeImageAnalyzer: ImageAnalysis? = null

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                activeCameraProvider = cameraProvider

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setTargetResolution(android.util.Size(1280, 720))
                    .build()
                activeImageAnalyzer = imageAnalyzer

                imageAnalyzer.setAnalyzer(analysisExecutor) { imageProxy ->
                    try {
                        // Step 1 & 2: Preprocess CameraX image via pure Kotlin pipeline
                        val preprocessor = classifier.preprocessor
                        val inputBuffer = preprocessor.preprocessImageProxy(imageProxy)

                        // Step 3: Run LiteRT card classification with QNN/GPU acceleration
                        val detections = classifier.detect(inputBuffer, preprocessor.sharpenedBuffer)

                        // Step 4 & 5: Safely dispatch bounding boxes and card labels to Main UI thread
                        val stringResults = if (detections.isNotEmpty()) {
                            detections.mapNotNull { det ->
                                CardMapping.getLabel(det.classId)
                            }
                        } else {
                            emptyList()
                        }

                        ContextCompat.getMainExecutor(context).execute {
                            currentOnBoxesDetected?.invoke(detections)
                            currentOnCardsDetected(stringResults)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("CameraView", "Inference error: ${e.message}", e)
                    } finally {
                        imageProxy.close()
                    }
                }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalyzer
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            activeImageAnalyzer?.clearAnalyzer()
            try {
                activeCameraProvider?.unbindAll()
            } catch (_: Exception) {}
            analysisExecutor.shutdown()
            classifier.close()
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}
