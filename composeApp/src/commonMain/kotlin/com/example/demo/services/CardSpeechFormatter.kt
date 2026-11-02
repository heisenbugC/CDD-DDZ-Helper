package com.example.demo.services

import com.example.demo.domain.Card
import com.example.demo.domain.DdzCard
import com.example.demo.domain.DdzRank
import com.example.demo.domain.DdzSuit
import com.example.demo.domain.Rank
import com.example.demo.domain.Suit
import com.example.demo.rules.*

object CardSpeechFormatter {

    enum class SupportedLang { ZH, HK, EN }

    fun resolveLang(language: String): SupportedLang {
        return when {
            language.equals("zh-HK", ignoreCase = true) || language.equals("yue", ignoreCase = true) -> SupportedLang.HK
            language.startsWith("zh", ignoreCase = true) -> SupportedLang.ZH
            else -> SupportedLang.EN
        }
    }

    /**
     * Formats Dou Di Zhu speech announcement.
     * Strictly ordered in DESCENDING order of combo size / priority:
     * 1. Total locked card count ("已锁定 X 张手牌。")
     * 2. Available combos (Rocket -> Bombs -> Airplanes -> Sister Pairs -> Straights -> Fours -> Trios -> Pairs -> Singles)
     * 3. Top recommended play
     */
    fun formatDdzSpeech(
        cards: List<DdzCard>,
        hands: List<DdzHand>,
        language: String = "zh",
        detailed: Boolean = false
    ): String {
        val lang = resolveLang(language)
        val effectiveCards = if (cards.isNotEmpty()) cards else hands.flatMap { it.cards }.distinct().sorted()
        if (effectiveCards.isEmpty() && hands.isEmpty()) {
            return when (lang) {
                SupportedLang.ZH -> "当前未识别到手牌，请将卡牌平放对准摄像头。"
                SupportedLang.HK -> "目前未識別到手牌，請將卡牌平放對準鏡頭。"
                SupportedLang.EN -> "No cards detected. Please place your cards clearly in the camera view."
            }
        }

        val count = effectiveCards.size
        val countStr = when (lang) {
            SupportedLang.ZH -> "已锁定 ${count} 张手牌。"
            SupportedLang.HK -> "已鎖定 ${count} 張手牌。"
            SupportedLang.EN -> "${count} cards locked."
        }

        // Order hands strictly in descending tactical combo priority
        val sortedHands = hands.sortedWith(Comparator { h1, h2 ->
            val p1 = ddzHandPriority(h1)
            val p2 = ddzHandPriority(h2)
            if (p1 != p2) p1.compareTo(p2) else h2.cards.size.compareTo(h1.cards.size)
        })

        val bestHand = sortedHands.firstOrNull()
        val bestStr = when (lang) {
            SupportedLang.ZH -> if (bestHand != null) "最佳推荐出牌：${describeDdzHand(bestHand, lang)}。" else "暂无可组合的最佳牌型。"
            SupportedLang.HK -> if (bestHand != null) "最佳推薦出牌：${describeDdzHand(bestHand, lang)}。" else "暫無可組合的最佳牌型。"
            SupportedLang.EN -> if (bestHand != null) "Recommended play: ${describeDdzHand(bestHand, lang)}." else "No combination found."
        }

        if (!detailed) {
            return "$countStr $bestStr"
        }

        // Detailed mode: list distinct categories in descending combo size order
        val comboSections = mutableListOf<String>()

        // 1. Rocket
        val rockets = sortedHands.filterIsInstance<DdzRocket>()
        if (rockets.isNotEmpty()) {
            comboSections.add(when (lang) {
                SupportedLang.ZH -> "王炸火箭，大王配小王"
                SupportedLang.HK -> "王炸火箭，大王配小王"
                SupportedLang.EN -> "Rocket, Red and Black Joker"
            })
        }

        // 2. Bombs
        val bombs = sortedHands.filterIsInstance<DdzBomb>().distinctBy { it.keyCard.rank }
        if (bombs.isNotEmpty()) {
            val desc = bombs.joinToString(if (lang == SupportedLang.EN) ", " else "，") {
                describeDdzHand(it, lang)
            }
            comboSections.add(desc)
        }

        // 3. Airplanes with Wings
        val airplanesWithWings = sortedHands.filterIsInstance<DdzAirplane>()
            .filter { it.hasWings }
            .distinctBy { it.cards.size to it.keyCard.rank }
        if (airplanesWithWings.isNotEmpty()) {
            val desc = airplanesWithWings.joinToString(if (lang == SupportedLang.EN) ", " else "，") {
                describeDdzHand(it, lang)
            }
            comboSections.add(desc)
        }

        // 4. Airplanes without Wings
        val airplanesPure = sortedHands.filterIsInstance<DdzAirplane>()
            .filter { !it.hasWings }
            .distinctBy { it.cards.size to it.keyCard.rank }
        if (airplanesPure.isNotEmpty()) {
            val desc = airplanesPure.joinToString(if (lang == SupportedLang.EN) ", " else "，") {
                describeDdzHand(it, lang)
            }
            comboSections.add(desc)
        }

        // 5. Consecutive Pairs
        val sisterPairs = sortedHands.filterIsInstance<DdzConsecutivePairs>()
            .distinctBy { it.cards.size to it.keyCard.rank }
        if (sisterPairs.isNotEmpty()) {
            val desc = sisterPairs.joinToString(if (lang == SupportedLang.EN) ", " else "，") {
                describeDdzHand(it, lang)
            }
            comboSections.add(desc)
        }

        // 6. Straights
        val straights = sortedHands.filterIsInstance<DdzStraight>()
            .distinctBy { it.cards.size to it.keyCard.rank }
        if (straights.isNotEmpty()) {
            val desc = straights.joinToString(if (lang == SupportedLang.EN) ", " else "，") {
                describeDdzHand(it, lang)
            }
            comboSections.add(desc)
        }

        // 7. Four with Two Pairs
        val fourWithPairs = sortedHands.filterIsInstance<DdzFourWithTwoPairs>().distinctBy { it.keyCard.rank }
        if (fourWithPairs.isNotEmpty()) {
            val desc = fourWithPairs.joinToString(if (lang == SupportedLang.EN) ", " else "，") {
                describeDdzHand(it, lang)
            }
            comboSections.add(desc)
        }

        // 8. Four with Two Singles
        val fourWithSingles = sortedHands.filterIsInstance<DdzFourWithTwoSingles>().distinctBy { it.keyCard.rank }
        if (fourWithSingles.isNotEmpty()) {
            val desc = fourWithSingles.joinToString(if (lang == SupportedLang.EN) ", " else "，") {
                describeDdzHand(it, lang)
            }
            comboSections.add(desc)
        }

        // 9. Trio with Pair
        val trioWithPairs = sortedHands.filterIsInstance<DdzTrioWithPair>().distinctBy { it.keyCard.rank }
        if (trioWithPairs.isNotEmpty()) {
            val desc = trioWithPairs.joinToString(if (lang == SupportedLang.EN) ", " else "，") {
                describeDdzHand(it, lang)
            }
            comboSections.add(desc)
        }

        // 10. Trio with Single
        val trioWithSingles = sortedHands.filterIsInstance<DdzTrioWithSingle>().distinctBy { it.keyCard.rank }
        if (trioWithSingles.isNotEmpty()) {
            val desc = trioWithSingles.joinToString(if (lang == SupportedLang.EN) ", " else "，") {
                describeDdzHand(it, lang)
            }
            comboSections.add(desc)
        }

        // 11. Trios
        val pureTrios = sortedHands.filterIsInstance<DdzTrio>().distinctBy { it.keyCard.rank }
        if (pureTrios.isNotEmpty()) {
            val desc = pureTrios.joinToString(if (lang == SupportedLang.EN) ", " else "，") {
                describeDdzHand(it, lang)
            }
            comboSections.add(desc)
        }

        // 12. Pairs
        val pairs = sortedHands.filterIsInstance<DdzPair>().distinctBy { it.keyCard.rank }
        if (pairs.isNotEmpty()) {
            val desc = pairs.joinToString(if (lang == SupportedLang.EN) ", " else "，") {
                describeDdzHand(it, lang)
            }
            comboSections.add(desc)
        }

        // 13. High Singles (Cards with weight >= 10 or Jokers)
        val singles = cards.filter { it.rank.weight >= DdzRank.TEN.weight }
            .sortedDescending()
            .distinctBy { it.rank }
        if (singles.isNotEmpty()) {
            val singlesStr = when (lang) {
                SupportedLang.ZH -> "单张大牌：" + singles.joinToString("、") { describeDdzCard(it, lang) }
                SupportedLang.HK -> "單張大牌：" + singles.joinToString("、") { describeDdzCard(it, lang) }
                SupportedLang.EN -> "High singles: " + singles.joinToString(", ") { describeDdzCard(it, lang) }
            }
            comboSections.add(singlesStr)
        }

        val detailIntro = when (lang) {
            SupportedLang.ZH -> "手牌组合包含："
            SupportedLang.HK -> "手牌組合包含："
            SupportedLang.EN -> "Available combinations: "
        }
        val detailBody = comboSections.joinToString(if (lang == SupportedLang.EN) ". " else "；")
        val fullDetail = if (comboSections.isNotEmpty()) "$detailIntro$detailBody。" else ""

        return "$countStr $fullDetail $bestStr"
    }

