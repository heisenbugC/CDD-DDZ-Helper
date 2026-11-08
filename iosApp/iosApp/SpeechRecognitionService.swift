import Foundation
import Speech
import AVFoundation
import AudioToolbox
import UIKit

/**
 * Real-time Speech Recognition Service using Apple's native Speech.framework (SFSpeechRecognizer).
 *
 * Architecture (iOS 18+):
 * - AVAudioEngine must be operated on the MAIN THREAD. All tap/prepare/start/stop ops use main.
 * - AVAudioSession is managed by AudioSessionCoordinator. `reactivate()` is called immediately
 *   before `audioEngine.start()` to re-assert the shared category in case AVCaptureSession
 *   temporarily changed it.
 * - `installTap` format: MUST call `audioEngine.prepare()` first, then read
 *   `inputNode.outputFormat(forBus:0)`. The format is only valid after prepare. Passing nil
 *   causes -10868 on iOS 18; passing a pre-prepare format causes SIGABRT.
 * - `SFSpeechRecognizer.recognitionTask` called on main thread per Apple documentation.
 * - Auto-restart after 500 ms on error or isFinal (Speech sessions expire after ~60 s).
 * - `authorizationStatus` didSet auto-starts on first-launch grant.
 *
 * Supported: Cantonese (zh-HK), Mandarin (zh-CN), English (en-US).
 */
public class SpeechRecognitionService: NSObject, ObservableObject {
    public static let shared = SpeechRecognitionService()

    // MARK: - Private state (all accessed on main thread)
    private var speechRecognizer: SFSpeechRecognizer?
    private var recognitionRequest: SFSpeechAudioBufferRecognitionRequest?
    private var recognitionTask: SFSpeechRecognitionTask?
    private let audioEngine = AVAudioEngine()

    private var currentLanguage: VoiceLanguage = .cantonese
    private var _isActive: Bool = false
    private var retryCount: Int = 0
    private static let maxRetries: Int = 5

    // MARK: - Published (main thread only)
    @Published public var isListening: Bool = false
    @Published public private(set) var isMutedForTTS: Bool = false
    public private(set) var isMutedForEarcon: Bool = false
    private var resumeWorkItem: DispatchWorkItem?
    @Published public var currentTranscript: String = ""
    @Published public var audioLevel: Float = 0.0
    @Published public var lastRecognizedCommand: GameVoiceCommand? = nil
    @Published public var authorizationStatus: SFSpeechRecognizerAuthorizationStatus = .notDetermined

    // MARK: - Recording Earcons
    public static func playStartRecordingEarcon() {
        AudioServicesPlaySystemSound(1113) // begin_record.caf
        if AppSettings.shared.enableHapticFeedback {
            let generator = UIImpactFeedbackGenerator(style: .medium)
            generator.prepare()
            generator.impactOccurred()
        }
    }

    public static func playStopRecordingEarcon() {
        AudioServicesPlaySystemSound(1114) // end_record.caf
        if AppSettings.shared.enableHapticFeedback {
            let generator = UIImpactFeedbackGenerator(style: .light)
            generator.prepare()
            generator.impactOccurred()
        }
    }

    // MARK: - Command callback
    public var onCommandMatched: ((GameVoiceCommand) -> Void)?

    // MARK: - Init
    public override init() {
        super.init()
    }

    // MARK: - Dual Authorization Checks (Microphone + Speech)

