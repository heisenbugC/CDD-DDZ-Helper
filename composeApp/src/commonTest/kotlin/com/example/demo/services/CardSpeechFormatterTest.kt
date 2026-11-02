package com.example.demo.services

import com.example.demo.domain.Card
import com.example.demo.domain.DdzCard
import com.example.demo.domain.DdzRank
import com.example.demo.domain.DdzSuit
import com.example.demo.domain.Rank
import com.example.demo.domain.Suit
import com.example.demo.rules.*
import kotlin.test.Test
import kotlin.test.assertTrue

class CardSpeechFormatterTest {

    @Test
    fun testEmptyCardsHand() {
        val speechZh = CardSpeechFormatter.formatDdzSpeech(emptyList(), emptyList(), "zh")
        assertTrue(speechZh.contains("当前未识别到手牌"))

        val speechEn = CardSpeechFormatter.formatDdzSpeech(emptyList(), emptyList(), "en")
        assertTrue(speechEn.contains("No cards detected"))
    }

    @Test
    fun testDdzSpeechLockedCardCountHeader() {
        val cards = listOf(
            DdzCard(DdzSuit.SPADE, DdzRank.THREE),
            DdzCard(DdzSuit.HEART, DdzRank.FOUR),
            DdzCard(DdzSuit.CLUB, DdzRank.FIVE),
            DdzCard(DdzSuit.DIAMOND, DdzRank.SIX),
            DdzCard(DdzSuit.SPADE, DdzRank.SEVEN)
        )
        val hands = DdzHandFinder.findAllHands(cards)

        val speechZh = CardSpeechFormatter.formatDdzSpeech(cards, hands, "zh", detailed = false)
        assertTrue(speechZh.startsWith("已锁定 5 张手牌。"))

        val speechEn = CardSpeechFormatter.formatDdzSpeech(cards, hands, "en", detailed = false)
        assertTrue(speechEn.startsWith("5 cards locked."))
    }

    @Test
    fun testDdzDescendingComboPriorityAnnouncement() {
        // Hand containing Rocket, a Bomb of Aces, and some singles
        val cards = listOf(
            DdzCard(DdzSuit.JOKER, DdzRank.BLACK_JOKER),
            DdzCard(DdzSuit.JOKER, DdzRank.RED_JOKER),
            DdzCard(DdzSuit.SPADE, DdzRank.ACE),
            DdzCard(DdzSuit.HEART, DdzRank.ACE),
            DdzCard(DdzSuit.CLUB, DdzRank.ACE),
            DdzCard(DdzSuit.DIAMOND, DdzRank.ACE),
            DdzCard(DdzSuit.SPADE, DdzRank.THREE)
        )
        val hands = DdzHandFinder.findAllHands(cards)

        val speechZh = CardSpeechFormatter.formatDdzSpeech(cards, hands, "zh", detailed = true)
        // Verify Rocket appears before Bomb
        val rocketIndex = speechZh.indexOf("王炸")
        val bombIndex = speechZh.indexOf("炸弹")
        assertTrue(rocketIndex != -1)
        assertTrue(bombIndex != -1)
        assertTrue(rocketIndex < bombIndex, "Rocket must be announced before Bomb")

        // Top recommended play must be the Rocket!
        assertTrue(speechZh.contains("最佳推荐出牌：王炸"))
    }

    @Test
    fun testCddSpeechPriorityAnnouncement() {
        // Full house (3 Kings + 2 Threes) + Pair of 5s + single 2
        val cards = listOf(
            Card(Suit.SPADE, Rank.KING), Card(Suit.HEART, Rank.KING), Card(Suit.CLUB, Rank.KING),
            Card(Suit.SPADE, Rank.THREE), Card(Suit.HEART, Rank.THREE),
            Card(Suit.SPADE, Rank.FIVE), Card(Suit.HEART, Rank.FIVE),
            Card(Suit.SPADE, Rank.TWO)
        )
        val hands = HandFinder.findAllHands(cards)

        val speechZh = CardSpeechFormatter.formatCddSpeech(cards, hands, "zh", detailed = true)
        assertTrue(speechZh.startsWith("锄大地模式：已锁定 8 张手牌。"))

        val fhIndex = speechZh.indexOf("葫芦")
        val pairIndex = speechZh.indexOf("对子")
        assertTrue(fhIndex != -1)
        assertTrue(pairIndex != -1)
        assertTrue(fhIndex < pairIndex, "Full house must be announced before Pair")

        // Best play recommendation should be the Full House
        assertTrue(speechZh.contains("最佳出牌推荐：葫芦"))
    }

    @Test
    fun testCantoneseSupport() {
        val cards = listOf(
            DdzCard(DdzSuit.JOKER, DdzRank.BLACK_JOKER),
            DdzCard(DdzSuit.JOKER, DdzRank.RED_JOKER)
        )
        val hands = DdzHandFinder.findAllHands(cards)

        val speechHk = CardSpeechFormatter.formatDdzSpeech(cards, hands, "zh-HK", detailed = false)
        assertTrue(speechHk.startsWith("已鎖定 2 張手牌。"))
        assertTrue(speechHk.contains("最佳推薦出牌：王炸"))
    }

    @Test
    fun testCddSpeechHandsFallbackWhenCardsListEmpty() {
        val cards = listOf(
            Card(Suit.SPADE, Rank.KING), Card(Suit.HEART, Rank.KING), Card(Suit.CLUB, Rank.KING),
            Card(Suit.SPADE, Rank.THREE), Card(Suit.HEART, Rank.THREE)
        )
        val hands = HandFinder.findAllHands(cards)

        // Pass empty cards list but populated hands list
        val speech = CardSpeechFormatter.formatCddSpeech(emptyList(), hands, "zh", detailed = false)
        assertTrue(speech.startsWith("锄大地模式：已锁定 5 张手牌。"))
        assertTrue(speech.contains("最佳出牌推荐：葫芦"))
    }
}
