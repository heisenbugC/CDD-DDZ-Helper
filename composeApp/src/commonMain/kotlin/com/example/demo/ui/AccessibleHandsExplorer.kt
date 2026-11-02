package com.example.demo.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.example.demo.domain.Card
import com.example.demo.domain.DdzCard
import com.example.demo.domain.GameMode
import com.example.demo.rules.*
import com.example.demo.services.CardSpeechFormatter

/**
 * Accessible Hands Explorer Bottom Sheet.
 * Displays valid card combinations strictly ordered in descending priority order
 * (Rocket -> Bombs -> Airplanes -> Sister Pairs -> Straights -> Trios -> Pairs -> Singles).
 */
@Composable
fun AccessibleHandsExplorer(
    gameMode: GameMode,
    ddzHands: List<DdzHand>,
    cddHands: List<CddHand>,
    onSpeakHand: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf("全部") }

    val categories = if (gameMode == GameMode.DOU_DI_ZHU) {
        listOf("全部", "火箭/炸弹", "飞机/连对", "顺子", "三带", "对子", "单牌")
    } else {
        listOf("全部", "同花顺", "铁支", "葫芦", "同花", "顺子", "三条/对子", "单牌")
    }

    // Sort DDZ hands: most cards first, then by tactical priority, then by key rank
    val sortedDdzHands = remember(ddzHands) {
        ddzHands.sortedWith(
            compareByDescending<DdzHand> { it.cards.size }
                .thenBy { CardSpeechFormatter.ddzHandPriority(it) }
                .thenByDescending { it.keyCard.rank.weight }
        )
    }

    // Sort CDD hands: most cards first (5-card > 3-card > 2-card > 1-card), then by priority
    val sortedCddHands = remember(cddHands) {
        cddHands.sortedWith(
            compareByDescending<CddHand> { it.cards.size }
                .thenBy { CardSpeechFormatter.cddHandPriority(it) }
                .thenByDescending { it.keyCard.rank.weight }
        )
    }

    val filteredDdzHands = remember(selectedCategory, sortedDdzHands) {
        when (selectedCategory) {
            "火箭/炸弹" -> sortedDdzHands.filter { it is DdzRocket || it is DdzBomb }
            "飞机/连对" -> sortedDdzHands.filter { it is DdzAirplane || it is DdzConsecutivePairs }
            "顺子" -> sortedDdzHands.filter { it is DdzStraight }
            "三带" -> sortedDdzHands.filter { it is DdzTrio || it is DdzTrioWithSingle || it is DdzTrioWithPair || it is DdzFourWithTwoSingles || it is DdzFourWithTwoPairs }
            "对子" -> sortedDdzHands.filter { it is DdzPair }
            "单牌" -> sortedDdzHands.filter { it is DdzSingle }
            else -> sortedDdzHands
        }
    }

    val filteredCddHands = remember(selectedCategory, sortedCddHands) {
        when (selectedCategory) {
            "同花顺" -> sortedCddHands.filterIsInstance<StraightFlush>()
            "铁支" -> sortedCddHands.filterIsInstance<FourOfAKind>()
            "葫芦" -> sortedCddHands.filterIsInstance<FullHouse>()
            "同花" -> sortedCddHands.filterIsInstance<Flush>()
            "顺子" -> sortedCddHands.filterIsInstance<Straight>()
            "三条/对子" -> sortedCddHands.filter { it is Trio || it is Pair }
            "单牌" -> sortedCddHands.filterIsInstance<Single>()
            else -> sortedCddHands
        }
    }

    Surface(
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = Color(0xFF161822),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
        shadowElevation = 16.dp,
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Drag handle
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(5.dp)
                    .background(Color.White.copy(alpha = 0.35f), CircleShape)
                    .align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (gameMode == GameMode.DOU_DI_ZHU) "🃏 斗地主牌型库" else "♠️ 锄大地牌型库",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    val totalHands = if (gameMode == GameMode.DOU_DI_ZHU) filteredDdzHands.size else filteredCddHands.size
                    Text(
                        text = "共发现 $totalHands 种可用组合 (按组合规模降序)",
                        color = Color(0xFFB0BEC5),
                        fontSize = 12.sp
                    )
                }

                // Close Button with accessible min 48dp touch target
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.10f),
                    modifier = Modifier
                        .size(48.dp)
                        .clickable(role = Role.Button, onClick = onClose)
                        .semantics {
                            role = Role.Button
                            contentDescription = "关闭牌型浏览器"
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("✕", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Category Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { cat ->
                    val isSelected = cat == selectedCategory
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) Color(0xFF2979FF) else Color.White.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, if (isSelected) Color(0xFF82B1FF) else Color.Transparent),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 44.dp)
                            .clickable(role = Role.Tab) { selectedCategory = cat }
                            .semantics {
                                role = Role.Tab
                                contentDescription = "$cat 类别，${if (isSelected) "已选中" else "未选中"}"
                            }
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = cat,
                                color = if (isSelected) Color.White else Color(0xFFCFD8DC),
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Hands List
            if (gameMode == GameMode.DOU_DI_ZHU) {
                if (filteredDdzHands.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("暂无匹配该类别的斗地主牌型", color = Color.Gray, fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredDdzHands) { hand ->
                            DdzHandCardItem(
                                hand = hand,
                                onSpeak = { onSpeakHand(describeDdzHand(hand)) }
                            )
                        }
                    }
                }
            } else {
                if (filteredCddHands.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("暂无匹配该类别的锄大地牌型", color = Color.Gray, fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredCddHands) { hand ->
                            CddHandCardItem(
                                hand = hand,
                                onSpeak = { onSpeakHand(describeCddHand(hand)) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DdzHandCardItem(
    hand: DdzHand,
    onSpeak: () -> Unit
) {
    val handName = when (hand) {
        is DdzRocket -> "🚀 王炸 (双王)"
        is DdzBomb -> "💣 炸弹"
        is DdzAirplane -> "✈️ 飞机带翅膀"
        is DdzConsecutivePairs -> "👯 连对"
        is DdzStraight -> "📏 顺子"
        is DdzFourWithTwoSingles -> "💥 四带两单"
        is DdzFourWithTwoPairs -> "💥 四带两对"
        is DdzTrioWithPair -> "三带一对"
        is DdzTrioWithSingle -> "三带一"
        is DdzTrio -> "三张"
        is DdzPair -> "对子"
        is DdzSingle -> "单牌"
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF202330),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = handName,
                        color = Color(0xFF90CAF9),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White.copy(alpha = 0.08f)
                    ) {
                        Text(
                            text = "${hand.cards.size}张",
                            color = Color(0xFFB0BEC5),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Speak Button (>= 48dp touch target)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF2979FF).copy(alpha = 0.25f),
                    border = BorderStroke(1.dp, Color(0xFF448AFF)),
                    modifier = Modifier
                        .defaultMinSize(minWidth = 56.dp, minHeight = 44.dp)
                        .clickable(role = Role.Button, onClick = onSpeak)
                        .semantics {
                            role = Role.Button
                            contentDescription = "朗读牌型: $handName"
                        }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("🔊 朗读", color = Color(0xFF82B1FF), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Cards row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(hand.cards) { card ->
                    AccessibleDdzCardBadge(card = card, isLocked = false)
                }
            }
        }
    }
}

@Composable
private fun CddHandCardItem(
    hand: CddHand,
    onSpeak: () -> Unit
) {
    val handName = when (hand) {
        is StraightFlush -> "👑 同花顺"
        is FourOfAKind -> "💣 铁支 (四条)"
        is FullHouse -> "🏠 葫芦"
        is Flush -> "🌊 同花"
        is Straight -> "📏 顺子"
        is Trio -> "三条"
        is Pair -> "对子"
        is Single -> "单牌"
        else -> "五张手牌"
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF202330),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = handName,
                        color = Color(0xFF90CAF9),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White.copy(alpha = 0.08f)
                    ) {
                        Text(
                            text = "${hand.cards.size}张",
                            color = Color(0xFFB0BEC5),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF2979FF).copy(alpha = 0.25f),
                    border = BorderStroke(1.dp, Color(0xFF448AFF)),
                    modifier = Modifier
                        .defaultMinSize(minWidth = 56.dp, minHeight = 44.dp)
                        .clickable(role = Role.Button, onClick = onSpeak)
                        .semantics {
                            role = Role.Button
                            contentDescription = "朗读牌型: $handName"
                        }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("🔊 朗读", color = Color(0xFF82B1FF), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(hand.cards) { card ->
                    AccessibleCddCardBadge(card = card, isLocked = false)
                }
            }
        }
    }
}

private fun describeDdzHand(hand: DdzHand): String {
    return CardSpeechFormatter.describeDdzHand(hand)
}

private fun describeCddHand(hand: CddHand): String {
    return CardSpeechFormatter.describeCddHand(hand)
}
