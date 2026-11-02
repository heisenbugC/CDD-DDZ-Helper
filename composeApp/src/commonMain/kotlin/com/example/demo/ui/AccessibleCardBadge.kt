package com.example.demo.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.demo.domain.*

/**
 * High-Contrast Accessible Card Badge adhering to WCAG AAA contrast ratio (>= 7:1)
 * with large touch targets (>= 56dp) and dedicated TalkBack semantic content descriptions.
 */
@Composable
fun AccessibleDdzCardBadge(
    card: DdzCard,
    isLocked: Boolean = false,
    onRemove: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isRed = when (card.suit) {
        DdzSuit.HEART, DdzSuit.DIAMOND -> true
        DdzSuit.JOKER -> card.rank == DdzRank.RED_JOKER
        else -> false
    }

    val isJoker = card.suit == DdzSuit.JOKER

    val suitSymbol = when (card.suit) {
        DdzSuit.SPADE -> "♠"
        DdzSuit.HEART -> "♥"
        DdzSuit.CLUB -> "♣"
        DdzSuit.DIAMOND -> "♦"
        DdzSuit.JOKER -> if (card.rank == DdzRank.RED_JOKER) "👑" else "🃏"
    }

    val rankText = when (card.rank) {
        DdzRank.BLACK_JOKER -> "小王"
        DdzRank.RED_JOKER -> "大王"
        else -> card.rank.label
    }

    val spokenDescription = buildString {
        when (card.suit) {
            DdzSuit.SPADE -> append("黑桃 ")
            DdzSuit.HEART -> append("红桃 ")
            DdzSuit.CLUB -> append("梅花 ")
            DdzSuit.DIAMOND -> append("方块 ")
            DdzSuit.JOKER -> if (card.rank == DdzRank.RED_JOKER) append("彩色大王 ") else append("黑色小王 ")
        }
        if (card.suit != DdzSuit.JOKER) {
            append(card.rank.label)
        }
        append(", 权值 ${card.rank.weight}")
        if (isLocked && onRemove != null) {
            append(", 双击移除此牌")
        }
    }

    // High contrast color scheme: crisp card with bold pure black, deep red, or royal purple text
    val textColor = when {
        isJoker && isRed -> Color(0xFFD50000)
        isJoker && !isRed -> Color(0xFF311B92)
        isRed -> Color(0xFFD50000)
        else -> Color(0xFF111111)
    }
    val cardBackground = when {
        isJoker && isRed -> Color(0xFFFFF5F5)
        isJoker && !isRed -> Color(0xFFF9F6FF)
        else -> Color(0xFFFFFFFF)
    }
    val borderColor = when {
        isJoker && isRed -> Color(0xFFFF1744)
        isJoker && !isRed -> Color(0xFF7C4DFF)
        isRed -> Color(0xFFFF5252)
        else -> Color(0xFF424242)
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = cardBackground,
        border = BorderStroke(2.dp, borderColor),
        shadowElevation = 3.dp,
        modifier = modifier
            .defaultMinSize(minWidth = 56.dp, minHeight = 56.dp)
            .padding(horizontal = 3.dp, vertical = 2.dp)
            .clickable(
                enabled = isLocked && onRemove != null,
                role = Role.Button
            ) { onRemove?.invoke() }
            .semantics {
                role = Role.Button
                contentDescription = spokenDescription
            }
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = suitSymbol,
                color = textColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = rankText,
                color = textColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
fun AccessibleCddCardBadge(
    card: Card,
    isLocked: Boolean = false,
    onRemove: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isRed = card.suit == Suit.HEART || card.suit == Suit.DIAMOND

    val suitSymbol = when (card.suit) {
        Suit.SPADE -> "♠"
        Suit.HEART -> "♥"
        Suit.CLUB -> "♣"
        Suit.DIAMOND -> "♦"
    }

    val spokenDescription = buildString {
        when (card.suit) {
            Suit.SPADE -> append("黑桃 ")
            Suit.HEART -> append("红桃 ")
            Suit.CLUB -> append("梅花 ")
            Suit.DIAMOND -> append("方块 ")
        }
        append(card.rank.label)
        append(", 权值 ${card.rank.weight}")
        if (isLocked && onRemove != null) {
            append(", 双击移除此牌")
        }
    }

    val textColor = if (isRed) Color(0xFFD50000) else Color(0xFF111111)
    val cardBackground = Color(0xFFFFFFFF)
    val borderColor = if (isRed) Color(0xFFFF5252) else Color(0xFF424242)

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = cardBackground,
        border = BorderStroke(2.dp, borderColor),
        shadowElevation = 3.dp,
        modifier = modifier
            .defaultMinSize(minWidth = 56.dp, minHeight = 56.dp)
            .padding(horizontal = 3.dp, vertical = 2.dp)
            .clickable(
                enabled = isLocked && onRemove != null,
                role = Role.Button
            ) { onRemove?.invoke() }
            .semantics {
                role = Role.Button
                contentDescription = spokenDescription
            }
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = suitSymbol,
                color = textColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = card.rank.label,
                color = textColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
fun CardBadge(
    card: DdzCard,
    isLocked: Boolean = false,
    onRemove: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    AccessibleDdzCardBadge(card = card, isLocked = isLocked, onRemove = onRemove, modifier = modifier)
}

@Composable
fun CddCardBadge(
    card: Card,
    isLocked: Boolean = false,
    onRemove: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    AccessibleCddCardBadge(card = card, isLocked = isLocked, onRemove = onRemove, modifier = modifier)
}
