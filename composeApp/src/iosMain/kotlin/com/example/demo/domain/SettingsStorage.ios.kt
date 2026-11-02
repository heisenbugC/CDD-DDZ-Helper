package com.example.demo.domain

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.example.demo.rules.Variation
import platform.Foundation.NSUserDefaults

class IosSettingsStorage : SettingsStorage {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun loadSettings(): AppSettings {
        val gameModeStr = defaults.stringForKey("game_mode") ?: GameMode.DOU_DI_ZHU.name
        val gameMode = try { GameMode.valueOf(gameModeStr) } catch (_: Exception) { GameMode.DOU_DI_ZHU }

        val ddzRoleStr = defaults.stringForKey("ddz_role") ?: DdzRole.PEASANT.name
        val ddzRole = try { DdzRole.valueOf(ddzRoleStr) } catch (_: Exception) { DdzRole.PEASANT }

        val cddVariationStr = defaults.stringForKey("cdd_variation") ?: Variation.MAINLAND.name
        val cddVariation = try { Variation.valueOf(cddVariationStr) } catch (_: Exception) { Variation.MAINLAND }

        val voiceLanguageStr = defaults.stringForKey("voice_language") ?: VoiceLanguage.CHINESE.name
        val voiceLanguage = try { VoiceLanguage.valueOf(voiceLanguageStr) } catch (_: Exception) { VoiceLanguage.CHINESE }

        val voiceDetailLevelStr = defaults.stringForKey("voice_detail_level") ?: VoiceDetailLevel.BRIEF.name
        val voiceDetailLevel = try { VoiceDetailLevel.valueOf(voiceDetailLevelStr) } catch (_: Exception) { VoiceDetailLevel.BRIEF }

        val speechRate = defaults.floatForKey("speech_rate").let { if (it == 0f) 1.0f else it }
        val speechPitch = defaults.floatForKey("speech_pitch").let { if (it == 0f) 1.0f else it }

        return AppSettings(
            gameMode = gameMode,
            ddzRole = ddzRole,
            cddVariation = cddVariation,
            audioFeedbackEnabled = defaults.objectForKey("audio_feedback_enabled")?.let { defaults.boolForKey("audio_feedback_enabled") } ?: true,
            hapticFeedbackEnabled = defaults.objectForKey("haptic_feedback_enabled")?.let { defaults.boolForKey("haptic_feedback_enabled") } ?: true,
            voiceTriggerEnabled = defaults.objectForKey("voice_trigger_enabled")?.let { defaults.boolForKey("voice_trigger_enabled") } ?: true,
            voiceLanguage = voiceLanguage,
            voiceDetailLevel = voiceDetailLevel,
            speechRate = speechRate,
            speechPitch = speechPitch,
            autoReadOnFullHand = defaults.boolForKey("auto_read_on_full_hand")
        )
    }

    override fun saveSettings(settings: AppSettings) {
        defaults.setObject(settings.gameMode.name, forKey = "game_mode")
        defaults.setObject(settings.ddzRole.name, forKey = "ddz_role")
        defaults.setObject(settings.cddVariation.name, forKey = "cdd_variation")
        defaults.setBool(settings.audioFeedbackEnabled, forKey = "audio_feedback_enabled")
        defaults.setBool(settings.hapticFeedbackEnabled, forKey = "haptic_feedback_enabled")
        defaults.setBool(settings.voiceTriggerEnabled, forKey = "voice_trigger_enabled")
        defaults.setObject(settings.voiceLanguage.name, forKey = "voice_language")
        defaults.setObject(settings.voiceDetailLevel.name, forKey = "voice_detail_level")
        defaults.setFloat(settings.speechRate, forKey = "speech_rate")
        defaults.setFloat(settings.speechPitch, forKey = "speech_pitch")
        defaults.setBool(settings.autoReadOnFullHand, forKey = "auto_read_on_full_hand")
        defaults.synchronize()
    }
}

@Composable
actual fun rememberSettingsStorage(): SettingsStorage {
    return remember { IosSettingsStorage() }
}

