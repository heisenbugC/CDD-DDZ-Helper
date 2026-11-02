package com.example.demo.preprocessing

import com.example.demo.services.ImagePreprocessor
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Pure Kotlin high-performance image preprocessor for Android.
 * Converts CameraX pixel buffers (YUV_420_888, NV21, or RGBA) or pixel arrays
 * into normalized 640x640 RGB float32 tensors for LiteRT model inference.
 *
 * Implements:
 * 1. Bilinear downsampling and center-crop to 640x640.
 * 2. 1000-point histogram sampling & black-level contrast stretch with adaptive gamma (gamma = 0.72 for mean < 90).
 * 3. 3x3 Unsharp Mask sharpening using integer fixed-point arithmetic (x256).
 * 4. Zero-allocation frame streaming via preallocated direct ByteBuffers.
 */
class KotlinImagePreprocessor(
    val targetSize: Int = 640
) : ImagePreprocessor {

    private val numPixels = targetSize * targetSize
    private val byteBufferSize = 1 * targetSize * targetSize * 3 * 4 // [1, 640, 640, 3] Float32

    // Reusable preallocated intermediate buffers to eliminate GC allocations
    val argbBuffer = IntArray(numPixels)
    val stretchedBuffer = IntArray(numPixels)
    val sharpenedBuffer = IntArray(numPixels)
    val directFloatBuffer: ByteBuffer = ByteBuffer.allocateDirect(byteBufferSize)
        .order(ByteOrder.nativeOrder())

    // Fast float normalization lookup table (0..255 -> 0.0f..1.0f)
    private val floatLut = FloatArray(256) { it / 255.0f }

    // Reusable lookup table for contrast stretching
    private val stretchLut = IntArray(256)

    // Histogram buffer (256 buckets)
    private val hist = IntArray(256)

    /**
     * Primary contract method: preprocesses input raw byte array into normalized 640x640 RGB float buffer.
     * Supports RGBA (width*height*4), RGB (width*height*3), or NV21/YUV420sp (width*height*3/2).
     */
    override fun preprocessToModelInput(
        pixels: ByteArray,
        width: Int,
        height: Int,
        rotationDegrees: Int
    ): ByteBuffer {
        // Step 1: Bilinear downsample and center-crop into argbBuffer
        bilinearCenterCrop(pixels, width, height, rotationDegrees, argbBuffer)

        // Step 2: Black-level contrast stretch with adaptive gamma
        applyContrastStretch(argbBuffer, stretchedBuffer)

        // Step 3: 3x3 Unsharp mask fixed-point convolution
        applyUnsharpMask(stretchedBuffer, sharpenedBuffer)

        // Step 4: Populate direct float ByteBuffer
        populateDirectBuffer(sharpenedBuffer, directFloatBuffer)

        return directFloatBuffer
    }

    /**
     * Preprocesses ARGB IntArray pixels directly (e.g. from Bitmap).
     */
    fun preprocessFromInts(
        srcPixels: IntArray,
        width: Int,
        height: Int,
        rotationDegrees: Int = 0
    ): ByteBuffer {
        bilinearCenterCropFromInts(srcPixels, width, height, rotationDegrees, argbBuffer)
        applyContrastStretch(argbBuffer, stretchedBuffer)
        applyUnsharpMask(stretchedBuffer, sharpenedBuffer)
        populateDirectBuffer(sharpenedBuffer, directFloatBuffer)
        return directFloatBuffer
    }

    /**
     * Step 1: Bilinear downsample and center-crop from raw ByteArray (RGBA, RGB, or NV21).
     */
    fun bilinearCenterCrop(
        pixels: ByteArray,
        srcWidth: Int,
        srcHeight: Int,
        rotationDegrees: Int,
        outArgb: IntArray
    ) {
        val isRgba = pixels.size >= srcWidth * srcHeight * 4
        val isRgb = !isRgba && (pixels.size >= srcWidth * srcHeight * 3)
        val isNv21 = !isRgba && !isRgb

        val cropSize = minOf(srcWidth, srcHeight)
        val cropX0 = (srcWidth - cropSize) / 2
        val cropY0 = (srcHeight - cropSize) / 2

        for (dstY in 0 until targetSize) {
            val normY = (dstY + 0.5f) / targetSize.toFloat()
            for (dstX in 0 until targetSize) {
                val normX = (dstX + 0.5f) / targetSize.toFloat()

                // Handle rotation mapping
                val (rx, ry) = when ((rotationDegrees % 360 + 360) % 360) {
                    90 -> Pair(normY, 1.0f - normX)
                    180 -> Pair(1.0f - normX, 1.0f - normY)
                    270 -> Pair(1.0f - normY, normX)
                    else -> Pair(normX, normY)
                }

                val srcX = cropX0 + rx * cropSize - 0.5f
                val srcY = cropY0 + ry * cropSize - 0.5f

                val x0 = srcX.toInt().coerceIn(0, srcWidth - 1)
                val x1 = (x0 + 1).coerceIn(0, srcWidth - 1)
                val y0 = srcY.toInt().coerceIn(0, srcHeight - 1)
                val y1 = (y0 + 1).coerceIn(0, srcHeight - 1)

                val wx = (srcX - x0).coerceIn(0.0f, 1.0f)
                val wy = (srcY - y0).coerceIn(0.0f, 1.0f)

                val p00 = getPixelRgb(pixels, x0, y0, srcWidth, srcHeight, isRgba, isRgb, isNv21)
                val p10 = getPixelRgb(pixels, x1, y0, srcWidth, srcHeight, isRgba, isRgb, isNv21)
                val p01 = getPixelRgb(pixels, x0, y1, srcWidth, srcHeight, isRgba, isRgb, isNv21)
                val p11 = getPixelRgb(pixels, x1, y1, srcWidth, srcHeight, isRgba, isRgb, isNv21)

                val r = bilinearInterp(p00.shr(16) and 0xFF, p10.shr(16) and 0xFF, p01.shr(16) and 0xFF, p11.shr(16) and 0xFF, wx, wy)
                val g = bilinearInterp(p00.shr(8) and 0xFF, p10.shr(8) and 0xFF, p01.shr(8) and 0xFF, p11.shr(8) and 0xFF, wx, wy)
                val b = bilinearInterp(p00 and 0xFF, p10 and 0xFF, p01 and 0xFF, p11 and 0xFF, wx, wy)

                outArgb[dstY * targetSize + dstX] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
    }

    /**
     * Bilinear center-crop from ARGB IntArray (e.g. from Bitmap).
     */
    fun bilinearCenterCropFromInts(
        srcPixels: IntArray,
        srcWidth: Int,
        srcHeight: Int,
        rotationDegrees: Int,
        outArgb: IntArray
    ) {
        val cropSize = minOf(srcWidth, srcHeight)
        val cropX0 = (srcWidth - cropSize) / 2
        val cropY0 = (srcHeight - cropSize) / 2

        for (dstY in 0 until targetSize) {
            val normY = (dstY + 0.5f) / targetSize.toFloat()
            for (dstX in 0 until targetSize) {
                val normX = (dstX + 0.5f) / targetSize.toFloat()

                val (rx, ry) = when ((rotationDegrees % 360 + 360) % 360) {
                    90 -> Pair(normY, 1.0f - normX)
                    180 -> Pair(1.0f - normX, 1.0f - normY)
                    270 -> Pair(1.0f - normY, normX)
                    else -> Pair(normX, normY)
                }

                val srcX = cropX0 + rx * cropSize - 0.5f
                val srcY = cropY0 + ry * cropSize - 0.5f

                val x0 = srcX.toInt().coerceIn(0, srcWidth - 1)
                val x1 = (x0 + 1).coerceIn(0, srcWidth - 1)
                val y0 = srcY.toInt().coerceIn(0, srcHeight - 1)
                val y1 = (y0 + 1).coerceIn(0, srcHeight - 1)

                val wx = (srcX - x0).coerceIn(0.0f, 1.0f)
                val wy = (srcY - y0).coerceIn(0.0f, 1.0f)

                val p00 = srcPixels[y0 * srcWidth + x0]
                val p10 = srcPixels[y0 * srcWidth + x1]
                val p01 = srcPixels[y1 * srcWidth + x0]
                val p11 = srcPixels[y1 * srcWidth + x1]

                val r = bilinearInterp(p00.shr(16) and 0xFF, p10.shr(16) and 0xFF, p01.shr(16) and 0xFF, p11.shr(16) and 0xFF, wx, wy)
                val g = bilinearInterp(p00.shr(8) and 0xFF, p10.shr(8) and 0xFF, p01.shr(8) and 0xFF, p11.shr(8) and 0xFF, wx, wy)
                val b = bilinearInterp(p00 and 0xFF, p10 and 0xFF, p01 and 0xFF, p11 and 0xFF, wx, wy)

                outArgb[dstY * targetSize + dstX] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
    }

    /**
     * Bilinear center-crop directly from CameraX YUV_420_888 ByteBuffers.
     */
    fun bilinearCenterCropFromYuvPlanes(
        yBuffer: ByteBuffer,
        uBuffer: ByteBuffer,
        vBuffer: ByteBuffer,
        srcWidth: Int,
        srcHeight: Int,
        yRowStride: Int,
        uvRowStride: Int,
        uvPixelStride: Int,
        rotationDegrees: Int,
        outArgb: IntArray
    ) {
        val cropSize = minOf(srcWidth, srcHeight)
        val cropX0 = (srcWidth - cropSize) / 2
        val cropY0 = (srcHeight - cropSize) / 2

        for (dstY in 0 until targetSize) {
            val normY = (dstY + 0.5f) / targetSize.toFloat()
            for (dstX in 0 until targetSize) {
                val normX = (dstX + 0.5f) / targetSize.toFloat()

                val (rx, ry) = when ((rotationDegrees % 360 + 360) % 360) {
                    90 -> Pair(normY, 1.0f - normX)
                    180 -> Pair(1.0f - normX, 1.0f - normY)
                    270 -> Pair(1.0f - normY, normX)
                    else -> Pair(normX, normY)
                }

                val srcX = cropX0 + rx * cropSize - 0.5f
                val srcY = cropY0 + ry * cropSize - 0.5f

                val x = srcX.toInt().coerceIn(0, srcWidth - 1)
                val y = srcY.toInt().coerceIn(0, srcHeight - 1)

                val yIdx = y * yRowStride + x
                val yVal = yBuffer.get(yIdx).toInt() and 0xFF

                val uvIdx = (y / 2) * uvRowStride + (x / 2) * uvPixelStride
                val uVal = (uBuffer.get(uvIdx).toInt() and 0xFF) - 128
                val vVal = (vBuffer.get(uvIdx).toInt() and 0xFF) - 128

                val r = (yVal + 1.370705f * vVal).toInt().coerceIn(0, 255)
                val g = (yVal - 0.337633f * uVal - 0.698001f * vVal).toInt().coerceIn(0, 255)
                val b = (yVal + 1.732446f * uVal).toInt().coerceIn(0, 255)

                outArgb[dstY * targetSize + dstX] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun bilinearInterp(c00: Int, c10: Int, c01: Int, c11: Int, wx: Float, wy: Float): Int {
        val top = c00 * (1.0f - wx) + c10 * wx
        val bot = c01 * (1.0f - wx) + c11 * wx
        return (top * (1.0f - wy) + bot * wy + 0.5f).toInt().coerceIn(0, 255)
    }

    private fun getPixelRgb(
        pixels: ByteArray,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        isRgba: Boolean,
        isRgb: Boolean,
        isNv21: Boolean
    ): Int {
        return when {
            isRgba -> {
                val idx = (y * width + x) * 4
                val r = pixels[idx].toInt() and 0xFF
                val g = pixels[idx + 1].toInt() and 0xFF
                val b = pixels[idx + 2].toInt() and 0xFF
                (r shl 16) or (g shl 8) or b
            }
            isRgb -> {
                val idx = (y * width + x) * 3
                val r = pixels[idx].toInt() and 0xFF
                val g = pixels[idx + 1].toInt() and 0xFF
                val b = pixels[idx + 2].toInt() and 0xFF
                (r shl 16) or (g shl 8) or b
            }
            isNv21 -> {
                val yVal = pixels[y * width + x].toInt() and 0xFF
                val uvStart = width * height
                val uvIdx = uvStart + (y / 2) * width + (x and 1.inv())
                val vVal = (pixels[uvIdx].toInt() and 0xFF) - 128
                val uVal = (pixels[uvIdx + 1].toInt() and 0xFF) - 128

                val r = (yVal + 1.370705f * vVal).toInt().coerceIn(0, 255)
                val g = (yVal - 0.337633f * uVal - 0.698001f * vVal).toInt().coerceIn(0, 255)
                val b = (yVal + 1.732446f * uVal).toInt().coerceIn(0, 255)
                (r shl 16) or (g shl 8) or b
            }
            else -> 0
        }
    }

    /**
     * Step 2: Histogram sampling (1000 points) & Black-level contrast stretch with adaptive gamma.
     * Algorithm from ARCHITECTURE.md 5.1:
     * 1. Sample 1000 pixel luminances Li = (299*R + 587*G + 114*B) / 1000.
     * 2. Compute 2nd percentile (Lmin) and 98th percentile (Lmax).
     * 3. If mean luminance mu < 90, apply gamma curve with gamma = 0.72.
     * 4. Scale each color channel: C_stretched = clamp((C - Lmin) / (Lmax - Lmin) * 255, 0, 255).
     */
    fun applyContrastStretch(src: IntArray, dst: IntArray) {
        hist.fill(0)
        var sumL = 0L
        val sampleCount = 1000
        val step = numPixels / sampleCount

        var idx = 0
        for (s in 0 until sampleCount) {
            val pixel = src[idx]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val lum = (299 * r + 587 * g + 114 * b) / 1000
            hist[lum]++
            sumL += lum
            idx += step
        }

        val meanL = sumL.toFloat() / sampleCount.toFloat()
        val applyGamma = meanL < 90.0f
        val gamma = 0.72

        // Find 2nd and 98th percentiles
        val count2Pct = (sampleCount * 0.02f).toInt()
        val count98Pct = (sampleCount * 0.98f).toInt()

        var cum = 0
        var lMin = 0
        var lMax = 255
        var minFound = false

        for (i in 0..255) {
            cum += hist[i]
            if (!minFound && cum >= count2Pct) {
                lMin = i
                minFound = true
            }
            if (cum >= count98Pct) {
                lMax = i
                break
            }
        }

        if (lMax <= lMin) {
            lMax = (lMin + 1).coerceAtMost(255)
        }

        val lMinGamma = if (applyGamma) (Math.pow(lMin / 255.0, gamma) * 255.0).toFloat() else lMin.toFloat()
        val lMaxGamma = if (applyGamma) (Math.pow(lMax / 255.0, gamma) * 255.0).toFloat() else lMax.toFloat()
        val range = (lMaxGamma - lMinGamma).coerceAtLeast(1.0f)

        // Build 256-entry lookup table
        for (v in 0..255) {
            val vGamma = if (applyGamma) {
                (Math.pow(v / 255.0, gamma) * 255.0).toFloat()
            } else {
                v.toFloat()
            }
            val stretched = ((vGamma - lMinGamma) / range) * 255.0f
            stretchLut[v] = stretched.toInt().coerceIn(0, 255)
        }

        // Apply lookup table across all pixels
        for (i in 0 until numPixels) {
            val pixel = src[i]
            val r = stretchLut[(pixel shr 16) and 0xFF]
            val g = stretchLut[(pixel shr 8) and 0xFF]
            val b = stretchLut[pixel and 0xFF]
            dst[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }

    /**
     * Step 3: 3x3 Unsharp Mask Sharpening Kernel
     * Kernel: [[0, -0.5, 0], [-0.5, 3.0, -0.5], [0, -0.5, 0]]
     * Fixed-point integer arithmetic (scaled by 256):
     * Center weight = 3.0 * 256 = 768.
     * Neighbor weight = -0.5 * 256 = -128.
     */
    fun applyUnsharpMask(src: IntArray, dst: IntArray) {
        val width = targetSize
        val height = targetSize

        // Copy top and bottom row directly
        System.arraycopy(src, 0, dst, 0, width)
        System.arraycopy(src, (height - 1) * width, dst, (height - 1) * width, width)

        for (y in 1 until height - 1) {
            val rowOffset = y * width
            val upOffset = (y - 1) * width
            val downOffset = (y + 1) * width

            // Pass-through left border pixel
            dst[rowOffset] = src[rowOffset]

            for (x in 1 until width - 1) {
                val curr = src[rowOffset + x]
                val up = src[upOffset + x]
                val down = src[downOffset + x]
                val left = src[rowOffset + x - 1]
                val right = src[rowOffset + x + 1]

                // Red
                val rCurr = (curr shr 16) and 0xFF
                val rNeighbors = ((up shr 16) and 0xFF) + ((down shr 16) and 0xFF) +
                                 ((left shr 16) and 0xFF) + ((right shr 16) and 0xFF)
                val rSharpened = ((rCurr * 768 - rNeighbors * 128 + 128) shr 8).coerceIn(0, 255)

                // Green
                val gCurr = (curr shr 8) and 0xFF
                val gNeighbors = ((up shr 8) and 0xFF) + ((down shr 8) and 0xFF) +
                                 ((left shr 8) and 0xFF) + ((right shr 8) and 0xFF)
                val gSharpened = ((gCurr * 768 - gNeighbors * 128 + 128) shr 8).coerceIn(0, 255)

                // Blue
                val bCurr = curr and 0xFF
                val bNeighbors = (up and 0xFF) + (down and 0xFF) +
                                 (left and 0xFF) + (right and 0xFF)
                val bSharpened = ((bCurr * 768 - bNeighbors * 128 + 128) shr 8).coerceIn(0, 255)

                dst[rowOffset + x] = (0xFF shl 24) or (rSharpened shl 16) or (gSharpened shl 8) or bSharpened
            }

            // Pass-through right border pixel
            dst[rowOffset + width - 1] = src[rowOffset + width - 1]
        }
    }

    /**
     * Step 4: Populate direct float ByteBuffer [1, 640, 640, 3] normalized to [0.0f, 1.0f].
     */
    fun populateDirectBuffer(src: IntArray, buffer: ByteBuffer) {
        buffer.clear()
        for (i in 0 until numPixels) {
            val pixel = src[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF

            buffer.putFloat(floatLut[r])
            buffer.putFloat(floatLut[g])
            buffer.putFloat(floatLut[b])
        }
        buffer.rewind()
    }
}
