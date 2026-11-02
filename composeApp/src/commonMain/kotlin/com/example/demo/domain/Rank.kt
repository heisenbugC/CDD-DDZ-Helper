package com.example.demo.domain

enum class Rank(val weight: Int, val label: String) {
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
    TWO(15, "2");

    companion object {
        fun fromWeight(weight: Int): Rank? = entries.firstOrNull { it.weight == weight }
        fun fromLabel(label: String): Rank? = entries.firstOrNull { it.label.equals(label, ignoreCase = true) }
    }
}
