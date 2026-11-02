import SwiftUI

/**
 * HandsExplorerView: Native iOS Sheet presenting all tactical card combinations.
 * Allows visual inspection and category filtering with native SwiftUI components.
 */
public struct HandsExplorerView: View {
    public let cards: [PlayingCard]
    public let gameMode: GameMode
    public let ddzRole: DdzRole
    public let cddVariation: CddVariation
    public let onSelectHand: ((_ handDescription: String) -> Void)?
    
    @Environment(\.presentationMode) var presentationMode
    @State private var selectedCategoryFilter: String = "all"
    
    public init(
        cards: [PlayingCard],
        gameMode: GameMode = .douDiZhu,
        ddzRole: DdzRole = .peasant,
        cddVariation: CddVariation = .mainland,
        onSelectHand: ((String) -> Void)? = nil
    ) {
        self.cards = cards
        self.gameMode = gameMode
        self.ddzRole = ddzRole
        self.cddVariation = cddVariation
        self.onSelectHand = onSelectHand
    }
    
    public var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // Category Filter Segmented Picker
                Picker("类别筛选", selection: $selectedCategoryFilter) {
                    Text("全部").tag("all")
                    if gameMode == .douDiZhu {
                        Text("💣 炸弹/火箭").tag("bomb")
                        Text("✈️ 飞机/带牌").tag("airplane")
                        Text("🪜 顺子/连对").tag("straight")
                        Text("👥 对子/单牌").tag("pair_single")
                    } else {
                        Text("🌟 五张大牌").tag("five_cards")
                        Text("🪜 顺子/同花").tag("straight_flush")
                        Text("👥 对子/单牌").tag("pair_single")
                    }
                }
                .pickerStyle(.segmented)
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
                .background(Color(UIColor.secondarySystemBackground))
                
                // Combinations List
                if gameMode == .douDiZhu {
                    ddzHandsListView
                } else {
                    cddHandsListView
                }
            }
            .navigationTitle(gameMode == .douDiZhu ? "🎴 斗地主可用牌型" : "♠️ 锄大地可用牌型")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("关闭") {
                        presentationMode.wrappedValue.dismiss()
                    }
                }
            }
        }
    }
    
    // MARK: - DDZ Hands List
    private var ddzHandsListView: some View {
        let allHands = DdzHandFinder.findAllHands(cards: cards)
        let filteredHands = allHands.filter { hand in
            switch selectedCategoryFilter {
            case "bomb":
                return hand.category == .rocket || hand.category == .bomb
            case "airplane":
                return hand.category == .airplaneWithWings || hand.category == .airplanePure ||
                    hand.category == .fourWithTwoPairs || hand.category == .fourWithTwoSingles ||
                    hand.category == .trioWithPair || hand.category == .trioWithSingle || hand.category == .trio
            case "straight":
                return hand.category == .straight || hand.category == .consecutivePairs
            case "pair_single":
                return hand.category == .pair || hand.category == .single
            default:
                return true
            }
        }
        
        return Group {
            if filteredHands.isEmpty {
                emptyPlaceholder(message: "该分类下暂无可用组合")
            } else {
                List {
                    ForEach(Array(filteredHands.enumerated()), id: \.element.id) { index, hand in
                        VStack(alignment: .leading, spacing: 8) {
                            HStack {
                                if index == 0 && selectedCategoryFilter == "all" {
                                    Text("🌟 最佳推荐")
                                        .font(.caption2)
                                        .bold()
                                        .foregroundColor(.white)
                                        .padding(.horizontal, 6)
                                        .padding(.vertical, 2)
                                        .background(Color.orange)
                                        .clipShape(Capsule())
                                }
                                
                                Text(hand.descriptionText)
                                    .font(.headline)
                                    .foregroundColor(.primary)
                                
                                Spacer()
                                
                                Text("\(hand.cards.count)张")
                                    .font(.subheadline)
                                    .foregroundColor(.secondary)
                            }
                            
                            // Horizontal card badges
                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack(spacing: 6) {
                                    ForEach(hand.cards, id: \.self) { card in
                                        AccessibleCardView(
                                            suit: card.suit.rawValue,
                                            rank: card.rank.label,
                                            isRed: card.isRed
                                        )
                                        .scaleEffect(0.85)
                                    }
                                }
                            }
                        }
                        .padding(.vertical, 4)
                        .contentShape(Rectangle())
                        .onTapGesture {
                            onSelectHand?(hand.descriptionText)
                            presentationMode.wrappedValue.dismiss()
                        }
                    }
                }
                .listStyle(.insetGrouped)
            }
        }
    }
    
    // MARK: - CDD Hands List
    private var cddHandsListView: some View {
        let allHands = CddHandFinder.findAllHands(cards: cards, variation: cddVariation)
        let filteredHands = allHands.filter { hand in
            switch selectedCategoryFilter {
            case "five_cards":
                return hand.category == .straightFlush || hand.category == .fourOfAKind || hand.category == .fullHouse
            case "straight_flush":
                return hand.category == .straight || hand.category == .flush || hand.category == .straightFlush
            case "pair_single":
                return hand.category == .pair || hand.category == .single || hand.category == .trio
            default:
                return true
            }
        }
        
        return Group {
            if filteredHands.isEmpty {
                emptyPlaceholder(message: "该分类下暂无可用组合")
            } else {
                List {
                    ForEach(Array(filteredHands.enumerated()), id: \.element.id) { index, hand in
                        VStack(alignment: .leading, spacing: 8) {
                            HStack {
                                if index == 0 && selectedCategoryFilter == "all" {
                                    Text("🌟 最佳推荐")
                                        .font(.caption2)
                                        .bold()
                                        .foregroundColor(.white)
                                        .padding(.horizontal, 6)
                                        .padding(.vertical, 2)
                                        .background(Color.blue)
                                        .clipShape(Capsule())
                                }
                                
                                Text(hand.descriptionText)
                                    .font(.headline)
                                    .foregroundColor(.primary)
                                
                                Spacer()
                                
                                Text("\(hand.cards.count)张")
                                    .font(.subheadline)
                                    .foregroundColor(.secondary)
                            }
                            
                            // Horizontal card badges
                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack(spacing: 6) {
                                    ForEach(hand.cards, id: \.self) { card in
                                        AccessibleCardView(
                                            suit: card.suit.rawValue,
                                            rank: card.rank.label,
                                            isRed: card.isRed
                                        )
                                        .scaleEffect(0.85)
                                    }
                                }
                            }
                        }
                        .padding(.vertical, 4)
                        .contentShape(Rectangle())
                        .onTapGesture {
                            onSelectHand?(hand.descriptionText)
                            presentationMode.wrappedValue.dismiss()
                        }
                    }
                }
                .listStyle(.insetGrouped)
            }
        }
    }
    
    private func emptyPlaceholder(message: String) -> some View {
        VStack(spacing: 12) {
            Spacer()
            Image(systemName: "rectangle.stack.badge.person.crop")
                .font(.system(size: 48))
                .foregroundColor(.secondary)
            Text(message)
                .font(.subheadline)
                .foregroundColor(.secondary)
            Spacer()
        }
    }
}

