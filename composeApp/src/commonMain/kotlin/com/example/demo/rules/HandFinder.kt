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

    private fun findSingles(cards: List<Card>): List<Single> = cards.map { Single(it) }

    private fun findPairs(cards: List<Card>): List<Pair> {
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

    private fun findTrios(cards: List<Card>): List<Trio> {
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
    
    private fun findStraights(cards: List<Card>): List<Straight> {
        val results = mutableListOf<Straight>()
        val combinations = getCombinations(cards, 5)
        for (combo in combinations) {
            if (isConsecutive(combo) && !hasBothExtremes(combo) && !isSameSuit(combo)) {
                results.add(Straight(combo))
            }
        }
        return results
    }
    
    private fun findFlushes(cards: List<Card>): List<Flush> {
        val results = mutableListOf<Flush>()
        val combinations = getCombinations(cards, 5)
        for (combo in combinations) {
            if (isSameSuit(combo) && (!isConsecutive(combo) || hasBothExtremes(combo))) {
                results.add(Flush(combo))
            }
        }
        return results
    }
    
    private fun findFullHouses(cards: List<Card>): List<FullHouse> {
        val results = mutableListOf<FullHouse>()
        val trios = findTrios(cards)
        val pairs = findPairs(cards)
        
        for (t in trios) {
            for (p in pairs) {
                if (t.cards.intersect(p.cards.toSet()).isEmpty()) {
                    results.add(FullHouse(t.cards, p.cards))
                }
            }
        }
        return results
    }
    
    private fun findFourOfAKinds(cards: List<Card>): List<FourOfAKind> {
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
    
    private fun findStraightFlushes(cards: List<Card>): List<StraightFlush> {
        val results = mutableListOf<StraightFlush>()
        val combinations = getCombinations(cards, 5)
        for (combo in combinations) {
            if (isConsecutive(combo) && isSameSuit(combo) && !hasBothExtremes(combo)) {
                results.add(StraightFlush(combo))
            }
        }
        return results
    }

    private fun isConsecutive(combo: List<Card>): Boolean {
        val sortedRanks = combo.map { it.rank.weight }.sorted()
        var isSeq = true
        for (i in 0 until 4) {
            if (sortedRanks[i+1] - sortedRanks[i] != 1) {
                isSeq = false
                break
            }
        }
        if (isSeq) return true
        
        val validSequences = listOf(
            listOf(14, 15, 3, 4, 5),
            listOf(15, 3, 4, 5, 6),
            listOf(12, 13, 14, 15, 3),
            listOf(13, 14, 15, 3, 4)
        )
        
        for (seq in validSequences) {
            if (sortedRanks == seq.sorted()) return true
        }
        return false
    }
    
    private fun hasBothExtremes(combo: List<Card>): Boolean {
        val hasSmallest = combo.any { it.rank == Rank.THREE }
        val hasBiggest = combo.any { it.rank == Rank.TWO }
        return hasSmallest && hasBiggest
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
}
