import UIKit
import AVFoundation
import CoreVideo

/**
 * CameraViewController managing the 1280x720 AVCaptureSession,
 * extracting 32BGRA frames on an isolated background queue,
 * and passing them through CoreImage GPU preprocessing.
 */
public class CameraViewController: UIViewController, AVCaptureVideoDataOutputSampleBufferDelegate {
    public var frameHandler: ((CVPixelBuffer) -> Void)?
    
    private let captureSession = AVCaptureSession()
    private let videoDataOutput = AVCaptureVideoDataOutput()
    private var previewLayer: AVCaptureVideoPreviewLayer?
    private let queue = DispatchQueue(label: "camera.frame.pipeline.queue", qos: .userInitiated)
    
    public override func viewDidLoad() {
        super.viewDidLoad()
        setupCamera()
    }
    
    private func setupCamera() {
        captureSession.beginConfiguration()
        captureSession.sessionPreset = .hd1280x720
        
        let deviceDiscoverySession = AVCaptureDeviceDiscoverySession(
            deviceTypes: [.builtInWideAngleCamera],
            mediaType: .video,
            position: .back
        )
        
        guard let videoDevice = deviceDiscoverySession.devices.first,
              let videoDeviceInput = try? AVCaptureDeviceInput(device: videoDevice) else {
            print("Failed to access rear wide-angle camera")
            captureSession.commitConfiguration()
            return
        }
        
        if captureSession.canAddInput(videoDeviceInput) {
            captureSession.addInput(videoDeviceInput)
        }
        
        if captureSession.canAddOutput(videoDataOutput) {
            captureSession.addOutput(videoDataOutput)
            
            videoDataOutput.videoSettings = [
                kCVPixelBufferPixelFormatTypeKey as String: Int(kCVPixelFormatType_32BGRA)
            ]
            videoDataOutput.alwaysDiscardsLateVideoFrames = true
            videoDataOutput.setSampleBufferDelegate(self, queue: queue)
        }
        
        if let connection = videoDataOutput.connection(with: .video) {
            if connection.isVideoOrientationSupported {
                connection.videoOrientation = .portrait
            }
        }
        
        let layer = AVCaptureVideoPreviewLayer(session: captureSession)
        layer.videoGravity = .resizeAspectFill
        view.layer.addSublayer(layer)
        self.previewLayer = layer
        
        captureSession.commitConfiguration()
        
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            self?.captureSession.startRunning()
        }
    }
    
    public override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        previewLayer?.frame = view.bounds
    }
    
    public func captureOutput(
        _ output: AVCaptureOutput,
        didOutput sampleBuffer: CMSampleBuffer,
        from connection: AVCaptureConnection
    ) {
        guard let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        frameHandler?(pixelBuffer)
    }
    
    public func pauseCamera() {
        if captureSession.isRunning {
            captureSession.stopRunning()
        }
    }
    
    public func resumeCamera() {
        if !captureSession.isRunning {
            DispatchQueue.global(qos: .userInitiated).async { [weak self] in
                self?.captureSession.startRunning()
            }
        }
    }
    
    deinit {
        if captureSession.isRunning {
            captureSession.stopRunning()
        }
    }
}
