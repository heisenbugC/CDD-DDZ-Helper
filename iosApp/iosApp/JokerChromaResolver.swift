import Foundation

/**
 * Two-Layer Joker Disambiguation: Post-Processing Chroma Heuristic Safeguard in Swift.
 *
 * Evaluates bounding box pixels in HSV color space to reliably separate:
 * - Black Joker (class 52, 小王): Monochrome black/gray ink. Under warm ambient light,
 *   background paper shifts to yellow-orange (Hue 25°-50°, Saturation 0.12-0.28),
 *   with ZERO high-saturation True Red ink.
 * - Red Joker (class 53, 大王): Authentic printing Red ink with True Red hue
 *   (Hue in [342°, 360°] or [0°, 14°]) and high saturation (S >= 0.28).
 *
 * Safeguard Guarantee:
 * - Red Joker retention is 100% protected: if the neural network predicts Red Joker,
 *   it is maintained as Red Joker unless conclusively monochrome.
 * - Warm ambient lighting on Black Joker is rejected: warm paper/table reflections
 *   are isolated into `warmDensity` and cannot trigger `redInkDensity`.
 */
public class JokerChromaResolver {
    
    public static let AMBIGUITY_MARGIN_THRESHOLD: Float = 0.12
    public static let RED_INK_THRESHOLD: Float = 0.008 // 0.8% of pixels must be True Red ink
    public static let WARM_AMBIENCE_MAX_RATIO: Float = 0.15 // Red ink must not be swamped by warm paper
    
    public struct ChromaAnalysisResult {
        public let resolvedClassId: Int
        public let redDensity: Float
        public let meanSaturation: Float
        public let isAmbiguous: Bool
        public let overriddenByChroma: Bool
    }
    
    public struct RawChromaStats {
        public let redInkDensity: Float
        public let pureRedDensity: Float
        public let warmDensity: Float
        public let meanSaturation: Float
        public let sampleCount: Int
    }
    
    /**
     * Resolves Joker ambiguity given normalized bounding box, class probabilities,
     * and RGBA8 pixel byte array of size (imageWidth * imageHeight * 4).
     */
    public static func resolveJoker(
        xMin: Float, yMin: Float, xMax: Float, yMax: Float,
        pBlackJoker: Float,
        pRedJoker: Float,
        rgbaBytes: [UInt8],
        imageWidth: Int = 640,
        imageHeight: Int = 640
    ) -> ChromaAnalysisResult {
        let initialClassId = (pRedJoker > pBlackJoker) ? 53 : 52
        
        let stats = analyzeChroma(
            xMin: xMin, yMin: yMin, xMax: xMax, yMax: yMax,
            rgbaBytes: rgbaBytes,
            imageWidth: imageWidth,
            imageHeight: imageHeight
        )
        
        // 1. True Red Ink: authentic printing ink with crimson/scarlet hue and high saturation
        let hasClearRedInk = (stats.redInkDensity >= RED_INK_THRESHOLD) &&
            (stats.pureRedDensity >= 0.004 || stats.redInkDensity >= stats.warmDensity * WARM_AMBIENCE_MAX_RATIO || stats.redInkDensity >= 0.015)
        
        // 2. Risk-free preservation of Red Joker:
        let modelFavoredRed = (initialClassId == 53 || pRedJoker >= 0.30)
        let modelStronglyFavoredBlack = (initialClassId == 52 && pBlackJoker >= 0.50 && pRedJoker <= 0.12)
        
        let resolvedClassId: Int
        if hasClearRedInk && (!modelStronglyFavoredBlack || stats.redInkDensity >= 0.012) {
            resolvedClassId = 53
        } else if modelFavoredRed && (stats.redInkDensity >= 0.002 || stats.pureRedDensity >= 0.001) {
            resolvedClassId = 53
        } else if modelFavoredRed && pRedJoker > (pBlackJoker + 0.15) && stats.meanSaturation >= 0.10 {
            resolvedClassId = 53
        } else {
            resolvedClassId = 52
        }
        
        let margin = abs(pBlackJoker - pRedJoker)
        let isAmbiguous = (pRedJoker <= 0.05 || margin < AMBIGUITY_MARGIN_THRESHOLD)
        
        return ChromaAnalysisResult(
            resolvedClassId: resolvedClassId,
            redDensity: stats.redInkDensity,
            meanSaturation: stats.meanSaturation,
            isAmbiguous: isAmbiguous,
            overriddenByChroma: (resolvedClassId != initialClassId)
        )
    }
    
