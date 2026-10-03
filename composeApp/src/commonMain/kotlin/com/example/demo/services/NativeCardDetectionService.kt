package com.example.demo.services

/**
 * 100% Native Kotlin implementation of bounding box extraction!
 * Adapted to accept grayscale ByteArray as suggested by the CameraAnalyzer guide.
 */
class NativeCardDetectionService {

    data class BoundingBox(val x: Int, val y: Int, val width: Int, val height: Int)

    /**
     * Finds white/bright rectangular regions in a raw Grayscale ByteArray (Y plane).
     */
    fun findCardBoundingBoxes(pixels: ByteArray, width: Int, height: Int): List<BoundingBox> {
        val boxes = mutableListOf<BoundingBox>()
        val visited = BooleanArray(width * height)

        // Step by 4 for performance (subsampling)
        for (y in 0 until height step 4) {
            for (x in 0 until width step 4) {
                val idx = y * width + x
                if (visited[idx]) continue

                val lum = pixels[idx].toInt() and 0xFF

                // Simple threshold: Cards are usually bright white/grey
                if (lum >= 200) {
                    val box = floodFillFindBox(pixels, visited, width, height, x, y)
                    // Filter out noise: keep boxes that are roughly card-sized
                    if (box.width > 50 && box.height > 50) {
                        boxes.add(box)
                    }
                }
            }
        }
        return boxes
    }

    private fun floodFillFindBox(
        pixels: ByteArray,
        visited: BooleanArray,
        width: Int,
        height: Int,
        startX: Int,
        startY: Int
    ): BoundingBox {
        var minX = startX
        var maxX = startX
        var minY = startY
        var maxY = startY

        val queue = IntArray(width * height * 2) // Flat queue for x, y
        var head = 0
        var tail = 0
        
        queue[tail++] = startX
        queue[tail++] = startY

        while (head < tail) {
            val x = queue[head++]
            val y = queue[head++]
            val idx = y * width + x

            if (visited[idx]) continue
            visited[idx] = true

            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y

            // Check neighbors (step by 4)
            val neighbors = intArrayOf(
                x - 4, y,
                x + 4, y,
                x, y - 4,
                x, y + 4
            )

            for (i in neighbors.indices step 2) {
                val nx = neighbors[i]
                val ny = neighbors[i + 1]

                if (nx in 0 until width && ny in 0 until height) {
                    val nIdx = ny * width + nx
                    if (!visited[nIdx]) {
                        val lum = pixels[nIdx].toInt() and 0xFF

                        // Slightly lower threshold for edges
                        if (lum >= 180) {
                            queue[tail++] = nx
                            queue[tail++] = ny
                        }
                    }
                }
            }
        }

        return BoundingBox(minX, minY, maxX - minX, maxY - minY)
    }
}
