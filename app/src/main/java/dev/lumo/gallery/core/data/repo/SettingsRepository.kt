package dev.lumo.gallery.core.data.repo

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import dev.lumo.gallery.core.model.ThumbnailQuality

private val Context.settingsDataStore by preferencesDataStore(name = "lumo_settings")

data class AppSettings(
    val slideshowIntervalSec: Int = 3,
    val thumbnailQuality: ThumbnailQuality = ThumbnailQuality.LOW,
    val dynamicColor: Boolean = true,
    val pinHash: String? = null,
    val pinSalt: String? = null,
    val biometricUnlock: Boolean = true
) {
    val hasPin: Boolean get() = !pinHash.isNullOrBlank() && !pinSalt.isNullOrBlank()
}

class SettingsRepository(private val context: Context) {

    private object Keys {
        val slideshowInterval = intPreferencesKey("slideshow_interval_sec")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val pinHash = stringPreferencesKey("pin_hash")
        val pinSalt = stringPreferencesKey("pin_salt")
        val biometric = booleanPreferencesKey("biometric_unlock")
        val thumbnailQuality = stringPreferencesKey("thumbnail_quality")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            slideshowIntervalSec = (prefs[Keys.slideshowInterval] ?: 3).coerceIn(1, 10),
            thumbnailQuality = prefs[Keys.thumbnailQuality]
                ?.let { runCatching { ThumbnailQuality.valueOf(it) }.getOrNull() }
                ?: ThumbnailQuality.LOW,
            dynamicColor = prefs[Keys.dynamicColor] ?: true,
            pinHash = prefs[Keys.pinHash],
            pinSalt = prefs[Keys.pinSalt],
            biometricUnlock = prefs[Keys.biometric] ?: true
        )
    }

    suspend fun setThumbnailQuality(quality: ThumbnailQuality) {
        context.settingsDataStore.edit {
            it[Keys.thumbnailQuality] = quality.name
        }
    }

    suspend fun setSlideshowInterval(seconds: Int) {
        context.settingsDataStore.edit {
            it[Keys.slideshowInterval] = seconds.coerceIn(1, 10)
        }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.dynamicColor] = enabled }
    }

    suspend fun setPin(pinHash: String?, pinSalt: String?) {
        context.settingsDataStore.edit { prefs ->
            if (pinHash == null || pinSalt == null) {
                prefs.remove(Keys.pinHash)
                prefs.remove(Keys.pinSalt)
            } else {
                prefs[Keys.pinHash] = pinHash
                prefs[Keys.pinSalt] = pinSalt
            }
        }
    }

    suspend fun setBiometricUnlock(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.biometric] = enabled }
    }
}
