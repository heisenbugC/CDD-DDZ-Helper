import Foundation

// MARK: - Suits and Ranks

public enum CardSuit: String, CaseIterable, Comparable {
    case diamond = "diamond"
    case club = "club"
    case heart = "heart"
    case spade = "spade"
    case joker = "joker"
    
    public var symbol: String {
        switch self {
        case .spade: return "♠"
        case .heart: return "♥"
        case .club: return "♣"
        case .diamond: return "♦"
        case .joker: return "🃏"
        }
    }
    
    public var isRed: Bool {
        return self == .heart || self == .diamond
    }
    
    // CDD suit order: Diamond < Club < Heart < Spade
    public var cddWeight: Int {
        switch self {
        case .diamond: return 1
        case .club: return 2
        case .heart: return 3
        case .spade: return 4
        case .joker: return 5
        }
    }
    
    public static func < (lhs: CardSuit, rhs: CardSuit) -> Bool {
        return lhs.cddWeight < rhs.cddWeight
    }
}

public enum CardRank: String, CaseIterable, Comparable {
    case three = "3"
    case four = "4"
    case five = "5"
    case six = "6"
    case seven = "7"
    case eight = "8"
    case nine = "9"
    case ten = "10"
    case jack = "J"
    case queen = "Q"
    case king = "K"
    case ace = "A"
    case two = "2"
    case blackJoker = "Black Joker"
    case redJoker = "Red Joker"
    
    public var label: String {
        switch self {
        case .blackJoker: return "小王"
        case .redJoker: return "大王"
        default: return rawValue
        }
    }
    
    public var weight: Int {
        switch self {
        case .three: return 3
        case .four: return 4
        case .five: return 5
        case .six: return 6
        case .seven: return 7
        case .eight: return 8
        case .nine: return 9
        case .ten: return 10
        case .jack: return 11
        case .queen: return 12
        case .king: return 13
        case .ace: return 14
        case .two: return 15
        case .blackJoker: return 16
        case .redJoker: return 17
        }
    }
    
    public static func < (lhs: CardRank, rhs: CardRank) -> Bool {
        return lhs.weight < rhs.weight
    }
}

// MARK: - PlayingCard

public struct PlayingCard: Identifiable, Hashable, Comparable {
    public let id: Int // 0..53 matching YOLO class IDs
    public let suit: CardSuit
    public let rank: CardRank
    
    public var isRed: Bool {
        if rank == .redJoker { return true }
        if rank == .blackJoker { return false }
        return suit.isRed
    }
    
    public var isJoker: Bool {
        return rank == .blackJoker || rank == .redJoker
    }
    
    public static func < (lhs: PlayingCard, rhs: PlayingCard) -> Bool {
        if lhs.rank.weight != rhs.rank.weight {
            return lhs.rank.weight < rhs.rank.weight
        }
        return lhs.suit < rhs.suit
    }
    
    // Class mapping for 54 YOLO classes
    public static let classNames: [String] = [
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
    ]
    
    public static func fromClassId(_ classId: Int) -> PlayingCard? {
        guard classId >= 0 && classId < classNames.count else { return nil }
        return fromLabel(classNames[classId], id: classId)
    }
    
    public static func fromLabel(_ label: String, id: Int = -1) -> PlayingCard? {
        let norm = label.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if norm == "black_joker" || norm == "black joker" || norm == "52" {
            return PlayingCard(id: 52, suit: .joker, rank: .blackJoker)
        }
        if norm == "red_joker" || norm == "red joker" || norm == "53" {
            return PlayingCard(id: 53, suit: .joker, rank: .redJoker)
        }
        
        let parts = norm.components(separatedBy: "_of_")
        guard parts.count == 2 else { return nil }
        
        let rankStr = parts[0]
        let suitStr = parts[1]
        
        let rank: CardRank
        switch rankStr {
        case "3": rank = .three
        case "4": rank = .four
        case "5": rank = .five
        case "6": rank = .six
        case "7": rank = .seven
        case "8": rank = .eight
        case "9": rank = .nine
        case "10": rank = .ten
        case "jack", "j": rank = .jack
        case "queen", "q": rank = .queen
        case "king", "k": rank = .king
        case "ace", "a": rank = .ace
        case "2": rank = .two
        default: return nil
        }
        
        let suit: CardSuit
        switch suitStr {
        case "diamonds", "diamond", "d": suit = .diamond
        case "clubs", "club", "c": suit = .club
        case "hearts", "heart", "h": suit = .heart
        case "spades", "spade", "s": suit = .spade
        default: return nil
        }
        
        let resolvedId = (id >= 0) ? id : (classNames.firstIndex(of: norm) ?? -1)
        return PlayingCard(id: resolvedId, suit: suit, rank: rank)
    }
}

