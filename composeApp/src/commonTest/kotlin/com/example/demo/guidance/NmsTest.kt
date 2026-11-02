package com.example.demo.guidance

import kotlin.test.Test
import kotlin.test.assertEquals

class NmsTest {

    @Test
    fun testNms_suppressesLowerConfidenceOverlappingBox() {
        val boxHighConf = NormalizedBox(0.2f, 0.2f, 0.6f, 0.6f, classId = 10, confidence = 0.90f)
        val boxLowConf = NormalizedBox(0.21f, 0.21f, 0.61f, 0.61f, classId = 10, confidence = 0.60f)

        val input = listOf(boxLowConf, boxHighConf)
        val result = input.applyNms(iouThreshold = 0.45f)

        assertEquals(1, result.size)
        assertEquals(0.90f, result[0].confidence)
    }

    @Test
    fun testNms_keepsDisjointBoxes() {
        val box1 = NormalizedBox(0.1f, 0.1f, 0.3f, 0.3f, classId = 1, confidence = 0.85f)
        val box2 = NormalizedBox(0.5f, 0.5f, 0.7f, 0.7f, classId = 2, confidence = 0.80f)

        val input = listOf(box1, box2)
        val result = input.applyNms(iouThreshold = 0.45f)

        assertEquals(2, result.size)
    }

    @Test
    fun testNms_emptyAndSingleElement() {
        assertEquals(0, emptyList<NormalizedBox>().applyNms().size)

        val single = listOf(NormalizedBox(0.1f, 0.1f, 0.4f, 0.4f, 0, 0.95f))
        assertEquals(1, single.applyNms().size)
    }
}
