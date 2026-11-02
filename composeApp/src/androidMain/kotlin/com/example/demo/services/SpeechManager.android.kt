package com.example.demo.services

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import java.util.Locale

class AndroidSpeechManager(
    private val context: Context,
    private val permissionLauncher: () -> Unit
) : SpeechManager {

    private val handler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var isTtsInitialized = false
    private var isChineseVoiceAvailable = false
    private var onCommandCallback: ((GameVoiceCommand) -> Unit)? = null

    private var consecutiveTimeouts = 0
    private var lastTriggerTimestamp = 0L
    private var recognitionLanguage: String = "zh-CN"
    private var speakingWatchdogRunnable: Runnable? = null

    private val _isListening = mutableStateOf(false)
    override val isListening: State<Boolean> = _isListening

    private val _isSpeaking = mutableStateOf(false)
    override val isSpeaking: State<Boolean> = _isSpeaking

    private val _lastHeardText = mutableStateOf<String?>(null)
    override val lastHeardText: State<String?> = _lastHeardText

    private val _liveTranscript = mutableStateOf<String?>(null)
    override val liveTranscript: State<String?> = _liveTranscript

    private val _soundLevelRms = mutableStateOf(0f)
    override val soundLevelRms: State<Float> = _soundLevelRms

    private val _hasAudioPermission = mutableStateOf(
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    )
    override val hasAudioPermission: State<Boolean> = _hasAudioPermission

    private val _ttsStatus = mutableStateOf("正在连接系统语音引擎 (Connecting TTS)...")
    override val ttsStatus: State<String> = _ttsStatus

    override val isSupported: Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    init {
        initTts()
    }

    private fun getVolumeInfo(): String {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return ""
            val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            if (currentVol == 0) {
                " [⚠️ 媒体音量静音中(0/$maxVol)，请按音量键调大]"
            } else {
                " [音量: $currentVol/$maxVol]"
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun initTts() {
        tts = TextToSpeech(context.applicationContext) { status ->
            handler.post {
                if (status == TextToSpeech.SUCCESS) {
                    isTtsInitialized = true
                    val volInfo = getVolumeInfo()

                    // Check Chinese locale support
                    val zhCandidates = listOf(
                        Locale.SIMPLIFIED_CHINESE,
                        Locale.CHINA,
                        Locale.Builder().setLanguage("zh").setRegion("CN").build(),
                        Locale.CHINESE
                    )

                    var matchedZh: Locale? = null
                    for (cand in zhCandidates) {
                        val avail = tts?.isLanguageAvailable(cand) ?: TextToSpeech.LANG_NOT_SUPPORTED
                        if (avail >= TextToSpeech.LANG_AVAILABLE) {
                            matchedZh = cand
                            break
                        }
                    }

                    if (matchedZh != null) {
                        isChineseVoiceAvailable = true
                        tts?.language = matchedZh
                        _ttsStatus.value = "就绪 (支持中文普通话)$volInfo"
                    } else {
                        isChineseVoiceAvailable = false
                        // Check English fallback
                        val enAvail = tts?.isLanguageAvailable(Locale.US) ?: TextToSpeech.LANG_NOT_SUPPORTED
                        if (enAvail >= TextToSpeech.LANG_AVAILABLE) {
                            tts?.language = Locale.US
                            _ttsStatus.value = "⚠️ 就绪 (仅英文语音，缺少中文语音包)$volInfo"
                        } else {
                            tts?.language = Locale.getDefault()
                            _ttsStatus.value = "⚠️ 就绪 (系统默认语言: ${Locale.getDefault().displayName})$volInfo"
                        }
                    }

                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            handler.post {
                                _isSpeaking.value = true
                                cancelSpeakingWatchdog()
                            }
                        }

                        override fun onDone(utteranceId: String?) {
                            handler.post {
                                _isSpeaking.value = false
                                cancelSpeakingWatchdog()
                                _ttsStatus.value = if (isChineseVoiceAvailable) "就绪 (支持中文)" + getVolumeInfo() else "就绪 (英文/缺中文包)" + getVolumeInfo()
                                if (_isListening.value) {
                                    safeRestartListening(500)
                                }
                            }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            handler.post {
                                _isSpeaking.value = false
                                cancelSpeakingWatchdog()
                                _ttsStatus.value = "播报完成或中断" + getVolumeInfo()
                                if (_isListening.value) {
                                    safeRestartListening(500)
                                }
                            }
                        }
                    })
                } else {
                    _ttsStatus.value = "TTS 初始化失败 (错误码: $status)，请检查系统TTS引擎"
                }
            }
        }
    }

    override fun openSystemTtsSettings() {
        try {
            val intent = Intent("com.android.settings.TTS_SETTINGS").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                android.util.Log.e("SpeechManager", "Cannot open settings", e)
            }
        }
    }

    override fun ensureAudibleVolume() {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            if (currentVol < (maxVol * 0.35f)) {
                val targetVol = (maxVol * 0.7f).toInt().coerceAtLeast(1)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, AudioManager.FLAG_SHOW_UI)
            }
        } catch (e: Exception) {
            android.util.Log.e("SpeechManager", "Failed to ensure volume", e)
        }
    }

    override fun requestPermission() {
        permissionLauncher()
    }

    fun updatePermission(granted: Boolean) {
        val changed = _hasAudioPermission.value != granted
        _hasAudioPermission.value = granted
        if (changed && granted && _isListening.value) {
            safeRestartListening(200)
        }
    }

    override fun startListening(onCommandDetected: (GameVoiceCommand) -> Unit) {
        this.onCommandCallback = onCommandDetected
        _isListening.value = true
        consecutiveTimeouts = 0

        if (!_hasAudioPermission.value) {
            requestPermission()
            return
        }

        if (!isSupported) {
            _liveTranscript.value = "设备不支持语音识别"
            return
        }

        handler.post {
            ensureRecognizerStarted()
        }
    }

    private fun createRecognitionIntent(): Intent {
        val targetLang = recognitionLanguage
        val isCantonese = targetLang.startsWith("zh-HK", ignoreCase = true) ||
                targetLang.startsWith("yue", ignoreCase = true)

        val primaryLang = if (isCantonese) "yue-Hant-HK" else targetLang

        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)

            putExtra(RecognizerIntent.EXTRA_LANGUAGE, primaryLang)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, primaryLang)
            putExtra("android.speech.extra.LANGUAGE", primaryLang)
            putExtra("android.speech.extra.LANGUAGE_PREFERENCE", primaryLang)

            // Do not force strict preference return to allow graceful match on various engine versions
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            putExtra("android.speech.extra.ONLY_RETURN_LANGUAGE_PREFERENCE", false)

            if (isCantonese) {
                // Official Android extra key is "android.speech.extra.ADDITIONAL_LANGUAGES"
                // Must be passed as an ArrayList<String>
                val cantoneseLocales = arrayListOf("yue-Hant-HK", "zh-HK", "yue-HK", "zh-yue", "yue-Hans-CN", "yue")
                putStringArrayListExtra("android.speech.extra.ADDITIONAL_LANGUAGES", cantoneseLocales)
                // Prefer online recognition if offline Cantonese pack is missing
                putExtra("android.speech.extra.PREFER_OFFLINE", false)
            }
        }
    }

    private fun ensureRecognizerStarted() {
        if (!_isListening.value || _isSpeaking.value) return

        try {
            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext).apply {
                    setRecognitionListener(createListener())
                }
            }

            val intent = createRecognitionIntent()
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            android.util.Log.e("SpeechManager", "Failed to start speech recognizer", e)
            safeRestartListening(1200)
        }
    }

    private fun createListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _soundLevelRms.value = 0f
            }

            override fun onBeginningOfSpeech() {
                consecutiveTimeouts = 0
                _liveTranscript.value = "检测到声音输入..."
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Map rmsdB (-2dB to ~12dB) into normalized 0.0 .. 1.0
                val norm = ((rmsdB + 2f) / 14f).coerceIn(0f, 1f)
                _soundLevelRms.value = norm
                if (norm > 0.2f) {
                    consecutiveTimeouts = 0
                }
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                _soundLevelRms.value = 0f
            }

            override fun onError(error: Int) {
                _soundLevelRms.value = 0f
                val errorMsg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "等待说话 (未检测到语音)"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "等待说话超时"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别器忙碌，重试中"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "缺少录音权限"
                    SpeechRecognizer.ERROR_NETWORK -> "网络连接异常"
                    else -> "识别暂停 (代码: $error)"
                }
                android.util.Log.d("SpeechManager", "STT Error: $error ($errorMsg)")

                if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    _hasAudioPermission.value = false
                    _isListening.value = false
                    return
                }

                if (_isListening.value && !_isSpeaking.value) {
                    // Safe linear backoff to avoid busy loop:
                    // 1st timeout -> 800ms, 2nd -> 1500ms, 3rd+ -> 2500ms
                    consecutiveTimeouts++
                    val delay = when {
                        error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == SpeechRecognizer.ERROR_CLIENT -> 1500L
                        consecutiveTimeouts == 1 -> 800L
                        consecutiveTimeouts == 2 -> 1500L
                        else -> 2500L
                    }
                    safeRestartListening(delay)
                }
            }

            override fun onResults(results: Bundle?) {
                _soundLevelRms.value = 0f
                consecutiveTimeouts = 0
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val primaryText = matches?.firstOrNull() ?: ""
                if (primaryText.isNotBlank()) {
                    _lastHeardText.value = primaryText
                    _liveTranscript.value = "听到: $primaryText"
                }

                checkAndTriggerCommand(matches)

                if (_isListening.value && !_isSpeaking.value) {
                    safeRestartListening(600)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val partialText = matches?.firstOrNull() ?: ""
                if (partialText.isNotBlank()) {
                    _liveTranscript.value = "正在听: $partialText"
                    checkAndTriggerCommand(matches)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    private fun checkAndTriggerCommand(matches: List<String>?) {
        if (matches.isNullOrEmpty()) return
        val now = System.currentTimeMillis()
        if (now - lastTriggerTimestamp < 1500) return // 1.5s debounce

        val activeLang = recognitionLanguage
        for (match in matches) {
            val cmd = matchCommand(match, activeLang)
            if (cmd != null) {
                lastTriggerTimestamp = now
                _lastHeardText.value = match
                _liveTranscript.value = "✅ 匹配指令: ${cmd.name} ('$match')"
                android.util.Log.i("SpeechManager", "Voice command triggered: $cmd from '$match' [Lang: $activeLang]")

                onCommandCallback?.invoke(cmd)
                return
            } else {
                android.util.Log.d("SpeechManager", "Voice heard: '$match' [Lang: $activeLang] (no command match)")
            }
        }
    }

    private fun matchCommand(text: String, languageCode: String? = null): GameVoiceCommand? {
        return VoiceCommandMatcher.matchCommand(text, languageCode ?: recognitionLanguage)
    }

    private var restartRunnable: Runnable? = null

    private fun safeRestartListening(delayMillis: Long) {
        restartRunnable?.let { handler.removeCallbacks(it) }
        val runnable = Runnable {
            restartRunnable = null
            if (_isListening.value && !_isSpeaking.value) {
                try {
                    speechRecognizer?.cancel()
                    speechRecognizer?.destroy()
                    speechRecognizer = null
                } catch (_: Exception) {}

                try {
                    if (speechRecognizer == null) {
                        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext).apply {
                            setRecognitionListener(createListener())
                        }
                    }
                    val intent = createRecognitionIntent()
                    speechRecognizer?.startListening(intent)
                } catch (e: Exception) {
                    android.util.Log.e("SpeechManager", "Error restarting recognizer", e)
                }
            }
        }
        restartRunnable = runnable
        handler.postDelayed(runnable, delayMillis)
    }

    override fun stopListening() {
        _isListening.value = false
        _soundLevelRms.value = 0f
        consecutiveTimeouts = 0
        restartRunnable?.let { handler.removeCallbacks(it) }
        restartRunnable = null
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (e: Exception) {
            android.util.Log.e("SpeechManager", "Error stopping recognizer", e)
        }
    }

    private fun cancelSpeakingWatchdog() {
        speakingWatchdogRunnable?.let { handler.removeCallbacks(it) }
        speakingWatchdogRunnable = null
    }

    override fun speak(text: String, language: String, rate: Float, pitch: Float, onDone: (() -> Unit)?) {
        if (!isTtsInitialized || tts == null) {
            _ttsStatus.value = "TTS 正在连接，请稍后点击..."
            initTts()
            return
        }

        try {
            ensureAudibleVolume()

            var textToSpeak = text
            val isCantonese = language.startsWith("zh-HK", ignoreCase = true) || language.startsWith("yue", ignoreCase = true)
            val isEnglish = language.startsWith("en", ignoreCase = true)

            val targetLocale = when {
                isCantonese -> {
                    val cantoneseCandidates = listOf(
                        Locale("zh", "HK"),
                        Locale("yue", "HK"),
                        Locale.forLanguageTag("zh-HK"),
                        Locale.forLanguageTag("yue-HK"),
                        Locale.TRADITIONAL_CHINESE
                    )
                    cantoneseCandidates.firstOrNull { cand ->
                        (tts?.isLanguageAvailable(cand) ?: TextToSpeech.LANG_NOT_SUPPORTED) >= TextToSpeech.LANG_AVAILABLE
                    } ?: Locale("zh", "HK")
                }
                isEnglish -> {
                    Locale.US
                }
                else -> {
                    val mandarinCandidates = listOf(
                        Locale.SIMPLIFIED_CHINESE,
                        Locale.CHINA,
                        Locale.CHINESE
                    )
                    mandarinCandidates.firstOrNull { cand ->
                        (tts?.isLanguageAvailable(cand) ?: TextToSpeech.LANG_NOT_SUPPORTED) >= TextToSpeech.LANG_AVAILABLE
                    } ?: Locale.SIMPLIFIED_CHINESE
                }
            }
            tts?.language = targetLocale

            if (!isEnglish && !isChineseVoiceAvailable && (tts?.isLanguageAvailable(targetLocale) ?: TextToSpeech.LANG_NOT_SUPPORTED) < TextToSpeech.LANG_AVAILABLE) {
                // Device lacks Chinese TTS voice pack!
                // To avoid total silence, use English TTS and speak an English translation / explanation
                tts?.language = Locale.US
                textToSpeak = when {
                    text.contains("测试语音") || text.contains("测试") ->
                        "Speech test successful. Volume is working properly in English because Chinese voice data is not installed."
                    text.contains("锁定") ->
                        "Cards locked. You can now say read cards to evaluate."
                    text.contains("新一局") ->
                        "New round started. Cards unlocked for scanning."
                    text.contains("推荐") || text.contains("出牌") ->
                        "Best hand ready. Please check on-screen recommendation."
                    else -> text
                }
            }

            tts?.setSpeechRate(rate)
            tts?.setPitch(pitch)

            // Temporarily cancel recognition so microphone does not pick up speaker sound
            restartRunnable?.let { handler.removeCallbacks(it) }
            restartRunnable = null
            try {
                speechRecognizer?.cancel()
            } catch (_: Exception) {}

            val params = Bundle().apply {
                putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
            }
            val utteranceId = "ddz_tts_${System.currentTimeMillis()}"

            val res = tts?.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            if (res == TextToSpeech.ERROR) {
                _ttsStatus.value = "TTS 发声失败，请检查音量设置"
                _isSpeaking.value = false
            } else {
                _isSpeaking.value = true
                _ttsStatus.value = "正在发声: ${textToSpeak.take(30)}..."

                // Safety Watchdog: in case TTS engine misses onDone callback, reset speaking after expected time
                val estimatedMs = ((textToSpeak.length * 350L) / rate.coerceAtLeast(0.5f)).toLong().coerceIn(1800L, 12000L)
                val runnable = Runnable {
                    if (_isSpeaking.value && tts?.isSpeaking != true) {
                        _isSpeaking.value = false
                        if (_isListening.value) {
                            safeRestartListening(500)
                        }
                    }
                }
                speakingWatchdogRunnable = runnable
                handler.postDelayed(runnable, estimatedMs + 1000L)
            }
        } catch (e: Exception) {
            _ttsStatus.value = "TTS 异常: ${e.message}"
            _isSpeaking.value = false
            android.util.Log.e("SpeechManager", "Error speaking text", e)
        }
    }

    override fun stopSpeaking() {
        cancelSpeakingWatchdog()
        tts?.stop()
        _isSpeaking.value = false
        _ttsStatus.value = "已停止发声"
    }

    override fun setRecognitionLanguage(languageCode: String) {
        val newCode = when {
            languageCode.startsWith("zh-HK", ignoreCase = true) || languageCode.startsWith("yue", ignoreCase = true) -> "yue-Hant-HK"
            languageCode.startsWith("zh", ignoreCase = true) -> "zh-CN"
            languageCode.startsWith("en", ignoreCase = true) -> "en-US"
            else -> languageCode
        }
        if (recognitionLanguage != newCode) {
            recognitionLanguage = newCode
            android.util.Log.i("SpeechManager", "Recognition language set to: $newCode")
            handler.post {
                try {
                    speechRecognizer?.cancel()
                    speechRecognizer?.destroy()
                    speechRecognizer = null
                } catch (_: Exception) {}
                if (_isListening.value && !_isSpeaking.value) {
                    safeRestartListening(200)
                }
            }
        }
    }

    override fun destroy() {
        cancelSpeakingWatchdog()
        stopListening()
        stopSpeaking()
        tts?.shutdown()
        tts = null
    }
}

@Composable
actual fun rememberSpeechManager(): SpeechManager {
    val context = LocalContext.current
    var launcherRef: (() -> Unit)? by remember { mutableStateOf(null) }

    val manager = remember(context) {
        AndroidSpeechManager(
            context = context,
            permissionLauncher = { launcherRef?.invoke() }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        manager.updatePermission(isGranted)
    }

    SideEffect {
        launcherRef = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
    }

    LaunchedEffect(Unit) {
        val currentPermissionGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (manager.hasAudioPermission.value != currentPermissionGranted) {
            manager.updatePermission(currentPermissionGranted)
        }
    }

    DisposableEffect(manager) {
        onDispose {
            manager.destroy()
        }
    }

    return manager
}
