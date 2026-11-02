package com.example.demo.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.demo.domain.CardMapping
import com.example.demo.domain.GameMode
import com.example.demo.guidance.NormalizedBox
import com.example.demo.services.CardSpeechFormatter

/**
 * Real-time Camera HUD Bounding Box and Label Tag Overlay.
 * Renders glowing bounding boxes and classification tags directly over the CameraX preview,
 * ensuring users receive immediate visual confirmation when Jokers or standard cards are detected.
 */
@Composable
fun CameraDetectionOverlay(
    boxes: List<NormalizedBox>,
    gameMode: GameMode,
    modifier: Modifier = Modifier
) {
    if (boxes.isEmpty()) return

    BoxWithConstraints(modifier = modifier) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight

        Canvas(modifier = Modifier.fillMaxSize()) {
            for (box in boxes) {
                val left = box.xMin * size.width
                val top = box.yMin * size.height
                val width = (box.xMax - box.xMin) * size.width
                val height = (box.yMax - box.yMin) * size.height

                val color = when (box.classId) {
                    CardMapping.CLASS_RED_JOKER -> Color(0xFFFF1744) // Vibrant Red
                    CardMapping.CLASS_BLACK_JOKER -> Color(0xFFD500F9) // Violet / Purple
                    else -> Color(0xFF00E5FF) // Electric Blue
                }

                drawRoundRect(
                    color = color.copy(alpha = 0.12f),
                    topLeft = Offset(left, top),
                    size = Size(width, height),
                    cornerRadius = CornerRadius(16f)
                )
                drawRoundRect(
                    color = color,
                    topLeft = Offset(left, top),
                    size = Size(width, height),
                    cornerRadius = CornerRadius(16f),
                    style = Stroke(width = 3.dp.toPx())
                )
            }
        }

        for (box in boxes) {
            val label = when (box.classId) {
                CardMapping.CLASS_RED_JOKER -> "👑 大王"
                CardMapping.CLASS_BLACK_JOKER -> "🃏 小王"
                else -> CardMapping.toDdzCard(box.classId)?.let { CardSpeechFormatter.describeDdzCardZh(it) } ?: "牌"
            }
            val confPct = (box.confidence * 100).toInt()
            val pillColor = when (box.classId) {
                CardMapping.CLASS_RED_JOKER -> Color(0xFFD50000)
                CardMapping.CLASS_BLACK_JOKER -> Color(0xFF4A148C)
                else -> Color(0xFF01579B)
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = pillColor.copy(alpha = 0.92f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.7f)),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .offset(
                        x = (screenWidth * box.xMin).coerceIn(0.dp, (screenWidth - 90.dp).coerceAtLeast(0.dp)),
                        y = (screenHeight * box.yMin - 26.dp).coerceIn(40.dp, (screenHeight - 60.dp).coerceAtLeast(40.dp))
                    )
            ) {
                Text(
                    text = "$label $confPct%",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

