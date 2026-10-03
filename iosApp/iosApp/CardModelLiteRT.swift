import Foundation
import CoreVideo
import CoreImage
import TensorFlowLite

class CardModelLiteRT: ObservableObject {
    @Published var suitResult: String = "Waiting..."
    @Published var rankResult: String = "Waiting..."
    
    private var interpreter: Interpreter?
    private let context = CIContext()
    
    private let suits = ["Diamond", "Club", "Heart", "Spade", "Joker"]
    private let ranks = ["3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A", "2", "Black Joker", "Red Joker"]
    
    init() {
        setupInterpreter()
    }
    
    private func setupInterpreter() {
        guard let modelPath = Bundle.main.path(forResource: "card_model", ofType: "tflite") else {
            print("Failed to load the model file with name: card_model.tflite")
            return
        }
        
        do {
            interpreter = try Interpreter(modelPath: modelPath)
            try interpreter?.allocateTensors()
        } catch {
            print("Failed to create the interpreter with error: \(error.localizedDescription)")
        }
    }
    
    func processFrame(_ pixelBuffer: CVPixelBuffer) {
        // We do this processing on a background queue, then dispatch results to main
        let ciImage = CIImage(cvPixelBuffer: pixelBuffer)
        
        // 1. Resize to 128x128 as expected by the model
        let scaleX = 128.0 / CGFloat(CVPixelBufferGetWidth(pixelBuffer))
        let scaleY = 128.0 / CGFloat(CVPixelBufferGetHeight(pixelBuffer))
        let scaledImage = ciImage.transformed(by: CGAffineTransform(scaleX: scaleX, y: scaleY))
        
        guard let cgImage = context.createCGImage(scaledImage, from: CGRect(x: 0, y: 0, width: 128, height: 128)) else { return }
        
        // 2. Convert to raw bytes (RGB / float32)
        guard let rgbData = rgbDataFromCGImage(cgImage) else { return }
        
        // 3. Run inference
        do {
            guard let interpreter = interpreter else { return }
            try interpreter.copy(rgbData, toInputAt: 0)
            try interpreter.invoke()
            
            let suitTensor = try interpreter.output(at: 0)
            let rankTensor = try interpreter.output(at: 1)
            
            let suitProbabilities = suitTensor.data.toArray(type: Float32.self)
            let rankProbabilities = rankTensor.data.toArray(type: Float32.self)
            
            let maxSuitIndex = suitProbabilities.firstIndex(of: suitProbabilities.max() ?? 0) ?? 0
            let maxRankIndex = rankProbabilities.firstIndex(of: rankProbabilities.max() ?? 0) ?? 0
            
            DispatchQueue.main.async {
                self.suitResult = self.suits[maxSuitIndex]
                self.rankResult = self.ranks[maxRankIndex]
            }
        } catch {
            print("Failed to invoke the interpreter with error: \(error.localizedDescription)")
        }
    }
    
    private func rgbDataFromCGImage(_ cgImage: CGImage) -> Data? {
        let width = cgImage.width
        let height = cgImage.height
        let colorSpace = CGColorSpaceCreateDeviceRGB()
        var rawData = [UInt8](repeating: 0, count: width * height * 4)
        let bytesPerPixel = 4
        let bytesPerRow = bytesPerPixel * width
        let bitsPerComponent = 8
        
        let context = CGContext(data: &rawData, width: width, height: height,
                                bitsPerComponent: bitsPerComponent, bytesPerRow: bytesPerRow,
                                space: colorSpace, bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue)
        context?.draw(cgImage, in: CGRect(x: 0, y: 0, width: CGFloat(width), height: CGFloat(height)))
        
        var floatData = [Float32]()
        for i in 0..<(width * height) {
            let r = Float32(rawData[i * 4]) / 255.0
            let g = Float32(rawData[i * 4 + 1]) / 255.0
            let b = Float32(rawData[i * 4 + 2]) / 255.0
            floatData.append(r)
            floatData.append(g)
            floatData.append(b)
        }
        return Data(buffer: UnsafeBufferPointer(start: floatData, count: floatData.count))
    }
}

// Extension to help extract Swift Arrays from TFLite Data
extension Data {
    func toArray<T>(type: T.Type) -> [T] {
        return self.withUnsafeBytes { pointer in
            guard let binded = pointer.bindMemory(to: T.self).baseAddress else { return [] }
            return Array(UnsafeBufferPointer(start: binded, count: self.count / MemoryLayout<T>.stride))
        }
    }
}
