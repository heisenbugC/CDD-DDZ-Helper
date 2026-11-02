import Foundation
import Speech
import AVFoundation

/**
 * Real-time Speech Recognition Service using Apple's native Speech.framework (SFSpeechRecognizer).
 * Supports Cantonese (zh-HK), Mandarin (zh-CN), and English (en-US).
 * Provides live audio metering and diagnostic testing.
 */
public class SpeechRecognitionService: NSObject, ObservableObject {
    public static let shared = SpeechRecognitionService()
    
    private var speechRecognizer: SFSpeechRecognizer?
    private var recognitionRequest: SFSpeechAudioBufferRecognitionRequest?
    private var recognitionTask: SFSpeechRecognitionTask?
    private let audioEngine = AVAudioEngine()
    
    @Published public var isListening: Bool = false
    @Published public var currentTranscript: String = ""
    @Published public var audioLevel: Float = 0.0 // 0.0 to 1.0 for UI audio wave
    @Published public var lastRecognizedCommand: GameVoiceCommand? = nil
    @Published public var authorizationStatus: SFSpeechRecognizerAuthorizationStatus = .notDetermined
    
    // Command listener callback
    public var onCommandMatched: ((GameVoiceCommand) -> Void)?
    
    public override init() {
        super.init()
        requestAuthorization()
    }
    
    public func requestAuthorization() {
        SFSpeechRecognizer.requestAuthorization { [weak self] status in
            DispatchQueue.main.async {
                self?.authorizationStatus = status
            }
        }
    }
    
    /**
     * Starts continuous listening for voice commands in the specified language.
     */
    public func startListening(language: VoiceLanguage = .cantonese) {
        guard !isListening else { return }
        guard authorizationStatus == .authorized else {
            requestAuthorization()
            return
        }
        
        let locale = Locale(identifier: language.localeIdentifier)
        speechRecognizer = SFSpeechRecognizer(locale: locale)
        guard let recognizer = speechRecognizer, recognizer.isAvailable else {
            print("SpeechRecognitionService: Recognizer for \(language.localeIdentifier) unavailable")
            return
        }
        
        do {
            try startAudioEngineAndRecognition(recognizer: recognizer, language: language)
            DispatchQueue.main.async {
                self.isListening = true
                self.currentTranscript = ""
            }
        } catch {
            print("SpeechRecognitionService: Failed to start audio engine: \(error)")
            stopListening()
        }
    }
    
    private func startAudioEngineAndRecognition(recognizer: SFSpeechRecognizer, language: VoiceLanguage) throws {
        // Cancel any existing task
        recognitionTask?.cancel()
        recognitionTask = nil
        
        let audioSession = AVAudioSession.sharedInstance()
        try audioSession.setCategory(.playAndRecord, mode: .measurement, options: [.duckOthers, .defaultToSpeaker, .allowBluetooth])
        try audioSession.setActive(true, options: .notifyOthersOnDeactivation)
        
        recognitionRequest = SFSpeechAudioBufferRecognitionRequest()
        guard let request = recognitionRequest else { return }
        request.shouldReportPartialResults = true
        
        if #available(iOS 13.0, *) {
            request.requiresOnDeviceRecognition = false // Allow cloud or on-device
        }
        
        let inputNode = audioEngine.inputNode
        let recordingFormat = inputNode.outputFormat(forBus: 0)
        
        inputNode.removeTap(onBus: 0)
        inputNode.installTap(onBus: 0, bufferSize: 1024, format: recordingFormat) { [weak self] buffer, _ in
            self?.recognitionRequest?.append(buffer)
            self?.calculateAudioLevel(from: buffer)
        }
        
        audioEngine.prepare()
        try audioEngine.start()
        
