import Foundation

/**
 * Supported Game Voice Commands
 */
public enum GameVoiceCommand: String, CaseIterable {
    case lockCards = "LOCK_CARDS"
    case readCards = "READ_CARDS"
    case newCards = "NEW_CARDS"
    case switchMode = "SWITCH_MODE"
    case switchToCdd = "SWITCH_TO_CDD"
    case switchToDdz = "SWITCH_TO_DDZ"
    case switchRole = "SWITCH_ROLE"
    case switchToLandlord = "SWITCH_TO_LANDLORD"
    case switchToPeasant = "SWITCH_TO_PEASANT"
    
    public var description: String {
        switch self {
        case .lockCards: return "锁定手牌"
        case .readCards: return "朗读报牌"
        case .newCards: return "重置/洗牌"
        case .switchMode: return "切换游戏模式"
        case .switchToCdd: return "切换到锄大地"
        case .switchToDdz: return "切换到斗地主"
        case .switchRole: return "切换地主/农民身份"
        case .switchToLandlord: return "成为地主"
        case .switchToPeasant: return "成为农民"
        }
    }
}

/**
 * Pure same-phonetic, same-tone (同音同调) Voice Trigger Matcher for Swift.
 * Accurately handles homophones, dialect variations (Mandarin, Cantonese),
 * and language-scoped matching to strictly honor user voice settings.
 */
public class VoiceCommandMatcher {
    
    // 1. LOCK_CARDS:
    // Mandarin: 锁定, 锁住, 锁牌, 锁卡, 锁定卡片
    // Cantonese: 鎖定, 鎖牌, 定牌, 得喇/得啦, 定咗, 停, 搞掂/搞定, 定晒
    private static let lockChineseRegex = try! NSRegularExpression(
        pattern: "[锁鎖所索琐瑣]([定订訂锭錠]|[住注助驻駐祝铸鑄]|手?([牌排徘]|卡))|[锁鎖]牌|定牌|得[喇啦]|好[喇啦]|定[咗左]|已[锁鎖]定|搞[掂定][喇啦]?|搞好|收牌|停牌?|定晒",
        options: [.caseInsensitive]
    )
    private static let lockEnglishKeywords = [
        "lockcard", "lockcards", "lockthecard", "lockthecards",
        "lockhand", "lockthehand", "lockmycards", "lockmycard", "lock"
    ]
    
    // 2. NEW_CARDS:
    private static let newRoundChineseRegex = try! NSRegularExpression(
        pattern: "([新心辛欣薪馨锌身]|[下夏])一?([局橘菊焗桔]|[盘盤]|[轮輪])|洗牌|细牌|細牌|西牌",
        options: [.caseInsensitive]
    )
    private static let newCardsChineseRegex = try! NSRegularExpression(
        pattern: "[新心辛欣薪馨锌身](手)?([牌排徘]|卡)",
        options: [.caseInsensitive]
    )
    private static let newDealChineseRegex = try! NSRegularExpression(
        pattern: "(重新|重)?([新心辛欣薪])[发發]([牌排徘]|卡)|重[开開]|重新[开開]始|再[嚟来來]過?|再玩|重嚟",
        options: [.caseInsensitive]
    )
    private static let unlockChineseRegex = try! NSRegularExpression(
        pattern: "[解姐改][锁鎖所索琐瑣]",
        options: [.caseInsensitive]
    )
    private static let rescanChineseRegex = try! NSRegularExpression(
        pattern: "(重新|重)(扫描|扫|描)",
        options: [.caseInsensitive]
    )
    private static let newEnglishKeywords = [
        "newcard", "newcards", "newround", "newgame", "nextround", "nextgame",
        "unlockcard", "unlockcards", "unlock", "resetcard", "resetcards", "reset",
        "new", "next", "clear", "deal"
    ]
    
    // 3. READ_CARDS:
    private static let readCardsChineseRegex = try! NSRegularExpression(
        pattern: "([读讀独獨毒督篤犊渎椟黩嘟]|[报報保宝寶步抱暴豹爆鲍鮑布怖]|[看瞰阚]|[睇体體弟第底梯替提啼题題]|[念唸廿]|[出初]|[讲講]|[查茶])(我的|我嘅|我啲|一下|一吓|下|吓|睇)?([牌排徘]|卡)|我([啲的]|手|副)?牌|睇[下吓睇]?我([啲的]|手|副)?牌",
        options: [.caseInsensitive]
    )
    private static let readQueryChineseRegex = try! NSRegularExpression(
        pattern: "[出初](什么|甚么|么|麼|咩|乜)(牌)?|(怎么|怎樣|怎样|点|點|點樣)[出初打](牌)?|有(什么|甚么|咩|乜)(牌|出)?|读一下|报一下|看一下|念一下|睇下|睇吓|睇睇|睇下牌|睇吓牌|睇睇牌|讀下|報下|講下|講牌|講出[嚟来]|查下|有咩牌|有乜牌|點出|點樣出|點出牌|出咩|出乜",
        options: [.caseInsensitive]
    )
    private static let readEnglishKeywords = [
        "readcard", "readcards", "readthecard", "readthecards", "readmycard", "readmycards",
        "readhand", "readthehand", "readout", "readaloud", "whatcard", "whatcards", "whattoplay",
        "read", "report", "speak", "cards", "play"
    ]
    