// MARK: - Hand Models (DDZ)

public enum DdzHandCategory: Int, Comparable {
    case rocket = 1
    case bomb = 2
    case airplaneWithWings = 3
    case airplanePure = 4
    case consecutivePairs = 5
    case straight = 6
    case fourWithTwoPairs = 7
    case fourWithTwoSingles = 8
    case trioWithPair = 9
    case trioWithSingle = 10
    case trio = 11
    case pair = 12
    case single = 13
    
    public static func < (lhs: DdzHandCategory, rhs: DdzHandCategory) -> Bool {
        return lhs.rawValue < rhs.rawValue
    }
}

public struct DdzHand: Identifiable, Hashable {
    public let id = UUID()
    public let category: DdzHandCategory
    public let cards: [PlayingCard]
    public let keyCard: PlayingCard
    public let descriptionText: String
}

public class DdzHandFinder {
    public static func findAllHands(cards: [PlayingCard]) -> [DdzHand] {
        var allHands: [DdzHand] = []
        let grouped = Dictionary(grouping: cards, by: { $0.rank })
        
        // 1. Singles
        for card in cards {
            allHands.append(DdzHand(
                category: .single,
                cards: [card],
                keyCard: card,
                descriptionText: card.isJoker ? (card.rank == .redJoker ? "单张大王" : "单张小王") : "单张\(card.rank.label)"
            ))
        }
        
        // 2. Pairs
        for (_, group) in grouped where group.count >= 2 {
            for i in 0..<group.count {
                for j in (i + 1)..<group.count {
                    let pairCards = [group[i], group[j]]
                    allHands.append(DdzHand(
                        category: .pair,
                        cards: pairCards,
                        keyCard: pairCards.max()!,
                        descriptionText: "对子：对\(group[i].rank.label)"
                    ))
                }
            }
        }
        
        // 3. Trios
        for (_, group) in grouped where group.count >= 3 {
            for i in 0..<group.count {
                for j in (i + 1)..<group.count {
                    for k in (j + 1)..<group.count {
                        let trioCards = [group[i], group[j], group[k]]
                        allHands.append(DdzHand(
                            category: .trio,
                            cards: trioCards,
                            keyCard: trioCards.max()!,
                            descriptionText: "三条：三张\(group[i].rank.label)"
                        ))
                    }
                }
            }
        }
        
        // 4. Bombs
        for (_, group) in grouped where group.count == 4 {
            allHands.append(DdzHand(
                category: .bomb,
                cards: group,
                keyCard: group.max()!,
                descriptionText: "炸弹4张\(group[0].rank.label)"
            ))
        }
        
        // 5. Rocket (King Bomb)
        let blackJoker = cards.first(where: { $0.rank == .blackJoker })
        let redJoker = cards.first(where: { $0.rank == .redJoker })
        if let bj = blackJoker, let rj = redJoker {
            allHands.append(DdzHand(
                category: .rocket,
                cards: [bj, rj],
                keyCard: rj,
                descriptionText: "王炸火箭，大王配小王"
            ))
        }
        
        // Representative trios and pairs for combinations
        let repTrios = grouped.filter { $0.value.count >= 3 }.mapValues { Array($0.value.prefix(3)) }
        let repPairs = grouped.filter { $0.value.count >= 2 }.mapValues { Array($0.value.prefix(2)) }
        
        // 6. Trios with single / pair
        for (trioRank, trioCards) in repTrios {
            let remainSingles = cards.filter { !trioCards.contains($0) && $0.rank != trioRank }
            let distinctSingles = Dictionary(grouping: remainSingles, by: { $0.rank }).compactMap { $0.value.first }
            for single in distinctSingles {
                allHands.append(DdzHand(
                    category: .trioWithSingle,
                    cards: trioCards + [single],
                    keyCard: trioCards.max()!,
                    descriptionText: "三带一：三张\(trioRank.label)带单张\(single.rank.label)"
                ))
            }
            
            for (pairRank, pairCards) in repPairs where pairRank != trioRank {
                allHands.append(DdzHand(
                    category: .trioWithPair,
                    cards: trioCards + pairCards,
                    keyCard: trioCards.max()!,
                    descriptionText: "三带二：三张\(trioRank.label)带一对\(pairRank.label)"
                ))
            }
        }
        
        // 7. Straights (Length >= 5, ranks 3 to Ace only, no 2 or Jokers)
        let straightRanks = grouped.keys
            .filter { $0.weight <= CardRank.ace.weight }
            .sorted(by: { $0.weight < $1.weight })
        
        if straightRanks.count >= 5 {
            for i in 0..<straightRanks.count {
                for len in 5...(straightRanks.count - i) {
                    let subRanks = Array(straightRanks[i..<(i + len)])
                    if subRanks.last!.weight - subRanks.first!.weight == len - 1 {
                        let straightCards = subRanks.map { grouped[$0]!.first! }
                        allHands.append(DdzHand(
                            category: .straight,
                            cards: straightCards,
                            keyCard: straightCards.last!,
                            descriptionText: "顺子\(len)张：\(subRanks.first!.label)到\(subRanks.last!.label)"
                        ))
                    }
                }
            }
        }
        
        // 8. Consecutive Pairs (Length >= 3 pairs, ranks 3 to Ace only)
        let pairRanks = repPairs.keys
            .filter { $0.weight <= CardRank.ace.weight }
            .sorted(by: { $0.weight < $1.weight })
        
        if pairRanks.count >= 3 {
            for i in 0..<pairRanks.count {
                for len in 3...(pairRanks.count - i) {
                    let subRanks = Array(pairRanks[i..<(i + len)])
                    if subRanks.last!.weight - subRanks.first!.weight == len - 1 {
                        let consecutiveCards = subRanks.flatMap { repPairs[$0]! }
                        allHands.append(DdzHand(
                            category: .consecutivePairs,
                            cards: consecutiveCards,
                            keyCard: consecutiveCards.max()!,
                            descriptionText: "连对\(len * 2)张：\(subRanks.first!.label)到\(subRanks.last!.label)"
                        ))
                    }
                }
            }
        }
        
        // 9. Airplanes (Length >= 2 consecutive trios, ranks 3 to Ace only)
        let trioRanks = repTrios.keys
            .filter { $0.weight <= CardRank.ace.weight }
            .sorted(by: { $0.weight < $1.weight })
        
        if trioRanks.count >= 2 {
            for i in 0..<trioRanks.count {
                for len in 2...(trioRanks.count - i) {
                    let subRanks = Array(trioRanks[i..<(i + len)])
                    if subRanks.last!.weight - subRanks.first!.weight == len - 1 {
                        let airplaneTrios = subRanks.flatMap { repTrios[$0]! }
                        let trioSet = Set(airplaneTrios)
                        
                        allHands.append(DdzHand(
                            category: .airplanePure,
                            cards: airplaneTrios,
                            keyCard: airplaneTrios.max()!,
                            descriptionText: "纯飞机\(len * 3)张：\(subRanks.first!.label)到\(subRanks.last!.label)"
                        ))
                        
                        let availableSingles = cards.filter { !trioSet.contains($0) }
                        if availableSingles.count >= len {
                            let wings = Array(availableSingles.prefix(len))
                            allHands.append(DdzHand(
                                category: .airplaneWithWings,
                                cards: airplaneTrios + wings,
                                keyCard: airplaneTrios.max()!,
                                descriptionText: "飞机带单牌\(len * 4)张：\(subRanks.first!.label)到\(subRanks.last!.label)带\(len)单"
                            ))
                        }
                        
                        let availablePairs = repPairs.filter { !subRanks.contains($0.key) }.values
                        if availablePairs.count >= len {
                            let wingPairs = Array(availablePairs.prefix(len)).flatMap { $0 }
                            allHands.append(DdzHand(
                                category: .airplaneWithWings,
                                cards: airplaneTrios + wingPairs,
                                keyCard: airplaneTrios.max()!,
                                descriptionText: "飞机带对子\(len * 5)张：\(subRanks.first!.label)到\(subRanks.last!.label)带\(len)对"
                            ))
                        }
                    }
                }
            }
        }
        
        // 10. Four with Two
        let bombs = allHands.filter { $0.category == .bomb }
        for bomb in bombs {
            let bombSet = Set(bomb.cards)
            let remaining = cards.filter { !bombSet.contains($0) }
            if remaining.count >= 2 {
                let twoSingles = Array(remaining.prefix(2))
                allHands.append(DdzHand(
                    category: .fourWithTwoSingles,
                    cards: bomb.cards + twoSingles,
                    keyCard: bomb.keyCard,
                    descriptionText: "四带二6张：4张\(bomb.keyCard.rank.label)带两单"
                ))
            }
            let remainPairs = repPairs.filter { $0.key != bomb.keyCard.rank }.values
            if remainPairs.count >= 2 {
                let twoPairs = Array(remainPairs.prefix(2)).flatMap { $0 }
                allHands.append(DdzHand(
                    category: .fourWithTwoPairs,
                    cards: bomb.cards + twoPairs,
                    keyCard: bomb.keyCard,
                    descriptionText: "四带两对8张：4张\(bomb.keyCard.rank.label)带两对"
                ))
            }
        }
        
        // Sort hands in descending tactical priority
        return allHands.sorted { h1, h2 in
            if h1.category != h2.category {
                return h1.category < h2.category
            }
            if h1.cards.count != h2.cards.count {
                return h1.cards.count > h2.cards.count
            }
            return h1.keyCard > h2.keyCard
        }
    }
}