    /**
     * Formats Cho Dai Dee (CDD / Big Two) speech announcement.
     * Strictly ordered in DESCENDING order of combo size / priority:
     * 1. Total locked card count ("已锁定 X 张手牌。")
     * 2. Five-card combinations (Straight Flush -> Four of a kind -> Full House -> Flush/Straight)
     * 3. Trios -> Pairs -> Singles
     * 4. Top recommended play
     */
    fun formatCddSpeech(
        cards: List<Card>,
        hands: List<CddHand>,
        language: String = "zh",
        variation: Variation = Variation.MAINLAND,
        detailed: Boolean = false
    ): String {
        val lang = resolveLang(language)
        val effectiveCards = if (cards.isNotEmpty()) cards else hands.flatMap { it.cards }.distinct().sorted()
        if (effectiveCards.isEmpty() && hands.isEmpty()) {
            return when (lang) {
                SupportedLang.ZH -> "当前未识别到手牌，请将卡牌平放对准摄像头。"
                SupportedLang.HK -> "目前未識別到手牌，請將卡牌平放對準鏡頭。"
                SupportedLang.EN -> "No cards detected. Please place your cards clearly in the camera view."
            }
        }

        val count = effectiveCards.size
        val countStr = when (lang) {
            SupportedLang.ZH -> "锄大地模式：已锁定 ${count} 张手牌。"
            SupportedLang.HK -> "鋤大D模式：已鎖定 ${count} 張手牌。"
            SupportedLang.EN -> "Big Two mode: ${count} cards locked."
        }

        // Order hands strictly in descending tactical priority
        val sortedHands = hands.sortedWith(Comparator { h1, h2 ->
            val p1 = cddHandPriority(h1, variation)
            val p2 = cddHandPriority(h2, variation)
            if (p1 != p2) p1.compareTo(p2) else h2.keyCard.compareTo(h1.keyCard)
        })

        val bestHand = sortedHands.firstOrNull()
        val bestStr = when (lang) {
            SupportedLang.ZH -> if (bestHand != null) "最佳出牌推荐：${describeCddHand(bestHand, lang)}。" else "暂无组合牌型。"
            SupportedLang.HK -> if (bestHand != null) "最佳出牌推薦：${describeCddHand(bestHand, lang)}。" else "暫無組合牌型。"
            SupportedLang.EN -> if (bestHand != null) "Recommended play: ${describeCddHand(bestHand, lang)}." else "No valid combination found."
        }

        if (!detailed) {
            return "$countStr $bestStr"
        }

        // Detailed mode: list distinct categories in descending combo size order
        val comboSections = mutableListOf<String>()

        val straightFlushes = sortedHands.filterIsInstance<StraightFlush>()
        if (straightFlushes.isNotEmpty()) {
            comboSections.add(straightFlushes.joinToString(if (lang == SupportedLang.EN) ", " else "，") {
                describeCddHand(it, lang)
            })
        }

        val quattros = sortedHands.filterIsInstance<FourOfAKind>().distinctBy { it.keyCard.rank }
        if (quattros.isNotEmpty()) {
            comboSections.add(quattros.joinToString(if (lang == SupportedLang.EN) ", " else "，") {
                describeCddHand(it, lang)
            })
        }

        val fullHouses = sortedHands.filterIsInstance<FullHouse>().distinctBy { it.keyCard.rank }
        if (fullHouses.isNotEmpty()) {
            comboSections.add(fullHouses.joinToString(if (lang == SupportedLang.EN) ", " else "，") {
                describeCddHand(it, lang)
            })
        }

        if (variation == Variation.MAINLAND) {
            val flushes = sortedHands.filterIsInstance<Flush>().distinctBy { it.keyCard.suit }
            if (flushes.isNotEmpty()) {
                comboSections.add(flushes.joinToString(if (lang == SupportedLang.EN) ", " else "，") {
                    describeCddHand(it, lang)
                })
            }
            val straights = sortedHands.filterIsInstance<Straight>().distinctBy { it.keyCard.rank }
            if (straights.isNotEmpty()) {
                comboSections.add(straights.joinToString(if (lang == SupportedLang.EN) ", " else "，") {
                    describeCddHand(it, lang)
                })
            }
        } else {
            // Hong Kong: Straight > Flush
            val straights = sortedHands.filterIsInstance<Straight>().distinctBy { it.keyCard.rank }
            if (straights.isNotEmpty()) {
                comboSections.add(straights.joinToString(if (lang == SupportedLang.EN) ", " else "，") {
                    describeCddHand(it, lang)
                })
            }
            val flushes = sortedHands.filterIsInstance<Flush>().distinctBy { it.keyCard.suit }
            if (flushes.isNotEmpty()) {
                comboSections.add(flushes.joinToString(if (lang == SupportedLang.EN) ", " else "，") {
                    describeCddHand(it, lang)
                })
            }
        }

        val trios = sortedHands.filterIsInstance<Trio>().distinctBy { it.keyCard.rank }
        if (trios.isNotEmpty()) {
            comboSections.add(trios.joinToString(if (lang == SupportedLang.EN) ", " else "，") {
                describeCddHand(it, lang)
            })
        }

        val pairs = sortedHands.filterIsInstance<Pair>().distinctBy { it.keyCard.rank }
        if (pairs.isNotEmpty()) {
            comboSections.add(pairs.joinToString(if (lang == SupportedLang.EN) ", " else "，") {
                describeCddHand(it, lang)
            })
        }

        val highSingles = effectiveCards.filter { it.rank.weight >= Rank.ACE.weight }.sortedDescending()
        if (highSingles.isNotEmpty()) {
            val label = when (lang) {
                SupportedLang.ZH -> "单张大牌：" + highSingles.joinToString("、") { describeCddCard(it, lang) }
                SupportedLang.HK -> "單張大牌：" + highSingles.joinToString("、") { describeCddCard(it, lang) }
                SupportedLang.EN -> "High singles: " + highSingles.joinToString(", ") { describeCddCard(it, lang) }
            }
            comboSections.add(label)
        }

        val detailIntro = when (lang) {
            SupportedLang.ZH -> "手牌组合包含："
            SupportedLang.HK -> "手牌組合包含："
            SupportedLang.EN -> "Available combinations: "
        }
        val detailBody = comboSections.joinToString(if (lang == SupportedLang.EN) ". " else "；")
        val fullDetail = if (comboSections.isNotEmpty()) "$detailIntro$detailBody。" else ""

        return "$countStr $fullDetail $bestStr"
    }

