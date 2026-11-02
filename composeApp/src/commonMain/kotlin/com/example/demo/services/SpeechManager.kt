package com.example.demo.services

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State

interface SpeechManager {
    val isListening: State<Boolean>
    val isSpeaking: State<Boolean>
    val lastHeardText: State<String?>
    val liveTranscript: State<String?>
    val soundLevelRms: State<Float> // 0.0f to 1.0f for sound level meter
    val hasAudioPermission: State<Boolean>
    val isSupported: Boolean
    val ttsStatus: State<String> // Diagnostic status for TTS

    fun requestPermission()
    fun startListening(onCommandDetected: (GameVoiceCommand) -> Unit)
    fun stopListening()
    fun speak(text: String, language: String = "zh", rate: Float = 1.0f, pitch: Float = 1.0f, onDone: (() -> Unit)? = null)
    fun stopSpeaking()
    fun openSystemTtsSettings()
    fun ensureAudibleVolume()
    fun setRecognitionLanguage(languageCode: String)
    fun destroy()
}

@Composable
expect fun rememberSpeechManager(): SpeechManager