// MARK: - Hand Models (CDD / Cho Dai Dee)

public enum CddHandCategory: Int, Comparable {
    case straightFlush = 1
    case fourOfAKind = 2
    case fullHouse = 3
    case flush = 4
    case straight = 5
    case trio = 6
    case pair = 7
    case single = 8
    
    public static func < (lhs: CddHandCategory, rhs: CddHandCategory) -> Bool {
        return lhs.rawValue < rhs.rawValue
    }
}

public struct CddHand: Identifiable, Hashable {
    public let id = UUID()
    public let category: CddHandCategory
    public let cards: [PlayingCard]
    public let keyCard: PlayingCard
    public let descriptionText: String
}

public class CddHandFinder {
    public static func findAllHands(cards: [PlayingCard], variation: CddVariation = .mainland) -> [CddHand] {
        var allHands: [CddHand] = []
        let nonJokers = cards.filter { !$0.isJoker }.sorted()
        let grouped = Dictionary(grouping: nonJokers, by: { $0.rank })
        
        // 1. Singles
        for card in nonJokers {
            allHands.append(CddHand(
                category: .single,
                cards: [card],
                keyCard: card,
                descriptionText: "单张\(card.suit.symbol)\(card.rank.label)"
            ))
        }
        
        // 2. Pairs
        for (_, group) in grouped where group.count >= 2 {
            for i in 0..<group.count {
                for j in (i + 1)..<group.count {
                    let pairCards = [group[i], group[j]]
                    allHands.append(CddHand(
                        category: .pair,
                        cards: pairCards,
                        keyCard: pairCards.max()!,
                        descriptionText: "对子：对\(group[0].rank.label)"
                    ))
                }
            }
        }
        
        // 3. Trios
        for (_, group) in grouped where group.count >= 3 {
            for i in 0..<group.count {
                for j in (i + 1)..<group.count {
                    for k in (j + 1)..<group.count {
                        let trioCards = [group[i], group[j], group[k]]
                        allHands.append(CddHand(
                            category: .trio,
                            cards: trioCards,
                            keyCard: trioCards.max()!,
                            descriptionText: "三条：三张\(group[0].rank.label)"
                        ))
                    }
                }
            }
        }
        
        // 4. Five-card combinations
        let combos = combinations(nonJokers, k: 5)
        for combo in combos {
            let isConsecutive = isStrictlyConsecutive(combo)
            let isSameSuit = combo.map { $0.suit }.allSatisfy { $0 == combo[0].suit }
            
            if isConsecutive && isSameSuit {
                allHands.append(CddHand(
                    category: .straightFlush,
                    cards: combo,
                    keyCard: combo.max()!,
                    descriptionText: "同花顺，最大\(combo.max()!.suit.symbol)\(combo.max()!.rank.label)"
                ))
            } else if isConsecutive {
                allHands.append(CddHand(
                    category: .straight,
                    cards: combo,
                    keyCard: combo.max()!,
                    descriptionText: "顺子(蛇)，最大到\(combo.max()!.rank.label)"
                ))
            } else if isSameSuit {
                allHands.append(CddHand(
                    category: .flush,
                    cards: combo,
                    keyCard: combo.max()!,
                    descriptionText: "同花\(combo[0].suit.symbol)，最大\(combo.max()!.rank.label)"
                ))
            }
        }
        
        // 5. Four of a kind
        for (_, group) in grouped where group.count == 4 {
            let remain = nonJokers.filter { !group.contains($0) }
            for kicker in remain {
                let fourCards = group + [kicker]
                allHands.append(CddHand(
                    category: .fourOfAKind,
                    cards: fourCards,
                    keyCard: group.max()!,
                    descriptionText: "铁支四条：4张\(group[0].rank.label)"
                ))
            }
        }
        
        // 6. Full House (葫芦)
        let trioCandidates = grouped.filter { $0.value.count >= 3 }
        let pairCandidates = grouped.filter { $0.value.count >= 2 }
        for (tRank, tCards) in trioCandidates {
            let trios = (tCards.count == 4) ? combinations(tCards, k: 3) : [tCards]
            for (pRank, pCards) in pairCandidates where pRank != tRank {
                let pairs = combinations(pCards, k: 2)
                for trio in trios {
                    for pair in pairs {
                        let fhCards = trio + pair
                        allHands.append(CddHand(
                            category: .fullHouse,
                            cards: fhCards,
                            keyCard: trio.max()!,
                            descriptionText: "葫芦：三张\(tRank.label)带一对\(pRank.label)"
                        ))
                    }
                }
            }
        }
        
        // Deduplicate and sort
        return allHands.sorted { h1, h2 in
            let p1 = priority(h1.category, variation: variation)
            let p2 = priority(h2.category, variation: variation)
            if p1 != p2 {
                return p1 < p2
            }
            return h1.keyCard > h2.keyCard
        }
    }
    