    fun ddzHandPriority(hand: DdzHand): Int {
        return when (hand) {
            is DdzRocket -> 1
            is DdzBomb -> 2
            is DdzAirplane -> if (hand.hasWings) 3 else 4
            is DdzConsecutivePairs -> 5
            is DdzStraight -> 6
            is DdzFourWithTwoPairs -> 7
            is DdzFourWithTwoSingles -> 8
            is DdzTrioWithPair -> 9
            is DdzTrioWithSingle -> 10
            is DdzTrio -> 11
            is DdzPair -> 12
            is DdzSingle -> 13
        }
    }

    fun cddHandPriority(hand: CddHand, variation: Variation = Variation.MAINLAND): Int {
        return when (hand) {
            is StraightFlush -> 1
            is FourOfAKind -> 2
            is FullHouse -> 3
            is Flush -> if (variation == Variation.MAINLAND) 4 else 5
            is Straight -> if (variation == Variation.MAINLAND) 5 else 4
            is Trio -> 6
            is Pair -> 7
            is Single -> 8
            else -> 9
        }
    }

    fun describeDdzHand(hand: DdzHand, lang: SupportedLang = SupportedLang.ZH): String {
        return when (lang) {
            SupportedLang.ZH -> describeDdzHandZh(hand)
            SupportedLang.HK -> describeDdzHandHk(hand)
            SupportedLang.EN -> describeDdzHandEn(hand)
        }
    }

