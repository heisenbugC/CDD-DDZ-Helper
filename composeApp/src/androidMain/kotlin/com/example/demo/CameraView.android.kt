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
import com.example.demo.tflite.CardClassifier
import com.example.demo.services.NativeCardDetectionService
import java.nio.ByteBuffer
import java.util.concurrent.Executors

@Composable
actual fun CameraView(modifier: Modifier, onCardsDetected: (List<String>) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewView = remember { PreviewView(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val classifier = remember { CardClassifier(context) }
    val detectionService = remember { NativeCardDetectionService() }

    val suits = listOf("Diamond", "Club", "Heart", "Spade", "Joker")
    val ranks = listOf("3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A", "2", "Black Joker", "Red Joker")

    // Camera binding must happen after the view is attached
    DisposableEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalyzer.setAnalyzer(analysisExecutor) { imageProxy ->
                try {
                    val buffer: ByteBuffer = imageProxy.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    val width = imageProxy.width
                    val height = imageProxy.height

                    val boxes = detectionService.findCardBoundingBoxes(bytes, width, height)

                    val bitmap = imageProxy.toBitmap()
                    val detections = classifier.classify(bitmap)
                    
                    android.util.Log.d("CardClassifier", "Detected ${detections.size} objects")

                    if (detections.isNotEmpty()) {
                        val classIdToName = arrayOf(
                            "10_of_clubs", "10_of_diamonds", "3_of_hearts", "3_of_spades", "4_of_clubs", 
                            "4_of_diamonds", "4_of_hearts", "4_of_spades", "5_of_clubs", "5_of_diamonds", 
                            "5_of_hearts", "5_of_spades", "10_of_hearts", "6_of_clubs", "6_of_diamonds", 
                            "6_of_hearts", "6_of_spades", "7_of_clubs", "7_of_diamonds", "7_of_hearts", 
                            "7_of_spades", "8_of_clubs", "8_of_diamonds", "10_of_spades", "8_of_hearts", 
                            "8_of_spades", "9_of_clubs", "9_of_diamonds", "9_of_hearts", "9_of_spades", 
                            "ace_of_clubs", "ace_of_diamonds", "ace_of_hearts", "ace_of_spades", "2_of_clubs", 
                            "jack_of_clubs", "jack_of_diamonds", "jack_of_hearts", "jack_of_spades", "king_of_clubs", 
                            "king_of_diamonds", "king_of_hearts", "king_of_spades", "queen_of_clubs", "queen_of_diamonds", 
                            "2_of_diamonds", "queen_of_hearts", "queen_of_spades", "2_of_hearts", "2_of_spades", 
                            "3_of_clubs", "3_of_diamonds", "black_joker", "red_joker"
                        )
                        
                        val stringResults = detections.map { det ->
                            if (det.classId in classIdToName.indices) {
                                classIdToName[det.classId]
                            } else {
                                "unknown_${det.classId}"
                            }
                        }
                        
                        android.util.Log.d("CardClassifier", "Cards: $stringResults")
                        onCardsDetected(stringResults)
                    }
                } finally {
                    imageProxy.close()
                }
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
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
            try {
                val cp = ProcessCameraProvider.getInstance(context).get()
                cp.unbindAll()
            } catch (_: Exception) {}
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}
