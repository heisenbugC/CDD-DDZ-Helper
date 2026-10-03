package com.example.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.demo.domain.DdzCard
import com.example.demo.domain.DdzRank
import com.example.demo.domain.DdzSuit
import com.example.demo.rules.DdzHandFinder
import com.example.demo.rules.DdzHand

@Composable
fun App() {
    MaterialTheme {
        CddHelperScreen()
    }
}

@Composable
fun CddHelperScreen() {
    var detectedCards by remember { mutableStateOf(emptyList<DdzCard>()) }
    var detectedHands by remember { mutableStateOf(emptyList<DdzHand>()) }
    var rawDetections by remember { mutableStateOf(emptyList<String>()) }
    
    fun parseCard(cardStr: String): DdzCard? {
        if (cardStr == "black_joker") return DdzCard(DdzSuit.SPADE, DdzRank.BLACK_JOKER) // Suit doesn't matter for Jokers
        if (cardStr == "red_joker") return DdzCard(DdzSuit.HEART, DdzRank.RED_JOKER)

        val parts = cardStr.split("_of_")
        if (parts.size != 2) return null
        
        val rankStr = parts[0]
        val suitStr = parts[1] // "clubs", "diamonds", etc.
        
        val suit = DdzSuit.entries.find { suitStr.startsWith(it.name, ignoreCase = true) } ?: return null
        
        val rank = when(rankStr) {
            "3" -> DdzRank.THREE
            "4" -> DdzRank.FOUR
            "5" -> DdzRank.FIVE
            "6" -> DdzRank.SIX
            "7" -> DdzRank.SEVEN
            "8" -> DdzRank.EIGHT
            "9" -> DdzRank.NINE
            "10" -> DdzRank.TEN
            "jack" -> DdzRank.JACK
            "queen" -> DdzRank.QUEEN
            "king" -> DdzRank.KING
            "ace" -> DdzRank.ACE
            "2" -> DdzRank.TWO
            else -> null
        }
        if (rank == null) return null
        return DdzCard(suit, rank)
    }

    // Full-screen camera with overlay bars
    Box(modifier = Modifier.fillMaxSize()) {
        // Camera fills entire screen
        CameraView(modifier = Modifier.fillMaxSize()) { stringCards ->
            rawDetections = stringCards
            val parsed = stringCards.mapNotNull { parseCard(it) }
            if (parsed.isNotEmpty()) {
                val updated = (detectedCards + parsed).distinct().take(17)
                if (updated != detectedCards) {
                    detectedCards = updated
                    detectedHands = DdzHandFinder.findAllHands(updated)
                }
            }
        }

        // Top title bar overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(Color.Black.copy(alpha = 0.55f))
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = "\u9504\u5927\u5730\u52a9\u624b",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterStart)
            )
            Text(
                text = "CDD-DDZ Helper",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }

        // Bottom nav bar overlay
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.65f))
                .navigationBarsPadding()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (rawDetections.isNotEmpty() || detectedCards.isNotEmpty()) {
                
                // Detected cards row
                Text(
                    "Detected: ${detectedCards.size} cards",
                    color = Color.Green,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(detectedCards) { card ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color.White.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = card.toString(),
                                color = Color.White,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Best hand suggestion
                if (detectedHands.isNotEmpty()) {
                    val bestHand = detectedHands.first()
                    Text(
                        "Best: ${bestHand::class.simpleName} - ${bestHand.cards.joinToString(", ")}",
                        color = Color.Yellow,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Clear button
                TextButton(onClick = {
                    detectedCards = emptyList()
                    detectedHands = emptyList()
                }) {
                    Text("Clear", color = Color.Red, fontSize = 14.sp)
                }
            } else {
                Text(
                    "Point camera at cards to detect...",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp
                )
            }
        }
    }
}
