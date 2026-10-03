package com.example.demo.rules

import com.example.demo.domain.Card

sealed class CddHand(val cards: List<Card>) : Comparable<CddHand> {
    abstract val keyCard: Card
    
    // Default comparator checks only hands of the same class type for now,
    // inter-type comparisons (like 5-card hands) are handled specifically.
    override fun compareTo(other: CddHand): Int {
        if (this::class != other::class) {
            throw IllegalArgumentException("Cannot directly compare different length/basic hand types.")
        }
        return this.keyCard.compareTo(other.keyCard)
    }
}

class Single(val card: Card) : CddHand(listOf(card)) {
    override val keyCard: Card = card
}

class Pair(val card1: Card, val card2: Card) : CddHand(listOf(card1, card2).sorted()) {
    init { require(card1.rank == card2.rank) { "Pair must have same rank" } }
    override val keyCard: Card = cards.maxOrNull()!! // Evaluates based on highest suit
}

class Trio(val card1: Card, val card2: Card, val card3: Card) : CddHand(listOf(card1, card2, card3).sorted()) {
    init { require(card1.rank == card2.rank && card2.rank == card3.rank) { "Trio must have same rank" } }
    override val keyCard: Card = cards.maxOrNull()!! // In Trio, rank is the same, just take max for ease
}

abstract class FiveCardHand(cards: List<Card>) : CddHand(cards) {
    init { require(cards.size == 5) { "Five card hand must have exactly 5 cards" } }
}

class Straight(cards: List<Card>) : FiveCardHand(cards) {
    override val keyCard: Card = cards.maxOrNull()!! 
}

class Flush(cards: List<Card>) : FiveCardHand(cards) {
    override val keyCard: Card = cards.maxOrNull()!!
}

class FullHouse(val trio: List<Card>, val pair: List<Card>) : FiveCardHand(trio + pair) {
    override val keyCard: Card = trio.maxOrNull()!! // Value based on Trio
}

class FourOfAKind(val quattro: List<Card>, val kicker: Card) : FiveCardHand(quattro + listOf(kicker)) {
    override val keyCard: Card = quattro.maxOrNull()!!
}

class StraightFlush(cards: List<Card>) : FiveCardHand(cards) {
    override val keyCard: Card = cards.maxOrNull()!!
}
