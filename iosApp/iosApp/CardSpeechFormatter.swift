import Foundation

// MARK: - CardRank Spoken Pronunciation Customization

extension CardRank {
    public func spokenName(for language: VoiceLanguage) -> String {
        switch language {
        case .mandarin:
            switch self {
            case .jack: return "勾"
            case .queen: return "圈儿"
            case .ace: return "尖儿"
            case .blackJoker: return "小王"
            case .redJoker: return "大王"
            default: return rawValue
            }
        case .cantonese:
            switch self {
            case .jack: return "积"
            case .queen: return "膂"
            case .king: return "倾"
            case .ace: return "烟"
            case .blackJoker: return "细鬼"
            case .redJoker: return "大鬼"
            default: return rawValue
            }
        case .english:
            switch self {
            case .jack: return "Jack"
            case .queen: return "Queen"
            case .king: return "King"
            case .ace: return "Ace"
            case .blackJoker: return "Black Joker"
            case .redJoker: return "Red Joker"
            default: return rawValue
            }
        }
    }
}

/**
 * CardSpeechFormatter: Natural language speech synthesis formatter
 * generating Cantonese, Mandarin, and English voice announcements.
 *
 * Strictly adheres to descending combination hierarchy:
 * 1. Total locked card count
 * 2. Top tactical recommended play
 * 3. Available combinations breakdown (Rocket -> Bomb -> Airplanes -> Straights -> Trios -> Pairs -> High Singles)
 */
public class CardSpeechFormatter {
    
    public static func formatDdzSpeech(
        cards: [PlayingCard],
        hands: [DdzHand],
        language: VoiceLanguage = .cantonese,
        detailed: Bool = false
    ) -> String {
        let count = cards.count
        if count == 0 && hands.isEmpty {
            switch language {
            case .cantonese: return "目前未識別到手牌，請將卡牌平放對準鏡頭。"
            case .mandarin: return "当前未识别到手牌，请将卡牌平放对准摄像头。"
            case .english: return "No cards detected. Please place cards in view."
            }
        }
        
        let countStr: String
        switch language {
        case .cantonese: countStr = "已鎖定 \(count) 張手牌。"
        case .mandarin: countStr = "已锁定 \(count) 张手牌。"
        case .english: countStr = "\(count) cards locked."
        }
        
        let bestHand = hands.first
        let bestStr: String
        if let best = bestHand {
            switch language {
            case .cantonese: bestStr = "最佳推薦出牌：\(describeDdzHand(best, language: language))。"
            case .mandarin: bestStr = "最佳推荐出牌：\(describeDdzHand(best, language: language))。"
            case .english: bestStr = "Recommended play: \(describeDdzHand(best, language: language))."
            }
        } else {
            switch language {
            case .cantonese: bestStr = "暫無可組合的牌型。"
            case .mandarin: bestStr = "暂无可组合的牌型。"
            case .english: bestStr = "No combinations found."
            }
        }
        
        if !detailed {
            return "\(countStr) \(bestStr)"
        }
        
        var sections: [String] = []
        
        // 1. Rocket
        if let _ = hands.first(where: { $0.category == .rocket }) {
            sections.append(language == .english ? "Rocket, Red and Black Joker" : (language == .cantonese ? "王炸火箭，大鬼配細鬼" : "王炸火箭，大王配小王"))
        }
        
        // 2. Bombs
        let bombs = hands.filter { $0.category == .bomb }
        if !bombs.isEmpty {
            let bombDesc = bombs.map { describeDdzHand($0, language: language) }.joined(separator: language == .english ? ", " : "，")
            sections.append(bombDesc)
        }
        
        // 3. Airplanes
        let airplanes = hands.filter { $0.category == .airplaneWithWings || $0.category == .airplanePure }
        if !airplanes.isEmpty {
            let planeDesc = airplanes.prefix(2).map { describeDdzHand($0, language: language) }.joined(separator: "，")
            sections.append(planeDesc)
        }
        
        // 4. Consecutive Pairs
        let sisterPairs = hands.filter { $0.category == .consecutivePairs }
        if !sisterPairs.isEmpty {
            let pairDesc = sisterPairs.prefix(2).map { describeDdzHand($0, language: language) }.joined(separator: "，")
            sections.append(pairDesc)
        }
        
        // 5. Straights
        let straights = hands.filter { $0.category == .straight }
        if !straights.isEmpty {
            let straightDesc = straights.prefix(2).map { describeDdzHand($0, language: language) }.joined(separator: "，")
            sections.append(straightDesc)
        }
        
        // 6. High singles
        let highSingles = cards.filter { $0.rank.weight >= CardRank.ten.weight }.sorted(by: { $0 > $1 })
        let distinctHighSingles = Dictionary(grouping: highSingles, by: { $0.rank }).compactMap { $0.value.first }.sorted(by: { $0 > $1 })
        if !distinctHighSingles.isEmpty {
            let singlesText = distinctHighSingles.prefix(4).map { describeCard($0, language: language) }.joined(separator: "、")
            let prefix = (language == .cantonese) ? "單張大牌：" : (language == .mandarin ? "单张大牌：" : "High singles: ")
            sections.append("\(prefix)\(singlesText)")
        }
        
        if sections.isEmpty {
            return "\(countStr) \(bestStr)"
        }
        
        let intro = (language == .cantonese) ? "手牌組合包含：" : (language == .mandarin ? "手牌组合包含：" : "Combinations: ")
        let body = sections.joined(separator: language == .english ? ". " : "；")
        return "\(countStr) \(intro)\(body)。\(bestStr)"
    }
    
