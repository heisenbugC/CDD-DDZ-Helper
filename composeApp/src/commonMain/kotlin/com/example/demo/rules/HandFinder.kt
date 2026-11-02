package com.example.demo.rules

import com.example.demo.domain.Card
import com.example.demo.domain.Rank

object HandFinder {

    fun findAllHands(cards: List<Card>): List<CddHand> {
        val sortedCards = cards.sorted()
        val allHands = mutableListOf<CddHand>()
        
        allHands.addAll(findSingles(sortedCards))
        allHands.addAll(findPairs(sortedCards))
        allHands.addAll(findTrios(sortedCards))
        allHands.addAll(findStraights(sortedCards))
        allHands.addAll(findFlushes(sortedCards))
        allHands.addAll(findFullHouses(sortedCards))
        allHands.addAll(findFourOfAKinds(sortedCards))
        allHands.addAll(findStraightFlushes(sortedCards))
        
        return allHands
    }

    fun findSingles(cards: List<Card>): List<Single> = cards.map { Single(it) }

    fun findPairs(cards: List<Card>): List<Pair> {
        val pairs = mutableListOf<Pair>()
        for (i in 0 until cards.size) {
            for (j in i + 1 until cards.size) {
                if (cards[i].rank == cards[j].rank) {
                    pairs.add(Pair(cards[i], cards[j]))
                }
            }
        }
        return pairs
    }

    fun findTrios(cards: List<Card>): List<Trio> {
        val trios = mutableListOf<Trio>()
        for (i in 0 until cards.size) {
            for (j in i + 1 until cards.size) {
                for (k in j + 1 until cards.size) {
                    if (cards[i].rank == cards[j].rank && cards[j].rank == cards[k].rank) {
                        trios.add(Trio(cards[i], cards[j], cards[k]))
                    }
                }
            }
        }
        return trios
    }
    
    fun findStraights(cards: List<Card>): List<Straight> {
        val results = mutableListOf<Straight>()
        val combinations = getCombinations(cards, 5)
        for (combo in combinations) {
            if (isStrictlyConsecutive(combo) && !isSameSuit(combo)) {
                results.add(Straight(combo))
            }
        }
        return results
    }
    
    fun findFlushes(cards: List<Card>): List<Flush> {
        val results = mutableListOf<Flush>()
        val combinations = getCombinations(cards, 5)
        for (combo in combinations) {
            if (isSameSuit(combo) && !isStrictlyConsecutive(combo)) {
                results.add(Flush(combo))
            }
        }
        return results
    }
    
    fun findFullHouses(cards: List<Card>): List<FullHouse> {
        val results = mutableListOf<FullHouse>()
        val grouped = cards.groupBy { it.rank }
        
        val trioCandidates = grouped.filter { it.value.size >= 3 }
        val pairCandidates = grouped.filter { it.value.size >= 2 }
        
        for ((trioRank, trioCards) in trioCandidates) {
            val trioCombos = if (trioCards.size == 4) getCombinations(trioCards, 3) else listOf(trioCards)
            for ((pairRank, pairCards) in pairCandidates) {
                if (trioRank == pairRank) continue
                val pairCombos = getCombinations(pairCards, 2)
                for (trio in trioCombos) {
                    for (pair in pairCombos) {
                        results.add(FullHouse(trio, pair))
                    }
                }
            }
        }
        return results.distinctBy { it.cards.toSet() }
    }
    
    fun findFourOfAKinds(cards: List<Card>): List<FourOfAKind> {
        val results = mutableListOf<FourOfAKind>()
        val grouped = cards.groupBy { it.rank }.filter { it.value.size == 4 }
        
        for ((_, quattro) in grouped) {
            val remainders = cards.filter { it !in quattro }
            for (kicker in remainders) {
                results.add(FourOfAKind(quattro, kicker))
            }
        }
        return results
    }
    
    fun findStraightFlushes(cards: List<Card>): List<StraightFlush> {
        val results = mutableListOf<StraightFlush>()
        val combinations = getCombinations(cards, 5)
        for (combo in combinations) {
            if (isStrictlyConsecutive(combo) && isSameSuit(combo)) {
                results.add(StraightFlush(combo))
            }
        }
        return results
    }

    /**
     * Checks if 5 cards are strictly consecutive without wrapping.
     * In CDD: Ranks are 3, 4, 5, 6, 7, 8, 9, 10, J, Q, K, A, 2.
     * Strictly no wrapping: Cannot contain both smallest rank 3 and biggest rank 2.
     * Valid straights have sorted rank weights: [w, w+1, w+2, w+3, w+4] with w in 3..11.
     * Examples of rejected wrapping hands: A-2-3-4-5, Q-K-A-2-3, K-A-2-3-4, 2-3-4-5-6.
     */
    fun isStrictlyConsecutive(combo: List<Card>): Boolean {
        if (combo.size != 5) return false
        
        // Disallow wrapping: cannot contain both 3 and 2
        val hasThree = combo.any { it.rank == Rank.THREE }
        val hasTwo = combo.any { it.rank == Rank.TWO }
        if (hasThree && hasTwo) return false

        val sortedWeights = combo.map { it.rank.weight }.sorted()
        for (i in 0 until 4) {
            if (sortedWeights[i + 1] - sortedWeights[i] != 1) {
                return false
            }
        }
        return true
    }
    
    private fun isSameSuit(combo: List<Card>): Boolean {
        return combo.map { it.suit }.distinct().size == 1
    }

    private fun <T> getCombinations(list: List<T>, k: Int): List<List<T>> {
        val results = mutableListOf<List<T>>()
        fun combine(start: Int, current: MutableList<T>) {
            if (current.size == k) {
                results.add(current.toList())
                return
            }
            for (i in start until list.size) {
                current.add(list[i])
                combine(i + 1, current)
                current.removeAt(current.size - 1)
            }
        }
        combine(0, mutableListOf())
        return results
    }

    fun getBestTacticalHand(hands: List<CddHand>, variation: Variation = Variation.MAINLAND): CddHand? {
        if (hands.isEmpty()) return null
        return hands.maxWithOrNull { h1, h2 ->
            val w1 = getHandTacticalWeight(h1, variation)
            val w2 = getHandTacticalWeight(h2, variation)
            if (w1 != w2) w1.compareTo(w2) else h1.keyCard.compareTo(h2.keyCard)
        }
    }

    private fun getHandTacticalWeight(hand: CddHand, variation: Variation): Int {
        return when (hand) {
            is StraightFlush -> 80
            is FourOfAKind -> 70
            is FullHouse -> 60
            is Flush -> if (variation == Variation.MAINLAND) 50 else 40
            is Straight -> if (variation == Variation.MAINLAND) 40 else 50
            is Trio -> 30
            is Pair -> 20
            is Single -> 10
            else -> 0
        }
    }
}
