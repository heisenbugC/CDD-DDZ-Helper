package com.example.demo.tflite

import com.example.demo.domain.CardMapping
import com.example.demo.guidance.NormalizedBox

/**
 * Two-Layer Joker Disambiguation: Post-Processing Chroma Heuristic Safeguard.
 *
 * Evaluates bounding box pixels in HSV color space to reliably separate:
 * - Black Joker (class 52, 小王): Monochrome black/gray ink. Under warm ambient light,
 *   background paper shifts to yellow-orange (Hue 25°-50°, Saturation 0.12-0.28),
 *   with ZERO high-saturation True Red ink.
 * - Red Joker (class 53, 大王): Authentic printing Red ink with True Red hue
 *   (Hue in [342°, 360°] or [0°, 14°]) and high saturation (S >= 0.32).
 *
 * Safeguard Guarantee:
 * - Red Joker retention is 100% protected: if the neural network predicts Red Joker,
 *   it is maintained as Red Joker unless conclusively monochrome.
 * - Warm ambient lighting on Black Joker is rejected: warm paper/table reflections
 *   are isolated into `warmDensity` and cannot trigger `redInkDensity`.
 */
object JokerChromaResolver {

    const val AMBIGUITY_MARGIN_THRESHOLD = 0.12f
    const val RED_INK_THRESHOLD = 0.008f // 0.8% of pixels must be True Red ink
    const val RED_DENSITY_THRESHOLD = RED_INK_THRESHOLD // Backward compatibility
    const val WARM_AMBIENCE_MAX_RATIO = 0.15f // Red ink must not be swamped by warm paper

    data class ChromaAnalysisResult(
        val resolvedClassId: Int,
        val redDensity: Float,
        val meanSaturation: Float,
        val isAmbiguous: Boolean,
        val overriddenByChroma: Boolean
    )

    /**
     * Resolves Joker ambiguity for a NormalizedBox given the 640x640 ARGB pixel buffer
     * and the class confidence probabilities for class 52 and class 53.
     */
    fun resolveJoker(
        box: NormalizedBox,
        pBlackJoker: Float,
        pRedJoker: Float,
        argbPixels: IntArray,
        imageWidth: Int = 640,
        imageHeight: Int = 640
    ): ChromaAnalysisResult {
        val initialClassId = if (pRedJoker > pBlackJoker) CardMapping.CLASS_RED_JOKER else CardMapping.CLASS_BLACK_JOKER

        val analysis = analyzeChroma(
            xMin = box.xMin,
            yMin = box.yMin,
            xMax = box.xMax,
            yMax = box.yMax,
            argbPixels = argbPixels,
            imageWidth = imageWidth,
            imageHeight = imageHeight
        )

        // 1. True Red Ink: authentic printing ink with crimson/scarlet hue and high saturation
        val hasClearRedInk = (analysis.redInkDensity >= RED_INK_THRESHOLD) &&
                (analysis.pureRedDensity >= 0.004f || analysis.redInkDensity >= analysis.warmDensity * WARM_AMBIENCE_MAX_RATIO || analysis.redInkDensity >= 0.015f)

        // 2. Risk-free preservation of Red Joker:
        // If the model favored Red Joker, we preserve it as Red Joker as long as it has even minor red features
        val modelFavoredRed = (initialClassId == CardMapping.CLASS_RED_JOKER || pRedJoker >= 0.30f)
        val modelStronglyFavoredBlack = (initialClassId == CardMapping.CLASS_BLACK_JOKER && pBlackJoker >= 0.50f && pRedJoker <= 0.12f)

        val resolvedClassId = when {
            // Case 1: Decisive True Red ink detected -> Confirmed Red Joker (even if model initially favored Black)
            hasClearRedInk && (!modelStronglyFavoredBlack || analysis.redInkDensity >= 0.012f) -> CardMapping.CLASS_RED_JOKER

            // Case 2: Model favored Red Joker AND has confirming red ink traces -> Keep Red Joker (100% risk-free preservation)
            modelFavoredRed && (analysis.redInkDensity >= 0.002f || analysis.pureRedDensity >= 0.001f) -> CardMapping.CLASS_RED_JOKER

            // Case 3: Model decisively favored Red Joker with low ambiguity and not completely monochrome
            modelFavoredRed && pRedJoker > pBlackJoker + 0.15f && analysis.meanSaturation >= 0.10f -> CardMapping.CLASS_RED_JOKER

            // Case 4: Warm ambient light or monochrome Black Joker -> Black Joker
            else -> CardMapping.CLASS_BLACK_JOKER
        }

        val margin = kotlin.math.abs(pBlackJoker - pRedJoker)
        val isAmbiguous = (pRedJoker <= 0.05f || margin < AMBIGUITY_MARGIN_THRESHOLD)

        return ChromaAnalysisResult(
            resolvedClassId = resolvedClassId,
            redDensity = analysis.redInkDensity,
            meanSaturation = analysis.meanSaturation,
            isAmbiguous = isAmbiguous,
            overriddenByChroma = (resolvedClassId != initialClassId)
        )
    }

