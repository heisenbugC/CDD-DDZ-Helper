import SwiftUI
import AVFoundation

/**
 * SwiftUI representable wrapper for CameraViewController.
 */
public struct CameraView: UIViewControllerRepresentable {
    public var frameHandler: (CVPixelBuffer) -> Void
    
    public init(frameHandler: @escaping (CVPixelBuffer) -> Void) {
        self.frameHandler = frameHandler
    }
    
    public func makeUIViewController(context: Context) -> CameraViewController {
        let controller = CameraViewController()
        controller.frameHandler = frameHandler
        return controller
    }
    
    public func updateUIViewController(_ uiViewController: CameraViewController, context: Context) {
        uiViewController.frameHandler = frameHandler
    }
}
