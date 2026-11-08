import Foundation
import CoreVideo
import CoreImage
#if canImport(TensorFlowLite)
import TensorFlowLite
#endif

public struct NormalizedBox: Identifiable, Hashable {
    public let id = UUID()
    public let xMin: Float
    public let yMin: Float
    public let xMax: Float
    public let yMax: Float
    public let classId: Int
    public let confidence: Float
    
    public var centerX: Float { (xMin + xMax) / 2.0 }
    public var centerY: Float { (yMin + yMax) / 2.0 }
}

public class CardModelLiteRT: ObservableObject {
    public static let INPUT_SIZE = 640
    public static let NUM_CHANNELS = 58 // 4 coords + 54 classes
    public static let NUM_ANCHORS = 8400
    public static let NUM_CLASSES = 54
    
    @Published public var detectedCards: [PlayingCard] = []
    @Published public var detectedBoxes: [NormalizedBox] = []
    @Published public var centeringScore: Float = 0.0
    @Published public var isInferring: Bool = false
    
    private let preprocessor = CoreImagePreprocessor()
    private let processingQueue = DispatchQueue(label: "card.detection.inference.queue", qos: .userInitiated)
    private let stateLock = NSLock()
    private var isInferringFrame = false
    
    #if canImport(TensorFlowLite)
    private var interpreter: Interpreter?
    #endif
    
    public init() {
        setupInterpreter()
    }
    
    private func setupInterpreter() {
        #if canImport(TensorFlowLite)
        guard let modelPath = Bundle.main.path(forResource: "card_model", ofType: "tflite") else {
            print("CardModelLiteRT: card_model.tflite not found in main bundle")
            return
        }
        
        do {
            var options = Interpreter.Options()
            options.threadCount = 4
            interpreter = try Interpreter(modelPath: modelPath, options: options)
            try interpreter?.allocateTensors()
            print("CardModelLiteRT: LiteRT Interpreter allocated successfully (640x640)")
        } catch {
            print("CardModelLiteRT: Failed to initialize LiteRT interpreter: \(error)")
        }
        #else
        print("CardModelLiteRT: TensorFlowLite module cannot be imported")
        #endif
    }
    
    /**
     * Processes live CVPixelBuffer from camera frame.
     */
    public func processFrame(_ pixelBuffer: CVPixelBuffer) {
        stateLock.lock()
        if isInferringFrame {
            stateLock.unlock()
            return
        }
        isInferringFrame = true
        stateLock.unlock()
        
        DispatchQueue.main.async {
            self.isInferring = true
        }
        
        processingQueue.async { [weak self] in
            autoreleasepool {
                guard let self = self else { return }
                defer {
                    self.stateLock.lock()
                    self.isInferringFrame = false
                    self.stateLock.unlock()
                    DispatchQueue.main.async {
                        self.isInferring = false
                    }
                }
                
                guard let preprocessed = self.preprocessor.preprocess(pixelBuffer: pixelBuffer) else {
                    return
                }
                
                let detections = self.runInference(inputData: preprocessed.modelInputData, rgbaBytes: preprocessed.rgbaBytes)
                
                // Map detected boxes to PlayingCards sorted in descending rank order
                var cards: [PlayingCard] = []
                for det in detections {
                    if let card = PlayingCard.fromClassId(det.classId) {
                        cards.append(card)
                    }
                }
                cards.sort(by: { $0 > $1 })
                
                // Compute aiming centering score Q in [0.0, 1.0]
                let score = self.computeCenteringScore(detections: detections)
                
                DispatchQueue.main.async {
                    self.detectedCards = cards
                    self.detectedBoxes = detections
                    self.centeringScore = score
                }
            }
        }
    }
    
    private func runInference(inputData: Data, rgbaBytes: [UInt8]) -> [NormalizedBox] {
        #if canImport(TensorFlowLite)
        guard let interp = interpreter else { return [] }
        
        do {
            try interp.copy(inputData, toInputAt: 0)
            try interp.invoke()
            
            let outputTensor = try interp.output(at: 0)
            let tensorFloats = outputTensor.data.withUnsafeBytes { raw in
                Array(raw.bindMemory(to: Float32.self))
            }
            
            guard tensorFloats.count >= Self.NUM_CHANNELS * Self.NUM_ANCHORS else {
                return []
            }
            
            return parseYoloOutput(tensorFloats: tensorFloats, rgbaBytes: rgbaBytes)
        } catch {
            print("CardModelLiteRT: Inference error: \(error)")
            return []
        }
        #else
        // Mock / Simulator fallback when TensorFlowLite framework is omitted
        return []
        #endif
    }
    
    private struct RawDetection {
        let xMin: Float
        let yMin: Float
        let xMax: Float
        let yMax: Float
        let confidence: Float
        let classId: Int
        let pBlackJoker: Float
        let pRedJoker: Float
    }
    
