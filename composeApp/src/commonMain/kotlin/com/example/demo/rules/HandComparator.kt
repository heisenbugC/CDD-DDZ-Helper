package com.example.demo.rules

import com.example.demo.rules.Straight
import com.example.demo.rules.Flush
import com.example.demo.rules.FullHouse
import com.example.demo.rules.FourOfAKind
import com.example.demo.rules.StraightFlush
import com.example.demo.rules.FiveCardHand

enum class Variation { MAINLAND, HONG_KONG }

object HandComparator {
    fun compareFiveCardHands(hand1: FiveCardHand, hand2: FiveCardHand, variation: Variation): Int {
        val typeWeight1 = getWeight(hand1, variation)
        val typeWeight2 = getWeight(hand2, variation)
        
        if (typeWeight1 != typeWeight2) {
            return typeWeight1.compareTo(typeWeight2)
        }
        
        return hand1.keyCard.compareTo(hand2.keyCard)
    }
    
    private fun getWeight(hand: FiveCardHand, variation: Variation): Int {
        return when (hand) {
            is Straight -> if (variation == Variation.MAINLAND) 1 else 2
            is Flush -> if (variation == Variation.MAINLAND) 2 else 1
            is FullHouse -> 3
            is FourOfAKind -> 4
            is StraightFlush -> 5
            else -> 0
        }
    }
}
