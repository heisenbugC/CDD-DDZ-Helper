package com.example.demo.domain

enum class DdzSuit {
    DIAMOND, CLUB, HEART, SPADE, JOKER
}

enum class DdzRank(val weight: Int, val label: String) {
    THREE(3, "3"),
    FOUR(4, "4"),
    FIVE(5, "5"),
    SIX(6, "6"),
    SEVEN(7, "7"),
    EIGHT(8, "8"),
    NINE(9, "9"),
    TEN(10, "10"),
    JACK(11, "J"),
    QUEEN(12, "Q"),
    KING(13, "K"),
    ACE(14, "A"),
    TWO(15, "2"),
    BLACK_JOKER(16, "Black Joker"),
    RED_JOKER(17, "Red Joker")
}

data class DdzCard(val suit: DdzSuit, val rank: DdzRank) : Comparable<DdzCard> {
    /**
     * Natural Comparable ordering for DDZ:
     * Primary: Rank weight (3 < 4 ... < 2 < Black Joker < Red Joker).
     * Secondary: Suit ordinal (Diamond < Club < Heart < Spade < Joker) for stable unique ordering.
     */
    override fun compareTo(other: DdzCard): Int {
        val rankDiff = this.rank.weight.compareTo(other.rank.weight)
        if (rankDiff != 0) return rankDiff
        return this.suit.ordinal.compareTo(other.suit.ordinal)
    }
    
    override fun toString(): String {
        return if (suit == DdzSuit.JOKER) rank.label else "${suit.name.first()}${rank.label}"
    }

    companion object {
        fun fromClassId(classId: Int): DdzCard? = CardMapping.toDdzCard(classId)
        fun fromLabel(label: String): DdzCard? = CardMapping.parseDdzLabel(label)
    }
}
