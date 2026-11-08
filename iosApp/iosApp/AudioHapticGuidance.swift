import Foundation
import AVFoundation
import CoreHaptics
import UIKit

/**
 * iOS Audio & Haptic Aiming Guidance Engine.
 * Features real-time AVAudioEngine sine synthesis with AVAudioSession ducking
 * and CoreHaptics tactile ticks/pulses matching Android parity.
 */
public class AudioHapticGuidance: ObservableObject {
    private var audioEngine = AVAudioEngine()
    private var playerNode = AVAudioPlayerNode()
    private var audioFormat: AVAudioFormat?
    
    private var hapticEngine: CHHapticEngine?
    private let lightImpact = UIImpactFeedbackGenerator(style: .light)
    private let mediumImpact = UIImpactFeedbackGenerator(style: .medium)
    private let heavyImpact = UIImpactFeedbackGenerator(style: .heavy)
    private let notificationFeedback = UINotificationFeedbackGenerator()
    
    private var guidanceTimer: Timer?
    
    @Published public var centeringScore: Float = 0.0
    @Published public var isRunning: BooleanLiteralType = false
    
    public init() {
        setupAudioEngine()
        setupHaptics()
    }
    
    private func setupAudioEngine() {
        audioFormat = AVAudioFormat(standardFormatWithSampleRate: 44100, channels: 1)
        guard let format = audioFormat else { return }
        
        audioEngine.attach(playerNode)
        audioEngine.connect(playerNode, to: audioEngine.mainMixerNode, format: format)
        
        do {
            try audioEngine.start()
        } catch {
            print("Failed to start AVAudioEngine: \(error)")
        }
    }
    
    private func setupHaptics() {
        guard CHHapticEngine.capabilitiesForHardware().supportsHaptics else { return }
        do {
            hapticEngine = try CHHapticEngine()
            try hapticEngine?.start()
        } catch {
            print("Failed to start CHHapticEngine: \(error)")
        }
        
        lightImpact.prepare()
        mediumImpact.prepare()
        heavyImpact.prepare()
        notificationFeedback.prepare()
    }
    
    public func startGuidance() {
        guard !isRunning else { return }
        isRunning = true
        if !playerNode.isPlaying {
            playerNode.play()
        }
        scheduleNextPulse()
    }
    
    public func stopGuidance() {
        isRunning = false
        guidanceTimer?.invalidate()
        guidanceTimer = nil
        playerNode.stop()
    }
    
    public func updateCentering(score: Float) {
        self.centeringScore = max(0.0, min(1.0, score))
    }
    
    private func scheduleNextPulse() {
        guard isRunning else { return }
        
        let q = centeringScore
        let interval: TimeInterval
        let freq: Float
        
        if q <= 0.05 {
            // Search click: 180Hz pulse every 1.2s
            freq = 180.0
            interval = 1.2
            playHaptic(type: "search_tick")
        } else {
            // Pitch curve: f(Q) = 220 + 660 * Q^1.5
            freq = 220.0 + 660.0 * pow(q, 1.5)
            // Pulse interval: delta_t(Q) = 900 - 750 * Q ms
            interval = max(0.15, (900.0 - 750.0 * Double(q)) / 1000.0)
            playHaptic(type: "alignment_pulse")
        }
        
        playTone(frequency: freq, duration: 0.04)
        
        guidanceTimer = Timer.scheduledTimer(withTimeInterval: interval, repeats: false) { [weak self] _ in
            self?.scheduleNextPulse()
        }
    }
    
    public func playTone(frequency: Float, duration: Double, amplitude: Float = 0.5) {
        guard let format = audioFormat else { return }
        let sampleRate = Float(format.sampleRate)
        let sampleCount = Int(sampleRate * Float(duration))
        
        guard let buffer = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: AVAudioFrameCount(sampleCount)) else { return }
        buffer.frameLength = AVAudioFrameCount(sampleCount)
        
        guard let channelData = buffer.floatChannelData?[0] else { return }
        
        let omega = 2.0 * Float.pi * frequency / sampleRate
        let rampSamples = min(sampleCount / 4, Int(sampleRate * 0.005)) // 5ms fade
        
        for i in 0..<sampleCount {
            var env: Float = 1.0
            if i < rampSamples {
                env = Float(i) / Float(rampSamples)
            } else if i > sampleCount - rampSamples {
                env = Float(sampleCount - i) / Float(rampSamples)
            }
            channelData[i] = sin(omega * Float(i)) * env * amplitude
        }
        
        playerNode.scheduleBuffer(buffer, at: nil, options: [], completionHandler: nil)
    }
    
    public func playCenteredChime() {
        playTone(frequency: 880.0, duration: 0.08, amplitude: 0.7)
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.08) { [weak self] in
            self?.playTone(frequency: 1320.0, duration: 0.12, amplitude: 0.8)
        }
        playHaptic(type: "centered_double_click")
    }
    
    public func playLockChord() {
        // G4-C5-E5 major triad
        guard let format = audioFormat else { return }
        let sampleRate = Float(format.sampleRate)
        let sampleCount = Int(sampleRate * 0.28)
        
        guard let buffer = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: AVAudioFrameCount(sampleCount)) else { return }
        buffer.frameLength = AVAudioFrameCount(sampleCount)
        guard let channelData = buffer.floatChannelData?[0] else { return }
        
        let omega1 = 2.0 * Float.pi * 392.00 / sampleRate
        let omega2 = 2.0 * Float.pi * 523.25 / sampleRate
        let omega3 = 2.0 * Float.pi * 659.25 / sampleRate
        
        for i in 0..<sampleCount {
            let decay = 1.0 - (Float(i) / Float(sampleCount))
            let s1 = sin(omega1 * Float(i)) * 0.35
            let s2 = sin(omega2 * Float(i)) * 0.35
            let s3 = sin(omega3 * Float(i)) * 0.30
            channelData[i] = (s1 + s2 + s3) * decay * 0.7
        }
        
        playerNode.scheduleBuffer(buffer, at: nil, options: [], completionHandler: nil)
        playHaptic(type: "lock_thud")
    }
    
    public func playHaptic(type: String) {
        switch type {
        case "search_tick":
            lightImpact.impactOccurred(intensity: 0.5)
        case "alignment_pulse":
            mediumImpact.impactOccurred(intensity: CGFloat(max(0.4, centeringScore)))
        case "centered_double_click":
            notificationFeedback.notificationOccurred(.success)
        case "lock_thud":
            heavyImpact.impactOccurred(intensity: 1.0)
        default:
            break
        }
    }
    
    deinit {
        stopGuidance()
        audioEngine.stop()
    }
}
