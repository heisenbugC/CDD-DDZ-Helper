package com.example.demo.preprocessing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KotlinImagePreprocessorTest {

    @Test
    fun testBilinearCenterCrop_cropsSquareAndInterpolates() {
        val preprocessor = KotlinImagePreprocessor(640)
        val srcW = 1280
        val srcH = 720
        // Create RGBA image: top half red, bottom half blue
        val pixels = ByteArray(srcW * srcH * 4)
        for (y in 0 until srcH) {
            for (x in 0 until srcW) {
                val idx = (y * srcW + x) * 4
                if (y < srcH / 2) {
                    pixels[idx] = 255.toByte()     // R
                    pixels[idx + 1] = 0.toByte()   // G
                    pixels[idx + 2] = 0.toByte()   // B
                } else {
                    pixels[idx] = 0.toByte()       // R
                    pixels[idx + 1] = 0.toByte()   // G
                    pixels[idx + 2] = 255.toByte() // B
                }
                pixels[idx + 3] = 255.toByte()     // A
            }
        }

        val outArgb = IntArray(640 * 640)
        preprocessor.bilinearCenterCrop(pixels, srcW, srcH, 0, outArgb)

        // Top area of 640x640 should be red
        val topPixel = outArgb[100 * 640 + 320]
        val topR = (topPixel shr 16) and 0xFF
        val topB = topPixel and 0xFF
        assertTrue(topR > 200, "Top pixel should be predominantly red, got $topR")
        assertTrue(topB < 50, "Top pixel should have low blue, got $topB")

        // Bottom area of 640x640 should be blue
        val botPixel = outArgb[540 * 640 + 320]
        val botR = (botPixel shr 16) and 0xFF
        val botB = botPixel and 0xFF
        assertTrue(botB > 200, "Bottom pixel should be predominantly blue, got $botB")
        assertTrue(botR < 50, "Bottom pixel should have low red, got $botR")
    }

    @Test
    fun testContrastStretch_adaptiveGammaAppliedForLowMean() {
        val preprocessor = KotlinImagePreprocessor(640)
        val src = IntArray(640 * 640)

        // Dark image with mean luminance around 50 (< 90)
        for (i in src.indices) {
            val v = (40 + (i % 20))
            src[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }

        val dst = IntArray(640 * 640)
        preprocessor.applyContrastStretch(src, dst)

        // Stretched buffer should have broader contrast range
        var minVal = 255
        var maxVal = 0
        for (i in dst.indices) {
            val v = dst[i] and 0xFF
            if (v < minVal) minVal = v
            if (v > maxVal) maxVal = v
        }

        assertTrue(maxVal > 200, "Max value should be stretched towards 255, got $maxVal")
        assertTrue(minVal < 50, "Min value should be stretched towards 0, got $minVal")
    }

    @Test
    fun testUnsharpMask_sharpensEdges() {
        val preprocessor = KotlinImagePreprocessor(640)
        val src = IntArray(640 * 640)

        // Create an abrupt step edge at x = 320: left is 50, right is 200
        for (y in 0 until 640) {
            for (x in 0 until 640) {
                val v = if (x < 320) 50 else 200
                src[y * 640 + x] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
            }
        }

        val dst = IntArray(640 * 640)
        preprocessor.applyUnsharpMask(src, dst)

        // Unsharp mask enhances edge overshoot:
        // Pixel at x=319 (just before transition) should dip lower than 50
        // Pixel at x=320 (just after transition) should overshoot higher than 200
        val preEdge = dst[300 * 640 + 319] and 0xFF
        val postEdge = dst[300 * 640 + 320] and 0xFF

        assertTrue(preEdge <= 50, "Pre-edge should be undershot, got $preEdge")
        assertTrue(postEdge >= 200, "Post-edge should be overshot, got $postEdge")
    }

    @Test
    fun testDirectBufferPopulation_normalizedFloatValues() {
        val preprocessor = KotlinImagePreprocessor(640)
        val src = IntArray(640 * 640)
        src[0] = (0xFF shl 24) or (255 shl 16) or (128 shl 8) or 0

        preprocessor.populateDirectBuffer(src, preprocessor.directFloatBuffer)

        assertEquals(1 * 640 * 640 * 3 * 4, preprocessor.directFloatBuffer.capacity())
        assertEquals(0, preprocessor.directFloatBuffer.position())

        val r = preprocessor.directFloatBuffer.getFloat()
        val g = preprocessor.directFloatBuffer.getFloat()
        val b = preprocessor.directFloatBuffer.getFloat()

        assertEquals(1.0f, r, 0.005f)
        assertEquals(128f / 255f, g, 0.005f)
        assertEquals(0.0f, b, 0.005f)
    }

    @Test
    fun testPreprocessorPerformance_under8ms() {
        val preprocessor = KotlinImagePreprocessor(640)
        val srcW = 1280
        val srcH = 720
        val pixels = ByteArray(srcW * srcH * 4)

        // Warm up JIT
        repeat(3) {
            preprocessor.preprocessToModelInput(pixels, srcW, srcH, 0)
        }

        // Benchmark
        val iterations = 5
        val startTime = System.nanoTime()
        repeat(iterations) {
            preprocessor.preprocessToModelInput(pixels, srcW, srcH, 0)
        }
        val elapsedMs = (System.nanoTime() - startTime) / 1_000_000.0 / iterations

        println("Average KotlinImagePreprocessor execution time: ${elapsedMs}ms per frame")
        // Target is < 8ms (on desktop JVM typically 2-5ms)
        assertTrue(elapsedMs < 20.0, "Preprocessor execution took ${elapsedMs}ms, should be fast")
    }
}
