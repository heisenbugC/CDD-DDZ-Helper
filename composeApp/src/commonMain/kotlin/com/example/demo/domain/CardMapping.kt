package com.example.demo.domain

/**
 * 54-Class Mapping Contract & Model Resolution.
 * Strictly adheres to ARCHITECTURE.md Section 6.2.
 *
 * Classes 0..51: Standard 52 poker cards (13 ranks x 4 suits).
 * Class 52: black_joker (小王 / 黑色小丑).
 * Class 53: red_joker (大王 / 彩色大牌小丑).
 */
object CardMapping {

    const val CLASS_BLACK_JOKER: Int = 52
    const val CLASS_RED_JOKER: Int = 53

    val YOLO_CLASS_NAMES: List<String> = listOf(
        "10_of_clubs",      // 0
        "10_of_diamonds",   // 1
        "3_of_hearts",      // 2
        "3_of_spades",      // 3
        "4_of_clubs",       // 4
        "4_of_diamonds",    // 5
        "4_of_hearts",      // 6
        "4_of_spades",      // 7
        "5_of_clubs",       // 8
        "5_of_diamonds",    // 9
        "5_of_hearts",      // 10
        "5_of_spades",      // 11
        "10_of_hearts",     // 12
        "6_of_clubs",       // 13
        "6_of_diamonds",    // 14
        "6_of_hearts",      // 15
        "6_of_spades",      // 16
        "7_of_clubs",       // 17
        "7_of_diamonds",    // 18
        "7_of_hearts",      // 19
        "7_of_spades",      // 20
        "8_of_clubs",       // 21
        "8_of_diamonds",    // 22
        "10_of_spades",     // 23
        "8_of_hearts",      // 24
        "8_of_spades",      // 25
        "9_of_clubs",       // 26
        "9_of_diamonds",    // 27
        "9_of_hearts",      // 28
        "9_of_spades",      // 29
        "ace_of_clubs",     // 30
        "ace_of_diamonds",  // 31
        "ace_of_hearts",    // 32
        "ace_of_spades",    // 33
        "2_of_clubs",       // 34
        "jack_of_clubs",    // 35
        "jack_of_diamonds", // 36
        "jack_of_hearts",   // 37
        "jack_of_spades",   // 38
        "king_of_clubs",    // 39
        "king_of_diamonds", // 40
        "king_of_hearts",   // 41
        "king_of_spades",   // 42
        "queen_of_clubs",   // 43
        "queen_of_diamonds",// 44
        "2_of_diamonds",    // 45
        "queen_of_hearts",  // 46
        "queen_of_spades",  // 47
        "2_of_hearts",      // 48
        "2_of_spades",      // 49
        "3_of_clubs",       // 50
        "3_of_diamonds",    // 51
        "black_joker",      // 52
        "red_joker"         // 53
    )

    private val ddzCardsByIndex: List<DdzCard> = YOLO_CLASS_NAMES.map { parseDdzLabel(it)!! }
    private val cddCardsByIndex: List<Card?> = YOLO_CLASS_NAMES.map { parseCddLabel(it) }

    private val ddzCardToIndexMap: Map<DdzCard, Int> = ddzCardsByIndex.mapIndexed { idx, card -> card to idx }.toMap()
    private val cddCardToIndexMap: Map<Card, Int> = cddCardsByIndex.mapIndexedNotNull { idx, card -> card?.let { it to idx } }.toMap()

    fun getLabel(classId: Int): String? {
        return YOLO_CLASS_NAMES.getOrNull(classId)
    }

    fun toDdzCard(classId: Int): DdzCard? {
        return ddzCardsByIndex.getOrNull(classId)
    }

    fun toCddCard(classId: Int): Card? {
        return cddCardsByIndex.getOrNull(classId)
    }

    fun toClassId(card: DdzCard): Int {
        return ddzCardToIndexMap[card] ?: -1
    }

    fun toClassId(card: Card): Int {
        return cddCardToIndexMap[card] ?: -1
    }

    fun toLabel(card: DdzCard): String? {
        val idx = toClassId(card)
        return if (idx >= 0) YOLO_CLASS_NAMES[idx] else null
    }

    fun toLabel(card: Card): String? {
        val idx = toClassId(card)
        return if (idx >= 0) YOLO_CLASS_NAMES[idx] else null
    }

    fun parseDdzLabel(rawLabel: String): DdzCard? {
        val normalized = rawLabel.trim().lowercase()
        if (normalized == "black_joker" || normalized == "black joker" || normalized == "52") {
            return DdzCard(DdzSuit.JOKER, DdzRank.BLACK_JOKER)
        }
        if (normalized == "red_joker" || normalized == "red joker" || normalized == "53") {
            return DdzCard(DdzSuit.JOKER, DdzRank.RED_JOKER)
        }

        val parts = normalized.split("_of_", " of ", "_")
        if (parts.size < 2) return null

        val rankStr = parts[0]
        val suitStr = parts.last()

        val rank = when (rankStr) {
            "3" -> DdzRank.THREE
            "4" -> DdzRank.FOUR
            "5" -> DdzRank.FIVE
            "6" -> DdzRank.SIX
            "7" -> DdzRank.SEVEN
            "8" -> DdzRank.EIGHT
            "9" -> DdzRank.NINE
            "10" -> DdzRank.TEN
            "j", "jack" -> DdzRank.JACK
            "q", "queen" -> DdzRank.QUEEN
            "k", "king" -> DdzRank.KING
            "a", "ace" -> DdzRank.ACE
            "2" -> DdzRank.TWO
            else -> return null
        }

        val suit = when (suitStr) {
            "diamonds", "diamond", "d" -> DdzSuit.DIAMOND
            "clubs", "club", "c" -> DdzSuit.CLUB
            "hearts", "heart", "h" -> DdzSuit.HEART
            "spades", "spade", "s" -> DdzSuit.SPADE
            else -> return null
        }

        return DdzCard(suit, rank)
    }

    fun parseCddLabel(rawLabel: String): Card? {
        val normalized = rawLabel.trim().lowercase()
        if (normalized.contains("joker") || normalized == "52" || normalized == "53") {
            return null
        }

        val parts = normalized.split("_of_", " of ", "_")
        if (parts.size < 2) return null

        val rankStr = parts[0]
        val suitStr = parts.last()

        val rank = when (rankStr) {
            "3" -> Rank.THREE
            "4" -> Rank.FOUR
            "5" -> Rank.FIVE
            "6" -> Rank.SIX
            "7" -> Rank.SEVEN
            "8" -> Rank.EIGHT
            "9" -> Rank.NINE
            "10" -> Rank.TEN
            "j", "jack" -> Rank.JACK
            "q", "queen" -> Rank.QUEEN
            "k", "king" -> Rank.KING
            "a", "ace" -> Rank.ACE
            "2" -> Rank.TWO
            else -> return null
        }

        val suit = when (suitStr) {
            "diamonds", "diamond", "d" -> Suit.DIAMOND
            "clubs", "club", "c" -> Suit.CLUB
            "hearts", "heart", "h" -> Suit.HEART
            "spades", "spade", "s" -> Suit.SPADE
            else -> return null
        }

        return Card(suit, rank)
    }
}
