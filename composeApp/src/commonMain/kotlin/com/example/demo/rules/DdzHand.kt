package com.example.demo.rules

import com.example.demo.domain.DdzCard

sealed class DdzHand(val cards: List<DdzCard>) : Comparable<DdzHand> {
    abstract val keyCard: DdzCard
    
    override fun compareTo(other: DdzHand): Int {
        if (this is DdzRocket) return 1
        if (other is DdzRocket) return -1
        if (this is DdzBomb && other !is DdzBomb) return 1
        if (this !is DdzBomb && other is DdzBomb) return -1
        
        if (this::class != other::class) {
            throw IllegalArgumentException("Cannot compare different DDZ hand types directly unless one is a Bomb/Rocket.")
        }
        if (this.cards.size != other.cards.size) {
            throw IllegalArgumentException("Cannot compare hands of different lengths.")
        }
        return this.keyCard.compareTo(other.keyCard)
    }
}

class DdzSingle(val card: DdzCard) : DdzHand(listOf(card)) {
    override val keyCard = card
}

class DdzPair(val card1: DdzCard, val card2: DdzCard) : DdzHand(listOf(card1, card2)) {
    override val keyCard = card1
}

class DdzTrio(val trioCards: List<DdzCard>) : DdzHand(trioCards) {
    override val keyCard = trioCards.first()
}

class DdzTrioWithSingle(val trioCards: List<DdzCard>, val kicker: DdzCard) : DdzHand(trioCards + kicker) {
    override val keyCard = trioCards.first()
}

class DdzTrioWithPair(val trioCards: List<DdzCard>, val kickerPair: List<DdzCard>) : DdzHand(trioCards + kickerPair) {
    override val keyCard = trioCards.first()
}

class DdzStraight(cards: List<DdzCard>) : DdzHand(cards.sorted()) {
    override val keyCard = cards.maxByOrNull { it.rank.weight }!!
}

class DdzConsecutivePairs(cards: List<DdzCard>) : DdzHand(cards.sorted()) {
    override val keyCard = cards.maxByOrNull { it.rank.weight }!!
}

class DdzAirplane(val trios: List<DdzCard>, val kickers: List<DdzCard> = emptyList()) : DdzHand(trios + kickers) {
    override val keyCard = trios.maxByOrNull { it.rank.weight }!!
}

class DdzFourWithTwoSingles(val quattro: List<DdzCard>, val kickers: List<DdzCard>) : DdzHand(quattro + kickers) {
    override val keyCard = quattro.first()
}

class DdzFourWithTwoPairs(val quattro: List<DdzCard>, val kickerPairs: List<DdzCard>) : DdzHand(quattro + kickerPairs) {
    override val keyCard = quattro.first()
}

class DdzBomb(val quattro: List<DdzCard>) : DdzHand(quattro) {
    override val keyCard = quattro.first()
}

class DdzRocket(jokers: List<DdzCard>) : DdzHand(jokers) {
    override val keyCard = jokers.maxByOrNull { it.rank.weight }!!
}
