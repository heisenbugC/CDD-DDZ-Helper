package com.example.demo.domain

data class Card(val suit: Suit, val rank: Rank) : Comparable<Card> {
    /**
     * Compares based on CDD rules: First by rank, then by suit.
     * Natural ordering: Rank weight (3 < 4 ... < K < A < 2), then Suit (Diamond < Club < Heart < Spade).
     */
    override fun compareTo(other: Card): Int {
        val rankDiff = this.rank.weight.compareTo(other.rank.weight)
        if (rankDiff != 0) return rankDiff
        return this.suit.weight.compareTo(other.suit.weight)
    }
    
    override fun toString(): String = "${suit.name.first()}${rank.label}"

    companion object {
        fun fromClassId(classId: Int): Card? = CardMapping.toCddCard(classId)
        fun fromLabel(label: String): Card? = CardMapping.parseCddLabel(label)
    }
}

fun DdzCard.toCddCard(): Card? {
    if (this.suit == DdzSuit.JOKER) return null
    val cddSuit = when (this.suit) {
        DdzSuit.SPADE -> Suit.SPADE
        DdzSuit.HEART -> Suit.HEART
        DdzSuit.CLUB -> Suit.CLUB
        DdzSuit.DIAMOND -> Suit.DIAMOND
        DdzSuit.JOKER -> return null
    }
    val cddRank = when (this.rank) {
        DdzRank.THREE -> Rank.THREE
        DdzRank.FOUR -> Rank.FOUR
        DdzRank.FIVE -> Rank.FIVE
        DdzRank.SIX -> Rank.SIX
        DdzRank.SEVEN -> Rank.SEVEN
        DdzRank.EIGHT -> Rank.EIGHT
        DdzRank.NINE -> Rank.NINE
        DdzRank.TEN -> Rank.TEN
        DdzRank.JACK -> Rank.JACK
        DdzRank.QUEEN -> Rank.QUEEN
        DdzRank.KING -> Rank.KING
        DdzRank.ACE -> Rank.ACE
        DdzRank.TWO -> Rank.TWO
        DdzRank.BLACK_JOKER, DdzRank.RED_JOKER -> return null
    }
    return Card(cddSuit, cddRank)
}

fun Card.toDdzCard(): DdzCard {
    val ddzSuit = when (this.suit) {
        Suit.SPADE -> DdzSuit.SPADE
        Suit.HEART -> DdzSuit.HEART
        Suit.CLUB -> DdzSuit.CLUB
        Suit.DIAMOND -> DdzSuit.DIAMOND
    }
    val ddzRank = when (this.rank) {
        Rank.THREE -> DdzRank.THREE
        Rank.FOUR -> DdzRank.FOUR
        Rank.FIVE -> DdzRank.FIVE
        Rank.SIX -> DdzRank.SIX
        Rank.SEVEN -> DdzRank.SEVEN
        Rank.EIGHT -> DdzRank.EIGHT
        Rank.NINE -> DdzRank.NINE
        Rank.TEN -> DdzRank.TEN
        Rank.JACK -> DdzRank.JACK
        Rank.QUEEN -> DdzRank.QUEEN
        Rank.KING -> DdzRank.KING
        Rank.ACE -> DdzRank.ACE
        Rank.TWO -> DdzRank.TWO
    }
    return DdzCard(ddzSuit, ddzRank)
}