    fun describeDdzHandZh(hand: DdzHand): String {
        return when (hand) {
            is DdzRocket -> "王炸火箭，大王配小王"
            is DdzBomb -> "炸弹4张${hand.keyCard.rank.label}"
            is DdzAirplane -> {
                val len = hand.trioCount
                val start = hand.trios.first().rank.label
                val end = hand.trios.last().rank.label
                if (hand.hasPairWings) {
                    "大飞机带翅膀${hand.cards.size}张：${start}到${end}带${len}对"
                } else if (hand.hasSingleWings) {
                    "大飞机带单牌${hand.cards.size}张：${start}到${end}带${len}单"
                } else {
                    "纯飞机${hand.cards.size}张：${start}到${end}"
                }
            }
            is DdzConsecutivePairs -> "连对共${hand.cards.size}张：${hand.pairCount}连对，${hand.cards.first().rank.label}到${hand.cards.last().rank.label}"
            is DdzStraight -> "顺子${hand.cards.size}张：${hand.cards.first().rank.label}到${hand.cards.last().rank.label}"
            is DdzFourWithTwoPairs -> "四带两对8张：4个${hand.keyCard.rank.label}带两对"
            is DdzFourWithTwoSingles -> "四带二6张：4个${hand.keyCard.rank.label}带两单"
            is DdzTrioWithPair -> "三带二5张：三张${hand.keyCard.rank.label}带一对"
            is DdzTrioWithSingle -> "三带一4张：三张${hand.keyCard.rank.label}带单张"
            is DdzTrio -> "三条3张：三张${hand.keyCard.rank.label}"
            is DdzPair -> "对子：对${hand.keyCard.rank.label}"
            is DdzSingle -> when (hand.keyCard.rank) {
                DdzRank.BLACK_JOKER -> "单张小王"
                DdzRank.RED_JOKER -> "单张大王"
                else -> "单张${hand.keyCard.rank.label}"
            }
        }
    }

