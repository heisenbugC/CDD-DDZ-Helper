import AVFoundation

/**
 * AudioSessionCoordinator — single point of truth for the app's AVAudioSession.
 *
 * Rules:
 *  - ONE shared category: `.playAndRecord` + `.mixWithOthers` + `.defaultToSpeaker`
 *  - Configured ONCE at app launch (iOSApp.init), before camera or mic pipeline starts.
 *  - `AVCaptureSession.usesApplicationAudioSession = true` +
 *    `automaticallyConfiguresApplicationAudioSession = false` prevents the camera
 *    session from hijacking the category after it is set.
 *  - `reactivate()` is called by SpeechRecognitionService just before AVAudioEngine.start()
 *    to re-assert the category in case anything changed it in the meantime.
 *
 * iOS 18+ note: Both AVSpeechSynthesizer and AVAudioEngine work in .playAndRecord.
 * .mixWithOthers is essential — without it, one audio producer stops the other.
 */
public final class AudioSessionCoordinator {
    public static let shared = AudioSessionCoordinator()
    private(set) var isConfigured = false

    private init() {}

    // MARK: - Initial configuration (call from iOSApp.init)

    public func configureOnce() {
        guard !isConfigured else { return }
        isConfigured = true
        applyCategory()
    }

    // MARK: - Re-assert category (call from SpeechRecognitionService before engine start)

    /// Re-applies the shared AVAudioSession category and re-activates the session.
    /// Safe to call from the main thread. Use this before starting AVAudioEngine to guard
    /// against AVCaptureSession or another component having changed the category.
    public func reactivate() {
        applyCategory()
    }

    // MARK: - Private

    private func applyCategory() {
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(
                .playAndRecord,
                mode: .default,
                options: [.mixWithOthers, .defaultToSpeaker, .allowBluetooth]
            )
            try session.setActive(true, options: .notifyOthersOnDeactivation)
            print("AudioSessionCoordinator: AVAudioSession active — category=playAndRecord, mixWithOthers")
        } catch {
            print("AudioSessionCoordinator: FAILED — \(error)")
        }
    }
}
