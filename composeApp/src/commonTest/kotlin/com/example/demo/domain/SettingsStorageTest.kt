package com.example.demo.domain

import com.example.demo.rules.Variation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsStorageTest {

    private class InMemorySettingsStorage : SettingsStorage {
        private var storedSettings: AppSettings = AppSettings()

        override fun loadSettings(): AppSettings = storedSettings

        override fun saveSettings(settings: AppSettings) {
            storedSettings = settings
        }
    }

    @Test
    fun testDefaultSettings_initializesCorrectly() {
        val storage = InMemorySettingsStorage()
        val settings = storage.loadSettings()

        assertEquals(GameMode.DOU_DI_ZHU, settings.gameMode)
        assertEquals(DdzRole.PEASANT, settings.ddzRole)
        assertEquals(Variation.MAINLAND, settings.cddVariation)
        assertEquals(VoiceLanguage.CHINESE, settings.voiceLanguage)
        assertEquals(VoiceDetailLevel.BRIEF, settings.voiceDetailLevel)
        assertTrue(settings.audioFeedbackEnabled)
        assertTrue(settings.hapticFeedbackEnabled)
        assertTrue(settings.voiceTriggerEnabled)
    }

    @Test
    fun testSaveAndReloadSettings_persistsAllValues() {
        val storage = InMemorySettingsStorage()
        val custom = AppSettings(
            gameMode = GameMode.CHO_DAI_DEE,
            ddzRole = DdzRole.LANDLORD,
            cddVariation = Variation.HONG_KONG,
            audioFeedbackEnabled = false,
            hapticFeedbackEnabled = false,
            voiceTriggerEnabled = true,
            voiceLanguage = VoiceLanguage.CANTONESE,
            voiceDetailLevel = VoiceDetailLevel.DETAILED,
            speechRate = 1.25f,
            speechPitch = 0.95f,
            autoReadOnFullHand = true
        )

        storage.saveSettings(custom)
        val reloaded = storage.loadSettings()

        assertEquals(GameMode.CHO_DAI_DEE, reloaded.gameMode)
        assertEquals(DdzRole.LANDLORD, reloaded.ddzRole)
        assertEquals(Variation.HONG_KONG, reloaded.cddVariation)
        assertEquals(VoiceLanguage.CANTONESE, reloaded.voiceLanguage)
        assertEquals(VoiceDetailLevel.DETAILED, reloaded.voiceDetailLevel)
        assertEquals(1.25f, reloaded.speechRate, 0.001f)
        assertEquals(0.95f, reloaded.speechPitch, 0.001f)
        assertEquals(false, reloaded.audioFeedbackEnabled)
        assertEquals(false, reloaded.hapticFeedbackEnabled)
        assertEquals(true, reloaded.autoReadOnFullHand)
    }
}

