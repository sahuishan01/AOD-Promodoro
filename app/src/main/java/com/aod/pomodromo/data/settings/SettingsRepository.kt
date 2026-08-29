package com.aod.pomodromo.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Settings-only persistence. No sensitive data is ever stored here
 * (durations, volume, background choice, UI prefs, picked content URIs).
 */
data class AppSettings(
    val workMinutes: Int = 25,
    val restMinutes: Int = 5,
    val volume: Float = 0.6f,
    /** "bundled:<id>" or "image:<uri>" */
    val backgroundId: String = "bundled:midnight",
    /** "bundled:<trackId>", "picked:<uri>", or "off" */
    val audioSelection: String = "off",
    val autoDimEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
)

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private object Keys {
        val WORK_MIN = intPreferencesKey("work_minutes")
        val REST_MIN = intPreferencesKey("rest_minutes")
        val VOLUME = floatPreferencesKey("volume")
        val BACKGROUND = stringPreferencesKey("background_id")
        val AUDIO = stringPreferencesKey("audio_selection")
        val AUTO_DIM = booleanPreferencesKey("auto_dim")
        val HAPTICS = booleanPreferencesKey("haptics")
    }

    val settings: Flow<AppSettings> = dataStore.data.map { p ->
        AppSettings(
            workMinutes = (p[Keys.WORK_MIN] ?: 25).coerceIn(1, 120),
            restMinutes = (p[Keys.REST_MIN] ?: 5).coerceIn(1, 60),
            volume = (p[Keys.VOLUME] ?: 0.6f).coerceIn(0f, 1f),
            backgroundId = p[Keys.BACKGROUND] ?: "bundled:midnight",
            audioSelection = p[Keys.AUDIO] ?: "off",
            autoDimEnabled = p[Keys.AUTO_DIM] ?: true,
            hapticsEnabled = p[Keys.HAPTICS] ?: true,
        )
    }

    suspend fun setWorkMinutes(v: Int) = dataStore.edit { it[Keys.WORK_MIN] = v.coerceIn(1, 120) }
    suspend fun setRestMinutes(v: Int) = dataStore.edit { it[Keys.REST_MIN] = v.coerceIn(1, 60) }
    suspend fun setVolume(v: Float) = dataStore.edit { it[Keys.VOLUME] = v.coerceIn(0f, 1f) }
    suspend fun setBackground(id: String) = dataStore.edit { it[Keys.BACKGROUND] = id }
    suspend fun setAudioSelection(sel: String) = dataStore.edit { it[Keys.AUDIO] = sel }
    suspend fun setAutoDim(enabled: Boolean) = dataStore.edit { it[Keys.AUTO_DIM] = enabled }
    suspend fun setHaptics(enabled: Boolean) = dataStore.edit { it[Keys.HAPTICS] = enabled }
}
