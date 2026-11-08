import SwiftUI
import AVFoundation

@main
struct iOSApp: App {
    init() {
        // Configure the shared AVAudioSession ONCE before any component
        // (NativeSpeechEngine, SpeechRecognitionService, AVCaptureSession) can override it.
        // Category: .playAndRecord + .mixWithOthers allows mic + speaker + camera to coexist.
        AudioSessionCoordinator.shared.configureOnce()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
