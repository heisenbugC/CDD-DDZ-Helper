import Foundation
import CoreVideo
import CoreImage
import Accelerate

/**
 * High-performance iOS GPU image preprocessor using CoreImage and Metal.
 * Replaces OpenCV with zero native C++ overhead.
 *
 * Implements:
 * 1. Center crop and bilinear scaling to 640x640.
 * 2. CIColorControls (contrast: 1.30, brightness: 0.04).
 * 3. CIUnsharpMask (radius: 2.5, intensity: 0.85).
 * 4. Rendering directly to normalized [0.0, 1.0] Float32 RGB tensor data.
 * 5. Providing RGBA8 byte buffer for Layer-2 Joker Chroma resolution.
 */
public class CoreImagePreprocessor {
    public let targetWidth: Int = 640
    public let targetHeight: Int = 640
    
    private let ciContext: CIContext
    private let colorSpace = CGColorSpaceCreateDeviceRGB()
    
    public init() {
        self.ciContext = CIContext(options: [
            .useSoftwareRenderer: false,
            .priorityRequestLow: false
        ])
    }
    
    public struct PreprocessedFrame {
        public let modelInputData: Data
        public let rgbaBytes: [UInt8]
    }
    
    /**
     * Preprocesses input CVPixelBuffer using CoreImage GPU pipeline.
     * Returns modelInputData (640*640*3 Float32 values in [0.0, 1.0]) and raw RGBA8 byte buffer.
     */
    public func preprocess(pixelBuffer: CVPixelBuffer) -> PreprocessedFrame? {
        var ciImage = CIImage(cvPixelBuffer: pixelBuffer)
        
        // 1. Center crop to square
        let srcWidth = CGFloat(CVPixelBufferGetWidth(pixelBuffer))
        let srcHeight = CGFloat(CVPixelBufferGetHeight(pixelBuffer))
        let cropSize = min(srcWidth, srcHeight)
        let cropX = (srcWidth - cropSize) / 2.0
        let cropY = (srcHeight - cropSize) / 2.0
        
        let cropRect = CGRect(x: cropX, y: cropY, width: cropSize, height: cropSize)
        ciImage = ciImage.cropped(to: cropRect)
        
        // Translate origin to (0, 0)
        ciImage = ciImage.transformed(by: CGAffineTransform(translationX: -cropX, y: -cropY))
        
        // Downsample / scale to 640x640
        let scale = CGFloat(targetWidth) / cropSize
        ciImage = ciImage.transformed(by: CGAffineTransform(scaleX: scale, y: scale))
        
        // 2. Black-level / Histogram contrast boost via CIColorControls
        if let colorFilter = CIFilter(name: "CIColorControls") {
            colorFilter.setValue(ciImage, forKey: kCIInputImageKey)
            colorFilter.setValue(1.30, forKey: kCIInputContrastKey)
            colorFilter.setValue(0.04, forKey: kCIInputBrightnessKey)
            if let output = colorFilter.outputImage {
                ciImage = output
            }
        }
        
        // 3. Edge sharpening via CIUnsharpMask
        if let unsharpFilter = CIFilter(name: "CIUnsharpMask") {
            unsharpFilter.setValue(ciImage, forKey: kCIInputImageKey)
            unsharpFilter.setValue(2.5, forKey: kCIInputRadiusKey)
            unsharpFilter.setValue(0.85, forKey: kCIInputIntensityKey)
            if let output = unsharpFilter.outputImage {
                ciImage = output
            }
        }
        
        // 4. Render GPU output to RGBA byte buffer
        var rgbaBytes = [UInt8](repeating: 0, count: targetWidth * targetHeight * 4)
        let bounds = CGRect(x: 0, y: 0, width: targetWidth, height: targetHeight)
        ciContext.render(
            ciImage,
            toBitmap: &rgbaBytes,
            rowBytes: targetWidth * 4,
            bounds: bounds,
            format: .RGBA8,
            colorSpace: colorSpace
        )
        
        // 5. Convert RGBA UInt8 to RGB Float32 normalized to [0.0, 1.0]
        let numPixels = targetWidth * targetHeight
        var floatData = [Float32](repeating: 0.0, count: numPixels * 3)
        
        let inv255: Float32 = 1.0 / 255.0
        var srcIdx = 0
        var dstIdx = 0
        for _ in 0..<numPixels {
            floatData[dstIdx] = Float32(rgbaBytes[srcIdx]) * inv255
            floatData[dstIdx + 1] = Float32(rgbaBytes[srcIdx + 1]) * inv255
            floatData[dstIdx + 2] = Float32(rgbaBytes[srcIdx + 2]) * inv255
            srcIdx += 4
            dstIdx += 3
        }
        
        let data = Data(bytes: floatData, count: floatData.count * MemoryLayout<Float32>.size)
        return PreprocessedFrame(modelInputData: data, rgbaBytes: rgbaBytes)
    }
}