    // 4. SWITCH_MODE (Toggle CDD <-> DDZ):
    private static let modeSwitchChineseRegex = try! NSRegularExpression(
        pattern: "(切[换換]|换|更[改换換]|改[变變]|轉[换換]|转[换換])[个個]?(模式|规则|規則|游戏|遊戲|玩法)|[轉转換换切][換换]?[下吓]?(模式|玩法|遊戲)|切[换換]玩法",
        options: [.caseInsensitive]
    )
    private static let modeSwitchEnglishKeywords = [
        "switchmode", "togglemode", "changemode", "switchgame", "changegame"
    ]
    
    // 5. SWITCH_TO_CDD (Cho Dai Dee / 锄大地):
    private static let cddSwitchChineseRegex = try! NSRegularExpression(
        pattern: "(切[换換到]|换[到成]?|玩|轉[到去]?|转换到?|切到)?([锄鋤除储初出][大][大地弟第底递遞啲Dd]|大[老二]|大2)|(切[换換到]|换[到成]?|玩)([锄鋤除储初出][大][大地弟第底递遞啲Dd])",
        options: [.caseInsensitive]
    )
    private static let cddSwitchEnglishKeywords = [
        "switchtocdd", "switchtobigtwo", "bigtwo", "chodaidee", "playbigtwo", "playcdd", "cdd"
    ]
    
    // 6. SWITCH_TO_DDZ (Dou Di Zhu / 斗地主):
    private static let ddzSwitchChineseRegex = try! NSRegularExpression(
        pattern: "(切[换換到]|换[到成]?|玩|轉[到去]?|转换到?|切到)?([斗鬥逗豆抖][地帝递第][主祝注煮])",
        options: [.caseInsensitive]
    )
    private static let ddzSwitchEnglishKeywords = [
        "switchtoddz", "switchtodoudizhu", "doudizhu", "fightthelandlord", "playddz", "playdoudizhu", "ddz"
    ]
    
    // 7. SWITCH_ROLE (Toggle Landlord <-> Peasant):
    private static let roleSwitchChineseRegex = try! NSRegularExpression(
        pattern: "(切[换換]|换|更[改换換]|改[变變]|轉[换換]|转[换換])[个個]?(身份|身分|角色|脚色)|[轉转換换切][換换]?[下吓]?(身份|身分|角色)",
        options: [.caseInsensitive]
    )
    private static let roleSwitchEnglishKeywords = [
        "switchrole", "togglerole", "changerole", "switchidentity"
    ]
    
    // 8. SWITCH_TO_LANDLORD (Landlord / 地主):
    private static let landlordChineseRegex = try! NSRegularExpression(
        pattern: "(切[换換到]|换[到成]?|当|當|我是|我係|做|叫|抢|搶|选|選|轉做|揀)[地帝第][主祝注煮]|叫地主|抢地主|搶地主|(?<![斗鬥逗豆抖])[地帝第][主祝注煮]",
        options: [.caseInsensitive]
    )
    private static let landlordEnglishKeywords = [
        "switchtolandlord", "iamlandlord", "landlord", "playlandlord", "becomelandlord"
    ]
    
    // 9. SWITCH_TO_PEASANT (Peasant / 农民):
    private static let peasantChineseRegex = try! NSRegularExpression(
        pattern: "(切[换換到]|换[到成]?|当|當|我是|我係|做|选|選|轉做|揀)?([农農浓侬][民名明]|平民|农夫)|不叫|不[抢搶]",
        options: [.caseInsensitive]
    )
    private static let peasantEnglishKeywords = [
        "switchtopeasant", "switchtofarmer", "iampeasant", "iamfarmer", "peasant", "farmer", "playpeasant"
    ]
    
    /**
     * Matches raw speech recognition transcript against supported game voice commands.
     */
    public static func matchCommand(_ rawText: String?, languageCode: String? = nil) -> GameVoiceCommand? {
        guard let text = rawText, !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return nil
        }
        
        let cleaned = text.lowercased()
            .replacingOccurrences(of: " ", with: "")
            .replacingOccurrences(of: "_", with: "")
        
