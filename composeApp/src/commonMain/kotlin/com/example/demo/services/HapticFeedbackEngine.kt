package com.example.demo.services

import com.example.demo.guidance.HapticType

/**
 * Platform haptic feedback engine interface for non-visual aiming guidance.
 */
interface HapticFeedbackEngine {
    fun playHaptic(type: HapticType)
    fun cancel()
}