    fun describeDdzHandHk(hand: DdzHand): String {
        return when (hand) {
            is DdzRocket -> "王炸火箭，大王配小王"
            is DdzBomb -> "炸彈4張${hand.keyCard.rank.label}"
            is DdzAirplane -> {
                val len = hand.trioCount
                val start = hand.trios.first().rank.label
                val end = hand.trios.last().rank.label
                if (hand.hasPairWings) {
                    "大飛機帶對子${hand.cards.size}張：${start}到${end}帶${len}對"
                } else if (hand.hasSingleWings) {
                    "大飛機帶單牌${hand.cards.size}張：${start}到${end}帶${len}單"
                } else {
                    "純飛機${hand.cards.size}張：${start}到${end}"
                }
            }
            is DdzConsecutivePairs -> "連對共${hand.cards.size}張：${hand.pairCount}連對，${hand.cards.first().rank.label}到${hand.cards.last().rank.label}"
            is DdzStraight -> "順子${hand.cards.size}張：${hand.cards.first().rank.label}到${hand.cards.last().rank.label}"
            is DdzFourWithTwoPairs -> "四帶兩對8張：4隻${hand.keyCard.rank.label}帶兩對"
            is DdzFourWithTwoSingles -> "四帶二6張：4隻${hand.keyCard.rank.label}帶兩單"
            is DdzTrioWithPair -> "三帶二5張：三隻${hand.keyCard.rank.label}帶一對"
            is DdzTrioWithSingle -> "三帶一4張：三隻${hand.keyCard.rank.label}帶單隻"
            is DdzTrio -> "三條3張：三隻${hand.keyCard.rank.label}"
            is DdzPair -> "對子：一對${hand.keyCard.rank.label}"
            is DdzSingle -> when (hand.keyCard.rank) {
                DdzRank.BLACK_JOKER -> "單隻小王"
                DdzRank.RED_JOKER -> "單隻大王"
                else -> "單隻${hand.keyCard.rank.label}"
            }
        }
    }

