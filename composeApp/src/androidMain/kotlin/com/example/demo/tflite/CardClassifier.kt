package com.example.demo.tflite

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class CardClassifier(context: Context) {
    private var interpreter: Interpreter? = null
    
    // Config matching our YOLOv9 model
    private val inputSize = 640
    private val confidenceThreshold = 0.5f
    private val iouThreshold = 0.45f
    
    init {
        try {
            val assetManager = context.assets
            val fd = assetManager.openFd("card_model.tflite")
            val inputStream = FileInputStream(fd.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = fd.startOffset
            val declaredLength = fd.declaredLength
            val modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
            
            val options = Interpreter.Options()
            options.setNumThreads(4)
            interpreter = Interpreter(modelBuffer, options)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun classify(bitmap: Bitmap): List<Detection> {
        val interp = interpreter ?: return emptyList()
        
        // 1. Preprocess
        val scaled = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, false)
        val inputArray = Array(1) { Array(inputSize) { Array(inputSize) { FloatArray(3) } } }
        
        val intValues = IntArray(inputSize * inputSize)
        scaled.getPixels(intValues, 0, scaled.width, 0, 0, scaled.width, scaled.height)
        
        var pixel = 0
        for (i in 0 until inputSize) {
            for (j in 0 until inputSize) {
                val value = intValues[pixel++]
                // Normalize to [0, 1]
                inputArray[0][i][j][0] = ((value shr 16 and 0xFF) / 255f)
                inputArray[0][i][j][1] = ((value shr 8 and 0xFF) / 255f)
                inputArray[0][i][j][2] = ((value and 0xFF) / 255f)
            }
        }
        
        // 2. Run Inference
        val outputTensor = interp.getOutputTensor(0)
        val shape = outputTensor.shape()
        // Expected shape: [1, numChannels, numAnchors] (e.g. [1, 57, 8400])
        val numChannels = shape[1] 
        val numAnchors = shape[2]  
        val numClasses = numChannels - 4
        
        val outputBuffer = Array(1) { Array(numChannels) { FloatArray(numAnchors) } }
        val outputs = mutableMapOf<Int, Any>(0 to outputBuffer)
        
        interp.runForMultipleInputsOutputs(arrayOf(inputArray), outputs)
        
        // 3. Postprocess
        val tensor = outputBuffer[0]
        val detections = mutableListOf<Detection>()
        
        for (i in 0 until numAnchors) {
            var maxClassConf = 0f
            var maxClassId = -1
            
            // Find class with highest confidence
            for (c in 0 until numClasses) {
                val conf = tensor[4 + c][i]
                if (conf > maxClassConf) {
                    maxClassConf = conf
                    maxClassId = c
                }
            }
            
            if (maxClassConf > confidenceThreshold) {
                val cx = tensor[0][i]
                val cy = tensor[1][i]
                val w = tensor[2][i]
                val h = tensor[3][i]
                
                val xmin = cx - w / 2
                val ymin = cy - h / 2
                val xmax = cx + w / 2
                val ymax = cy + h / 2
                
                detections.add(Detection(xmin, ymin, xmax, ymax, maxClassConf, maxClassId))
            }
        }
        
        // 4. Non-Maximum Suppression (NMS)
        return applyNMS(detections, iouThreshold)
    }

    private fun applyNMS(detections: List<Detection>, iouThresh: Float): List<Detection> {
        val sorted = detections.sortedByDescending { it.confidence }
        val selected = mutableListOf<Detection>()
        val active = BooleanArray(sorted.size) { true }
        
        for (i in sorted.indices) {
            if (!active[i]) continue
            val a = sorted[i]
            selected.add(a)
            
            for (j in i + 1 until sorted.size) {
                if (!active[j]) continue
                val b = sorted[j]
                
                val interXMin = maxOf(a.xmin, b.xmin)
                val interYMin = maxOf(a.ymin, b.ymin)
                val interXMax = minOf(a.xmax, b.xmax)
                val interYMax = minOf(a.ymax, b.ymax)
                
                val interArea = maxOf(0f, interXMax - interXMin) * maxOf(0f, interYMax - interYMin)
                val aArea = (a.xmax - a.xmin) * (a.ymax - a.ymin)
                val bArea = (b.xmax - b.xmin) * (b.ymax - b.ymin)
                val unionArea = aArea + bArea - interArea
                
                if (unionArea > 0f) {
                    val iou = interArea / unionArea
                    if (iou > iouThresh) {
                        active[j] = false
                    }
                }
            }
        }
        
        return selected
    }

    data class Detection(
        val xmin: Float,
        val ymin: Float,
        val xmax: Float,
        val ymax: Float,
        val confidence: Float,
        val classId: Int
    )
}
