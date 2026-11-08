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
public class NativeSpeechEngine: NSObject, ObservableObject, AVSpeechSynthesizerDelegate, AVAudioPlayerDelegate {
    public static let shared = NativeSpeechEngine()
    
    private let synthesizer = AVSpeechSynthesizer()
    private var audioPlayer: AVAudioPlayer?
    private var speakingWatchdogItem: DispatchWorkItem?
    private var currentUtterance: AVSpeechUtterance?
    
    @Published public var isSpeaking: Bool = false
    @Published public var lastSpokenText: String = ""
    @Published public var usedFallback: Bool = false
    
    public override init() {
        super.init()
        synthesizer.delegate = self
        // NOTE: AVAudioSession is configured globally by AudioSessionCoordinator.
        // NativeSpeechEngine must NOT override the session category — doing so would
        // break the microphone pipeline (AVCaptureSession + SpeechRecognitionService).
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
        currentUtterance = nil
        if synthesizer.isSpeaking {
            synthesizer.stopSpeaking(at: .immediate)
        }
        audioPlayer?.stop()
        
        // 立即通知语音识别暂停收音，防止自激
        SpeechRecognitionService.shared.pauseListeningForTTS()
        
        DispatchQueue.main.async {
            self.lastSpokenText = text
            self.isSpeaking = true
            self.usedFallback = false
        }
        
        // 启动 Watchdog 看门狗（估算朗读时长 + 1.5s 安全余量，上限 15s），防止委托丢失造成麦克风永久静音
        let charCount = Double(text.count)
        let safeRate = Double(rate > 0 ? rate : 0.52)
        let estimatedSeconds = min(15.0, max(2.0, (charCount * 0.35) / (safeRate * 1.5))) + 1.5
        
        speakingWatchdogItem?.cancel()
        let watchdog = DispatchWorkItem { [weak self] in
            print("NativeSpeechEngine: ⚠️ Watchdog 触发，强制恢复麦克风收音")
            self?.finishSpeaking()
        }
        speakingWatchdogItem = watchdog
        DispatchQueue.main.asyncAfter(deadline: .now() + estimatedSeconds, execute: watchdog)
        
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
            
            currentUtterance = utterance
            synthesizer.speak(utterance)
        } else {
            // 2. Fallback to Google Cloud TTS REST API if Apple native voice is completely absent
            print("NativeSpeechEngine: Apple voice for \(targetLocale) not found. Triggering Google TTS fallback...")
            DispatchQueue.main.async { self.usedFallback = true }
            speakWithGoogleFallback(text: text, language: language, completion: completion)
        }
    }
    
    public func stop() {
        currentUtterance = nil
        if synthesizer.isSpeaking {
            synthesizer.stopSpeaking(at: .immediate)
        }
        audioPlayer?.stop()
        finishSpeaking()
    }
    
    private func finishSpeaking() {
        DispatchQueue.main.async {
            guard self.isSpeaking else { return }
            self.speakingWatchdogItem?.cancel()
            self.speakingWatchdogItem = nil
            self.isSpeaking = false
            SpeechRecognitionService.shared.resumeListeningAfterTTS(delay: 0.5)
        }
    }
    
    // MARK: - AVSpeechSynthesizerDelegate
    public func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) {
        guard utterance == currentUtterance else { return }
        currentUtterance = nil
        finishSpeaking()
    }
    
    public func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didCancel utterance: AVSpeechUtterance) {
        guard utterance == currentUtterance else { return }
        currentUtterance = nil
        finishSpeaking()
    }
    
    // MARK: - AVAudioPlayerDelegate
    public func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        finishSpeaking()
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
                    self.audioPlayer?.delegate = self
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

