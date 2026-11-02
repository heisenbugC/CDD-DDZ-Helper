package com.example.demo.services

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.demo.guidance.HapticType

/**
 * Native Android Haptic Feedback Engine.
 * Executes tactual feedback patterns for non-visual card camera guidance:
 * - SEARCH_TICK (15ms light pulse)
 * - ALIGNMENT_PULSE (30ms centering pulse)
 * - CENTERED_DOUBLE_CLICK (dual crisp clicks)
 * - LOCK_CONFIRMATION_THUD (120ms deep confirmation pulse)
 */
class AndroidHapticEngine(
    context: Context
) : HapticFeedbackEngine {

    @Suppress("DEPRECATION")
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    override fun playHaptic(type: HapticType) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return

        when (type) {
            HapticType.NONE -> {
                // No vibration
            }
            HapticType.SEARCH_TICK -> {
                playSearchTick(vib)
            }
            HapticType.ALIGNMENT_PULSE -> {
                playAlignmentPulse(vib)
            }
            HapticType.CENTERED_DOUBLE_CLICK -> {
                playCenteredDoubleClick(vib)
            }
            HapticType.LOCK_CONFIRMATION_THUD -> {
                playLockThud(vib)
            }
        }
    }

    private fun playSearchTick(vib: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                return
            } catch (_: Exception) {}
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vib.vibrate(VibrationEffect.createOneShot(15, 60))
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(15)
        }
    }

    private fun playAlignmentPulse(vib: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vib.vibrate(VibrationEffect.createOneShot(30, 140))
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(30)
        }
    }

    private fun playCenteredDoubleClick(vib: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
                return
            } catch (_: Exception) {}
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings = longArrayOf(0, 25, 50, 25)
            val amplitudes = intArrayOf(0, 200, 0, 200)
            vib.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(longArrayOf(0, 25, 50, 25), -1)
        }
    }

    private fun playLockThud(vib: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
                return
            } catch (_: Exception) {}
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vib.vibrate(VibrationEffect.createOneShot(120, 255))
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(120)
        }
    }

    override fun cancel() {
        vibrator?.cancel()
    }
}
