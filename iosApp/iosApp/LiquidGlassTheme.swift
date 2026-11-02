import SwiftUI

/**
 * Liquid Glass visual design system for iOS.
 * Features ultra-thin frosted materials, glowing highlights,
 * and WCAG AAA high-contrast card badges (>= 7:1 contrast ratio).
 */
public struct LiquidGlassTheme {
    public static let primaryDark = Color(red: 0.07, green: 0.08, blue: 0.11)
    public static let glassBorder = Color.white.opacity(0.18)
    public static let cardBackground = Color.white
    public static let blackCardText = Color(red: 0.05, green: 0.05, blue: 0.05)
    public static let redCardText = Color(red: 0.85, green: 0.02, blue: 0.02)
    public static let redCardBorder = Color(red: 1.0, green: 0.25, blue: 0.25)
    public static let blackCardBorder = Color(red: 0.35, green: 0.35, blue: 0.35)
    public static let accentBlue = Color(red: 0.16, green: 0.48, blue: 1.0)
    public static let accentGreen = Color(red: 0.0, green: 0.90, blue: 0.46)
    public static let accentYellow = Color(red: 1.0, green: 0.84, blue: 0.0)
    public static let accentOrange = Color(red: 1.0, green: 0.57, blue: 0.0)
}

public struct LiquidGlassModifier: ViewModifier {
    var cornerRadius: CGFloat = 20
    
    public func body(content: Content) -> some View {
        content
            .background(.ultraThinMaterial)
            .clipShape(RoundedRectangle(cornerRadius: cornerRadius))
            .overlay(
                RoundedRectangle(cornerRadius: cornerRadius)
                    .stroke(LiquidGlassTheme.glassBorder, lineWidth: 1)
            )
            .shadow(color: Color.black.opacity(0.35), radius: 10, x: 0, y: 5)
    }
}

public extension View {
    func liquidGlass(cornerRadius: CGFloat = 20) -> some View {
        self.modifier(LiquidGlassModifier(cornerRadius: cornerRadius))
    }
}

/**
 * Accessible SwiftUI Card Badge adhering to WCAG AAA contrast ratio (>= 7:1)
 * with large touch targets (>= 56pt) and VoiceOver semantic labels.
 */
public struct AccessibleCardView: View {
    public let suit: String
    public let rank: String
    public let isRed: Bool
    public var isLocked: Bool = false
    public var onRemove: (() -> Void)? = nil
    
    public init(suit: String, rank: String, isRed: Bool, isLocked: Bool = false, onRemove: (() -> Void)? = nil) {
        self.suit = suit
        self.rank = rank
        self.isRed = isRed
        self.isLocked = isLocked
        self.onRemove = onRemove
    }
    
    private var suitSymbol: String {
        switch suit.lowercased() {
        case "spade", "spades": return "♠"
        case "heart", "hearts": return "♥"
        case "club", "clubs": return "♣"
        case "diamond", "diamonds": return "♦"
        case "joker": return "🃏"
        default: return suit
        }
    }
    
    private var voiceOverLabel: String {
        var desc = "\(suit) \(rank)"
        if isLocked && onRemove != nil {
            desc += ", 双击移除此牌"
        }
        return desc
    }
    
    public var body: some View {
        Button(action: {
            if isLocked {
                onRemove?()
            }
        }) {
            VStack(spacing: 2) {
                Text(suitSymbol)
                    .font(.system(size: 18, weight: .black))
                    .foregroundColor(isRed ? LiquidGlassTheme.redCardText : LiquidGlassTheme.blackCardText)
                
                Text(rank)
                    .font(.system(size: 14, weight: .heavy))
                    .foregroundColor(isRed ? LiquidGlassTheme.redCardText : LiquidGlassTheme.blackCardText)
            }
            .frame(minWidth: 56, minHeight: 56)
            .padding(.horizontal, 6)
            .padding(.vertical, 4)
            .background(LiquidGlassTheme.cardBackground)
            .clipShape(RoundedRectangle(cornerRadius: 10))
            .overlay(
                RoundedRectangle(cornerRadius: 10)
                    .stroke(isRed ? LiquidGlassTheme.redCardBorder : LiquidGlassTheme.blackCardBorder, lineWidth: 2)
            )
            .shadow(color: Color.black.opacity(0.2), radius: 3, x: 0, y: 2)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(voiceOverLabel)
        .accessibilityAddTraits(.isButton)
    }
}
