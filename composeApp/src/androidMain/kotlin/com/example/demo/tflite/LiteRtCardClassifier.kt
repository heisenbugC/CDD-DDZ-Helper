package com.example.demo.tflite

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import com.example.demo.domain.CardMapping
import com.example.demo.guidance.NormalizedBox
import com.example.demo.preprocessing.KotlinImagePreprocessor
import com.example.demo.preprocessing.preprocessBitmap
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * Google LiteRT (v1.0.1) Card Classifier with multi-tier hardware acceleration:
 * 1. Qualcomm QNN NPU Delegate (Snapdragon Hexagon HTP - target latency ~8-12ms)
 * 2. LiteRT GPU Delegate (Adreno/Mali GPU - target latency ~18-25ms)
 * 3. 4-Thread CPU Fallback (~45-65ms)
 *
 * Runs YOLO 54-class card detection on 640x640 float32 input tensors.
 * Parses [1, 58, 8400] output tensor, executes NMS (IoU 0.45, Conf 0.45),
 * and applies Layer-2 JokerChromaResolver for ambiguous Joker detections.
 */
class LiteRtCardClassifier(
    private val context: Context,
    private val modelFileName: String = "card_model.tflite",
    private val confidenceThreshold: Float = 0.45f,
    private val iouThreshold: Float = 0.45f
) : AutoCloseable {

    companion object {
        private const val TAG = "LiteRtCardClassifier"
        const val INPUT_SIZE = 640
        const val NUM_CHANNELS = 58   // 4 coords + 54 classes
        const val NUM_ANCHORS = 8400
        const val NUM_CLASSES = 54
    }

    enum class ActiveDelegate {
        QUALCOMM_QNN_NPU,
        LITERT_GPU,
        CPU_4_THREADS
    }

    private val executionLock = Any()
    @Volatile
    private var isClosed = false
    private var interpreter: Interpreter? = null
    var activeDelegate: ActiveDelegate = ActiveDelegate.CPU_4_THREADS
        private set

    // Reusable output tensor buffer: [1, 58, 8400]
    private val outputBuffer = Array(1) { Array(NUM_CHANNELS) { FloatArray(NUM_ANCHORS) } }
    private val outputMap = mutableMapOf<Int, Any>(0 to outputBuffer)

    // Preprocessor instance for bitmap/pixel conversion
    val preprocessor = KotlinImagePreprocessor(INPUT_SIZE)

    init {
        initializeInterpreter()
    }

    private fun initializeInterpreter() {
        try {
            val modelBuffer = loadModelFile(context, modelFileName)
            val options = Interpreter.Options()

            // Hardware acceleration selection pipeline
            val isQualcomm = isQualcommPlatform()
            var delegateInitialized = false

            if (isQualcomm) {
                Log.d(TAG, "Qualcomm SoC detected (${Build.HARDWARE} / ${getSocModel()}). Attempting QNN/Hexagon delegate...")
                delegateInitialized = tryInitializeQnnDelegate(options)
                if (delegateInitialized) {
                    activeDelegate = ActiveDelegate.QUALCOMM_QNN_NPU
                    Log.i(TAG, "Successfully initialized Qualcomm QNN NPU delegate!")
                }
            }

            if (!delegateInitialized) {
                Log.d(TAG, "Attempting LiteRT GPU delegate fallback...")
                delegateInitialized = tryInitializeGpuDelegate(options)
                if (delegateInitialized) {
                    activeDelegate = ActiveDelegate.LITERT_GPU
                    Log.i(TAG, "Successfully initialized LiteRT GPU delegate!")
                }
            }

            if (!delegateInitialized) {
                Log.i(TAG, "Using 4-thread CPU fallback.")
                options.setNumThreads(4)
                activeDelegate = ActiveDelegate.CPU_4_THREADS
            }

            interpreter = Interpreter(modelBuffer, options)
            Log.i(TAG, "LiteRT Interpreter initialized with delegate: $activeDelegate")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize LiteRT Interpreter: ${e.message}", e)
        }
    }

    private fun isQualcommPlatform(): Boolean {
        val hardware = (Build.HARDWARE ?: "").lowercase()
        val board = (Build.BOARD ?: "").lowercase()
        val soc = getSocModel().lowercase()

        return hardware.contains("qcom") ||
               hardware.contains("qualcomm") ||
               soc.contains("sm") ||
               soc.contains("snapdragon") ||
               soc.contains("qcm") ||
               board.contains("qcom") ||
               hasQnnSystemLibraries()
    }

    private fun getSocModel(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL ?: ""
        } else {
            ""
        }
    }

    private fun hasQnnSystemLibraries(): Boolean {
        val qnnCandidatePaths = arrayOf(
            "/vendor/lib64/libQnnHtp.so",
            "/system/vendor/lib64/libQnnHtp.so",
            "/vendor/lib64/libQnnCpu.so"
        )
        return qnnCandidatePaths.any { File(it).exists() }
    }

    private fun tryInitializeQnnDelegate(options: Interpreter.Options): Boolean {
        return try {
            val qnnClassNames = arrayOf(
                "com.google.ai.edge.litert.qnn.QnnDelegate",
                "org.tensorflow.lite.qnn.QnnDelegate"
            )
            for (className in qnnClassNames) {
                try {
                    val clazz = Class.forName(className)
                    val delegateInstance = clazz.getDeclaredConstructor().newInstance()
                    val addDelegateMethod = options.javaClass.getMethod("addDelegate", Class.forName("org.tensorflow.lite.Delegate"))
                    addDelegateMethod.invoke(options, delegateInstance)
                    return true
                } catch (_: ClassNotFoundException) {
                    continue
                }
            }
            false
        } catch (e: Throwable) {
            Log.w(TAG, "QNN delegate initialization failed: ${e.message}")
            false
        }
    }

    private fun tryInitializeGpuDelegate(options: Interpreter.Options): Boolean {
        return try {
            val gpuClassNames = arrayOf(
                "com.google.ai.edge.litert.gpu.GpuDelegate",
                "org.tensorflow.lite.gpu.GpuDelegate"
            )
            for (className in gpuClassNames) {
                try {
                    val clazz = Class.forName(className)
                    val delegateInstance = clazz.getDeclaredConstructor().newInstance()
                    val addDelegateMethod = options.javaClass.getMethod("addDelegate", Class.forName("org.tensorflow.lite.Delegate"))
                    addDelegateMethod.invoke(options, delegateInstance)
                    return true
                } catch (_: ClassNotFoundException) {
                    continue
                }
            }
            false
        } catch (e: Throwable) {
            Log.w(TAG, "GPU delegate initialization failed: ${e.message}")
            false
        }
    }

    private fun loadModelFile(context: Context, modelPath: String): ByteBuffer {
        val assetFd = context.assets.openFd(modelPath)
        FileInputStream(assetFd.fileDescriptor).use { inputStream ->
            val fileChannel = inputStream.channel
            return fileChannel.map(
                FileChannel.MapMode.READ_ONLY,
                assetFd.startOffset,
                assetFd.declaredLength
            )
        }
    }

    /**
     * Executes card detection on preprocessed 640x640 float32 ByteBuffer.
     * Optionally takes the preprocessed ARGB IntArray for Layer-2 Joker Chroma resolution.
     */
    fun detect(
        inputBuffer: ByteBuffer,
        sourceArgbPixels: IntArray? = null
    ): List<NormalizedBox> {
        synchronized(executionLock) {
            if (isClosed) return emptyList()
            val interp = interpreter ?: return emptyList()

            // Run inference
            interp.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputMap)

            val tensor = outputBuffer[0]
            val rawCandidates = mutableListOf<RawDetection>()
            var maxAnchorBlackJoker = 0.0f
            var maxAnchorRedJoker = 0.0f

            // YOLOv8 / YOLOv9 output: [1, 58, 8400]
            for (i in 0 until NUM_ANCHORS) {
                var maxClassConf = 0.0f
                var maxClassId = -1

                for (c in 0 until NUM_CLASSES) {
                    val conf = tensor[4 + c][i]
                    if (conf > maxClassConf) {
                        maxClassConf = conf
                        maxClassId = c
                    }
                }

                val pBlackJoker = tensor[4 + CardMapping.CLASS_BLACK_JOKER][i]
                val pRedJoker = tensor[4 + CardMapping.CLASS_RED_JOKER][i]
                if (pBlackJoker > maxAnchorBlackJoker) maxAnchorBlackJoker = pBlackJoker
                if (pRedJoker > maxAnchorRedJoker) maxAnchorRedJoker = pRedJoker

                val isJokerCandidate = (maxClassId == CardMapping.CLASS_BLACK_JOKER ||
                        maxClassId == CardMapping.CLASS_RED_JOKER ||
                        pBlackJoker >= 0.25f ||
                        pRedJoker >= 0.25f)

                // Occluded cards in dense multi-card hands (e.g. 5 to 18 cards) show only 15%-30%
                // of their corner index, causing anchor activations to hover around 0.28 - 0.42.
                val effectiveConfThreshold = if (isJokerCandidate) 0.28f else confidenceThreshold

                if (maxClassConf >= effectiveConfThreshold) {
                    val cx = tensor[0][i]
                    val cy = tensor[1][i]
                    val w = tensor[2][i]
                    val h = tensor[3][i]

                    // Normalization to [0.0, 1.0]
                    val xMin = ((cx - w / 2.0f) / INPUT_SIZE.toFloat()).coerceIn(0.0f, 1.0f)
                    val yMin = ((cy - h / 2.0f) / INPUT_SIZE.toFloat()).coerceIn(0.0f, 1.0f)
                    val xMax = ((cx + w / 2.0f) / INPUT_SIZE.toFloat()).coerceIn(0.0f, 1.0f)
                    val yMax = ((cy + h / 2.0f) / INPUT_SIZE.toFloat()).coerceIn(0.0f, 1.0f)

                    rawCandidates.add(
                        RawDetection(
                            xMin = xMin,
                            yMin = yMin,
                            xMax = xMax,
                            yMax = yMax,
                            confidence = maxClassConf,
                            classId = maxClassId,
                            pBlackJoker = pBlackJoker,
                            pRedJoker = pRedJoker
                        )
                    )
                }
            }

            // Apply Non-Maximum Suppression (NMS)
            val nmsFiltered = applyNms(rawCandidates, iouThreshold)

            var foundJokerInFrame = false

            // Apply Layer-2 Joker Chroma Resolver if ambiguous
            val finalDetections = nmsFiltered.map { det ->
                var finalClassId = det.classId
                var overridden = false
                var chromaResult: JokerChromaResolver.ChromaAnalysisResult? = null

                val isJokerCandidate = (det.classId == CardMapping.CLASS_BLACK_JOKER ||
                        det.classId == CardMapping.CLASS_RED_JOKER ||
                        det.pBlackJoker >= 0.15f ||
                        det.pRedJoker >= 0.15f)

                if ((det.classId == CardMapping.CLASS_BLACK_JOKER || det.classId == CardMapping.CLASS_RED_JOKER) &&
                    sourceArgbPixels != null
                ) {
                    val res = JokerChromaResolver.resolveJoker(
                        box = NormalizedBox(det.xMin, det.yMin, det.xMax, det.yMax, det.classId, det.confidence),
                        pBlackJoker = det.pBlackJoker,
                        pRedJoker = det.pRedJoker,
                        argbPixels = sourceArgbPixels,
                        imageWidth = INPUT_SIZE,
                        imageHeight = INPUT_SIZE
                    )
                    chromaResult = res
                    finalClassId = res.resolvedClassId
                    overridden = res.overriddenByChroma
                }

                if (isJokerCandidate) {
                    foundJokerInFrame = true
                    val modelPickedName = CardMapping.getLabel(det.classId) ?: "class_${det.classId}"
                    val finalName = CardMapping.getLabel(finalClassId) ?: "class_$finalClassId"
                    val margin = kotlin.math.abs(det.pRedJoker - det.pBlackJoker)
                    val pBlackPct = (det.pBlackJoker * 1000).toInt() / 10.0
                    val pRedPct = (det.pRedJoker * 1000).toInt() / 10.0
                    val marginPct = (margin * 1000).toInt() / 10.0
                    val confPct = (det.confidence * 1000).toInt() / 10.0

                    val chromaSummary = if (chromaResult != null) {
                        val redPct = (chromaResult.redDensity * 1000).toInt() / 10.0
                        val satPct = (chromaResult.meanSaturation * 1000).toInt() / 10.0
                        "Chroma[Ambiguous=${chromaResult.isAmbiguous}, RedDensity=${redPct}%, Sat=${satPct}%, Overridden=$overridden]"
                    } else {
                        "Chroma[Skipped]"
                    }

                    android.util.Log.i(
                        "JokerAccuracy",
                        "[JOKER] Box=[${(det.xMin * 100).toInt()}%, ${(det.yMin * 100).toInt()}% - ${(det.xMax * 100).toInt()}%, ${(det.yMax * 100).toInt()}%] | Accuracy/Score: P(Black_52)=${pBlackPct}%, P(Red_53)=${pRedPct}% (Margin=${marginPct}%) | Model Picked: $modelPickedName (${confPct}%) | $chromaSummary | FINAL CLASS: $finalName (ID: $finalClassId)"
                    )
                }

                NormalizedBox(
                    xMin = det.xMin,
                    yMin = det.yMin,
                    xMax = det.xMax,
                    yMax = det.yMax,
                    classId = finalClassId,
                    confidence = det.confidence
                )
            }

            // Sub-threshold diagnostic: if no joker survived NMS but an anchor had prominent joker activation
            if (!foundJokerInFrame && (maxAnchorBlackJoker >= 0.25f || maxAnchorRedJoker >= 0.25f)) {
                android.util.Log.d(
                    "JokerAccuracy",
                    "[JOKER SUB-THRESHOLD] Peak Anchor: P(Black_52)=${(maxAnchorBlackJoker * 1000).toInt() / 10.0}%, P(Red_53)=${(maxAnchorRedJoker * 1000).toInt() / 10.0}% (Confidence threshold: ${(confidenceThreshold * 100).toInt()}%)"
                )
            }

            return finalDetections
        }
    }

    /**
     * Helper to classify a Bitmap directly.
     */
    fun classifyBitmap(bitmap: Bitmap): List<NormalizedBox> {
        val byteBuffer = preprocessor.preprocessBitmap(bitmap)
        return detect(byteBuffer, preprocessor.sharpenedBuffer)
    }

    /**
     * Non-Maximum Suppression (NMS) algorithm.
     */
    fun applyNms(detections: List<RawDetection>, iouThresh: Float): List<RawDetection> {
        val sorted = detections.sortedByDescending { it.confidence }
        val selected = mutableListOf<RawDetection>()
        val active = BooleanArray(sorted.size) { true }

        for (i in sorted.indices) {
            if (!active[i]) continue
            val a = sorted[i]
            selected.add(a)

            for (j in i + 1 until sorted.size) {
                if (!active[j]) continue
                val b = sorted[j]

                val interXMin = maxOf(a.xMin, b.xMin)
                val interYMin = maxOf(a.yMin, b.yMin)
                val interXMax = minOf(a.xMax, b.xMax)
                val interYMax = minOf(a.yMax, b.yMax)

                val interW = maxOf(0.0f, interXMax - interXMin)
                val interH = maxOf(0.0f, interYMax - interYMin)
                val interArea = interW * interH

                val aArea = (a.xMax - a.xMin) * (a.yMax - a.yMin)
                val bArea = (b.xMax - b.xMin) * (b.yMax - b.yMin)
                val unionArea = aArea + bArea - interArea

                if (unionArea > 0.0f) {
                    val iou = interArea / unionArea
                    val isSameClass = (a.classId == b.classId)
                    val isBothJokers = (a.classId == CardMapping.CLASS_BLACK_JOKER || a.classId == CardMapping.CLASS_RED_JOKER) &&
                            (b.classId == CardMapping.CLASS_BLACK_JOKER || b.classId == CardMapping.CLASS_RED_JOKER)

                    val aCenterX = (a.xMin + a.xMax) / 2.0f
                    val aCenterY = (a.yMin + a.yMax) / 2.0f
                    val bCenterX = (b.xMin + b.xMax) / 2.0f
                    val bCenterY = (b.yMin + b.yMax) / 2.0f
                    val centerDistSq = (aCenterX - bCenterX) * (aCenterX - bCenterX) + (aCenterY - bCenterY) * (aCenterY - bCenterY)

                    // 1. Same card class: suppress if IoU > threshold (e.g. duplicate anchors on same card)
                    // 2. Big Joker vs Small Joker: in a multi-card hand, they often overlap by 50-70%.
                    //    Only suppress if their centers virtually coincide (< 2.8% distance AND IoU > 0.72)
                    // 3. Different card classes: never suppress adjacent overlapping cards in a hand unless
                    //    they are duplicate predictions on the exact same card (centerDistSq < 0.0010 AND IoU > 0.75)
                    val shouldSuppress = when {
                        isSameClass -> iou > iouThresh
                        isBothJokers -> centerDistSq < 0.0008f && iou > 0.72f
                        else -> centerDistSq < 0.0010f && iou > 0.75f
                    }

                    if (shouldSuppress) {
                        active[j] = false
                    }
                }
            }
        }

        return selected
    }

    override fun close() {
        synchronized(executionLock) {
            isClosed = true
            try {
                interpreter?.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing LiteRT interpreter: ${e.message}")
            } finally {
                interpreter = null
            }
        }
    }

    data class RawDetection(
        val xMin: Float,
        val yMin: Float,
        val xMax: Float,
        val yMax: Float,
        val confidence: Float,
        val classId: Int,
        val pBlackJoker: Float,
        val pRedJoker: Float
    )
}