    fun describeDdzHandEn(hand: DdzHand): String {
        return when (hand) {
            is DdzRocket -> "Rocket, Red and Black Joker"
            is DdzBomb -> "Bomb four ${hand.keyCard.rank.label}s"
            is DdzAirplane -> {
                val len = hand.trioCount
                if (hand.hasPairWings) {
                    "Airplane with pairs ${hand.cards.size} cards: $len trios with pairs"
                } else if (hand.hasSingleWings) {
                    "Airplane with singles ${hand.cards.size} cards: $len trios with singles"
                } else {
                    "Airplane ${hand.cards.size} cards: $len trios"
                }
            }
            is DdzConsecutivePairs -> "Consecutive pairs ${hand.cards.size} cards: ${hand.cards.first().rank.label} through ${hand.cards.last().rank.label}"
            is DdzStraight -> "Straight ${hand.cards.size} cards: ${hand.cards.first().rank.label} through ${hand.cards.last().rank.label}"
            is DdzFourWithTwoPairs -> "Four of a kind ${hand.keyCard.rank.label}s with two pairs"
            is DdzFourWithTwoSingles -> "Four of a kind ${hand.keyCard.rank.label}s with two singles"
            is DdzTrioWithPair -> "Trio of ${hand.keyCard.rank.label}s with pair"
            is DdzTrioWithSingle -> "Trio of ${hand.keyCard.rank.label}s with single"
            is DdzTrio -> "Three ${hand.keyCard.rank.label}s"
            is DdzPair -> "Pair of ${hand.keyCard.rank.label}s"
            is DdzSingle -> when (hand.keyCard.rank) {
                DdzRank.BLACK_JOKER -> "Single Black Joker"
                DdzRank.RED_JOKER -> "Single Red Joker"
                else -> "Single ${hand.keyCard.rank.label}"
            }
        }
    }

    fun describeCddHand(hand: CddHand, lang: SupportedLang = SupportedLang.ZH): String {
        return when (lang) {
            SupportedLang.ZH -> describeCddHandZh(hand)
            SupportedLang.HK -> describeCddHandHk(hand)
            SupportedLang.EN -> describeCddHandEn(hand)
        }
    }

    fun describeCddHandZh(hand: CddHand): String {
        return when (hand) {
            is StraightFlush -> "同花顺，最大${describeCddCardZh(hand.keyCard)}"
            is FourOfAKind -> "铁支四条，4张${hand.keyCard.rank.label}带单张"
            is FullHouse -> "葫芦，三张${hand.keyCard.rank.label}带一对"
            is Flush -> "同花，${describeSuitZh(hand.keyCard.suit)}，最大${hand.keyCard.rank.label}"
            is Straight -> "顺子，最大到${hand.keyCard.rank.label}"
            is Trio -> "三条${hand.keyCard.rank.label}"
            is Pair -> "对子：对${hand.keyCard.rank.label}"
            is Single -> "单张${describeCddCardZh(hand.keyCard)}"
            else -> "手牌"
        }
    }

    fun describeCddHandHk(hand: CddHand): String {
        return when (hand) {
            is StraightFlush -> "同花順，最大${describeCddCardHk(hand.keyCard)}"
            is FourOfAKind -> "四條鐵支，4隻${hand.keyCard.rank.label}帶單隻"
            is FullHouse -> "葫蘆(巨)，三隻${hand.keyCard.rank.label}帶一對"
            is Flush -> "同花，${describeSuitHk(hand.keyCard.suit)}，最大${hand.keyCard.rank.label}"
            is Straight -> "蛇(順子)，最大到${hand.keyCard.rank.label}"
            is Trio -> "三條${hand.keyCard.rank.label}"
            is Pair -> "對子：一對${hand.keyCard.rank.label}"
            is Single -> "單隻${describeCddCardHk(hand.keyCard)}"
            else -> "手牌"
        }
    }

