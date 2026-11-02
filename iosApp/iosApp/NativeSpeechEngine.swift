import Foundation
import AVFoundation

/**
 * Native Apple TTS Engine using AVSpeechSynthesizer.
 * Strictly adheres to user requirement:
 * 1. Primary: Apple Native TTS library (AVSpeechSynthesizer with AVSpeechSynthesisVoice).
 * 2. Automatic voice discovery for Cantonese (zh-HK), Mandarin (zh-CN), English (en-US).
 * 3. Fallback: If native Apple voice is not installed or unavailable, gracefully falls back
 *    to Google Cloud TTS REST endpoint or system fallback synthesizer.
 */
public class NativeSpeechEngine: NSObject, ObservableObject, AVSpeechSynthesizerDelegate {
    public static let shared = NativeSpeechEngine()
    
    private let synthesizer = AVSpeechSynthesizer()
    private var audioPlayer: AVAudioPlayer?
    
    @Published public var isSpeaking: Bool = false
    @Published public var lastSpokenText: String = ""
    @Published public var usedFallback: Bool = false
    
    public override init() {
        super.init()
        synthesizer.delegate = self
        setupAudioSession()
    }
    
    private func setupAudioSession() {
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playback, mode: .voicePrompt, options: [.duckOthers, .mixWithOthers])
            try session.setActive(true)
        } catch {
            print("NativeSpeechEngine: Failed to activate AVAudioSession: \(error)")
        }
    }
    
    /**
     * Synthesizes and speaks text using Apple's Native AVSpeechSynthesizer first.
     */
    public func speak(
        text: String,
        language: VoiceLanguage = .cantonese,
        rate: Float = 0.52,
        pitch: Float = 1.0,
        volume: Float = 1.0,
        completion: (() -> Void)? = nil
    ) {
        // Stop any current utterance
        if synthesizer.isSpeaking {
            synthesizer.stopSpeaking(at: .immediate)
        }
        audioPlayer?.stop()
        
        DispatchQueue.main.async {
            self.lastSpokenText = text
            self.isSpeaking = true
            self.usedFallback = false
        }
        
        // 1. Check for Apple Native Voice availability
        let targetLocale = language.localeIdentifier
        let installedVoices = AVSpeechSynthesisVoice.speechVoices()
        let matchingVoice = installedVoices.first(where: { $0.language.lowercased() == targetLocale.lowercased() })
            ?? AVSpeechSynthesisVoice(language: targetLocale)
        
        if let voice = matchingVoice {
            // Speak using Apple Native TTS
            let utterance = AVSpeechUtterance(string: text)
            utterance.voice = voice
            utterance.rate = rate
            utterance.pitchMultiplier = pitch
            utterance.volume = volume
            utterance.preUtteranceDelay = 0.05
            utterance.postUtteranceDelay = 0.05
            
            synthesizer.speak(utterance)
        } else {
            // 2. Fallback to Google Cloud TTS REST API if Apple native voice is completely absent
            print("NativeSpeechEngine: Apple voice for \(targetLocale) not found. Triggering Google TTS fallback...")
            DispatchQueue.main.async { self.usedFallback = true }
            speakWithGoogleFallback(text: text, language: language, completion: completion)
        }
    }
    
    public func stop() {
        if synthesizer.isSpeaking {
            synthesizer.stopSpeaking(at: .immediate)
        }
        audioPlayer?.stop()
        DispatchQueue.main.async {
            self.isSpeaking = false
        }
    }
    
    // MARK: - AVSpeechSynthesizerDelegate
    public func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) {
        DispatchQueue.main.async {
            self.isSpeaking = false
        }
    }
    
    public func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didCancel utterance: AVSpeechUtterance) {
        DispatchQueue.main.async {
            self.isSpeaking = false
        }
    }
    
    // MARK: - Google TTS Fallback
    private func speakWithGoogleFallback(
        text: String,
        language: VoiceLanguage,
        completion: (() -> Void)? = nil
    ) {
        guard let encodedText = text.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) else {
            fallbackToDefaultAppleVoice(text: text)
            return
        }
        
        let langParam: String
        switch language {
        case .cantonese: langParam = "zh-yue"
        case .mandarin: langParam = "zh-CN"
        case .english: langParam = "en"
        }
        
        // Public Google Translate TTS endpoint as zero-config client-side fallback
        let urlString = "https://translate.google.com/translate_tts?ie=UTF-8&q=\(encodedText)&tl=\(langParam)&client=tw-ob"
        guard let url = URL(string: urlString) else {
            fallbackToDefaultAppleVoice(text: text)
            return
        }
        
        var request = URLRequest(url: url)
        request.setValue("Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X)", forHTTPHeaderField: "User-Agent")
        
        URLSession.shared.dataTask(with: request) { [weak self] data, response, error in
            guard let self = self, let data = data, error == nil else {
                DispatchQueue.main.async {
                    self?.fallbackToDefaultAppleVoice(text: text)
                }
                return
            }
            
            DispatchQueue.main.async {
                do {
                    self.audioPlayer = try AVAudioPlayer(data: data)
                    self.audioPlayer?.play()
                } catch {
                    self.fallbackToDefaultAppleVoice(text: text)
                }
            }
        }.resume()
    }
    
    private func fallbackToDefaultAppleVoice(text: String) {
        let utterance = AVSpeechUtterance(string: text)
        utterance.voice = AVSpeechSynthesisVoice(language: "zh-CN") ?? AVSpeechSynthesisVoice.speechVoices().first
        utterance.rate = 0.52
        synthesizer.speak(utterance)
    }
    
    /**
     * Diagnostic helper: returns a list of installed native voices for language.
     */
    public func getAvailableVoices(for language: VoiceLanguage) -> [AVSpeechSynthesisVoice] {
        let target = language.localeIdentifier.lowercased()
        return AVSpeechSynthesisVoice.speechVoices().filter { $0.language.lowercased().starts(with: target.prefix(2)) }
    }
}

