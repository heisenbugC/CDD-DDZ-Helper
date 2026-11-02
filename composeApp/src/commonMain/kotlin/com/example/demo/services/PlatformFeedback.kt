package com.example.demo.services

import androidx.compose.runtime.Composable

@Composable
expect fun rememberAudioToneGenerator(): AudioToneGenerator?

@Composable
expect fun rememberHapticEngine(): HapticFeedbackEngine?
