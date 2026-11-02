package com.example.demo.rules

import com.example.demo.domain.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CddHandFinderTest {

    @Test
    fun testCardComparison() {
        val diamond3 = Card(Suit.DIAMOND, Rank.THREE)
        val spade3 = Card(Suit.SPADE, Rank.THREE)
        val diamond4 = Card(Suit.DIAMOND, Rank.FOUR)
        val spade2 = Card(Suit.SPADE, Rank.TWO)

        // Rank weight takes precedence: 3 < 4 < ... < 2
        assertTrue(diamond3 < diamond4)
        assertTrue(diamond4 < spade2)

        // Same rank: Suit weight: Diamond < Club < Heart < Spade
        assertTrue(diamond3 < spade3)
    }

    @Test
    fun testStrictlyConsecutiveStraightNoWrapping() {
        // Valid normal straight: 3-4-5-6-7
        val s34567 = listOf(
            Card(Suit.SPADE, Rank.THREE),
            Card(Suit.HEART, Rank.FOUR),
            Card(Suit.CLUB, Rank.FIVE),
            Card(Suit.DIAMOND, Rank.SIX),
            Card(Suit.SPADE, Rank.SEVEN)
        )
        assertTrue(HandFinder.isStrictlyConsecutive(s34567))

        // Valid straight: 10-J-Q-K-A
        val s10JQKA = listOf(
            Card(Suit.SPADE, Rank.TEN),
            Card(Suit.HEART, Rank.JACK),
            Card(Suit.CLUB, Rank.QUEEN),
            Card(Suit.DIAMOND, Rank.KING),
            Card(Suit.SPADE, Rank.ACE)
        )
        assertTrue(HandFinder.isStrictlyConsecutive(s10JQKA))

        // Valid straight: J-Q-K-A-2
        val sJQKA2 = listOf(
            Card(Suit.SPADE, Rank.JACK),
            Card(Suit.HEART, Rank.QUEEN),
            Card(Suit.CLUB, Rank.KING),
            Card(Suit.DIAMOND, Rank.ACE),
            Card(Suit.SPADE, Rank.TWO)
        )
        assertTrue(HandFinder.isStrictlyConsecutive(sJQKA2))

        // Rejected wrapping straight: A-2-3-4-5 (contains both 3 and 2)
        val wrappingA2345 = listOf(
            Card(Suit.SPADE, Rank.ACE),
            Card(Suit.HEART, Rank.TWO),
            Card(Suit.CLUB, Rank.THREE),
            Card(Suit.DIAMOND, Rank.FOUR),
            Card(Suit.SPADE, Rank.FIVE)
        )
        assertFalse(HandFinder.isStrictlyConsecutive(wrappingA2345))

        // Rejected wrapping straight: Q-K-A-2-3 (contains both 3 and 2)
        val wrappingQKA23 = listOf(
            Card(Suit.SPADE, Rank.QUEEN),
            Card(Suit.HEART, Rank.KING),
            Card(Suit.CLUB, Rank.ACE),
            Card(Suit.DIAMOND, Rank.TWO),
            Card(Suit.SPADE, Rank.THREE)
        )
        assertFalse(HandFinder.isStrictlyConsecutive(wrappingQKA23))
    }

    @Test
    fun testFullHouseEvaluatedStrictlyByTrio() {
        // Full house 1: 3 Kings + 2 Threes
        val trioK = listOf(
            Card(Suit.SPADE, Rank.KING),
            Card(Suit.HEART, Rank.KING),
            Card(Suit.CLUB, Rank.KING)
        )
        val pair3 = listOf(
            Card(Suit.SPADE, Rank.THREE),
            Card(Suit.HEART, Rank.THREE)
        )
        val fhK = FullHouse(trioK, pair3)

        // Full house 2: 3 Queens + 2 Twos (even though pair has 2s, trio of Queens is smaller than trio of Kings)
        val trioQ = listOf(
            Card(Suit.SPADE, Rank.QUEEN),
            Card(Suit.HEART, Rank.QUEEN),
            Card(Suit.CLUB, Rank.QUEEN)
        )
        val pair2 = listOf(
            Card(Suit.SPADE, Rank.TWO),
            Card(Suit.HEART, Rank.TWO)
        )
        val fhQ = FullHouse(trioQ, pair2)

        // fhK must beat fhQ because King trio > Queen trio
        assertTrue(HandComparator.compareFiveCardHands(fhK, fhQ) > 0)
        assertTrue(HandComparator.canBeat(fhK, fhQ))
        assertFalse(HandComparator.canBeat(fhQ, fhK))
    }

    @Test
    fun testFourOfAKindEvaluatedByQuattro() {
        val quattro9 = listOf(
            Card(Suit.SPADE, Rank.NINE),
            Card(Suit.HEART, Rank.NINE),
            Card(Suit.CLUB, Rank.NINE),
            Card(Suit.DIAMOND, Rank.NINE)
        )
        val kicker2 = Card(Suit.SPADE, Rank.TWO)
        val foak9 = FourOfAKind(quattro9, kicker2)

        val quattro8 = listOf(
            Card(Suit.SPADE, Rank.EIGHT),
            Card(Suit.HEART, Rank.EIGHT),
            Card(Suit.CLUB, Rank.EIGHT),
            Card(Suit.DIAMOND, Rank.EIGHT)
        )
        val kickerA = Card(Suit.SPADE, Rank.ACE)
        val foak8 = FourOfAKind(quattro8, kickerA)

        assertTrue(HandComparator.compareFiveCardHands(foak9, foak8) > 0)
    }

    @Test
    fun testMainlandVsHongKongVariation() {
        val straight = Straight(listOf(
            Card(Suit.SPADE, Rank.THREE),
            Card(Suit.HEART, Rank.FOUR),
            Card(Suit.CLUB, Rank.FIVE),
            Card(Suit.DIAMOND, Rank.SIX),
            Card(Suit.SPADE, Rank.SEVEN)
        ))

        val flush = Flush(listOf(
            Card(Suit.SPADE, Rank.THREE),
            Card(Suit.SPADE, Rank.FIVE),
            Card(Suit.SPADE, Rank.SEVEN),
            Card(Suit.SPADE, Rank.NINE),
            Card(Suit.SPADE, Rank.JACK)
        ))

        // Mainland: Flush > Straight
        assertTrue(HandComparator.compareFiveCardHands(flush, straight, Variation.MAINLAND) > 0)

        // Hong Kong: Straight > Flush
        assertTrue(HandComparator.compareFiveCardHands(straight, flush, Variation.HONG_KONG) > 0)
    }

    @Test
    fun testFindAllHandsInThirteenCardHand() {
        val cards = listOf(
            Card(Suit.SPADE, Rank.THREE),
            Card(Suit.HEART, Rank.THREE),
            Card(Suit.CLUB, Rank.THREE),
            Card(Suit.DIAMOND, Rank.THREE), // 4 of 3s!
            Card(Suit.SPADE, Rank.FOUR),
            Card(Suit.HEART, Rank.FIVE),
            Card(Suit.CLUB, Rank.SIX),
            Card(Suit.DIAMOND, Rank.SEVEN), // 3-4-5-6-7 straight
            Card(Suit.SPADE, Rank.TEN),
            Card(Suit.SPADE, Rank.JACK),
            Card(Suit.SPADE, Rank.QUEEN),
            Card(Suit.SPADE, Rank.KING),
            Card(Suit.SPADE, Rank.ACE) // 10-J-Q-K-A Spade Flush & Straight Flush!
        )

        val hands = HandFinder.findAllHands(cards)
        assertTrue(hands.filterIsInstance<Single>().size == 13)
        assertTrue(hands.filterIsInstance<FourOfAKind>().isNotEmpty())
        assertTrue(hands.filterIsInstance<StraightFlush>().isNotEmpty())
        assertTrue(hands.filterIsInstance<Straight>().isNotEmpty())
    }

    @Test
    fun testFullHouseOrderIndependence() {
        val interleaved = listOf(
            Card(Suit.SPADE, Rank.FIVE),
            Card(Suit.HEART, Rank.EIGHT),
            Card(Suit.CLUB, Rank.FIVE),
            Card(Suit.SPADE, Rank.EIGHT),
            Card(Suit.DIAMOND, Rank.EIGHT)
        )
        val fullHouses = HandFinder.findFullHouses(interleaved)
        assertEquals(1, fullHouses.size)
        assertEquals(Rank.EIGHT, fullHouses.first().trio.first().rank)
        assertEquals(Rank.FIVE, fullHouses.first().pair.first().rank)
    }

    @Test
    fun testGetBestTacticalHandReturnsFullHouseOverSinglePair() {
        val interleaved = listOf(
            Card(Suit.SPADE, Rank.FIVE),
            Card(Suit.HEART, Rank.EIGHT),
            Card(Suit.CLUB, Rank.FIVE),
            Card(Suit.SPADE, Rank.EIGHT),
            Card(Suit.DIAMOND, Rank.EIGHT)
        )
        val allHands = HandFinder.findAllHands(interleaved)
        val bestHand = HandFinder.getBestTacticalHand(allHands)
        assertTrue(bestHand is FullHouse, "Best tactical hand must be FullHouse, but got $bestHand")
        assertEquals(Rank.EIGHT, (bestHand as FullHouse).trio.first().rank)
    }

    @Test
    fun testCardBidirectionalConversion() {
        val cddCard = Card(Suit.SPADE, Rank.TWO)
        val ddzCard = cddCard.toDdzCard()
        assertEquals(DdzSuit.SPADE, ddzCard.suit)
        assertEquals(DdzRank.TWO, ddzCard.rank)

        val backToCdd = ddzCard.toCddCard()
        assertEquals(cddCard, backToCdd)

        // Test Joker returns null in CDD
        val blackJoker = DdzCard(DdzSuit.JOKER, DdzRank.BLACK_JOKER)
        assertNull(blackJoker.toCddCard())
        val redJoker = DdzCard(DdzSuit.JOKER, DdzRank.RED_JOKER)
        assertNull(redJoker.toCddCard())
    }

    @Test
    fun testThirteenCardScatteredFullHouseRecognition() {
        // User example: A, A, 2, 3, 4, 5, 6, 7, 5, J, J, 5, Q
        // Trio of 5s scattered at indices 5, 8, 11
        // Pair of Aces at indices 0, 1
        // Pair of Jacks at indices 9, 10
        val thirteenCards = listOf(
            Card(Suit.SPADE, Rank.ACE),      // 0: A
            Card(Suit.HEART, Rank.ACE),      // 1: A
            Card(Suit.DIAMOND, Rank.TWO),    // 2: 2
            Card(Suit.CLUB, Rank.THREE),     // 3: 3
            Card(Suit.SPADE, Rank.FOUR),     // 4: 4
            Card(Suit.DIAMOND, Rank.FIVE),   // 5: 5 (first 5)
            Card(Suit.HEART, Rank.SIX),      // 6: 6
            Card(Suit.CLUB, Rank.SEVEN),     // 7: 7
            Card(Suit.SPADE, Rank.FIVE),     // 8: 5 (second 5)
            Card(Suit.CLUB, Rank.JACK),      // 9: J
            Card(Suit.HEART, Rank.JACK),     // 10: J
            Card(Suit.HEART, Rank.FIVE),     // 11: 5 (third 5)
            Card(Suit.DIAMOND, Rank.QUEEN)   // 12: Q
        )

        val allHands = HandFinder.findAllHands(thirteenCards)
        val fullHouses = allHands.filterIsInstance<FullHouse>()
        
        // Must recognize 2 distinct Full Houses:
        // 1. Trio 5s + Pair of Aces (5-5-5-A-A)
        // 2. Trio 5s + Pair of Jacks (5-5-5-J-J)
        assertEquals(2, fullHouses.size, "Should find exactly 2 full houses from this 13-card hand")

        val fhWithAces = fullHouses.find { it.pair.first().rank == Rank.ACE }
        assertTrue(fhWithAces != null, "Must recognize Full House: Trio 5s with Pair of Aces")
        assertEquals(Rank.FIVE, fhWithAces!!.trio.first().rank)

        val fhWithJacks = fullHouses.find { it.pair.first().rank == Rank.JACK }
        assertTrue(fhWithJacks != null, "Must recognize Full House: Trio 5s with Pair of Jacks")
        assertEquals(Rank.FIVE, fhWithJacks!!.trio.first().rank)
    }
}
