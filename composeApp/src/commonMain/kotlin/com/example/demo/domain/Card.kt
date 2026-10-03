package com.example.demo.domain

data class Card(val suit: Suit, val rank: Rank) : Comparable<Card> {
    /**
     * Compares based on CDD rules: First by rank, then by suit.
     */
    override fun compareTo(other: Card): Int {
        val rankDiff = this.rank.weight.compareTo(other.rank.weight)
        if (rankDiff != 0) return rankDiff
        return this.suit.weight.compareTo(other.suit.weight)
    }
    
    override fun toString(): String = "${suit.name.first()}${rank.label}"
}
