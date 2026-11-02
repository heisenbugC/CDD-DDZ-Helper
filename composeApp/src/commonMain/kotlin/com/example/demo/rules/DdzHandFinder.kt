package com.example.demo.rules

import com.example.demo.domain.DdzCard
import com.example.demo.domain.DdzRank

object DdzHandFinder {

    fun findAllHands(cards: List<DdzCard>): List<DdzHand> {
        val allHands = mutableListOf<DdzHand>()
        val groupedByRank = cards.groupBy { it.rank }
        
        // 1. Basic components
        val singles = mutableListOf<DdzSingle>()
        val pairs = mutableListOf<DdzPair>()
        val trios = mutableListOf<DdzTrio>()
        val bombs = mutableListOf<DdzBomb>()
        
        for ((_, group) in groupedByRank) {
            group.forEach { singles.add(DdzSingle(it)) }
            if (group.size >= 2) {
                // Add pairs
                for (i in 0 until group.size) {
                    for (j in i + 1 until group.size) {
                        pairs.add(DdzPair(group[i], group[j]))
                    }
                }
            }
            if (group.size >= 3) {
                for (i in 0 until group.size) {
                    for (j in i + 1 until group.size) {
                        for (k in j + 1 until group.size) {
                            trios.add(DdzTrio(listOf(group[i], group[j], group[k])))
                        }
                    }
                }
            }
            if (group.size == 4) {
                bombs.add(DdzBomb(group))
            }
        }
        
        allHands.addAll(singles)
        // Deduplicate pairs by distinct card contents
        val distinctPairs = pairs.distinctBy { it.cards.toSet() }
        allHands.addAll(distinctPairs)
        
        val distinctTrios = trios.distinctBy { it.cards.toSet() }
        allHands.addAll(distinctTrios)
        allHands.addAll(bombs)
        
        // 2. Rocket (King Bomb)
        val blackJoker = cards.find { it.rank == DdzRank.BLACK_JOKER }
        val redJoker = cards.find { it.rank == DdzRank.RED_JOKER }
        if (blackJoker != null && redJoker != null) {
            allHands.add(DdzRocket(listOf(blackJoker, redJoker)))
        }

        // 3. Trios with kickers (Single and Pair)
        // Use representative trio per rank to avoid combinatoric explosion while covering all tactical hands
        val repTriosByRank = groupedByRank.filter { it.value.size >= 3 }.mapValues { it.value.take(3) }
        val repPairsByRank = groupedByRank.filter { it.value.size >= 2 }.mapValues { it.value.take(2) }

        for ((trioRank, trioCards) in repTriosByRank) {
            // Trio + Single
            val remainingSingles = cards.filter { it !in trioCards && it.rank != trioRank }
                .distinctBy { it.rank }
            for (single in remainingSingles) {
                allHands.add(DdzTrioWithSingle(trioCards, single))
            }
            
            // Trio + Pair
            for ((pairRank, pairCards) in repPairsByRank) {
                if (pairRank != trioRank) {
                    allHands.add(DdzTrioWithPair(trioCards, pairCards))
                }
            }
        }

        // 4. Straights (Length >= 5, ranks 3 to Ace only, no 2 or Jokers)
        val plainRanks = groupedByRank.keys
            .filter { it.weight <= DdzRank.ACE.weight }
            .sortedBy { it.weight }
            
        for (i in 0 until plainRanks.size) {
            for (len in 5..(plainRanks.size - i)) {
                val subRanks = plainRanks.subList(i, i + len)
                // Check if consecutive
                if (subRanks.last().weight - subRanks.first().weight == len - 1) {
                    val straightCards = subRanks.map { groupedByRank[it]!!.first() }
                    allHands.add(DdzStraight(straightCards))
                }
            }
        }

        // 5. Consecutive Pairs (Length >= 3 pairs, ranks 3 to Ace only)
        val pairRanks = repPairsByRank.keys
            .filter { it.weight <= DdzRank.ACE.weight }
            .sortedBy { it.weight }
            
        for (i in 0 until pairRanks.size) {
            for (len in 3..(pairRanks.size - i)) {
                val subRanks = pairRanks.subList(i, i + len)
                if (subRanks.last().weight - subRanks.first().weight == len - 1) {
                    val consecutiveCards = subRanks.flatMap { repPairsByRank[it]!! }
                    allHands.add(DdzConsecutivePairs(consecutiveCards))
                }
            }
        }
        
        // 6. Airplanes (>= 2 consecutive trios, ranks 3 to Ace only)
        val trioRanks = repTriosByRank.keys
            .filter { it.weight <= DdzRank.ACE.weight }
            .sortedBy { it.weight }
            
        for (i in 0 until trioRanks.size) {
            for (len in 2..(trioRanks.size - i)) {
                val subRanks = trioRanks.subList(i, i + len)
                if (subRanks.last().weight - subRanks.first().weight == len - 1) {
                    val airplaneTrios = subRanks.flatMap { repTriosByRank[it]!! }
                    val airplaneTrioSet = airplaneTrios.toSet()
                    
                    // Airplane without wings
                    allHands.add(DdzAirplane(airplaneTrios))
                    
                    // Airplane with single wings (len distinct singles outside the trios)
                    val availableSingles = cards.filter { it !in airplaneTrioSet }
                    if (availableSingles.size >= len) {
                        allHands.add(DdzAirplane(airplaneTrios, availableSingles.take(len)))
                    }
                    
                    // Airplane with pair wings (len pairs outside the trios)
                    val availablePairs = repPairsByRank
                        .filter { it.key !in subRanks }
                        .values.toList()
                    if (availablePairs.size >= len) {
                        val wingPairs = availablePairs.take(len).flatten()
                        allHands.add(DdzAirplane(airplaneTrios, wingPairs))
                    }
                }
            }
        }
        
        // 7. Four with Two (singles / pairs)
        for (bomb in bombs) {
            val quattroSet = bomb.cards.toSet()
            val remainCards = cards.filter { it !in quattroSet }
            
            // Four with 2 singles
            if (remainCards.size >= 2) {
                allHands.add(DdzFourWithTwoSingles(bomb.cards, remainCards.take(2)))
            }
            
            // Four with 2 pairs
            val otherPairs = repPairsByRank
                .filter { it.key != bomb.keyCard.rank }
                .values.toList()
            if (otherPairs.size >= 2) {
                allHands.add(DdzFourWithTwoPairs(bomb.cards, otherPairs.take(2).flatten()))
            }
        }

        return allHands
    }

    fun getBestTacticalHand(hands: List<DdzHand>): DdzHand? {
        if (hands.isEmpty()) return null
        return hands.maxWithOrNull { h1, h2 ->
            val w1 = getDdzTacticalWeight(h1)
            val w2 = getDdzTacticalWeight(h2)
            if (w1 != w2) w1.compareTo(w2) else h1.keyCard.rank.weight.compareTo(h2.keyCard.rank.weight)
        }
    }

    private fun getDdzTacticalWeight(hand: DdzHand): Int {
        return when (hand) {
            is DdzRocket -> 100
            is DdzBomb -> 90
            is DdzAirplane -> if (hand.kickers.isNotEmpty()) 80 else 75
            is DdzConsecutivePairs -> 70
            is DdzStraight -> 60
            is DdzFourWithTwoPairs -> 55
            is DdzFourWithTwoSingles -> 50
            is DdzTrioWithPair -> 45
            is DdzTrioWithSingle -> 40
            is DdzTrio -> 30
            is DdzPair -> 20
            is DdzSingle -> 10
        }
    }
}
