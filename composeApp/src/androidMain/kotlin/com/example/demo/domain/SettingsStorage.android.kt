package com.example.demo.domain

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.demo.rules.Variation

class AndroidSettingsStorage(context: Context) : SettingsStorage {
    private val prefs: SharedPreferences = context.getSharedPreferences("cdd_ddz_app_settings", Context.MODE_PRIVATE)

    override fun loadSettings(): AppSettings {
        val gameMode = try {
            val name = prefs.getString("game_mode", GameMode.DOU_DI_ZHU.name) ?: GameMode.DOU_DI_ZHU.name
            GameMode.valueOf(name)
        } catch (_: Exception) {
            GameMode.DOU_DI_ZHU
        }

        val ddzRole = try {
            val name = prefs.getString("ddz_role", DdzRole.PEASANT.name) ?: DdzRole.PEASANT.name
            DdzRole.valueOf(name)
        } catch (_: Exception) {
            DdzRole.PEASANT
        }

        val cddVariation = try {
            val name = prefs.getString("cdd_variation", Variation.MAINLAND.name) ?: Variation.MAINLAND.name
            Variation.valueOf(name)
        } catch (_: Exception) {
            Variation.MAINLAND
        }

        val voiceLanguage = try {
            val name = prefs.getString("voice_language", VoiceLanguage.CHINESE.name) ?: VoiceLanguage.CHINESE.name
            VoiceLanguage.valueOf(name)
        } catch (_: Exception) {
            VoiceLanguage.CHINESE
        }

        val voiceDetailLevel = try {
            val name = prefs.getString("voice_detail_level", VoiceDetailLevel.BRIEF.name) ?: VoiceDetailLevel.BRIEF.name
            VoiceDetailLevel.valueOf(name)
        } catch (_: Exception) {
            VoiceDetailLevel.BRIEF
        }

        return AppSettings(
            gameMode = gameMode,
            ddzRole = ddzRole,
            cddVariation = cddVariation,
            audioFeedbackEnabled = prefs.getBoolean("audio_feedback_enabled", true),
            hapticFeedbackEnabled = prefs.getBoolean("haptic_feedback_enabled", true),
            voiceTriggerEnabled = prefs.getBoolean("voice_trigger_enabled", true),
            voiceLanguage = voiceLanguage,
            voiceDetailLevel = voiceDetailLevel,
            speechRate = prefs.getFloat("speech_rate", 1.0f),
            speechPitch = prefs.getFloat("speech_pitch", 1.0f),
            autoReadOnFullHand = prefs.getBoolean("auto_read_on_full_hand", false)
        )
    }

    override fun saveSettings(settings: AppSettings) {
        prefs.edit()
            .putString("game_mode", settings.gameMode.name)
            .putString("ddz_role", settings.ddzRole.name)
            .putString("cdd_variation", settings.cddVariation.name)
            .putBoolean("audio_feedback_enabled", settings.audioFeedbackEnabled)
            .putBoolean("haptic_feedback_enabled", settings.hapticFeedbackEnabled)
            .putBoolean("voice_trigger_enabled", settings.voiceTriggerEnabled)
            .putString("voice_language", settings.voiceLanguage.name)
            .putString("voice_detail_level", settings.voiceDetailLevel.name)
            .putFloat("speech_rate", settings.speechRate)
            .putFloat("speech_pitch", settings.speechPitch)
            .putBoolean("auto_read_on_full_hand", settings.autoReadOnFullHand)
            .apply()
    }
}

@Composable
actual fun rememberSettingsStorage(): SettingsStorage {
    val context = LocalContext.current
    return remember(context) { AndroidSettingsStorage(context.applicationContext) }
}

