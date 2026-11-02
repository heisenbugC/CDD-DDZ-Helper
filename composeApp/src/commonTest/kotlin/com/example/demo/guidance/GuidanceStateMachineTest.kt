package com.example.demo.guidance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GuidanceStateMachineTest {

    @Test
    fun testCenteringScoreCalculation() {
        // Ideal centered boxes cluster around (0.5, 0.5) with envelope area >= 0.35 (e.g. 0.6 x 0.6 = 0.36)
        val centeredBoxes = listOf(
            NormalizedBox(0.20f, 0.20f, 0.40f, 0.45f, classId = 1, confidence = 0.9f),
            NormalizedBox(0.60f, 0.20f, 0.80f, 0.45f, classId = 2, confidence = 0.9f),
            NormalizedBox(0.20f, 0.55f, 0.40f, 0.80f, classId = 3, confidence = 0.9f),
            NormalizedBox(0.60f, 0.55f, 0.80f, 0.80f, classId = 4, confidence = 0.9f)
        )
        val qCentered = AimingGuidanceStateMachine.calculateCenteringScore(centeredBoxes)
        assertTrue(qCentered >= 0.85f, "Centered cards should have Q >= 0.85, got $qCentered")

        // Highly off-center boxes (shifted to top-left corner)
        val offCenterBoxes = listOf(
            NormalizedBox(0.01f, 0.01f, 0.10f, 0.15f, classId = 1, confidence = 0.9f),
            NormalizedBox(0.12f, 0.01f, 0.20f, 0.15f, classId = 2, confidence = 0.9f)
        )
        val qOffCenter = AimingGuidanceStateMachine.calculateCenteringScore(offCenterBoxes)
        assertTrue(qOffCenter < 0.50f, "Off-center cards should have low Q, got $qOffCenter")

        // Empty boxes
        assertEquals(0f, AimingGuidanceStateMachine.calculateCenteringScore(emptyList()))
    }

    @Test
    fun testAudioPitchAndPulseCurves() {
        // When Q = 0.0: pitch is 220Hz, pulse interval is 900ms
        assertEquals(220f, AimingGuidanceStateMachine.calculateAudioPitch(0.0f))
        assertEquals(900L, AimingGuidanceStateMachine.calculatePulseInterval(0.0f))

        // When Q = 1.0: pitch is 880Hz (220 + 660), pulse interval is 150ms (900 - 750)
        assertEquals(880f, AimingGuidanceStateMachine.calculateAudioPitch(1.0f))
        assertEquals(150L, AimingGuidanceStateMachine.calculatePulseInterval(1.0f))

        // Monotonicity check
        val pLow = AimingGuidanceStateMachine.calculateAudioPitch(0.3f)
        val pHigh = AimingGuidanceStateMachine.calculateAudioPitch(0.8f)
        assertTrue(pHigh > pLow)

        val intLow = AimingGuidanceStateMachine.calculatePulseInterval(0.3f)
        val intHigh = AimingGuidanceStateMachine.calculatePulseInterval(0.8f)
        assertTrue(intLow > intHigh, "Higher Q should have faster tempo (shorter interval)")
    }

    @Test
    fun testStabilityDetectorOneSecondLock() {
        val detector = StabilityDetector(requiredStableDurationMs = 1000L, maxCentroidJitter = 0.03f)

        val steadyBoxes = listOf(
            NormalizedBox(0.3f, 0.3f, 0.5f, 0.5f, classId = 10, confidence = 0.9f),
            NormalizedBox(0.5f, 0.5f, 0.7f, 0.7f, classId = 12, confidence = 0.9f)
        )

        // Frame at t = 0ms
        assertFalse(detector.update(steadyBoxes, 0L))

        // Frame at t = 500ms
        assertFalse(detector.update(steadyBoxes, 500L))

        // Frame at t = 999ms (not quite 1000ms yet)
        assertFalse(detector.update(steadyBoxes, 999L))

        // Frame at t = 1001ms (stable for >= 1000ms!)
        assertTrue(detector.update(steadyBoxes, 1001L), "Should trigger stability lock after 1.0s")
    }

    @Test
    fun testStabilityDetectorResetsOnJitter() {
        val detector = StabilityDetector(requiredStableDurationMs = 1000L, maxCentroidJitter = 0.03f)

        val steadyBoxes = listOf(
            NormalizedBox(0.4f, 0.4f, 0.6f, 0.6f, classId = 5, confidence = 0.9f)
        )
        detector.update(steadyBoxes, 0L)
        detector.update(steadyBoxes, 500L)

        // Sudden erratic motion at t = 800ms (centroid jumps > 0.03)
        val shiftedBoxes = listOf(
            NormalizedBox(0.1f, 0.1f, 0.3f, 0.3f, classId = 5, confidence = 0.9f)
        )
        detector.update(shiftedBoxes, 800L)

        // At t = 1001ms, because of the jitter, it must NOT lock!
        assertFalse(detector.update(shiftedBoxes, 1001L))
    }

    @Test
    fun testStateMachineLifecycle() {
        val stateMachine = AimingGuidanceStateMachine()
        assertEquals(AimingState.SEARCHING_FOR_CARDS, stateMachine.currentState.value)
        assertEquals(HapticType.SEARCH_TICK, stateMachine.feedbackSignal.value.hapticFeedbackType)

        // Center boxes with Q >= 0.85
        val centeredBoxes = listOf(
            NormalizedBox(0.20f, 0.20f, 0.40f, 0.45f, classId = 1, confidence = 0.9f),
            NormalizedBox(0.60f, 0.20f, 0.80f, 0.45f, classId = 2, confidence = 0.9f),
            NormalizedBox(0.20f, 0.55f, 0.40f, 0.80f, classId = 3, confidence = 0.9f),
            NormalizedBox(0.60f, 0.55f, 0.80f, 0.80f, classId = 4, confidence = 0.9f)
        )

        // First centered frame at t = 100ms: fires chime
        stateMachine.onNewFrameDetections(centeredBoxes, 100L)
        assertEquals(AimingState.CARDS_CENTERED, stateMachine.currentState.value)
        assertTrue(stateMachine.feedbackSignal.value.playCenteredChime)
        assertEquals(HapticType.CENTERED_DOUBLE_CLICK, stateMachine.feedbackSignal.value.hapticFeedbackType)

        // Second frame at t = 600ms: countdown in progress
        stateMachine.onNewFrameDetections(centeredBoxes, 600L)
        assertEquals(AimingState.STABILITY_COUNTDOWN, stateMachine.currentState.value)
        assertFalse(stateMachine.feedbackSignal.value.playCenteredChime)

        // Final frame at t = 1150ms (> 1000ms duration!): locks automatically
        stateMachine.onNewFrameDetections(centeredBoxes, 1150L)
        assertEquals(AimingState.LOCKED, stateMachine.currentState.value)
        assertTrue(stateMachine.feedbackSignal.value.playLockChord)
        assertEquals(HapticType.LOCK_CONFIRMATION_THUD, stateMachine.feedbackSignal.value.hapticFeedbackType)

        // Unlock returns to searching
        stateMachine.unlockForNewRound()
        assertEquals(AimingState.SEARCHING_FOR_CARDS, stateMachine.currentState.value)
    }

    @Test
    fun testManualLockTrigger() {
        val stateMachine = AimingGuidanceStateMachine()
        assertEquals(AimingState.SEARCHING_FOR_CARDS, stateMachine.currentState.value)

        stateMachine.triggerManualLock()
        assertEquals(AimingState.LOCKED, stateMachine.currentState.value)
        assertTrue(stateMachine.feedbackSignal.value.playLockChord)
        assertEquals(HapticType.LOCK_CONFIRMATION_THUD, stateMachine.feedbackSignal.value.hapticFeedbackType)
    }
}
