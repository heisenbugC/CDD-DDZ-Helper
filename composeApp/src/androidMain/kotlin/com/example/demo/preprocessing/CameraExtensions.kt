package com.example.demo.preprocessing

import android.graphics.Bitmap
import android.graphics.ImageFormat
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer

/**
 * Android-specific platform extensions for KotlinImagePreprocessor.
 */
fun KotlinImagePreprocessor.preprocessBitmap(bitmap: Bitmap): ByteBuffer {
    val width = bitmap.width
    val height = bitmap.height
    val srcPixels = IntArray(width * height)
    bitmap.getPixels(srcPixels, 0, width, 0, 0, width, height)
    return preprocessFromInts(srcPixels, width, height, 0)
}

fun KotlinImagePreprocessor.preprocessImageProxy(imageProxy: ImageProxy): ByteBuffer {
    val width = imageProxy.width
    val height = imageProxy.height
    val rotation = imageProxy.imageInfo.rotationDegrees

    if (imageProxy.format == ImageFormat.YUV_420_888) {
        val yPlane = imageProxy.planes[0]
        val uPlane = imageProxy.planes[1]
        val vPlane = imageProxy.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val yRowStride = yPlane.rowStride
        val uvRowStride = uPlane.rowStride
        val uvPixelStride = uPlane.pixelStride

        bilinearCenterCropFromYuvPlanes(
            yBuffer, uBuffer, vBuffer,
            width, height,
            yRowStride, uvRowStride, uvPixelStride,
            rotation, argbBuffer
        )
    } else {
        val plane = imageProxy.planes[0]
        val buffer = plane.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        bilinearCenterCrop(bytes, width, height, rotation, argbBuffer)
    }

    applyContrastStretch(argbBuffer, stretchedBuffer)
    applyUnsharpMask(stretchedBuffer, sharpenedBuffer)
    populateDirectBuffer(sharpenedBuffer, directFloatBuffer)

    return directFloatBuffer
}