        let isEnglishOnly = languageCode?.lowercased().starts(with: "en") ?? false
        let isChineseOnly = (languageCode?.lowercased().starts(with: "zh") ?? false) || (languageCode?.lowercased().starts(with: "yue") ?? false)
        
        if isEnglishOnly {
            if ddzSwitchEnglishKeywords.contains(where: { cleaned.contains($0) }) { return .switchToDdz }
            if cddSwitchEnglishKeywords.contains(where: { cleaned.contains($0) }) { return .switchToCdd }
            if modeSwitchEnglishKeywords.contains(where: { cleaned.contains($0) }) { return .switchMode }
            
            if landlordEnglishKeywords.contains(where: { cleaned.contains($0) }) { return .switchToLandlord }
            if peasantEnglishKeywords.contains(where: { cleaned.contains($0) }) { return .switchToPeasant }
            if roleSwitchEnglishKeywords.contains(where: { cleaned.contains($0) }) { return .switchRole }
            
            if newEnglishKeywords.contains(where: { cleaned.contains($0) }) { return .newCards }
            if lockEnglishKeywords.contains(where: { cleaned.contains($0) }) { return .lockCards }
            if readEnglishKeywords.contains(where: { cleaned.contains($0) }) { return .readCards }
            return nil
        }
        
        if isChineseOnly {
            if matches(ddzSwitchChineseRegex, in: cleaned) { return .switchToDdz }
            if matches(cddSwitchChineseRegex, in: cleaned) { return .switchToCdd }
            if matches(modeSwitchChineseRegex, in: cleaned) { return .switchMode }
            
            if matches(landlordChineseRegex, in: cleaned) { return .switchToLandlord }
            if matches(peasantChineseRegex, in: cleaned) { return .switchToPeasant }
            if matches(roleSwitchChineseRegex, in: cleaned) { return .switchRole }
            
            if matches(newRoundChineseRegex, in: cleaned) ||
                matches(newCardsChineseRegex, in: cleaned) ||
                matches(newDealChineseRegex, in: cleaned) ||
                matches(unlockChineseRegex, in: cleaned) ||
                matches(rescanChineseRegex, in: cleaned) {
                return .newCards
            }
            if matches(lockChineseRegex, in: cleaned) { return .lockCards }
            if matches(readCardsChineseRegex, in: cleaned) || matches(readQueryChineseRegex, in: cleaned) {
                return .readCards
            }
            return nil
        }
        
        // Default / Hybrid: checks both
        if ddzSwitchEnglishKeywords.contains(where: { cleaned.contains($0) }) || matches(ddzSwitchChineseRegex, in: cleaned) {
            return .switchToDdz
        }
        if cddSwitchEnglishKeywords.contains(where: { cleaned.contains($0) }) || matches(cddSwitchChineseRegex, in: cleaned) {
            return .switchToCdd
        }
        if modeSwitchEnglishKeywords.contains(where: { cleaned.contains($0) }) || matches(modeSwitchChineseRegex, in: cleaned) {
            return .switchMode
        }
        
        if landlordEnglishKeywords.contains(where: { cleaned.contains($0) }) || matches(landlordChineseRegex, in: cleaned) {
            return .switchToLandlord
        }
        if peasantEnglishKeywords.contains(where: { cleaned.contains($0) }) || matches(peasantChineseRegex, in: cleaned) {
            return .switchToPeasant
        }
        if roleSwitchEnglishKeywords.contains(where: { cleaned.contains($0) }) || matches(roleSwitchChineseRegex, in: cleaned) {
            return .switchRole
        }
        
        if newEnglishKeywords.contains(where: { cleaned.contains($0) }) ||
            matches(newRoundChineseRegex, in: cleaned) ||
            matches(newCardsChineseRegex, in: cleaned) ||
            matches(newDealChineseRegex, in: cleaned) ||
            matches(unlockChineseRegex, in: cleaned) ||
            matches(rescanChineseRegex, in: cleaned) {
            return .newCards
        }
        if lockEnglishKeywords.contains(where: { cleaned.contains($0) }) || matches(lockChineseRegex, in: cleaned) {
            return .lockCards
        }
        if readEnglishKeywords.contains(where: { cleaned.contains($0) }) ||
            matches(readCardsChineseRegex, in: cleaned) ||
            matches(readQueryChineseRegex, in: cleaned) {
            return .readCards
        }
        
        return nil
    }
    
    private static func matches(_ regex: NSRegularExpression, in text: String) -> Bool {
        let range = NSRange(location: 0, length: text.utf16.count)
        return regex.firstMatch(in: text, options: [], range: range) != nil
    }
}