    public static func formatCddSpeech(
        cards: [PlayingCard],
        hands: [CddHand],
        language: VoiceLanguage = .cantonese,
        variation: CddVariation = .mainland,
        detailed: Bool = false
    ) -> String {
        let nonJokers = cards.filter { !$0.isJoker }
        let count = nonJokers.count
        if count == 0 && hands.isEmpty {
            switch language {
            case .cantonese: return "目前未識別到手牌，請將卡牌平放對準鏡頭。"
            case .mandarin: return "当前未识别到手牌，请将卡牌平放对准摄像头。"
            case .english: return "No cards detected. Please place cards in view."
            }
        }
        
        let countStr: String
        switch language {
        case .cantonese: countStr = "鋤大D模式：已鎖定 \(count) 張手牌。"
        case .mandarin: countStr = "锄大地模式：已锁定 \(count) 张手牌。"
        case .english: countStr = "Big Two mode: \(count) cards locked."
        }
        
        let bestHand = hands.first
        let bestStr: String
        if let best = bestHand {
            switch language {
            case .cantonese: bestStr = "最佳出牌推薦：\(describeCddHand(best, language: language))。"
            case .mandarin: bestStr = "最佳出牌推荐：\(describeCddHand(best, language: language))。"
            case .english: bestStr = "Recommended play: \(describeCddHand(best, language: language))."
            }
        } else {
            switch language {
            case .cantonese: bestStr = "暫無組合牌型。"
            case .mandarin: bestStr = "暂无组合牌型。"
            case .english: bestStr = "No valid combination found."
            }
        }
        
        if !detailed {
            return "\(countStr) \(bestStr)"
        }
        
        var sections: [String] = []
        let straightFlushes = hands.filter { $0.category == .straightFlush }
        if !straightFlushes.isEmpty {
            sections.append(straightFlushes.map { describeCddHand($0, language: language) }.joined(separator: "，"))
        }
        let quattros = hands.filter { $0.category == .fourOfAKind }
        if !quattros.isEmpty {
            sections.append(quattros.map { describeCddHand($0, language: language) }.joined(separator: "，"))
        }
        let fullHouses = hands.filter { $0.category == .fullHouse }
        if !fullHouses.isEmpty {
            sections.append(fullHouses.prefix(3).map { describeCddHand($0, language: language) }.joined(separator: "，"))
        }
        let flushes = hands.filter { $0.category == .flush }
        if !flushes.isEmpty {
            sections.append(flushes.prefix(2).map { describeCddHand($0, language: language) }.joined(separator: "，"))
        }
        let straights = hands.filter { $0.category == .straight }
        if !straights.isEmpty {
            sections.append(straights.prefix(2).map { describeCddHand($0, language: language) }.joined(separator: "，"))
        }
        
        let highSingles = nonJokers.filter { $0.rank.weight >= CardRank.ace.weight }.sorted(by: { $0 > $1 })
        let distinctHighSingles = Dictionary(grouping: highSingles, by: { $0.rank }).compactMap { $0.value.first }.sorted(by: { $0 > $1 })
        if !distinctHighSingles.isEmpty {
            let singlesText = distinctHighSingles.map { describeCard($0, language: language) }.joined(separator: "、")
            let prefix = (language == .cantonese) ? "單張大牌：" : (language == .mandarin ? "单张大牌：" : "High singles: ")
            sections.append("\(prefix)\(singlesText)")
        }
        
        if sections.isEmpty {
            return "\(countStr) \(bestStr)"
        }
        
        let intro = (language == .cantonese) ? "手牌組合包含：" : (language == .mandarin ? "手牌组合包含：" : "Combinations: ")
        let body = sections.joined(separator: language == .english ? ". " : "；")
        return "\(countStr) \(intro)\(body)。\(bestStr)"
    }
    
