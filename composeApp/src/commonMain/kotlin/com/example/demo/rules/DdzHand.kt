package com.example.demo.rules

import com.example.demo.domain.DdzCard
import com.example.demo.domain.DdzRank

sealed class DdzHand(val cards: List<DdzCard>) : Comparable<DdzHand> {
    abstract val keyCard: DdzCard
    
    override fun compareTo(other: DdzHand): Int {
        if (this is DdzRocket && other is DdzRocket) return 0
        if (this is DdzRocket) return 1
        if (other is DdzRocket) return -1
        
        if (this is DdzBomb && other is DdzBomb) {
            return this.keyCard.rank.weight.compareTo(other.keyCard.rank.weight)
        }
        if (this is DdzBomb) return 1
        if (other is DdzBomb) return -1
        
        if (this::class != other::class) {
            throw IllegalArgumentException("Cannot compare different DDZ hand types directly unless one is a Bomb or Rocket.")
        }
        if (this.cards.size != other.cards.size) {
            throw IllegalArgumentException("Cannot compare hands of different lengths: ${this.cards.size} vs ${other.cards.size}")
        }
        if (this is DdzAirplane && other is DdzAirplane) {
            if (this.trioCount != other.trioCount || this.kickers.size != other.kickers.size) {
                throw IllegalArgumentException("Cannot compare airplanes of different trio counts or wing configurations.")
            }
        }
        return this.keyCard.rank.weight.compareTo(other.keyCard.rank.weight)
    }

    fun canBeat(other: DdzHand): Boolean {
        return try {
            this > other
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as DdzHand
        return cards.toSet() == other.cards.toSet()
    }

    override fun hashCode(): Int {
        return cards.toSet().hashCode()
    }

    override fun toString(): String {
        return "${this::class.simpleName}(${cards.joinToString(", ")})"
    }
}

class DdzSingle(val card: DdzCard) : DdzHand(listOf(card)) {
    override val keyCard: DdzCard = card
}

class DdzPair(val card1: DdzCard, val card2: DdzCard) : DdzHand(listOf(card1, card2)) {
    init {
        require(card1.rank == card2.rank) { "DdzPair cards must have same rank: ${card1.rank} vs ${card2.rank}" }
    }
    override val keyCard: DdzCard = card1
}

class DdzTrio(val trioCards: List<DdzCard>) : DdzHand(trioCards) {
    init {
        require(trioCards.size == 3) { "DdzTrio must contain exactly 3 cards" }
        require(trioCards.all { it.rank == trioCards.first().rank }) { "All trio cards must have same rank" }
    }
    override val keyCard: DdzCard = trioCards.first()
}

class DdzTrioWithSingle(val trioCards: List<DdzCard>, val kicker: DdzCard) : DdzHand(trioCards + kicker) {
    init {
        require(trioCards.size == 3) { "Trio must have 3 cards" }
        require(trioCards.all { it.rank == trioCards.first().rank }) { "All trio cards must have same rank" }
        require(kicker.rank != trioCards.first().rank) { "Kicker must have different rank than trio" }
    }
    override val keyCard: DdzCard = trioCards.first()
}

class DdzTrioWithPair(val trioCards: List<DdzCard>, val kickerPair: List<DdzCard>) : DdzHand(trioCards + kickerPair) {
    init {
        require(trioCards.size == 3) { "Trio must have 3 cards" }
        require(kickerPair.size == 2) { "Kicker pair must have 2 cards" }
        require(trioCards.all { it.rank == trioCards.first().rank }) { "All trio cards must have same rank" }
        require(kickerPair.all { it.rank == kickerPair.first().rank }) { "All pair cards must have same rank" }
        require(kickerPair.first().rank != trioCards.first().rank) { "Kicker pair must have different rank than trio" }
    }
    override val keyCard: DdzCard = trioCards.first()
}

class DdzStraight(cards: List<DdzCard>) : DdzHand(cards.sortedBy { it.rank.weight }) {
    init {
        require(cards.size >= 5) { "DdzStraight must have at least 5 cards" }
        require(cards.all { it.rank.weight <= DdzRank.ACE.weight }) { "Straight cannot contain 2 or Jokers" }
    }
    override val keyCard: DdzCard = cards.maxByOrNull { it.rank.weight }!!
}

class DdzConsecutivePairs(cards: List<DdzCard>) : DdzHand(cards.sortedBy { it.rank.weight }) {
    init {
        require(cards.size >= 6 && cards.size % 2 == 0) { "Consecutive pairs must have at least 3 pairs (6+ cards)" }
        require(cards.all { it.rank.weight <= DdzRank.ACE.weight }) { "Consecutive pairs cannot contain 2 or Jokers" }
    }
    val pairCount: Int get() = cards.size / 2
    override val keyCard: DdzCard = cards.maxByOrNull { it.rank.weight }!!
}

class DdzAirplane(val trios: List<DdzCard>, val kickers: List<DdzCard> = emptyList()) : DdzHand(trios + kickers) {
    init {
        require(trios.size >= 6 && trios.size % 3 == 0) { "Airplane must have at least 2 trios (6+ trio cards)" }
        require(trios.all { it.rank.weight <= DdzRank.ACE.weight }) { "Airplane trios cannot contain 2 or Jokers" }
    }
    val trioCount: Int get() = trios.size / 3
    val hasWings: Boolean get() = kickers.isNotEmpty()
    val hasPairWings: Boolean get() = kickers.size == trioCount * 2
    val hasSingleWings: Boolean get() = kickers.size == trioCount
    override val keyCard: DdzCard = trios.maxByOrNull { it.rank.weight }!!
}

class DdzFourWithTwoSingles(val quattro: List<DdzCard>, val kickers: List<DdzCard>) : DdzHand(quattro + kickers) {
    init {
        require(quattro.size == 4) { "Quattro must have 4 cards" }
        require(kickers.size == 2) { "Four with two singles must have 2 kickers" }
        require(quattro.all { it.rank == quattro.first().rank }) { "All quattro cards must have same rank" }
    }
    override val keyCard: DdzCard = quattro.first()
}

class DdzFourWithTwoPairs(val quattro: List<DdzCard>, val kickerPairs: List<DdzCard>) : DdzHand(quattro + kickerPairs) {
    init {
        require(quattro.size == 4) { "Quattro must have 4 cards" }
        require(kickerPairs.size == 4) { "Four with two pairs must have 4 kicker cards (2 pairs)" }
        require(quattro.all { it.rank == quattro.first().rank }) { "All quattro cards must have same rank" }
    }
    override val keyCard: DdzCard = quattro.first()
}

class DdzBomb(val quattro: List<DdzCard>) : DdzHand(quattro) {
    init {
        require(quattro.size == 4) { "Bomb must have 4 cards" }
        require(quattro.all { it.rank == quattro.first().rank }) { "All bomb cards must have same rank" }
    }
    override val keyCard: DdzCard = quattro.first()
}

class DdzRocket(jokers: List<DdzCard>) : DdzHand(jokers.sortedBy { it.rank.weight }) {
    init {
        require(jokers.size == 2) { "Rocket must have exactly 2 jokers" }
        val hasBlack = jokers.any { it.rank == DdzRank.BLACK_JOKER }
        val hasRed = jokers.any { it.rank == DdzRank.RED_JOKER }
        require(hasBlack && hasRed) { "Rocket must contain both Black Joker and Red Joker" }
    }
    override val keyCard: DdzCard = jokers.maxByOrNull { it.rank.weight }!!
}
