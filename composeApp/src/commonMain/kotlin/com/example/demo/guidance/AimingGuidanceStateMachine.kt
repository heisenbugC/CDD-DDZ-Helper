package com.example.demo.guidance

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Non-Visual Aiming Guidance State Machine.
 * Strictly adheres to ARCHITECTURE.md Section 1.2, 1.3, 3.1, 3.2 & 8.1.
 *
 * Implements:
 * - Centering Metric Calculation (Q)
 * - Audio Pitch Curve: f(Q) = 220 + 660 * Q^1.5 Hz
 * - Pulse Interval Curve: delta_t(Q) = 900 - 750 * Q ms
 * - Melodious centered chime trigger at Q >= 0.85
 * - 1.0s temporal stability locking integration
 */
class AimingGuidanceStateMachine(
    private val stabilityDetector: StabilityDetector = StabilityDetector(),
    initialState: AimingState = AimingState.SEARCHING_FOR_CARDS
) : AimingGuidanceController {

    private val _currentState = MutableStateFlow(initialState)
    override val currentState: StateFlow<AimingState> = _currentState.asStateFlow()

    private val _feedbackSignal = MutableStateFlow(
        if (initialState == AimingState.SEARCHING_FOR_CARDS) {
            NonVisualFeedbackSignal(
                audioToneFrequencyHz = 180f,
                pulseIntervalMs = 1200L,
                hapticFeedbackType = HapticType.SEARCH_TICK
            )
        } else {
            NonVisualFeedbackSignal()
        }
    )
    override val feedbackSignal: StateFlow<NonVisualFeedbackSignal> = _feedbackSignal.asStateFlow()

    private val _centeringScore = MutableStateFlow(0f)
    val centeringScore: StateFlow<Float> = _centeringScore.asStateFlow()

    private var hasFiredCenteredChime = false

    override fun onNewFrameDetections(boxes: List<NormalizedBox>, timestampMs: Long) {
        val state = _currentState.value
        if (state == AimingState.IDLE || state == AimingState.LOCKED) {
            return
        }

        if (boxes.isEmpty()) {
            stabilityDetector.reset()
            hasFiredCenteredChime = false
            _centeringScore.value = 0f
            _currentState.value = AimingState.SEARCHING_FOR_CARDS
            _feedbackSignal.value = NonVisualFeedbackSignal(
                audioToneFrequencyHz = 180f,
                pulseIntervalMs = 1200L,
                playCenteredChime = false,
                playLockChord = false,
                hapticFeedbackType = HapticType.SEARCH_TICK
            )
            return
        }

        val q = calculateCenteringScore(boxes)
        _centeringScore.value = q

        val pitch = calculateAudioPitch(q)
        val pulse = calculatePulseInterval(q)

        if (q < 0.85f) {
            stabilityDetector.reset()
            hasFiredCenteredChime = false
            _currentState.value = AimingState.CARDS_PARTIALLY_VISIBLE
            _feedbackSignal.value = NonVisualFeedbackSignal(
                audioToneFrequencyHz = pitch,
                pulseIntervalMs = pulse,
                playCenteredChime = false,
                playLockChord = false,
                hapticFeedbackType = HapticType.ALIGNMENT_PULSE
            )
        } else {
            // Q >= 0.85: Cards Centered
            val triggerChime = !hasFiredCenteredChime
            if (triggerChime) {
                hasFiredCenteredChime = true
            }

            val isStable = stabilityDetector.update(boxes, timestampMs)
            if (isStable) {
                // Transition to LOCKED!
                _currentState.value = AimingState.LOCKED
                _feedbackSignal.value = NonVisualFeedbackSignal(
                    audioToneFrequencyHz = 0f, // Mute continuous tone on lock
                    pulseIntervalMs = 0L,
                    playCenteredChime = false,
                    playLockChord = true,
                    hapticFeedbackType = HapticType.LOCK_CONFIRMATION_THUD
                )
            } else {
                _currentState.value = if (triggerChime) AimingState.CARDS_CENTERED else AimingState.STABILITY_COUNTDOWN
                _feedbackSignal.value = NonVisualFeedbackSignal(
                    audioToneFrequencyHz = pitch,
                    pulseIntervalMs = pulse,
                    playCenteredChime = triggerChime,
                    playLockChord = false,
                    hapticFeedbackType = if (triggerChime) HapticType.CENTERED_DOUBLE_CLICK else HapticType.ALIGNMENT_PULSE
                )
            }
        }
    }

    override fun triggerManualLock() {
        if (_currentState.value != AimingState.LOCKED) {
            _currentState.value = AimingState.LOCKED
            _feedbackSignal.value = NonVisualFeedbackSignal(
                audioToneFrequencyHz = 0f,
                pulseIntervalMs = 0L,
                playCenteredChime = false,
                playLockChord = true,
                hapticFeedbackType = HapticType.LOCK_CONFIRMATION_THUD
            )
        }
    }

    override fun unlockForNewRound() {
        stabilityDetector.reset()
        hasFiredCenteredChime = false
        _centeringScore.value = 0f
        _currentState.value = AimingState.SEARCHING_FOR_CARDS
        _feedbackSignal.value = NonVisualFeedbackSignal(
            audioToneFrequencyHz = 180f,
            pulseIntervalMs = 1200L,
            playCenteredChime = false,
            playLockChord = false,
            hapticFeedbackType = HapticType.SEARCH_TICK
        )
    }

    companion object {
        /**
         * Calculates composite centering quality score Q in [0.0, 1.0].
         * Formula from ARCHITECTURE.md Section 3.2.1:
         * D_center = sqrt((C_x - 0.5)^2 + (C_y - 0.5)^2)
         * Area_envelope = (max_x - min_x) * (max_y - min_y)
         * S_area = piecewise function of Area_envelope
         * Q = max(0.0, 1.0 - D_center / 0.35) * S_area
         */
        fun calculateCenteringScore(boxes: List<NormalizedBox>): Float {
            if (boxes.isEmpty()) return 0f

            val count = boxes.size
            var sumX = 0f
            var sumY = 0f
            var minX = Float.MAX_VALUE
            var maxX = Float.MIN_VALUE
            var minY = Float.MAX_VALUE
            var maxY = Float.MIN_VALUE

            for (b in boxes) {
                sumX += b.centerX
                sumY += b.centerY
                if (b.xMin < minX) minX = b.xMin
                if (b.xMax > maxX) maxX = b.xMax
                if (b.yMin < minY) minY = b.yMin
                if (b.yMax > maxY) maxY = b.yMax
            }

            val cX = sumX / count
            val cY = sumY / count

            val dx = cX - 0.5f
            val dy = cY - 0.5f
            val dCenter = sqrt((dx * dx + dy * dy).toDouble()).toFloat()

            val envelopeWidth = (maxX - minX).coerceAtLeast(0f)
            val envelopeHeight = (maxY - minY).coerceAtLeast(0f)
            val areaEnvelope = envelopeWidth * envelopeHeight

            val sArea = when {
                areaEnvelope < 0.35f -> areaEnvelope / 0.35f
                areaEnvelope <= 0.75f -> 1.0f
                else -> max(0.0f, 1.0f - (areaEnvelope - 0.75f) / 0.25f)
            }

            val centerScore = max(0.0f, 1.0f - dCenter / 0.35f)
            val q = centerScore * sArea
            return q.coerceIn(0.0f, 1.0f)
        }

        /**
         * Pitch curve: f(Q) = 220 + 660 * Q^1.5 Hz (Range: 220Hz to 880Hz)
         */
        fun calculateAudioPitch(q: Float): Float {
            val clampedQ = q.coerceIn(0f, 1f)
            return 220f + 660f * clampedQ.toDouble().pow(1.5).toFloat()
        }

        /**
         * Pulse Interval (Tempo): delta_t(Q) = 900 - 750 * Q ms (Range: 900ms down to 150ms)
         */
        fun calculatePulseInterval(q: Float): Long {
            val clampedQ = q.coerceIn(0f, 1f)
            return (900f - 750f * clampedQ).toLong().coerceIn(150L, 900L)
        }
    }
}
