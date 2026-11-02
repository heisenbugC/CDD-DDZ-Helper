package com.example.demo.rules

import com.example.demo.domain.Card

sealed class CddHand(val cards: List<Card>) : Comparable<CddHand> {
    abstract val keyCard: Card
    
    // Default comparator checks hands of the same class type.
    // Inter-type comparisons between different 5-card hands are handled by HandComparator.
    override fun compareTo(other: CddHand): Int {
        if (this::class != other::class) {
            throw IllegalArgumentException("Cannot directly compare different basic hand types. Use HandComparator.compareHands.")
        }
        return this.keyCard.compareTo(other.keyCard)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as CddHand
        return cards.toSet() == other.cards.toSet()
    }

    override fun hashCode(): Int {
        return cards.toSet().hashCode()
    }

    override fun toString(): String {
        return "${this::class.simpleName}(${cards.joinToString(", ")})"
    }
}

class Single(val card: Card) : CddHand(listOf(card)) {
    override val keyCard: Card = card
}

class Pair(val card1: Card, val card2: Card) : CddHand(listOf(card1, card2).sorted()) {
    init {
        require(card1.rank == card2.rank) { "Pair must have same rank: ${card1.rank} vs ${card2.rank}" }
    }
    // Evaluates based on highest suit card
    override val keyCard: Card = cards.maxOrNull()!!
}

class Trio(val card1: Card, val card2: Card, val card3: Card) : CddHand(listOf(card1, card2, card3).sorted()) {
    init {
        require(card1.rank == card2.rank && card2.rank == card3.rank) {
            "Trio must have same rank: ${card1.rank}, ${card2.rank}, ${card3.rank}"
        }
    }
    override val keyCard: Card = cards.maxOrNull()!!
}

abstract class FiveCardHand(cards: List<Card>) : CddHand(cards.sorted()) {
    init {
        require(cards.size == 5) { "Five card hand must have exactly 5 cards, but got ${cards.size}" }
    }
}

class Straight(cards: List<Card>) : FiveCardHand(cards) {
    // Highest rank card (and suit) determines winning straight
    override val keyCard: Card = cards.maxOrNull()!!
}

class Flush(cards: List<Card>) : FiveCardHand(cards) {
    init {
        require(cards.map { it.suit }.distinct().size == 1) { "Flush must all have same suit" }
    }
    // Key card is highest ranked card in the flush
    override val keyCard: Card = cards.maxOrNull()!!
}

class FullHouse(val trio: List<Card>, val pair: List<Card>) : FiveCardHand(trio + pair) {
    init {
        require(trio.size == 3) { "FullHouse trio must have 3 cards" }
        require(pair.size == 2) { "FullHouse pair must have 2 cards" }
        require(trio.all { it.rank == trio.first().rank }) { "All trio cards must have same rank" }
        require(pair.all { it.rank == pair.first().rank }) { "All pair cards must have same rank" }
        require(trio.first().rank != pair.first().rank) { "Trio and pair must have different ranks" }
    }
    // Evaluated strictly by Trio
    override val keyCard: Card = trio.maxOrNull()!!
}

class FourOfAKind(val quattro: List<Card>, val kicker: Card) : FiveCardHand(quattro + listOf(kicker)) {
    init {
        require(quattro.size == 4) { "FourOfAKind quattro must have 4 cards" }
        require(quattro.all { it.rank == quattro.first().rank }) { "All quattro cards must have same rank" }
        require(kicker.rank != quattro.first().rank) { "Kicker must have different rank than quattro" }
    }
    // Evaluated strictly by 4-rank (quartet)
    override val keyCard: Card = quattro.maxOrNull()!!
}

class StraightFlush(cards: List<Card>) : FiveCardHand(cards) {
    init {
        require(cards.map { it.suit }.distinct().size == 1) { "StraightFlush must all have same suit" }
    }
    override val keyCard: Card = cards.maxOrNull()!!
}
