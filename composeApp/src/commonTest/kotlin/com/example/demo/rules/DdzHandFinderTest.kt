package com.example.demo.rules

import com.example.demo.domain.DdzCard
import com.example.demo.domain.DdzRank
import com.example.demo.domain.DdzSuit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DdzHandFinderTest {

    @Test
    fun testRocketBeatsAll() {
        val rocket = DdzRocket(listOf(
            DdzCard(DdzSuit.JOKER, DdzRank.BLACK_JOKER),
            DdzCard(DdzSuit.JOKER, DdzRank.RED_JOKER)
        ))

        val bomb2 = DdzBomb(listOf(
            DdzCard(DdzSuit.SPADE, DdzRank.TWO),
            DdzCard(DdzSuit.HEART, DdzRank.TWO),
            DdzCard(DdzSuit.CLUB, DdzRank.TWO),
            DdzCard(DdzSuit.DIAMOND, DdzRank.TWO)
        ))

        assertTrue(rocket > bomb2)
        assertTrue(rocket.canBeat(bomb2))
        assertFalse(bomb2.canBeat(rocket))
    }

    @Test
    fun testBombComparisons() {
        val bombA = DdzBomb(listOf(
            DdzCard(DdzSuit.SPADE, DdzRank.ACE),
            DdzCard(DdzSuit.HEART, DdzRank.ACE),
            DdzCard(DdzSuit.CLUB, DdzRank.ACE),
            DdzCard(DdzSuit.DIAMOND, DdzRank.ACE)
        ))

        val bombK = DdzBomb(listOf(
            DdzCard(DdzSuit.SPADE, DdzRank.KING),
            DdzCard(DdzSuit.HEART, DdzRank.KING),
            DdzCard(DdzSuit.CLUB, DdzRank.KING),
            DdzCard(DdzSuit.DIAMOND, DdzRank.KING)
        ))

        val pair2 = DdzPair(
            DdzCard(DdzSuit.SPADE, DdzRank.TWO),
            DdzCard(DdzSuit.HEART, DdzRank.TWO)
        )

        // Bomb beats non-bomb regardless of card rank
        assertTrue(bombK > pair2)
        assertTrue(bombK.canBeat(pair2))

        // Bigger bomb beats smaller bomb
        assertTrue(bombA > bombK)
        assertTrue(bombA.canBeat(bombK))
        assertFalse(bombK.canBeat(bombA))
    }

    @Test
    fun testStraightsAndConsecutivePairs() {
        // 5-card straight: 7-8-9-10-J
        val cards = listOf(
            DdzCard(DdzSuit.SPADE, DdzRank.SEVEN),
            DdzCard(DdzSuit.HEART, DdzRank.EIGHT),
            DdzCard(DdzSuit.CLUB, DdzRank.NINE),
            DdzCard(DdzSuit.DIAMOND, DdzRank.TEN),
            DdzCard(DdzSuit.SPADE, DdzRank.JACK)
        )
        val hands = DdzHandFinder.findAllHands(cards)
        val straights = hands.filterIsInstance<DdzStraight>()
        assertEquals(1, straights.size)
        assertEquals(DdzRank.JACK, straights.first().keyCard.rank)

        // Sister pairs (3 consecutive pairs): 55-66-77
        val pairCards = listOf(
            DdzCard(DdzSuit.SPADE, DdzRank.FIVE), DdzCard(DdzSuit.HEART, DdzRank.FIVE),
            DdzCard(DdzSuit.SPADE, DdzRank.SIX), DdzCard(DdzSuit.HEART, DdzRank.SIX),
            DdzCard(DdzSuit.SPADE, DdzRank.SEVEN), DdzCard(DdzSuit.HEART, DdzRank.SEVEN)
        )
        val sisterHands = DdzHandFinder.findAllHands(pairCards)
        val sisterPairs = sisterHands.filterIsInstance<DdzConsecutivePairs>()
        assertTrue(sisterPairs.isNotEmpty())
        assertEquals(3, sisterPairs.first().pairCount)
    }

    @Test
    fun testAirplanesWithAndWithoutWings() {
        // 777-888 (2 consecutive trios) + 2 singles (3 and 4)
        val cards = listOf(
            DdzCard(DdzSuit.SPADE, DdzRank.SEVEN), DdzCard(DdzSuit.HEART, DdzRank.SEVEN), DdzCard(DdzSuit.CLUB, DdzRank.SEVEN),
            DdzCard(DdzSuit.SPADE, DdzRank.EIGHT), DdzCard(DdzSuit.HEART, DdzRank.EIGHT), DdzCard(DdzSuit.CLUB, DdzRank.EIGHT),
            DdzCard(DdzSuit.SPADE, DdzRank.THREE),
            DdzCard(DdzSuit.DIAMOND, DdzRank.FOUR)
        )

        val hands = DdzHandFinder.findAllHands(cards)
        val airplanes = hands.filterIsInstance<DdzAirplane>()
        assertTrue(airplanes.isNotEmpty())

        val pureAirplane = airplanes.find { !it.hasWings }
        assertTrue(pureAirplane != null)
        assertEquals(6, pureAirplane.cards.size)

        val wingedAirplane = airplanes.find { it.hasSingleWings }
        assertTrue(wingedAirplane != null)
        assertEquals(8, wingedAirplane.cards.size)
    }

    @Test
    fun testSeventeenCardPeasantHand() {
        val cards = listOf(
            // Rocket
            DdzCard(DdzSuit.JOKER, DdzRank.BLACK_JOKER),
            DdzCard(DdzSuit.JOKER, DdzRank.RED_JOKER),
            // 4 Twos (Bomb)
            DdzCard(DdzSuit.SPADE, DdzRank.TWO),
            DdzCard(DdzSuit.HEART, DdzRank.TWO),
            DdzCard(DdzSuit.CLUB, DdzRank.TWO),
            DdzCard(DdzSuit.DIAMOND, DdzRank.TWO),
            // Consecutive trios 999-101010
            DdzCard(DdzSuit.SPADE, DdzRank.NINE),
            DdzCard(DdzSuit.HEART, DdzRank.NINE),
            DdzCard(DdzSuit.CLUB, DdzRank.NINE),
            DdzCard(DdzSuit.SPADE, DdzRank.TEN),
            DdzCard(DdzSuit.HEART, DdzRank.TEN),
            DdzCard(DdzSuit.CLUB, DdzRank.TEN),
            // Straight 3-4-5-6-7
            DdzCard(DdzSuit.SPADE, DdzRank.THREE),
            DdzCard(DdzSuit.HEART, DdzRank.FOUR),
            DdzCard(DdzSuit.CLUB, DdzRank.FIVE),
            DdzCard(DdzSuit.DIAMOND, DdzRank.SIX),
            DdzCard(DdzSuit.SPADE, DdzRank.SEVEN)
        )
        assertEquals(17, cards.size)

        val hands = DdzHandFinder.findAllHands(cards)
        assertTrue(hands.filterIsInstance<DdzRocket>().isNotEmpty())
        assertTrue(hands.filterIsInstance<DdzBomb>().isNotEmpty())
        assertTrue(hands.filterIsInstance<DdzAirplane>().isNotEmpty())
        assertTrue(hands.filterIsInstance<DdzStraight>().isNotEmpty())

        val bestHand = DdzHandFinder.getBestTacticalHand(hands)
        assertTrue(bestHand is DdzRocket, "Best hand in 17-card hand containing Rocket must be DdzRocket, but got $bestHand")
    }

    @Test
    fun testThirteenCardScatteredTrioWithPairRecognition() {
        // User example in DDZ: A, A, 2, 3, 4, 5, 6, 7, 5, J, J, 5, Q
        // Trio of 5s scattered at indices 5, 8, 11
        // Pair of Aces at indices 0, 1
        // Pair of Jacks at indices 9, 10
        val thirteenCards = listOf(
            DdzCard(DdzSuit.SPADE, DdzRank.ACE),      // 0: A
            DdzCard(DdzSuit.HEART, DdzRank.ACE),      // 1: A
            DdzCard(DdzSuit.DIAMOND, DdzRank.TWO),    // 2: 2
            DdzCard(DdzSuit.CLUB, DdzRank.THREE),     // 3: 3
            DdzCard(DdzSuit.SPADE, DdzRank.FOUR),     // 4: 4
            DdzCard(DdzSuit.DIAMOND, DdzRank.FIVE),   // 5: 5
            DdzCard(DdzSuit.HEART, DdzRank.SIX),      // 6: 6
            DdzCard(DdzSuit.CLUB, DdzRank.SEVEN),     // 7: 7
            DdzCard(DdzSuit.SPADE, DdzRank.FIVE),     // 8: 5
            DdzCard(DdzSuit.CLUB, DdzRank.JACK),      // 9: J
            DdzCard(DdzSuit.HEART, DdzRank.JACK),     // 10: J
            DdzCard(DdzSuit.HEART, DdzRank.FIVE),     // 11: 5
            DdzCard(DdzSuit.DIAMOND, DdzRank.QUEEN)   // 12: Q
        )

        val allHands = DdzHandFinder.findAllHands(thirteenCards)
        val triosWithPair = allHands.filterIsInstance<DdzTrioWithPair>()
        
        // Must recognize both TrioWithPair combos:
        // 1. Trio 5s with Pair of Aces
        // 2. Trio 5s with Pair of Jacks
        assertEquals(2, triosWithPair.size, "Should find exactly 2 trios with pair from this 13-card hand")

        val twpAces = triosWithPair.find { it.kickerPair.first().rank == DdzRank.ACE }
        assertTrue(twpAces != null, "Must recognize Trio 5s with Pair of Aces")
        assertEquals(DdzRank.FIVE, twpAces!!.keyCard.rank)

        val twpJacks = triosWithPair.find { it.kickerPair.first().rank == DdzRank.JACK }
        assertTrue(twpJacks != null, "Must recognize Trio 5s with Pair of Jacks")
        assertEquals(DdzRank.FIVE, twpJacks!!.keyCard.rank)
    }
}
