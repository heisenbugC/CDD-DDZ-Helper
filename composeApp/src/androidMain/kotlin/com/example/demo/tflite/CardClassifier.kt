package com.example.demo.tflite

import android.content.Context
import android.graphics.Bitmap

/**
 * Backward-compatible CardClassifier wrapper delegating to LiteRtCardClassifier.
 */
class CardClassifier(context: Context) {
    val liteRtClassifier = LiteRtCardClassifier(context)

    fun classify(bitmap: Bitmap): List<Detection> {
        val boxes = liteRtClassifier.classifyBitmap(bitmap)
        return boxes.map { b ->
            Detection(
                xmin = b.xMin,
                ymin = b.yMin,
                xmax = b.xMax,
                ymax = b.yMax,
                confidence = b.confidence,
                classId = b.classId
            )
        }
    }

    data class Detection(
        val xmin: Float,
        val ymin: Float,
        val xmax: Float,
        val ymax: Float,
        val confidence: Float,
        val classId: Int
    )
}
