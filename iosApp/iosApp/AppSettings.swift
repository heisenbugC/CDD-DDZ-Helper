import Foundation
import SwiftUI

/**
 * Game Mode enum
 */
public enum GameMode: String, CaseIterable, Identifiable {
    case douDiZhu = "doudizhu"
    case choDaiDee = "chodaidee"
    
    public var id: String { rawValue }
    
    public var title: String {
        switch self {
        case .douDiZhu: return "斗地主"
        case .choDaiDee: return "锄大地"
        }
    }
}

/**
 * Dou Di Zhu Role enum
 */
public enum DdzRole: String, CaseIterable, Identifiable {
    case peasant = "peasant"
    case landlord = "landlord"
    
    public var id: String { rawValue }
    
    public var title: String {
        switch self {
        case .peasant: return "农民 (17张)"
        case .landlord: return "地主 (20张)"
        }
    }
}

/**
 * Cho Dai Dee Variation enum
 */
public enum CddVariation: String, CaseIterable, Identifiable {
    case mainland = "mainland"
    case hongKong = "hongkong"
    
    public var id: String { rawValue }
    
    public var title: String {
        switch self {
        case .mainland: return "大陆规则 (同花 > 顺子)"
        case .hongKong: return "香港规则 (蛇 > 同花)"
        }
    }
}

/**
 * Spoken Voice Language enum
 */
public enum VoiceLanguage: String, CaseIterable, Identifiable {
    case cantonese = "zh-HK"
    case mandarin = "zh-CN"
    case english = "en-US"
    
    public var id: String { rawValue }
    
    public var displayName: String {
        switch self {
        case .cantonese: return "粤语 (香港)"
        case .mandarin: return "普通话"
        case .english: return "English"
        }
    }
    
    public var localeIdentifier: String {
        return rawValue
    }
}

/**
 * Persistent App Settings managing all user preferences with UserDefaults.
 */
public class AppSettings: ObservableObject {
    public static let shared = AppSettings()
    
    private let defaults = UserDefaults.standard
    
    @Published public var gameMode: GameMode {
        didSet { defaults.set(gameMode.rawValue, forKey: "settings_game_mode") }
    }
    
    @Published public var ddzRole: DdzRole {
        didSet { defaults.set(ddzRole.rawValue, forKey: "settings_ddz_role") }
    }
    
    @Published public var cddVariation: CddVariation {
        didSet { defaults.set(cddVariation.rawValue, forKey: "settings_cdd_variation") }
    }
    
    @Published public var voiceLanguage: VoiceLanguage {
        didSet { defaults.set(voiceLanguage.rawValue, forKey: "settings_voice_language") }
    }
    
    @Published public var enableVoiceCommands: Bool {
        didSet { defaults.set(enableVoiceCommands, forKey: "settings_enable_voice_commands") }
    }
    
    @Published public var enableAimingGuidance: Bool {
        didSet { defaults.set(enableAimingGuidance, forKey: "settings_enable_aiming_guidance") }
    }
    
    @Published public var enableHapticFeedback: Bool {
        didSet { defaults.set(enableHapticFeedback, forKey: "settings_enable_haptics") }
    }
    
    @Published public var speechRate: Float {
        didSet { defaults.set(speechRate, forKey: "settings_speech_rate") }
    }
    
    @Published public var speechPitch: Float {
        didSet { defaults.set(speechPitch, forKey: "settings_speech_pitch") }
    }
    
    @Published public var speechVolume: Float {
        didSet { defaults.set(speechVolume, forKey: "settings_speech_volume") }
    }
    
    private init() {
        // Load stored values or defaults
        let storedMode = defaults.string(forKey: "settings_game_mode") ?? GameMode.douDiZhu.rawValue
        self.gameMode = GameMode(rawValue: storedMode) ?? .douDiZhu
        
        let storedRole = defaults.string(forKey: "settings_ddz_role") ?? DdzRole.peasant.rawValue
        self.ddzRole = DdzRole(rawValue: storedRole) ?? .peasant
        
        let storedVariation = defaults.string(forKey: "settings_cdd_variation") ?? CddVariation.mainland.rawValue
        self.cddVariation = CddVariation(rawValue: storedVariation) ?? .mainland
        
        let storedLang = defaults.string(forKey: "settings_voice_language") ?? VoiceLanguage.cantonese.rawValue
        self.voiceLanguage = VoiceLanguage(rawValue: storedLang) ?? .cantonese
        
        self.enableVoiceCommands = defaults.object(forKey: "settings_enable_voice_commands") as? Bool ?? true
        self.enableAimingGuidance = defaults.object(forKey: "settings_enable_aiming_guidance") as? Bool ?? true
        self.enableHapticFeedback = defaults.object(forKey: "settings_enable_haptics") as? Bool ?? true
        
        self.speechRate = defaults.object(forKey: "settings_speech_rate") as? Float ?? 0.52
        self.speechPitch = defaults.object(forKey: "settings_speech_pitch") as? Float ?? 1.0
        self.speechVolume = defaults.object(forKey: "settings_speech_volume") as? Float ?? 1.0
    }
}