    public static func describeDdzHand(_ hand: DdzHand, language: VoiceLanguage) -> String {
        let rankSpoken = hand.keyCard.rank.spokenName(for: language)
        switch language {
        case .cantonese:
            switch hand.category {
            case .rocket: return "王炸火箭，大鬼配細鬼"
            case .bomb: return "炸彈4隻\(rankSpoken)"
            case .airplaneWithWings: return "大飛機帶翅膀\(hand.cards.count)張"
            case .airplanePure: return "純飛機\(hand.cards.count)張"
            case .consecutivePairs:
                let startRank = hand.cards.min()?.rank.spokenName(for: language) ?? ""
                let endRank = hand.cards.max()?.rank.spokenName(for: language) ?? ""
                return "連對\(hand.cards.count)張：\(startRank)到\(endRank)"
            case .straight:
                let startRank = hand.cards.min()?.rank.spokenName(for: language) ?? ""
                let endRank = hand.cards.max()?.rank.spokenName(for: language) ?? ""
                return "順子\(hand.cards.count)張：\(startRank)到\(endRank)"
            case .fourWithTwoPairs: return "四帶兩對8張：4隻\(rankSpoken)帶兩對"
            case .fourWithTwoSingles: return "四帶二6張：4隻\(rankSpoken)帶兩隻"
            case .trioWithPair: return "三帶二5張：三隻\(rankSpoken)帶一對"
            case .trioWithSingle: return "三帶一4張：三隻\(rankSpoken)帶單隻"
            case .trio: return "三條3張：三隻\(rankSpoken)"
            case .pair: return "對子：一對\(rankSpoken)"
            case .single: return describeCard(hand.keyCard, language: language)
            }
        case .mandarin:
            switch hand.category {
            case .rocket: return "王炸火箭，大王配小王"
            case .bomb: return "炸弹4张\(rankSpoken)"
            case .airplaneWithWings: return "大飞机带翅膀\(hand.cards.count)张"
            case .airplanePure: return "纯飞机\(hand.cards.count)张"
            case .consecutivePairs:
                let startRank = hand.cards.min()?.rank.spokenName(for: language) ?? ""
                let endRank = hand.cards.max()?.rank.spokenName(for: language) ?? ""
                return "连对\(hand.cards.count)张：\(startRank)到\(endRank)"
            case .straight:
                let startRank = hand.cards.min()?.rank.spokenName(for: language) ?? ""
                let endRank = hand.cards.max()?.rank.spokenName(for: language) ?? ""
                return "顺子\(hand.cards.count)张：\(startRank)到\(endRank)"
            case .fourWithTwoPairs: return "四带两对8张：4张\(rankSpoken)带两对"
            case .fourWithTwoSingles: return "四带二6张：4张\(rankSpoken)带两张"
            case .trioWithPair: return "三带二5张：三张\(rankSpoken)带一对"
            case .trioWithSingle: return "三带一4张：三张\(rankSpoken)带单张"
            case .trio: return "三条3张：三张\(rankSpoken)"
            case .pair: return "对子：对\(rankSpoken)"
            case .single: return describeCard(hand.keyCard, language: language)
            }
        case .english:
            switch hand.category {
            case .rocket: return "Rocket, Red and Black Joker"
            case .bomb: return "Bomb four \(rankSpoken)s"
            case .airplaneWithWings: return "Airplane with wings \(hand.cards.count) cards"
            case .airplanePure: return "Airplane \(hand.cards.count) cards"
            case .consecutivePairs:
                let startRank = hand.cards.min()?.rank.spokenName(for: language) ?? ""
                let endRank = hand.cards.max()?.rank.spokenName(for: language) ?? ""
                return "Consecutive pairs \(hand.cards.count) cards: \(startRank) to \(endRank)"
            case .straight:
                let startRank = hand.cards.min()?.rank.spokenName(for: language) ?? ""
                let endRank = hand.cards.max()?.rank.spokenName(for: language) ?? ""
                return "Straight \(hand.cards.count) cards: \(startRank) to \(endRank)"
            case .fourWithTwoPairs: return "Four \(rankSpoken)s with two pairs"
            case .fourWithTwoSingles: return "Four \(rankSpoken)s with two singles"
            case .trioWithPair: return "Three \(rankSpoken)s with pair"
            case .trioWithSingle: return "Three \(rankSpoken)s with single"
            case .trio: return "Three \(rankSpoken)s"
            case .pair: return "Pair of \(rankSpoken)s"
            case .single: return describeCard(hand.keyCard, language: language)
            }
        }
    }
    
