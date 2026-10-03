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
    override fun compareTo(other: DdzCard): Int {
        // In DDZ suit has no impact on weight for logic comparison
        return this.rank.weight.compareTo(other.rank.weight)
    }
    
    override fun toString(): String {
        return if (suit == DdzSuit.JOKER) rank.label else "${suit.name.first()}${rank.label}"
    }
}