    private func checkMicrophonePermission(completion: @escaping (Bool) -> Void) {
        if #available(iOS 17.0, *) {
            switch AVAudioApplication.shared.recordPermission {
            case .granted:
                completion(true)
            case .denied:
                completion(false)
            case .undetermined:
                AVAudioApplication.requestRecordPermission { granted in
                    DispatchQueue.main.async { completion(granted) }
                }
            @unknown default:
                completion(false)
            }
        } else {
            switch AVAudioSession.sharedInstance().recordPermission {
            case .granted:
                completion(true)
            case .denied:
                completion(false)
            case .undetermined:
                AVAudioSession.sharedInstance().requestRecordPermission { granted in
                    DispatchQueue.main.async { completion(granted) }
                }
            @unknown default:
                completion(false)
            }
        }
    }

    private func checkSpeechPermission(completion: @escaping (Bool) -> Void) {
        let status = SFSpeechRecognizer.authorizationStatus()
        self.authorizationStatus = status
        switch status {
        case .authorized:
            completion(true)
        case .notDetermined:
            SFSpeechRecognizer.requestAuthorization { [weak self] newStatus in
                DispatchQueue.main.async {
                    self?.authorizationStatus = newStatus
                    completion(newStatus == .authorized)
                }
            }
        case .denied, .restricted:
            completion(false)
        @unknown default:
            completion(false)
        }
    }

    public func checkDualPermissions(completion: @escaping (Bool) -> Void) {
        checkMicrophonePermission { [weak self] micGranted in
            guard let self else { completion(false); return }
            guard micGranted else {
                print("SpeechRecognitionService: 麦克风录音权限被拒绝或未授权")
                completion(false)
                return
            }
            self.checkSpeechPermission { speechGranted in
                if !speechGranted {
                    print("SpeechRecognitionService: 语音识别权限被拒绝或未授权")
                }
                completion(speechGranted)
            }
        }
    }

    public func requestAuthorization() {
        checkDualPermissions { _ in }
    }

    // MARK: - Start / Stop

    public func startListening(language: VoiceLanguage = .cantonese, playEarcon: Bool = true) {
        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            guard !self._isActive else { return }
            self.currentLanguage = language

            self.checkDualPermissions { [weak self] granted in
                guard let self else { return }
                guard granted else {
                    print("SpeechRecognitionService: 双重权限未完全就绪，取消启动监听")
                    self.resetActiveFlag()
                    return
                }
                guard !self._isActive else { return }
                self._isActive = true
                self.retryCount = 0
                self.startAudioPipelineOnMain(language: language, playEarcon: playEarcon)
            }
        }
    }

    public func stopListening(playEarcon: Bool = true) {
        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            self.resumeWorkItem?.cancel()
            self.resumeWorkItem = nil
            self.isMutedForTTS = false
            guard self._isActive else { return }
            if playEarcon {
                Self.playStopRecordingEarcon()
            }
            self.isMutedForEarcon = false
            self._isActive = false
            self.retryCount = 0
            self.teardownAudioPipelineOnMain()
            self.isListening = false
            self.audioLevel = 0.0
        }
    }

    /// 当 TTS 播报开始时立即调用，切断收音并重置已接收的残音
    public func pauseListeningForTTS() {
        let block = { [weak self] in
            guard let self = self else { return }
            self.resumeWorkItem?.cancel()
            self.resumeWorkItem = nil
            self.isMutedForTTS = true
            self.audioLevel = 0.0
            
            if self._isActive, let recognizer = self.speechRecognizer {
                self.resetRecognitionRequestOnMain(recognizer: recognizer, language: self.currentLanguage)
            }
        }
        if Thread.isMainThread {
            block()
        } else {
            DispatchQueue.main.async(execute: block)
        }
    }

    /// 当 TTS 播报彻底结束（加混响衰减延时）后调用，恢复正常收音
    public func resumeListeningAfterTTS(delay: Double = 0.5) {
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            self.resumeWorkItem?.cancel()
            let item = DispatchWorkItem { [weak self] in
                guard let self = self else { return }
                self.isMutedForTTS = false
                print("SpeechRecognitionService: TTS 播报与混响结束，麦克风恢复监听")
            }
            self.resumeWorkItem = item
            DispatchQueue.main.asyncAfter(deadline: .now() + delay, execute: item)
        }
    }

    // MARK: - Audio pipeline (MAIN THREAD ONLY)

    private func scheduleRetry(language: VoiceLanguage) {
        guard _isActive else { return }
        guard retryCount < Self.maxRetries else {
            print("SpeechRecognitionService: ❌ 超过最大重试次数 (\(Self.maxRetries))，安全停止语音监听")
            resetActiveFlag()
            return
        }
        retryCount += 1
        print("SpeechRecognitionService: 正在调度第 \(retryCount)/\(Self.maxRetries) 次重试 (300ms 后)...")
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { [weak self] in
            guard let self, self._isActive else { return }
            self.startAudioPipelineOnMain(language: language, playEarcon: false)
        }
    }

    private func startAudioPipelineOnMain(language: VoiceLanguage, playEarcon: Bool = false) {
        // Tear down any previous session first.
        teardownAudioPipelineOnMain()

        // 1. Build recognizer.
        let locale = Locale(identifier: language.localeIdentifier)
        guard let recognizer = SFSpeechRecognizer(locale: locale), recognizer.isAvailable else {
            print("SpeechRecognitionService: Recognizer unavailable for \(language.localeIdentifier)")
            resetActiveFlag()
            return
        }
        speechRecognizer = recognizer

        // 2. Recognition request.
        let request = SFSpeechAudioBufferRecognitionRequest()
        request.shouldReportPartialResults = true
        if #available(iOS 13, *) { request.requiresOnDeviceRecognition = false }
        recognitionRequest = request

        // 3. Audio Node & Format validation guard
        let inputNode = audioEngine.inputNode
        inputNode.removeTap(onBus: 0)
        AudioSessionCoordinator.shared.reactivate()
        audioEngine.prepare()

        let hwFormat = inputNode.outputFormat(forBus: 0)
        guard hwFormat.sampleRate > 0 && hwFormat.channelCount > 0 else {
            print("SpeechRecognitionService: ⚠️ 硬件格式未就绪 (\(hwFormat))，终止 installTap，300ms 后自动重试")
            scheduleRetry(language: language)
            return
        }

        retryCount = 0

        inputNode.installTap(onBus: 0, bufferSize: 4096, format: hwFormat) { [weak self] buffer, _ in
            guard let self = self else { return }
            if self.isMutedForTTS || self.isMutedForEarcon { return } // TTS 或提示音发声期间直接丢弃，不送入识别器
            self.recognitionRequest?.append(buffer)
            self.calculateAudioLevel(from: buffer)
        }

        if playEarcon {
            self.isMutedForEarcon = true
        }

        do {
            try audioEngine.start()
        } catch {
            print("SpeechRecognitionService: audioEngine.start() failed — \(error)")
            inputNode.removeTap(onBus: 0)
            self.isMutedForEarcon = false
            scheduleRetry(language: language)
            return
        }

        if playEarcon {
            Self.playStartRecordingEarcon()
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.22) { [weak self] in
                self?.isMutedForEarcon = false
            }
        }

        // 7. Start recognition task (main thread per Apple docs).
        recognitionTask = recognizer.recognitionTask(with: request) { [weak self] result, error in
            DispatchQueue.main.async {
                guard let self else { return }

                if let result {
                    let transcript = result.bestTranscription.formattedString
                    self.currentTranscript = transcript

                    if let command = VoiceCommandMatcher.matchCommand(
                        transcript, languageCode: language.localeIdentifier
                    ) {
                        self.lastRecognizedCommand = command
                        self.onCommandMatched?(command)
                        self.currentTranscript = "已执行: \(command.description)"
                        self.resetRecognitionRequestOnMain(recognizer: recognizer, language: language)
                        return
                    }
                }

                if let error = error {
                    let nsError = error as NSError
                    if nsError.code == 216 || (nsError.domain == "kAFAssistantErrorDomain" && nsError.code == 216) {
                        return // 忽略主动取消产生的取消事件，严禁触发重试！
                    }
                    print("SpeechRecognitionService: 识别异常 — \(error.localizedDescription)")
                    guard self._isActive else { return }
                    self.scheduleRetry(language: language)
                } else if result?.isFinal == true {
                    print("SpeechRecognitionService: 识别会话周期正常结束 (isFinal)，无缝重启下一周期")
                    guard self._isActive else { return }
                    self.retryCount = 0
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { [weak self] in
                        guard let self, self._isActive else { return }
                        self.startAudioPipelineOnMain(language: language, playEarcon: false)
                    }
                }
            }
        }

        isListening = true
        currentTranscript = ""
        print("SpeechRecognitionService: pipeline active — locale=\(language.localeIdentifier)")
    }

    private func teardownAudioPipelineOnMain() {
        audioEngine.inputNode.removeTap(onBus: 0)
        if audioEngine.isRunning { audioEngine.stop() }
        recognitionRequest?.endAudio()
        recognitionRequest = nil
        recognitionTask?.cancel()
        recognitionTask = nil
    }

    private func resetRecognitionRequestOnMain(recognizer: SFSpeechRecognizer, language: VoiceLanguage) {
        recognitionTask?.cancel()
        recognitionTask = nil
        recognitionRequest?.endAudio()

        let newRequest = SFSpeechAudioBufferRecognitionRequest()
        newRequest.shouldReportPartialResults = true
        if #available(iOS 13, *) { newRequest.requiresOnDeviceRecognition = false }
        recognitionRequest = newRequest

        recognitionTask = recognizer.recognitionTask(with: newRequest) { [weak self] result, error in
            DispatchQueue.main.async {
                guard let self else { return }
                if let result {
                    let transcript = result.bestTranscription.formattedString
                    self.currentTranscript = transcript
                    if let command = VoiceCommandMatcher.matchCommand(transcript, languageCode: language.localeIdentifier) {
                        self.lastRecognizedCommand = command
                        self.onCommandMatched?(command)
                        self.currentTranscript = "已执行: \(command.description)"
                        self.resetRecognitionRequestOnMain(recognizer: recognizer, language: language)
                        return
                    }
                }
                if let error = error {
                    let nsError = error as NSError
                    if nsError.code == 216 || (nsError.domain == "kAFAssistantErrorDomain" && nsError.code == 216) {
                        return // 忽略主动取消产生的取消事件，严禁触发重试！
                    }
                    print("SpeechRecognitionService: 识别异常 (reset request) — \(error.localizedDescription)")
                    guard self._isActive else { return }
                    self.scheduleRetry(language: language)
                } else if result?.isFinal == true {
                    print("SpeechRecognitionService: 识别会话周期正常结束 (isFinal, reset request)，无缝重启下一周期")
                    guard self._isActive else { return }
                    self.retryCount = 0
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { [weak self] in
                        guard let self, self._isActive else { return }
                        self.startAudioPipelineOnMain(language: language, playEarcon: false)
                    }
                }
            }
        }
    }

    private func resetActiveFlag() {
        _isActive = false
        isListening = false
        retryCount = 0
        resumeWorkItem?.cancel()
        resumeWorkItem = nil
        isMutedForTTS = false
        isMutedForEarcon = false
    }

    // MARK: - Audio Level Metering

    private func calculateAudioLevel(from buffer: AVAudioPCMBuffer) {
        guard let channelData = buffer.floatChannelData?[0],
              buffer.frameLength > 0 else { return }
        let count = Int(buffer.frameLength)
        var sum: Float = 0.0
        for i in 0..<count { sum += channelData[i] * channelData[i] }
        let level = min(1.0, max(0.0, sqrt(sum / Float(count)) * 5.0))
        DispatchQueue.main.async { self.audioLevel = level }
    }

    // MARK: - Diagnostic Test

    public func runDiagnosticTest(
        language: VoiceLanguage = .cantonese,
        testDuration: TimeInterval = 4.0,
        completion: @escaping (String, GameVoiceCommand?) -> Void
    ) {
        stopListening(playEarcon: false)

        DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) { [weak self] in
            guard let self else { return }

            let locale = Locale(identifier: language.localeIdentifier)
            guard let recognizer = SFSpeechRecognizer(locale: locale), recognizer.isAvailable else {
                completion("错误：当前系统不支持 \(language.displayName) 语音识别", nil)
                return
            }

            let request = SFSpeechAudioBufferRecognitionRequest()
            request.shouldReportPartialResults = true

            let inputNode = self.audioEngine.inputNode
            inputNode.removeTap(onBus: 0)
            AudioSessionCoordinator.shared.reactivate()

            let hwFormat = inputNode.outputFormat(forBus: 0)
            guard hwFormat.sampleRate > 0 && hwFormat.channelCount > 0 else {
                completion("录音启动失败: 硬件格式未就绪 (\(hwFormat))", nil)
                return
            }

            var isDiagnosticMutedForEarcon = true
            inputNode.installTap(onBus: 0, bufferSize: 4096, format: hwFormat) { buffer, _ in
                if isDiagnosticMutedForEarcon { return }
                request.append(buffer)
            }
            self.audioEngine.prepare()

            do {
                try self.audioEngine.start()
            } catch {
                inputNode.removeTap(onBus: 0)
                completion("录音启动失败: \(error.localizedDescription)", nil)
                return
            }

            Self.playStartRecordingEarcon()
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.22) {
                isDiagnosticMutedForEarcon = false
            }

            var finalTranscript = ""
            let task = recognizer.recognitionTask(with: request) { result, _ in
                if let result { finalTranscript = result.bestTranscription.formattedString }
            }

            DispatchQueue.main.asyncAfter(deadline: .now() + testDuration) { [weak self] in
                Self.playStopRecordingEarcon()
                task.cancel()
                self?.audioEngine.inputNode.removeTap(onBus: 0)
                if self?.audioEngine.isRunning == true {
                    self?.audioEngine.stop()
                }
                let matched = VoiceCommandMatcher.matchCommand(finalTranscript, languageCode: language.localeIdentifier)
                let text = finalTranscript.isEmpty ? "未检测到有效声音输入" : finalTranscript
                completion(text, matched)
            }
        }
    }
}
