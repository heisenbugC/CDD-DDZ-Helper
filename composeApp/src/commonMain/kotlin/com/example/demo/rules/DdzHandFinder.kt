package com.example.demo.rules

import com.example.demo.domain.DdzCard
import com.example.demo.domain.DdzRank

object DdzHandFinder {

    fun findAllHands(cards: List<DdzCard>): List<DdzHand> {
        val allHands = mutableListOf<DdzHand>()
        val groupedByRank = cards.groupBy { it.rank }
        val counts = groupedByRank.mapValues { it.value.size }
        
        // Basic components
        val singles = mutableListOf<DdzSingle>()
        val pairs = mutableListOf<DdzPair>()
        val trios = mutableListOf<DdzTrio>()
        val quattros = mutableListOf<List<DdzCard>>()
        
        for ((rank, group) in groupedByRank) {
            group.forEach { singles.add(DdzSingle(it)) }
            if (group.size >= 2) {
                // For pairs, we just take the first two for simplicity in finding hands, 
                // in reality all combinations of pairs from the group could be considered.
                pairs.add(DdzPair(group[0], group[1])) 
            }
            if (group.size >= 3) trios.add(DdzTrio(group.take(3)))
            if (group.size == 4) quattros.add(group)
        }
        
        allHands.addAll(singles)
        allHands.addAll(pairs)
        allHands.addAll(trios)
        allHands.addAll(quattros.map { DdzBomb(it) })
        
        // Rocket
        val jokers = cards.filter { it.rank == DdzRank.BLACK_JOKER || it.rank == DdzRank.RED_JOKER }
        if (jokers.size == 2) {
            allHands.add(DdzRocket(jokers))
        }

        // Trios with kickers
        for (trio in trios) {
            val remainingSingles = singles.filter { it.card.rank != trio.keyCard.rank }
            remainingSingles.forEach { allHands.add(DdzTrioWithSingle(trio.trioCards, it.card)) }
            
            val remainingPairs = pairs.filter { it.keyCard.rank != trio.keyCard.rank }
            remainingPairs.forEach { allHands.add(DdzTrioWithPair(trio.trioCards, it.cards)) }
        }

        // Straights (Length >= 5, max rank A)
        val plainCards = cards.filter { it.rank.weight <= DdzRank.ACE.weight }.distinctBy { it.rank }
        val sortedPlain = plainCards.sortedBy { it.rank.weight }
        for (i in 0 until sortedPlain.size) {
            for (len in 5..sortedPlain.size - i) {
                val sub = sortedPlain.subList(i, i + len)
                if (sub.last().rank.weight - sub.first().rank.weight == len - 1) {
                    allHands.add(DdzStraight(sub))
                }
            }
        }

        // Consecutive Pairs (Length >= 3 pairs, max rank A)
        val validPairs = pairs.filter { it.keyCard.rank.weight <= DdzRank.ACE.weight }.sortedBy { it.keyCard.rank.weight }
        for (i in 0 until validPairs.size) {
            for (len in 3..validPairs.size - i) {
                val sub = validPairs.subList(i, i + len)
                if (sub.last().keyCard.rank.weight - sub.first().keyCard.rank.weight == len - 1) {
                    allHands.add(DdzConsecutivePairs(sub.flatMap { it.cards }))
                }
            }
        }
        
        // Airplanes
        val validTrios = trios.filter { it.keyCard.rank.weight <= DdzRank.ACE.weight }.sortedBy { it.keyCard.rank.weight }
        for (i in 0 until validTrios.size) {
            for (len in 2..validTrios.size - i) {
                val sub = validTrios.subList(i, i + len)
                if (sub.last().keyCard.rank.weight - sub.first().keyCard.rank.weight == len - 1) {
                    val airplaneCards = sub.flatMap { it.trioCards }
                    allHands.add(DdzAirplane(airplaneCards)) // Without wings
                    
                    // Airplane with small wings (single kickers)
                    // Simplified: just check if we have enough other cards
                    val otherCards = cards.filter { it !in airplaneCards }
                    if (otherCards.size >= len) {
                        allHands.add(DdzAirplane(airplaneCards, otherCards.take(len)))
                    }
                }
            }
        }
        
        // Four with Two (singles / pairs)
        for (four in quattros) {
            val remainCards = cards.filter { it !in four }
            if (remainCards.size >= 2) {
                allHands.add(DdzFourWithTwoSingles(four, remainCards.take(2)))
            }
            if (pairs.size >= 3) { // 1 pair is from the quattro itself, so we need > 1 other pair usually, but doing generic check
                val otherPairs = pairs.filter { it.keyCard.rank != four.first().rank }
                if (otherPairs.size >= 2) {
                    allHands.add(DdzFourWithTwoPairs(four, otherPairs.take(2).flatMap { it.cards }))
                }
            }
        }

        return allHands
    }
}