    private func parseYoloOutput(tensorFloats: [Float32], rgbaBytes: [UInt8]) -> [NormalizedBox] {
        var rawCandidates: [RawDetection] = []
        let numAnchors = Self.NUM_ANCHORS
        let inputSize = Float(Self.INPUT_SIZE)
        
        for i in 0..<numAnchors {
            var maxClassConf: Float = 0.0
            var maxClassId: Int = -1
            
            for c in 0..<Self.NUM_CLASSES {
                let conf = tensorFloats[(4 + c) * numAnchors + i]
                if conf > maxClassConf {
                    maxClassConf = conf
                    maxClassId = c
                }
            }
            
            let pBlackJoker = tensorFloats[(4 + 52) * numAnchors + i]
            let pRedJoker = tensorFloats[(4 + 53) * numAnchors + i]
            
            let isJokerCandidate = (maxClassId == 52 || maxClassId == 53 || pBlackJoker >= 0.25 || pRedJoker >= 0.25)
            // Adaptive candidate threshold: regular cards >= 0.35, Jokers in dense hands >= 0.25
            let effectiveThreshold: Float = isJokerCandidate ? 0.25 : 0.35
            
            if maxClassConf >= effectiveThreshold {
                let cx = tensorFloats[0 * numAnchors + i]
                let cy = tensorFloats[1 * numAnchors + i]
                let w = tensorFloats[2 * numAnchors + i]
                let h = tensorFloats[3 * numAnchors + i]
                
                let xMin = max(0.0, min(1.0, (cx - w / 2.0) / inputSize))
                let yMin = max(0.0, min(1.0, (cy - h / 2.0) / inputSize))
                let xMax = max(0.0, min(1.0, (cx + w / 2.0) / inputSize))
                let yMax = max(0.0, min(1.0, (cy + h / 2.0) / inputSize))
                
                rawCandidates.append(RawDetection(
                    xMin: xMin, yMin: yMin, xMax: xMax, yMax: yMax,
                    confidence: maxClassConf,
                    classId: maxClassId,
                    pBlackJoker: pBlackJoker,
                    pRedJoker: pRedJoker
                ))
            }
        }
        
        // Class-aware NMS
        let nmsFiltered = applyNms(detections: rawCandidates, iouThreshold: 0.45)
        
        // Layer-2 Joker Chroma Resolver
        var finalDetections: [NormalizedBox] = []
        for det in nmsFiltered {
            var finalClassId = det.classId
            if det.classId == 52 || det.classId == 53 {
                let chromaRes = JokerChromaResolver.resolveJoker(
                    xMin: det.xMin, yMin: det.yMin, xMax: det.xMax, yMax: det.yMax,
                    pBlackJoker: det.pBlackJoker,
                    pRedJoker: det.pRedJoker,
                    rgbaBytes: rgbaBytes,
                    imageWidth: Self.INPUT_SIZE,
                    imageHeight: Self.INPUT_SIZE
                )
                finalClassId = chromaRes.resolvedClassId
            }
            
            finalDetections.append(NormalizedBox(
                xMin: det.xMin,
                yMin: det.yMin,
                xMax: det.xMax,
                yMax: det.yMax,
                classId: finalClassId,
                confidence: det.confidence
            ))
        }
        
        return finalDetections
    }
    
    private func applyNms(detections: [RawDetection], iouThreshold: Float) -> [RawDetection] {
        let sorted = detections.sorted(by: { $0.confidence > $1.confidence })
        var selected: [RawDetection] = []
        var active = [Bool](repeating: true, count: sorted.count)
        
        for i in 0..<sorted.count {
            guard active[i] else { continue }
            let a = sorted[i]
            selected.append(a)
            
            for j in (i + 1)..<sorted.count {
                guard active[j] else { continue }
                let b = sorted[j]
                
                let interXMin = max(a.xMin, b.xMin)
                let interYMin = max(a.yMin, b.yMin)
                let interXMax = min(a.xMax, b.xMax)
                let interYMax = min(a.yMax, b.yMax)
                
                let interW = max(0.0, interXMax - interXMin)
                let interH = max(0.0, interYMax - interYMin)
                let interArea = interW * interH
                
                let aArea = (a.xMax - a.xMin) * (a.yMax - a.yMin)
                let bArea = (b.xMax - b.xMin) * (b.yMax - b.yMin)
                let unionArea = aArea + bArea - interArea
                
                if unionArea > 0.0 {
                    let iou = interArea / unionArea
                    let isSameClass = (a.classId == b.classId)
                    let isBothJokers = (a.classId == 52 || a.classId == 53) && (b.classId == 52 || b.classId == 53)
                    
                    let aCenterX = (a.xMin + a.xMax) / 2.0
                    let aCenterY = (a.yMin + a.yMax) / 2.0
                    let bCenterX = (b.xMin + b.xMax) / 2.0
                    let bCenterY = (b.yMin + b.yMax) / 2.0
                    let centerDistSq = (aCenterX - bCenterX) * (aCenterX - bCenterX) + (aCenterY - bCenterY) * (aCenterY - bCenterY)
                    
                    let shouldSuppress: Bool
                    if isSameClass {
                        shouldSuppress = (iou > iouThreshold)
                    } else if isBothJokers {
                        shouldSuppress = (centerDistSq < 0.0008 && iou > 0.72)
                    } else {
                        shouldSuppress = (centerDistSq < 0.0010 && iou > 0.75)
                    }
                    
                    if shouldSuppress {
                        active[j] = false
                    }
                }
            }
        }
        
        return selected
    }
    
    private func computeCenteringScore(detections: [NormalizedBox]) -> Float {
        guard !detections.isEmpty else { return 0.0 }
        
        var totalDist: Float = 0.0
        for box in detections {
            let dx = box.centerX - 0.5
            let dy = box.centerY - 0.5
            let dist = sqrt(dx * dx + dy * dy)
            totalDist += dist
        }
        
        let avgDist = totalDist / Float(detections.count)
        // Score Q = 1.0 when perfectly centered, decaying towards 0 when off-center
        let alignment = max(0.0, min(1.0, 1.0 - (avgDist / 0.5)))
        let countFactor = min(1.0, Float(detections.count) / 5.0)
        return alignment * 0.7 + countFactor * 0.3
    }
}
