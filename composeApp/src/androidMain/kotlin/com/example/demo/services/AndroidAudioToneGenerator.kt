package com.example.demo.services

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import com.example.demo.guidance.NonVisualFeedbackSignal
import kotlinx.coroutines.*
import kotlin.math.PI
import kotlin.math.sin

/**
 * Real-time Audio Tone Generator with Audio Focus Ducking.
 * Synthesizes guidance audio pulses (220Hz-880Hz), two-tone centered chimes (880Hz->1320Hz),
 * and lock triad chords (G4-C5-E5) via AudioTrack without relying on audio files.
 */
class AndroidAudioToneGenerator(
    private val context: Context
) : AudioToneGenerator {

    companion object {
        private const val TAG = "AudioToneGenerator"
        private const val SAMPLE_RATE = 44100
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var audioTrack: AudioTrack? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    private val audioLock = Any()
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Audio tone coroutine exception: ${throwable.message}", throwable)
    }
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob() + exceptionHandler)
    private var pulseJob: Job? = null

    @Volatile
    private var currentSignal: NonVisualFeedbackSignal = NonVisualFeedbackSignal()

    @Volatile
    private var isRunning: Boolean = false

    init {
        initializeAudioTrack()
    }

    private fun initializeAudioTrack() {
        synchronized(audioLock) {
            try {
                val minBufferSize = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val bufferSize = maxOf(minBufferSize, SAMPLE_RATE / 4) // ~250ms buffer

                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()

                val audioFormat = AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()

                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(audioAttributes)
                    .setAudioFormat(audioFormat)
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                audioTrack?.play()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize AudioTrack: ${e.message}", e)
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        if (audioManager == null) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setOnAudioFocusChangeListener { /* No-op for transient ducking */ }
                .build()
            audioFocusRequest = focusRequest
            audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                { /* No-op */ },
                AudioManager.STREAM_NOTIFICATION,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        if (audioManager == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus { /* No-op */ }
        }
    }

    override fun updateSignal(signal: NonVisualFeedbackSignal) {
        currentSignal = signal
    }

    override fun start() {
        if (isRunning) return
        isRunning = true
        requestAudioFocus()

        if (audioTrack == null || audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
            initializeAudioTrack()
        }

        pulseJob = scope.launch {
            while (isActive && isRunning) {
                val signal = currentSignal

                when {
                    signal.playLockChord -> {
                        playLockTriadChord()
                        delay(300)
                    }
                    signal.playCenteredChime -> {
                        playCenteredHarmonicChime()
                        delay(250)
                    }
                    signal.audioToneFrequencyHz > 0f -> {
                        val durationMs = if (signal.audioToneFrequencyHz <= 200f) 35 else 45
                        val pcm = synthesizeSineTone(signal.audioToneFrequencyHz, durationMs)
                        writePcm(pcm)
                        val sleepTime = (signal.pulseIntervalMs - durationMs).coerceAtLeast(10L)
                        delay(sleepTime)
                    }
                    else -> {
                        delay(80)
                    }
                }
            }
        }
    }

    override fun stop() {
        isRunning = false
        pulseJob?.cancel()
        pulseJob = null
        abandonAudioFocus()
    }

    override fun release() {
        stop()
        scope.cancel()
        synchronized(audioLock) {
            try {
                audioTrack?.stop()
                audioTrack?.release()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to release AudioTrack: ${e.message}")
            } finally {
                audioTrack = null
            }
        }
    }

    private fun writePcm(pcm: ShortArray) {
        synchronized(audioLock) {
            val track = audioTrack ?: return
            try {
                if (track.state == AudioTrack.STATE_INITIALIZED && track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.write(pcm, 0, pcm.size)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to write PCM data: ${e.message}")
            }
        }
    }

    /**
     * Synthesizes a sine tone with a smooth cosine window to prevent speaker pops.
     */
    fun synthesizeSineTone(freqHz: Float, durationMs: Int, volume: Float = 0.7f): ShortArray {
        val numSamples = (SAMPLE_RATE * durationMs) / 1000
        val pcm = ShortArray(numSamples)
        val rampSamples = minOf(numSamples / 4, (SAMPLE_RATE * 5) / 1000) // 5ms fade
        val omega = 2.0 * PI * freqHz / SAMPLE_RATE

        for (i in 0 until numSamples) {
            var envelope = 1.0
            if (i < rampSamples) {
                envelope = 0.5 * (1.0 - kotlin.math.cos(PI * i / rampSamples))
            } else if (i > numSamples - rampSamples) {
                envelope = 0.5 * (1.0 - kotlin.math.cos(PI * (numSamples - i) / rampSamples))
            }
            val sample = (sin(omega * i) * envelope * volume * Short.MAX_VALUE).toInt()
            pcm[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return pcm
    }

    /**
     * Synthesizes two-tone harmonic chime (880Hz -> 1320Hz) when cards are centered.
     */
    private fun playCenteredHarmonicChime() {
        val tone1 = synthesizeSineTone(880f, 80, 0.8f)
        val tone2 = synthesizeSineTone(1320f, 120, 0.85f)
        writePcm(tone1)
        writePcm(tone2)
    }

    /**
     * Synthesizes G4-C5-E5 major triad chord (392.00Hz, 523.25Hz, 659.25Hz) upon stability lock.
     */
    private fun playLockTriadChord() {
        val durationMs = 280
        val numSamples = (SAMPLE_RATE * durationMs) / 1000
        val pcm = ShortArray(numSamples)

        val omega1 = 2.0 * PI * 392.00 / SAMPLE_RATE
        val omega2 = 2.0 * PI * 523.25 / SAMPLE_RATE
        val omega3 = 2.0 * PI * 659.25 / SAMPLE_RATE

        val rampSamples = (SAMPLE_RATE * 8) / 1000

        for (i in 0 until numSamples) {
            var envelope = (1.0 - i.toDouble() / numSamples) // Exponential-like linear decay
            if (i < rampSamples) {
                envelope *= (i.toDouble() / rampSamples)
            }
            val val1 = sin(omega1 * i) * 0.35
            val val2 = sin(omega2 * i) * 0.35
            val val3 = sin(omega3 * i) * 0.30
            val mixed = ((val1 + val2 + val3) * envelope * 0.85 * Short.MAX_VALUE).toInt()
            pcm[i] = mixed.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        writePcm(pcm)
    }
}