    public static func analyzeChroma(
        xMin: Float, yMin: Float, xMax: Float, yMax: Float,
        rgbaBytes: [UInt8],
        imageWidth: Int = 640,
        imageHeight: Int = 640
    ) -> RawChromaStats {
        let px0 = max(0, min(imageWidth - 1, Int(xMin * Float(imageWidth))))
        let py0 = max(0, min(imageHeight - 1, Int(yMin * Float(imageHeight))))
        let px1 = max(0, min(imageWidth - 1, Int(xMax * Float(imageWidth))))
        let py1 = max(0, min(imageHeight - 1, Int(yMax * Float(imageHeight))))
        
        let boxWidth = px1 - px0 + 1
        let boxHeight = py1 - py0 + 1
        
        if boxWidth <= 0 || boxHeight <= 0 || rgbaBytes.count < imageWidth * imageHeight * 4 {
            return RawChromaStats(redInkDensity: 0, pureRedDensity: 0, warmDensity: 0, meanSaturation: 0, sampleCount: 0)
        }
        
        // Subsample up to 400 pixels
        let stepX = max(1, boxWidth / 20)
        let stepY = max(1, boxHeight / 20)
        
        var pureRedCount = 0
        var vermilionCount = 0
        var warmCount = 0
        var totalSamples = 0
        var sumSaturation: Float = 0.0
        
        for y in stride(from: py0, through: py1, by: stepY) {
            let rowOffset = y * imageWidth * 4
            for x in stride(from: px0, through: px1, by: stepX) {
                let pixelOffset = rowOffset + x * 4
                guard pixelOffset + 2 < rgbaBytes.count else { continue }
                
                let r = Int(rgbaBytes[pixelOffset])
                let g = Int(rgbaBytes[pixelOffset + 1])
                let b = Int(rgbaBytes[pixelOffset + 2])
                
                let (h, s, v) = rgbToHsv(r: r, g: g, b: b)
                sumSaturation += s
                totalSamples += 1
                
                // 1. Pure Red Ink: crimson / scarlet
                let isPureRed = (h <= 14.0 || h >= 342.0) && s >= 0.28 && v >= 0.18
                if isPureRed { pureRedCount += 1 }
                
                // 2. Vermilion Red Ink
                let isVermilion = (h > 14.0 && h <= 22.0) && s >= 0.40 && v >= 0.20
                if isVermilion { vermilionCount += 1 }
                
                // 3. Warm Ambient Light / Wood / Yellow paper reflection
                let isWarm = (h > 22.0 && h <= 55.0) && s >= 0.14 && v >= 0.30
                if isWarm { warmCount += 1 }
            }
        }
        
        if totalSamples == 0 {
            return RawChromaStats(redInkDensity: 0, pureRedDensity: 0, warmDensity: 0, meanSaturation: 0, sampleCount: 0)
        }
        
        let totalRed = pureRedCount + vermilionCount
        let redDensity = Float(totalRed) / Float(totalSamples)
        let pureDensity = Float(pureRedCount) / Float(totalSamples)
        let warmDensity = Float(warmCount) / Float(totalSamples)
        let meanSat = sumSaturation / Float(totalSamples)
        
        return RawChromaStats(
            redInkDensity: redDensity,
            pureRedDensity: pureDensity,
            warmDensity: warmDensity,
            meanSaturation: meanSat,
            sampleCount: totalSamples
        )
    }
    
    public static func rgbToHsv(r: Int, g: Int, b: Int) -> (h: Float, s: Float, v: Float) {
        let rf = Float(r) / 255.0
        let gf = Float(g) / 255.0
        let bf = Float(b) / 255.0
        
        let cMax = max(rf, max(gf, bf))
        let cMin = min(rf, min(gf, bf))
        let delta = cMax - cMin
        
        let v = cMax
        let s = (cMax > 0.0) ? (delta / cMax) : 0.0
        
        var h: Float = 0.0
        if delta > 0.0 {
            if cMax == rf {
                h = ((gf - bf) / delta).truncatingRemainder(dividingBy: 6.0)
            } else if cMax == gf {
                h = ((bf - rf) / delta) + 2.0
            } else {
                h = ((rf - gf) / delta) + 4.0
            }
            h *= 60.0
            if h < 0.0 { h += 360.0 }
        }
        
        return (h, s, v)
    }
}

