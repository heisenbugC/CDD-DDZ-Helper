package com.example.demo

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.demo.guidance.NormalizedBox

@Composable
actual fun CameraView(
    modifier: Modifier,
    onCardsDetected: (List<String>) -> Unit,
    onBoxesDetected: ((List<NormalizedBox>) -> Unit)?
) {
    // We already have native CameraView in iOS side (CameraView.swift)
    // This is just a placeholder if it's used inside KMP Compose tree on iOS.
}