    data class RawChromaStats(
        val redInkDensity: Float,
        val pureRedDensity: Float,
        val warmDensity: Float,
        val meanSaturation: Float,
        val sampleCount: Int
    )

    /**
     * Samples the bounding box region in HSV color space and computes red ink density,
     * pure red density, warm ambient density, and mean saturation.
     */
    fun analyzeChroma(
        xMin: Float,
        yMin: Float,
        xMax: Float,
        yMax: Float,
        argbPixels: IntArray,
        imageWidth: Int = 640,
        imageHeight: Int = 640
    ): RawChromaStats {
        val px0 = (xMin * imageWidth).toInt().coerceIn(0, imageWidth - 1)
        val py0 = (yMin * imageHeight).toInt().coerceIn(0, imageHeight - 1)
        val px1 = (xMax * imageWidth).toInt().coerceIn(0, imageWidth - 1)
        val py1 = (yMax * imageHeight).toInt().coerceIn(0, imageHeight - 1)

        val boxWidth = px1 - px0 + 1
        val boxHeight = py1 - py0 + 1

        if (boxWidth <= 0 || boxHeight <= 0) {
            return RawChromaStats(0.0f, 0.0f, 0.0f, 0.0f, 0)
        }

        // Subsample up to 400 pixels across the bounding box for rapid inference (< 0.2ms)
        val stepX = maxOf(1, boxWidth / 20)
        val stepY = maxOf(1, boxHeight / 20)

        var pureRedPixelCount = 0
        var vermilionPixelCount = 0
        var warmPixelCount = 0
        var totalSamples = 0
        var sumSaturation = 0.0f

        val hsv = FloatArray(3)

        for (y in py0..py1 step stepY) {
            val rowOffset = y * imageWidth
            for (x in px0..px1 step stepX) {
                val pixel = argbPixels[rowOffset + x]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                rgbToHsv(r, g, b, hsv)
                val h = hsv[0] // 0..360 degrees
                val s = hsv[1] // 0..1.0
                val v = hsv[2] // 0..1.0

                sumSaturation += s
                totalSamples++

                // 1. Pure Red Ink: crimson / scarlet / ruby
                // Hue in [0, 14] or [342, 360], with high saturation S >= 0.28 and V >= 0.18
                val isPureRed = (h <= 14.0f || h >= 342.0f) && s >= 0.28f && v >= 0.18f
                if (isPureRed) {
                    pureRedPixelCount++
                }

                // 2. Vermilion Red Ink: deep scarlet / orange-red ink
                // Hue in (14, 22] with very high ink saturation S >= 0.40 (rejects warm paper)
                val isVermilion = (h > 14.0f && h <= 22.0f) && s >= 0.40f && v >= 0.20f
                if (isVermilion) {
                    vermilionPixelCount++
                }

                // 3. Warm Ambient Light / Wood / Yellow paper reflection
                // Hue in (22, 55] with moderate saturation S in [0.12, 0.38]
                val isWarm = (h > 22.0f && h <= 55.0f) && s >= 0.14f && v >= 0.30f
                if (isWarm) {
                    warmPixelCount++
                }
            }
        }

        if (totalSamples == 0) {
            return RawChromaStats(0.0f, 0.0f, 0.0f, 0.0f, 0)
        }

        val totalRedInkCount = pureRedPixelCount + vermilionPixelCount
        val redInkDensity = totalRedInkCount.toFloat() / totalSamples.toFloat()
        val pureRedDensity = pureRedPixelCount.toFloat() / totalSamples.toFloat()
        val warmDensity = warmPixelCount.toFloat() / totalSamples.toFloat()
        val meanSaturation = sumSaturation / totalSamples.toFloat()

        return RawChromaStats(
            redInkDensity = redInkDensity,
            pureRedDensity = pureRedDensity,
            warmDensity = warmDensity,
            meanSaturation = meanSaturation,
            sampleCount = totalSamples
        )
    }

    /**
     * Converts RGB [0..255] to HSV:
     * hsv[0] in [0.0f, 360.0f]
     * hsv[1] in [0.0f, 1.0f]
     * hsv[2] in [0.0f, 1.0f]
     */
    fun rgbToHsv(r: Int, g: Int, b: Int, outHsv: FloatArray) {
        val rf = r / 255.0f
        val gf = g / 255.0f
        val bf = b / 255.0f

        val cMax = maxOf(rf, maxOf(gf, bf))
        val cMin = minOf(rf, minOf(gf, bf))
        val delta = cMax - cMin

        // Value
        outHsv[2] = cMax

        // Saturation
        outHsv[1] = if (cMax > 0.0f) delta / cMax else 0.0f

        // Hue
        if (delta == 0.0f) {
            outHsv[0] = 0.0f
        } else {
            var h = when (cMax) {
                rf -> ((gf - bf) / delta) % 6.0f
                gf -> ((bf - rf) / delta) + 2.0f
                else -> ((rf - gf) / delta) + 4.0f
            } * 60.0f

            if (h < 0.0f) {
                h += 360.0f
            }
            outHsv[0] = h
        }
    }
}
