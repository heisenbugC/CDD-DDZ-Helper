package com.example.demo.services

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberAudioToneGenerator(): AudioToneGenerator? {
    val context = LocalContext.current
    val generator = remember { AndroidAudioToneGenerator(context) }
    DisposableEffect(generator) {
        onDispose {
            generator.release()
        }
    }
    return generator
}

@Composable
actual fun rememberHapticEngine(): HapticFeedbackEngine? {
    val context = LocalContext.current
    return remember { AndroidHapticEngine(context) }
}