    fun describeCddHandEn(hand: CddHand): String {
        return when (hand) {
            is StraightFlush -> "Straight Flush, high card ${describeCddCardEn(hand.keyCard)}"
            is FourOfAKind -> "Four of a kind ${hand.keyCard.rank.label}s"
            is FullHouse -> "Full House, three ${hand.keyCard.rank.label}s"
            is Flush -> "Flush in ${hand.keyCard.suit.name.lowercase()}, high ${hand.keyCard.rank.label}"
            is Straight -> "Straight up to ${hand.keyCard.rank.label}"
            is Trio -> "Three ${hand.keyCard.rank.label}s"
            is Pair -> "Pair of ${hand.keyCard.rank.label}s"
            is Single -> "Single ${describeCddCardEn(hand.keyCard)}"
            else -> "Hand"
        }
    }

    fun describeDdzCard(card: DdzCard, lang: SupportedLang = SupportedLang.ZH): String {
        return when (lang) {
            SupportedLang.ZH -> describeDdzCardZh(card)
            SupportedLang.HK -> describeDdzCardHk(card)
            SupportedLang.EN -> describeDdzCardEn(card)
        }
    }

    fun describeDdzCardZh(card: DdzCard): String {
        return when (card.rank) {
            DdzRank.RED_JOKER -> "大王"
            DdzRank.BLACK_JOKER -> "小王"
            else -> "${describeDdzSuitZh(card.suit)}${card.rank.label}"
        }
    }

    fun describeDdzCardHk(card: DdzCard): String {
        return when (card.rank) {
            DdzRank.RED_JOKER -> "大王"
            DdzRank.BLACK_JOKER -> "細王"
            else -> "${describeDdzSuitHk(card.suit)}${card.rank.label}"
        }
    }

    fun describeDdzCardEn(card: DdzCard): String {
        return when (card.rank) {
            DdzRank.RED_JOKER -> "Red Joker"
            DdzRank.BLACK_JOKER -> "Black Joker"
            else -> "${card.rank.label} of ${card.suit.name.lowercase()}s"
        }
    }

    fun describeCddCard(card: Card, lang: SupportedLang = SupportedLang.ZH): String {
        return when (lang) {
            SupportedLang.ZH -> describeCddCardZh(card)
            SupportedLang.HK -> describeCddCardHk(card)
            SupportedLang.EN -> describeCddCardEn(card)
        }
    }

    fun describeCddCardZh(card: Card): String = "${describeSuitZh(card.suit)}${card.rank.label}"
    fun describeCddCardHk(card: Card): String = "${describeSuitHk(card.suit)}${card.rank.label}"
    fun describeCddCardEn(card: Card): String = "${card.rank.label} of ${card.suit.name.lowercase()}s"

    fun describeSuitZh(suit: Suit): String {
        return when (suit) {
            Suit.SPADE -> "黑桃"
            Suit.HEART -> "红桃"
            Suit.CLUB -> "梅花"
            Suit.DIAMOND -> "方块"
        }
    }

    fun describeSuitHk(suit: Suit): String {
        return when (suit) {
            Suit.SPADE -> "黑桃"
            Suit.HEART -> "紅桃"
            Suit.CLUB -> "梅花"
            Suit.DIAMOND -> "方塊"
        }
    }

    fun describeSuitEn(suit: Suit): String = suit.name.lowercase().replaceFirstChar { it.uppercase() }

    fun describeDdzSuitZh(suit: DdzSuit): String {
        return when (suit) {
            DdzSuit.SPADE -> "黑桃"
            DdzSuit.HEART -> "红桃"
            DdzSuit.CLUB -> "梅花"
            DdzSuit.DIAMOND -> "方块"
            DdzSuit.JOKER -> ""
        }
    }

    fun describeDdzSuitHk(suit: DdzSuit): String {
        return when (suit) {
            DdzSuit.SPADE -> "黑桃"
            DdzSuit.HEART -> "紅桃"
            DdzSuit.CLUB -> "梅花"
            DdzSuit.DIAMOND -> "方塊"
            DdzSuit.JOKER -> ""
        }
    }
}