    public static func describeCddHand(_ hand: CddHand, language: VoiceLanguage) -> String {
        let rankSpoken = hand.keyCard.rank.spokenName(for: language)
        switch language {
        case .cantonese:
            switch hand.category {
            case .straightFlush: return "同花順，最大\(describeCard(hand.keyCard, language: language))"
            case .fourOfAKind: return "四條鐵支，4隻\(rankSpoken)帶單隻"
            case .fullHouse: return "葫蘆，三隻\(rankSpoken)帶一對"
            case .flush: return "同花，\(describeSuit(hand.keyCard.suit, language: language))，最大\(rankSpoken)"
            case .straight: return "蛇，最大到\(rankSpoken)"
            case .trio: return "三條\(rankSpoken)"
            case .pair: return "對子：一對\(rankSpoken)"
            case .single: return describeCard(hand.keyCard, language: language)
            }
        case .mandarin:
            switch hand.category {
            case .straightFlush: return "同花顺，最大\(describeCard(hand.keyCard, language: language))"
            case .fourOfAKind: return "铁支四条，4张\(rankSpoken)带单张"
            case .fullHouse: return "葫芦，三张\(rankSpoken)带一对"
            case .flush: return "同花，\(describeSuit(hand.keyCard.suit, language: language))，最大\(rankSpoken)"
            case .straight: return "顺子，最大到\(rankSpoken)"
            case .trio: return "三条\(rankSpoken)"
            case .pair: return "对子：对\(rankSpoken)"
            case .single: return describeCard(hand.keyCard, language: language)
            }
        case .english:
            switch hand.category {
            case .straightFlush: return "Straight Flush, high card \(describeCard(hand.keyCard, language: language))"
            case .fourOfAKind: return "Four of a kind \(rankSpoken)s"
            case .fullHouse: return "Full House with \(rankSpoken)s"
            case .flush: return "Flush in \(describeSuit(hand.keyCard.suit, language: language)), high \(rankSpoken)"
            case .straight: return "Straight up to \(rankSpoken)"
            case .trio: return "Three \(rankSpoken)s"
            case .pair: return "Pair of \(rankSpoken)s"
            case .single: return describeCard(hand.keyCard, language: language)
            }
        }
    }
    
    public static func describeCard(_ card: PlayingCard, language: VoiceLanguage) -> String {
        if card.isJoker {
            return card.rank.spokenName(for: language)
        }
        let suitStr = describeSuit(card.suit, language: language)
        if language == .english {
            return "\(suitStr) \(card.rank.spokenName(for: language))"
        } else {
            return "\(suitStr)\(card.rank.spokenName(for: language))"
        }
    }
    
    public static func describeSuit(_ suit: CardSuit, language: VoiceLanguage) -> String {
        switch language {
        case .cantonese, .mandarin:
            switch suit {
            case .spade: return "黑桃"
            case .heart: return "紅桃"
            case .club: return "梅花"
            case .diamond: return "方塊"
            case .joker: return ""
            }
        case .english:
            return suit.rawValue.capitalized
        }
    }
}