        recognitionTask = recognizer.recognitionTask(with: request) { [weak self] result, error in
            guard let self = self else { return }
            
            if let result = result {
                let transcript = result.bestTranscription.formattedString
                DispatchQueue.main.async {
                    self.currentTranscript = transcript
                }
                
                // Match command
                if let command = VoiceCommandMatcher.matchCommand(transcript, languageCode: language.localeIdentifier) {
                    DispatchQueue.main.async {
                        self.lastRecognizedCommand = command
                        self.onCommandMatched?(command)
                        // Reset request buffer to avoid repeat execution of the same phrase
                        self.currentTranscript = "已执行: \(command.description)"
                    }
                }
            }
            
            if error != nil || (result?.isFinal ?? false) {
                // If unexpected error, restart after slight pause if still listening
                if self.isListening {
                    self.restartListening(language: language)
                }
            }
        }
    }
    
    private func calculateAudioLevel(from buffer: AVAudioPCMBuffer) {
        guard let channelData = buffer.floatChannelData?[0] else { return }
        let channelDataValue = Array(UnsafeBufferPointer(start: channelData, count: Int(buffer.frameLength)))
        
        var rms: Float = 0.0
        for sample in channelDataValue {
            rms += sample * sample
        }
        rms = sqrt(rms / Float(buffer.frameLength))
        
        // Convert to normalized 0.0 .. 1.0 level
        let level = min(1.0, max(0.0, (rms * 5.0)))
        DispatchQueue.main.async {
            self.audioLevel = level
        }
    }
    
    private func restartListening(language: VoiceLanguage) {
        stopListening()
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { [weak self] in
            self?.startListening(language: language)
        }
    }
    
    public func stopListening() {
        recognitionTask?.cancel()
        recognitionTask = nil
        recognitionRequest?.endAudio()
        recognitionRequest = nil
        
        if audioEngine.isRunning {
            audioEngine.stop()
            audioEngine.inputNode.removeTap(onBus: 0)
        }
        
        DispatchQueue.main.async {
            self.isListening = false
            self.audioLevel = 0.0
        }
    }
    
    /**
     * Diagnostic Test: Records for testDuration seconds, transcribes speech,
     * and triggers completion with the recognized text and detected command.
     */
    public func runDiagnosticTest(
        language: VoiceLanguage = .cantonese,
        testDuration: TimeInterval = 4.0,
        completion: @escaping (String, GameVoiceCommand?) -> Void
    ) {
        stopListening()
        
        let locale = Locale(identifier: language.localeIdentifier)
        speechRecognizer = SFSpeechRecognizer(locale: locale)
        guard let recognizer = speechRecognizer, recognizer.isAvailable else {
            completion("错误：当前系统不支持 \(language.displayName) 语音识别", nil)
            return
        }
        
        do {
            let audioSession = AVAudioSession.sharedInstance()
            try audioSession.setCategory(.playAndRecord, mode: .measurement, options: [.duckOthers, .defaultToSpeaker])
            try audioSession.setActive(true)
            
            let request = SFSpeechAudioBufferRecognitionRequest()
            request.shouldReportPartialResults = true
            
            let inputNode = audioEngine.inputNode
            let recordingFormat = inputNode.outputFormat(forBus: 0)
            
            inputNode.removeTap(onBus: 0)
            inputNode.installTap(onBus: 0, bufferSize: 1024, format: recordingFormat) { buffer, _ in
                request.append(buffer)
            }
            
            audioEngine.prepare()
            try audioEngine.start()
            
            var finalTranscript = ""
            
            let task = recognizer.recognitionTask(with: request) { result, error in
                if let result = result {
                    finalTranscript = result.bestTranscription.formattedString
                }
            }
            
            // Wait testDuration seconds then stop and return result
            DispatchQueue.main.asyncAfter(deadline: .now() + testDuration) { [weak self] in
                task.cancel()
                if self?.audioEngine.isRunning == true {
                    self?.audioEngine.stop()
                    self?.audioEngine.inputNode.removeTap(onBus: 0)
                }
                
                let matched = VoiceCommandMatcher.matchCommand(finalTranscript, languageCode: language.localeIdentifier)
                let text = finalTranscript.isEmpty ? "未检测到有效声音输入" : finalTranscript
                completion(text, matched)
            }
        } catch {
            completion("录音初始化失败: \(error.localizedDescription)", nil)
        }
    }
}

