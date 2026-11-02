package com.example.demo.domain

import com.example.demo.rules.Variation

enum class GameMode(val label: String, val maxCards: Int) {
    DOU_DI_ZHU("斗地主 (Dou Di Zhu)", 20),
    CHO_DAI_DEE("锄大地 (Big Two)", 13)
}

enum class DdzRole(val label: String, val cardsCount: Int) {
    PEASANT("农民 (17张)", 17),
    LANDLORD("地主 (20张)", 20)
}

enum class VoiceLanguage(val code: String, val label: String) {
    CHINESE("zh", "中文 (普通话)"),
    ENGLISH("en", "English"),
    CANTONESE("zh-HK", "粤语 (Cantonese)")
}

enum class VoiceDetailLevel(val label: String) {
    BRIEF("简要播报 (张数 + 最佳出牌)"),
    DETAILED("详细播报 (手牌组成 + 最佳出牌)")
}

data class AppSettings(
    val gameMode: GameMode = GameMode.DOU_DI_ZHU,
    val ddzRole: DdzRole = DdzRole.PEASANT,
    val cddVariation: Variation = Variation.MAINLAND,
    val audioFeedbackEnabled: Boolean = true,
    val hapticFeedbackEnabled: Boolean = true,
    val voiceTriggerEnabled: Boolean = true,
    val voiceLanguage: VoiceLanguage = VoiceLanguage.CHINESE,
    val voiceDetailLevel: VoiceDetailLevel = VoiceDetailLevel.BRIEF,
    val speechRate: Float = 1.0f,
    val speechPitch: Float = 1.0f,
    val autoReadOnFullHand: Boolean = false
)
