package com.example.demo.guidance

import kotlin.math.sqrt

/**
 * 1.0-Second Rolling Window Stability Detector.
 * Strictly adheres to ARCHITECTURE.md Section 3.2.3 & 8.3.
 *
 * Requirements:
 * 1. Count Invariance: All frames in window have identical card count >= 1.
 * 2. Centroid Jitter Ceiling: std_dev(centroid_x) < 0.03, std_dev(centroid_y) < 0.03.
 * 3. Identity Coherence: Jaccard similarity between each frame's class IDs and latest frame >= 0.90.
 * 4. Duration: Continuous stable frames span >= requiredStableDurationMs (1000ms).
 */
class StabilityDetector(
    val requiredStableDurationMs: Long = 1000L,
    val maxCentroidJitter: Float = 0.03f,
    val minJaccardSimilarity: Float = 0.90f
) {
    data class FrameSnapshot(
        val timestampMs: Long,
        val centroidX: Float,
        val centroidY: Float,
        val classIds: Set<Int>,
        val count: Int
    )

    private val window = mutableListOf<FrameSnapshot>()

    /**
     * Updates stability detector with latest detections and returns true if stable for required duration.
     */
    fun update(boxes: List<NormalizedBox>, timestampMs: Long): Boolean {
        if (boxes.isEmpty()) {
            reset()
            return false
        }

        val count = boxes.size
        var sumX = 0f
        var sumY = 0f
        val classIds = HashSet<Int>(boxes.size)
        for (box in boxes) {
            sumX += box.centerX
            sumY += box.centerY
            classIds.add(box.classId)
        }
        val centroidX = sumX / count
        val centroidY = sumY / count

        val latestSnapshot = FrameSnapshot(
            timestampMs = timestampMs,
            centroidX = centroidX,
            centroidY = centroidY,
            classIds = classIds,
            count = count
        )

        // If card count changed compared to previous window, reset
        if (window.isNotEmpty() && window.last().count != count) {
            reset()
        }

        // Check identity coherence against latest snapshot
        if (window.isNotEmpty()) {
            val jaccard = computeJaccard(window.last().classIds, classIds)
            if (jaccard < minJaccardSimilarity) {
                reset()
            }
        }

        window.add(latestSnapshot)

        // Evict snapshots older than required window + small margin, keeping only the rolling window
        val windowStartTime = timestampMs - requiredStableDurationMs
        while (window.size > 2 && window[1].timestampMs <= windowStartTime) {
            window.removeAt(0)
        }

        // Check if rolling window covers at least requiredStableDurationMs
        val duration = latestSnapshot.timestampMs - window.first().timestampMs
        if (duration < requiredStableDurationMs) {
            return false
        }

        // Verify count invariance across all snapshots in window
        if (window.any { it.count != count }) {
            reset()
            return false
        }

        // Verify identity coherence across all snapshots in window
        val latestClasses = latestSnapshot.classIds
        for (f in window) {
            if (computeJaccard(f.classIds, latestClasses) < minJaccardSimilarity) {
                reset()
                return false
            }
        }

        // Verify centroid jitter ceiling (standard deviation < maxCentroidJitter)
        val k = window.size
        val meanX = window.map { it.centroidX }.average().toFloat()
        val meanY = window.map { it.centroidY }.average().toFloat()

        var varSumX = 0f
        var varSumY = 0f
        for (f in window) {
            val dx = f.centroidX - meanX
            val dy = f.centroidY - meanY
            varSumX += dx * dx
            varSumY += dy * dy
        }
        val stdDevX = sqrt((varSumX / k).toDouble()).toFloat()
        val stdDevY = sqrt((varSumY / k).toDouble()).toFloat()

        if (stdDevX >= maxCentroidJitter || stdDevY >= maxCentroidJitter) {
            // Centroid jitter exceeded, motion detected: drop oldest frames to re-establish stability
            while (window.size > 1 && (window.last().timestampMs - window.first().timestampMs) >= requiredStableDurationMs) {
                window.removeAt(0)
            }
            return false
        }

        return true
    }

    fun reset() {
        window.clear()
    }

    val currentWindowSize: Int get() = window.size

    val currentDurationMs: Long
        get() = if (window.size >= 2) window.last().timestampMs - window.first().timestampMs else 0L

    private fun computeJaccard(a: Set<Int>, b: Set<Int>): Float {
        if (a.isEmpty() && b.isEmpty()) return 1.0f
        val intersectionSize = a.intersect(b).size
        val unionSize = a.union(b).size
        return if (unionSize > 0) intersectionSize.toFloat() / unionSize.toFloat() else 0.0f
    }
}
