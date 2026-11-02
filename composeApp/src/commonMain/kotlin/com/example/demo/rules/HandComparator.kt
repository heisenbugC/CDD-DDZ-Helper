package com.example.demo.rules

enum class Variation {
    MAINLAND,
    HONG_KONG
}

object HandComparator {

    /**
     * Compares two 5-card hands based on regional CDD variation rules.
     *
     * Mainland: Straight Flush > Four of a Kind > Full House > Flush > Straight.
     * Hong Kong: Straight Flush > Four of a Kind > Full House > Straight > Flush.
     */
    fun compareFiveCardHands(
        hand1: FiveCardHand,
        hand2: FiveCardHand,
        variation: Variation = Variation.MAINLAND
    ): Int {
        val typeWeight1 = getFiveCardTypeWeight(hand1, variation)
        val typeWeight2 = getFiveCardTypeWeight(hand2, variation)
        
        if (typeWeight1 != typeWeight2) {
            return typeWeight1.compareTo(typeWeight2)
        }
        
        // Equal hand type comparison
        return when {
            hand1 is Flush && hand2 is Flush -> {
                if (variation == Variation.HONG_KONG) {
                    // In Hong Kong Big Two, Flush vs Flush compares suit first, then high rank
                    val suitDiff = hand1.keyCard.suit.weight.compareTo(hand2.keyCard.suit.weight)
                    if (suitDiff != 0) suitDiff else hand1.keyCard.rank.weight.compareTo(hand2.keyCard.rank.weight)
                } else {
                    // In Mainland rules, evaluated by highest rank, then suit
                    hand1.keyCard.compareTo(hand2.keyCard)
                }
            }
            hand1 is FullHouse && hand2 is FullHouse -> {
                // Evaluated strictly by Trio
                hand1.keyCard.rank.weight.compareTo(hand2.keyCard.rank.weight)
            }
            hand1 is FourOfAKind && hand2 is FourOfAKind -> {
                // Evaluated strictly by 4-rank (quartet)
                hand1.keyCard.rank.weight.compareTo(hand2.keyCard.rank.weight)
            }
            else -> {
                // Straight, StraightFlush: evaluated by highest card (rank, then suit)
                hand1.keyCard.compareTo(hand2.keyCard)
            }
        }
    }

    /**
     * General hand comparator supporting all CDD hand types.
     */
    fun compareHands(
        hand1: CddHand,
        hand2: CddHand,
        variation: Variation = Variation.MAINLAND
    ): Int {
        if (hand1 is FiveCardHand && hand2 is FiveCardHand) {
            return compareFiveCardHands(hand1, hand2, variation)
        }
        if (hand1::class != hand2::class) {
            throw IllegalArgumentException("Cannot compare hands of different types: ${hand1::class.simpleName} vs ${hand2::class.simpleName}")
        }
        return hand1.keyCard.compareTo(hand2.keyCard)
    }

    /**
     * Returns true if handToPlay beats currentHand according to variation rules.
     */
    fun canBeat(
        handToPlay: CddHand,
        currentHand: CddHand,
        variation: Variation = Variation.MAINLAND
    ): Boolean {
        return try {
            compareHands(handToPlay, currentHand, variation) > 0
        } catch (_: IllegalArgumentException) {
            false
        }
    }
    
    private fun getFiveCardTypeWeight(hand: FiveCardHand, variation: Variation): Int {
        return when (hand) {
            is StraightFlush -> 5
            is FourOfAKind -> 4
            is FullHouse -> 3
            is Flush -> if (variation == Variation.MAINLAND) 2 else 1
            is Straight -> if (variation == Variation.MAINLAND) 1 else 2
            else -> 0
        }
    }
}
