package com.example.demo

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun CameraView(modifier: Modifier = Modifier, onCardsDetected: (List<String>) -> Unit)
