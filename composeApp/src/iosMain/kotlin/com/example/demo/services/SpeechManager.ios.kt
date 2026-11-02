package com.example.demo.services

import androidx.compose.runtime.*

class IosSpeechManager : SpeechManager {
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

    private val _hasAudioPermission = mutableStateOf(true)
    override val hasAudioPermission: State<Boolean> = _hasAudioPermission

    override val isSupported: Boolean = false

    private val _ttsStatus = mutableStateOf("iOS TTS Stub")
    override val ttsStatus: State<String> = _ttsStatus

    override fun requestPermission() {}
    override fun startListening(onCommandDetected: (GameVoiceCommand) -> Unit) {
        _isListening.value = true
    }
    override fun stopListening() {
        _isListening.value = false
    }
    override fun speak(text: String, language: String, rate: Float, pitch: Float, onDone: (() -> Unit)?) {
        _isSpeaking.value = true
    }
    override fun stopSpeaking() {
        _isSpeaking.value = false
    }
    override fun openSystemTtsSettings() {}
    override fun ensureAudibleVolume() {}
    override fun setRecognitionLanguage(languageCode: String) {}
    override fun destroy() {}
}

@Composable
actual fun rememberSpeechManager(): SpeechManager {
    return remember { IosSpeechManager() }
}