    private static func priority(_ cat: CddHandCategory, variation: CddVariation) -> Int {
        switch cat {
        case .straightFlush: return 1
        case .fourOfAKind: return 2
        case .fullHouse: return 3
        case .flush: return (variation == .mainland) ? 4 : 5
        case .straight: return (variation == .mainland) ? 5 : 4
        case .trio: return 6
        case .pair: return 7
        case .single: return 8
        }
    }
    
    public static func isStrictlyConsecutive(_ combo: [PlayingCard]) -> Bool {
        guard combo.count == 5 else { return false }
        let hasThree = combo.contains { $0.rank == .three }
        let hasTwo = combo.contains { $0.rank == .two }
        if hasThree && hasTwo { return false } // Strictly no wrapping between 3 and 2
        
        let sortedWeights = combo.map { $0.rank.weight }.sorted()
        for i in 0..<4 {
            if sortedWeights[i + 1] - sortedWeights[i] != 1 {
                return false
            }
        }
        return true
    }
    
    private static func combinations<T>(_ elements: [T], k: Int) -> [[T]] {
        guard k > 0 && elements.count >= k else { return [] }
        if k == elements.count { return [elements] }
        if k == 1 { return elements.map { [$0] } }
        
        var result: [[T]] = []
        let head = elements[0]
        let sub = Array(elements[1...])
        
        let withHead = combinations(sub, k: k - 1).map { [head] + $0 }
        let withoutHead = combinations(sub, k: k)
        
        result.append(contentsOf: withHead)
        result.append(contentsOf: withoutHead)
        return result
    }
}

