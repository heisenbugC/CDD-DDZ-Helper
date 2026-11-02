package com.example.demo.services

import com.example.demo.guidance.NonVisualFeedbackSignal

/**
 * Platform audio tone generator interface for non-visual camera aiming guidance.
 */
interface AudioToneGenerator {
    fun updateSignal(signal: NonVisualFeedbackSignal)
    fun start()
    fun stop()
    fun release()
}
