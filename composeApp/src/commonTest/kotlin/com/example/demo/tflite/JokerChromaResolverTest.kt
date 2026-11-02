package com.example.demo.tflite

import com.example.demo.domain.CardMapping
import com.example.demo.guidance.NormalizedBox
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JokerChromaResolverTest {

    @Test
    fun testRgbToHsv_pureColors() {
        val hsv = FloatArray(3)

        // Pure Red
        JokerChromaResolver.rgbToHsv(255, 0, 0, hsv)
        assertEquals(0.0f, hsv[0], 0.01f)
        assertEquals(1.0f, hsv[1], 0.01f)
        assertEquals(1.0f, hsv[2], 0.01f)

        // Pure Green
        JokerChromaResolver.rgbToHsv(0, 255, 0, hsv)
        assertEquals(120.0f, hsv[0], 0.01f)
        assertEquals(1.0f, hsv[1], 0.01f)
        assertEquals(1.0f, hsv[2], 0.01f)

        // Pure Blue
        JokerChromaResolver.rgbToHsv(0, 0, 255, hsv)
        assertEquals(240.0f, hsv[0], 0.01f)
        assertEquals(1.0f, hsv[1], 0.01f)
        assertEquals(1.0f, hsv[2], 0.01f)

        // Grayscale / Black
        JokerChromaResolver.rgbToHsv(0, 0, 0, hsv)
        assertEquals(0.0f, hsv[1], 0.01f)
        assertEquals(0.0f, hsv[2], 0.01f)

        // Grayscale / White
        JokerChromaResolver.rgbToHsv(255, 255, 255, hsv)
        assertEquals(0.0f, hsv[1], 0.01f)
        assertEquals(1.0f, hsv[2], 0.01f)
    }

    @Test
    fun testResolveJoker_decisiveMargin_preservesModelPrediction() {
        val box = NormalizedBox(0.1f, 0.1f, 0.5f, 0.5f, CardMapping.CLASS_BLACK_JOKER, 0.85f)
        val dummyPixels = IntArray(640 * 640)

        // High margin: P(52) = 0.85, P(53) = 0.20 -> margin = 0.65 >= 0.25
        val result = JokerChromaResolver.resolveJoker(
            box = box,
            pBlackJoker = 0.85f,
            pRedJoker = 0.20f,
            argbPixels = dummyPixels,
            imageWidth = 640,
            imageHeight = 640
        )

        assertFalse(result.isAmbiguous)
        assertFalse(result.overriddenByChroma)
        assertEquals(CardMapping.CLASS_BLACK_JOKER, result.resolvedClassId)
    }

    @Test
    fun testResolveJoker_ambiguousMargin_resolvesRedJokerWhenRedPresent() {
        val width = 640
        val height = 640
        val pixels = IntArray(width * height)

        // Simulate a card box in the center [0.2, 0.2, 0.8, 0.8]
        val x0 = (0.2f * width).toInt()
        val y0 = (0.2f * height).toInt()
        val x1 = (0.8f * width).toInt()
        val y1 = (0.8f * height).toInt()

        for (y in y0..y1) {
            for (x in x0..x1) {
                // Background is white/light gray
                pixels[y * width + x] = (0xFF shl 24) or (240 shl 16) or (240 shl 8) or 240
            }
        }

        // Add prominent red pixels (~20% of the card area, representing Red Joker costume/pips)
        for (y in y0 + 30..y0 + 200) {
            for (x in x0 + 30..x0 + 200) {
                pixels[y * width + x] = (0xFF shl 24) or (230 shl 16) or (20 shl 8) or 20
            }
        }

        val box = NormalizedBox(0.2f, 0.2f, 0.8f, 0.8f, CardMapping.CLASS_BLACK_JOKER, 0.52f)

        // Ambiguous margin: P(52) = 0.52, P(53) = 0.48 -> |0.52 - 0.48| = 0.04 < 0.25
        val result = JokerChromaResolver.resolveJoker(
            box = box,
            pBlackJoker = 0.52f,
            pRedJoker = 0.48f,
            argbPixels = pixels,
            imageWidth = width,
            imageHeight = height
        )

        assertTrue(result.isAmbiguous)
        assertTrue(result.redDensity > JokerChromaResolver.RED_DENSITY_THRESHOLD, "Red density should be > 3%, got ${result.redDensity}")
        assertEquals(CardMapping.CLASS_RED_JOKER, result.resolvedClassId)
        assertTrue(result.overriddenByChroma)
    }

    @Test
    fun testResolveJoker_ambiguousMargin_resolvesBlackJokerWhenMonochrome() {
        val width = 640
        val height = 640
        val pixels = IntArray(width * height)

        // Simulate monochrome/black-and-white card box [0.2, 0.2, 0.8, 0.8]
        val x0 = (0.2f * width).toInt()
        val y0 = (0.2f * height).toInt()
        val x1 = (0.8f * width).toInt()
        val y1 = (0.8f * height).toInt()

        for (y in y0..y1) {
            for (x in x0..x1) {
                // Grayscale pixels (gray/black text and drawings on white card)
                val lum = if ((x + y) % 8 == 0) 30 else 230
                pixels[y * width + x] = (0xFF shl 24) or (lum shl 16) or (lum shl 8) or lum
            }
        }

        val box = NormalizedBox(0.2f, 0.2f, 0.8f, 0.8f, CardMapping.CLASS_RED_JOKER, 0.51f)

        // Ambiguous margin: P(52) = 0.49, P(53) = 0.51 -> |0.49 - 0.51| = 0.02 < 0.25
        val result = JokerChromaResolver.resolveJoker(
            box = box,
            pBlackJoker = 0.49f,
            pRedJoker = 0.51f,
            argbPixels = pixels,
            imageWidth = width,
            imageHeight = height
        )

        assertTrue(result.isAmbiguous)
        assertEquals(0.0f, result.redDensity)
        assertEquals(0.0f, result.meanSaturation)
        assertEquals(CardMapping.CLASS_BLACK_JOKER, result.resolvedClassId)
        assertTrue(result.overriddenByChroma)
    }

    @Test
    fun testResolveJoker_modelPredictsOnlyBlackJoker_resolvesRedJokerWhenRedPixelsPresent() {
        val width = 640
        val height = 640
        val pixels = IntArray(width * height)

        val x0 = (0.32f * width).toInt()
        val y0 = (0.09f * height).toInt()
        val x1 = (0.36f * width).toInt()
        val y1 = (0.30f * height).toInt()

        for (y in y0..y1) {
            for (x in x0..x1) {
                // Background white
                pixels[y * width + x] = (0xFF shl 24) or (245 shl 16) or (245 shl 8) or 245
            }
        }

        // Add scarlet red ink inside corner JOKER box
        for (y in y0 + 10..y0 + 50) {
            for (x in x0 + 2..x1 - 2) {
                pixels[y * width + x] = (0xFF shl 24) or (220 shl 16) or (30 shl 8) or 30
            }
        }

        val box = NormalizedBox(0.32f, 0.09f, 0.36f, 0.30f, CardMapping.CLASS_BLACK_JOKER, 0.75f)

        // As seen in user logcat: P(Black)=75%, P(Red)=0%
        val result = JokerChromaResolver.resolveJoker(
            box = box,
            pBlackJoker = 0.75f,
            pRedJoker = 0.00f,
            argbPixels = pixels,
            imageWidth = width,
            imageHeight = height
        )

        assertEquals(CardMapping.CLASS_RED_JOKER, result.resolvedClassId)
        assertTrue(result.overriddenByChroma)
        assertTrue(result.redDensity > 0.015f)
    }

    @Test
    fun testResolveJoker_blackJokerUnderWarmAmbientLight_staysBlackJoker() {
        val width = 640
        val height = 640
        val pixels = IntArray(width * height)

        val x0 = (0.30f * width).toInt()
        val y0 = (0.20f * height).toInt()
        val x1 = (0.70f * width).toInt()
        val y1 = (0.80f * height).toInt()

        for (y in y0..y1) {
            for (x in x0..x1) {
                // Warm ambient light on white paper: yellowish-orange tint (2700K incandescent light)
                // RGB=(245, 220, 175): Hue=38.5 deg, Saturation=0.285, Value=0.96
                pixels[y * width + x] = (0xFF shl 24) or (245 shl 16) or (220 shl 8) or 175
            }
        }

        // Add black monochrome jester / JOKER lettering (RGB=35, 30, 25)
        for (y in y0 + 20..y0 + 100 step 4) {
            for (x in x0 + 20..x1 - 20 step 4) {
                pixels[y * width + x] = (0xFF shl 24) or (35 shl 16) or (30 shl 8) or 25
            }
        }

        val box = NormalizedBox(0.30f, 0.20f, 0.70f, 0.80f, CardMapping.CLASS_BLACK_JOKER, 0.65f)

        // Model predicts Black Joker under warm ambient light
        val result = JokerChromaResolver.resolveJoker(
            box = box,
            pBlackJoker = 0.65f,
            pRedJoker = 0.05f,
            argbPixels = pixels,
            imageWidth = width,
            imageHeight = height
        )

        // Warm lighting on black joker MUST NOT trigger Red Joker!
        assertEquals(CardMapping.CLASS_BLACK_JOKER, result.resolvedClassId)
        assertFalse(result.overriddenByChroma)
    }

    @Test
    fun testResolveJoker_redJokerUnderWarmAmbientLight_staysRedJoker() {
        val width = 640
        val height = 640
        val pixels = IntArray(width * height)

        val x0 = (0.30f * width).toInt()
        val y0 = (0.20f * height).toInt()
        val x1 = (0.70f * width).toInt()
        val y1 = (0.80f * height).toInt()

        for (y in y0..y1) {
            for (x in x0..x1) {
                // Warm background paper
                pixels[y * width + x] = (0xFF shl 24) or (245 shl 16) or (220 shl 8) or 175
            }
        }

        // Add genuine red ink (letters / red clown hat) RGB=(225, 25, 30) (Hue=358 deg, Sat=0.88)
        for (y in y0 + 15..y0 + 80) {
            for (x in x0 + 15..x0 + 60) {
                pixels[y * width + x] = (0xFF shl 24) or (225 shl 16) or (25 shl 8) or 30
            }
        }

        val box = NormalizedBox(0.30f, 0.20f, 0.70f, 0.80f, CardMapping.CLASS_RED_JOKER, 0.60f)

        val result = JokerChromaResolver.resolveJoker(
            box = box,
            pBlackJoker = 0.40f,
            pRedJoker = 0.60f,
            argbPixels = pixels,
            imageWidth = width,
            imageHeight = height
        )

        // Red Joker must remain Red Joker under warm ambient light
        assertEquals(CardMapping.CLASS_RED_JOKER, result.resolvedClassId)
        assertTrue(result.redDensity > 0.010f)
    }
}
