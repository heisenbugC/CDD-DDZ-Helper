package com.example.demo.domain

enum class Suit(val weight: Int) {
    DIAMOND(1),
    CLUB(2),
    HEART(3),
    SPADE(4);
    
    companion object {
        val mostValuable = SPADE
        val leastValuable = DIAMOND

        fun fromWeight(weight: Int): Suit? = entries.firstOrNull { it.weight == weight }
    }
}
