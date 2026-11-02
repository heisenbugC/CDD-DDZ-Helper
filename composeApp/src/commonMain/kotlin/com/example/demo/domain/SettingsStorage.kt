package com.example.demo.domain

import androidx.compose.runtime.Composable

/**
 * Cross-platform persistence interface for AppSettings.
 * Stores settings permanently across app launches.
 */
interface SettingsStorage {
    fun loadSettings(): AppSettings
    fun saveSettings(settings: AppSettings)
}

/**
 * Provides a platform-specific SettingsStorage instance:
 * - Android: SharedPreferences
 * - iOS: NSUserDefaults
 */
@Composable
expect fun rememberSettingsStorage(): SettingsStorage

