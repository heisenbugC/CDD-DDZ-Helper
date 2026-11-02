package com.example.demo.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import com.example.demo.domain.AppSettings
import com.example.demo.domain.DdzRole
import com.example.demo.domain.GameMode
import com.example.demo.guidance.AimingState

/**
 * NowInAndroid-inspired Dynamic Island Floating Bar.
 * Accessible, high-contrast floating pill displaying:
 * - Game mode (CDD / DDZ) and DDZ Role toggle
 * - Visual Centering Quality score (Q) with color-coded feedback
 * - Voice trigger microphone activity meter
 * - Settings shortcut
 */
@Composable
fun NowInAndroidIslandBar(
    settings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
    aimingState: AimingState,
    centeringScore: Float,
    isLocked: Boolean,
    cardCount: Int,
    soundLevel: Float,
    isListening: Boolean,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Centering score color transition
    val centeringColor by animateColorAsState(
        targetValue = when {
            centeringScore >= 0.85f -> Color(0xFF00E676) // Bright Green
            centeringScore >= 0.50f -> Color(0xFFFFD600) // Vibrant Yellow
            centeringScore > 0.05f -> Color(0xFFFF9100)  // Orange
            else -> Color(0xFF757575)                     // Inactive Gray
        },
        animationSpec = tween(250)
    )

    // Pulse animation for listening state
    val infiniteTransition = rememberInfiniteTransition()
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color(0xEE161A22), // High contrast dark slate
        shadowElevation = 8.dp,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(24.dp))
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append(if (settings.gameMode == GameMode.DOU_DI_ZHU) "当前模式: 斗地主" else "当前模式: 锄大地")
                    append(", 状态: ${if (isLocked) "已锁定 $cardCount 张手牌" else "正在扫描，居中得分 ${(centeringScore * 100).toInt()}%"}")
                }
            }
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Row 1: Game Mode Toggle, Centering Meter, and Settings Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Game Mode & Role Chips
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Game Mode Toggle
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF2979FF).copy(alpha = 0.22f),
                        border = BorderStroke(1.dp, Color(0xFF448AFF)),
                        modifier = Modifier
                            .defaultMinSize(minWidth = 56.dp, minHeight = 44.dp)
                            .clickable(role = Role.Button) {
                                val nextMode = if (settings.gameMode == GameMode.DOU_DI_ZHU) GameMode.CHO_DAI_DEE else GameMode.DOU_DI_ZHU
                                onSettingsChanged(settings.copy(gameMode = nextMode))
                            }
                            .semantics {
                                contentDescription = "切换游戏模式，当前: ${if (settings.gameMode == GameMode.DOU_DI_ZHU) "斗地主" else "锄大地"}。可点击或语音说'切换模式'、'锄大地'、'斗地主'"
                            }
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (settings.gameMode == GameMode.DOU_DI_ZHU) "🎴 斗地主" else "♠️ 锄大地",
                                color = Color(0xFF82B1FF),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // DDZ Role Toggle (if DDZ)
                    if (settings.gameMode == GameMode.DOU_DI_ZHU) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (settings.ddzRole == DdzRole.LANDLORD) Color(0xFFFF6D00).copy(alpha = 0.22f) else Color(0xFF00B0FF).copy(alpha = 0.22f),
                            border = BorderStroke(1.dp, if (settings.ddzRole == DdzRole.LANDLORD) Color(0xFFFF9100) else Color(0xFF40C4FF)),
                            modifier = Modifier
                                .defaultMinSize(minWidth = 56.dp, minHeight = 44.dp)
                                .clickable(role = Role.Button) {
                                    val nextRole = if (settings.ddzRole == DdzRole.LANDLORD) DdzRole.PEASANT else DdzRole.LANDLORD
                                    onSettingsChanged(settings.copy(ddzRole = nextRole))
                                }
                                .semantics {
                                    contentDescription = "切换身份，当前: ${if (settings.ddzRole == DdzRole.LANDLORD) "地主 (20张)" else "农民 (17张)"}。可点击或语音说'切换身份'、'地主'、'农民'"
                                }
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = if (settings.ddzRole == DdzRole.LANDLORD) "👑 地主" else "🌾 农民",
                                    color = if (settings.ddzRole == DdzRole.LANDLORD) Color(0xFFFFB74D) else Color(0xFF80D8FF),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Centering Quality & Status Indicator Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Centering Visual Ring / Bar
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Black.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, centeringColor.copy(alpha = 0.6f)),
                        modifier = Modifier.semantics {
                            contentDescription = "瞄准居中得分: ${(centeringScore * 100).toInt()}%"
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(centeringColor, CircleShape)
                            )
                            Text(
                                text = if (isLocked) "已锁定" else "${(centeringScore * 100).toInt()}%",
                                color = centeringColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    // Settings Button (Min 48dp touch target)
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .size(44.dp)
                            .semantics {
                                role = Role.Button
                                contentDescription = "打开设置"
                            }
                    ) {
                        Text(
                            text = "⚙️",
                            fontSize = 18.sp
                        )
                    }
                }
            }

            // Row 2: Status & Voice Command Audio Feedback
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    val statusDotColor = if (isListening) Color(0xFF00E676).copy(alpha = pulseAlpha) else Color.Gray
                    Box(
                        modifier = Modifier
                            .size((7 + soundLevel * 6).dp)
                            .background(statusDotColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            isLocked -> "🔒 手牌已锁定 ($cardCount 张) · 按音量键读牌"
                            aimingState == AimingState.STABILITY_COUNTDOWN -> "⏳ 保持稳定中... 正在锁定"
                            aimingState == AimingState.CARDS_CENTERED -> "🎯 卡牌已居中"
                            cardCount > 0 -> "📸 扫描到 $cardCount 张手牌 · 请居中对齐"
                            else -> "🔍 正在寻找卡牌... 请将手牌正对镜头"
                        },
                        color = if (isLocked) Color(0xFF69F0AE) else Color(0xFFE0E0E0),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
