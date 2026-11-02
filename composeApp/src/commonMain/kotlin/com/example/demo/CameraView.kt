package com.example.demo

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.demo.guidance.NormalizedBox

@Composable
expect fun CameraView(
    modifier: Modifier = Modifier,
    onCardsDetected: (List<String>) -> Unit = {},
    onBoxesDetected: ((List<NormalizedBox>) -> Unit)? = null
)
