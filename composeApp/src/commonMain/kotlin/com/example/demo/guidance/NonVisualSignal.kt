package com.example.demo.guidance

import kotlinx.coroutines.flow.StateFlow

enum class AimingState {
    IDLE,
    SEARCHING_FOR_CARDS,
    CARDS_PARTIALLY_VISIBLE,
    CARDS_CENTERED,
    STABILITY_COUNTDOWN,
    LOCKED
}

data class NormalizedBox(
    val xMin: Float,
    val yMin: Float,
    val xMax: Float,
    val yMax: Float,
    val classId: Int,
    val confidence: Float
) {
    val centerX: Float get() = (xMin + xMax) / 2f
    val centerY: Float get() = (yMin + yMax) / 2f
    val width: Float get() = (xMax - xMin).coerceAtLeast(0f)
    val height: Float get() = (yMax - yMin).coerceAtLeast(0f)
    val area: Float get() = width * height
}

/**
 * Executes Non-Maximum Suppression (NMS) over a collection of NormalizedBox detections.
 */
fun List<NormalizedBox>.applyNms(iouThreshold: Float = 0.45f): List<NormalizedBox> {
    if (this.size <= 1) return this

    val sorted = this.sortedByDescending { it.confidence }
    val selected = mutableListOf<NormalizedBox>()
    val active = BooleanArray(sorted.size) { true }

    for (i in sorted.indices) {
        if (!active[i]) continue
        val a = sorted[i]
        selected.add(a)

        for (j in i + 1 until sorted.size) {
            if (!active[j]) continue
            val b = sorted[j]

            val interXMin = maxOf(a.xMin, b.xMin)
            val interYMin = maxOf(a.yMin, b.yMin)
            val interXMax = minOf(a.xMax, b.xMax)
            val interYMax = minOf(a.yMax, b.yMax)

            val interW = maxOf(0.0f, interXMax - interXMin)
            val interH = maxOf(0.0f, interYMax - interYMin)
            val interArea = interW * interH

            val unionArea = a.area + b.area - interArea
            if (unionArea > 0.0f) {
                val iou = interArea / unionArea
                if (iou > iouThreshold) {
                    active[j] = false
                }
            }
        }
    }
    return selected
}

data class NonVisualFeedbackSignal(
    val audioToneFrequencyHz: Float = 0f,    // 0f = mute, 200f..880f
    val pulseIntervalMs: Long = 1200L,       // 150ms .. 1200ms
    val playCenteredChime: Boolean = false,  // True when cards first hit centered threshold
    val playLockChord: Boolean = false,      // True on transition to LOCKED
    val hapticFeedbackType: HapticType = HapticType.NONE
)

enum class HapticType {
    NONE,
    SEARCH_TICK,
    ALIGNMENT_PULSE,
    CENTERED_DOUBLE_CLICK,
    LOCK_CONFIRMATION_THUD
}

interface AimingGuidanceController {
    val currentState: StateFlow<AimingState>
    val feedbackSignal: StateFlow<NonVisualFeedbackSignal>
    
    fun onNewFrameDetections(boxes: List<NormalizedBox>, timestampMs: Long)
    fun triggerManualLock()
    fun unlockForNewRound()
}
