package com.jarbis.alexa.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jarbis.alexa.AppConfig
import com.jarbis.alexa.Language
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "assistant_settings")

data class AppSettings(
    val wakeWord: String = AppConfig.DEFAULT_WAKE_WORD,
    val language: Language = Language.ES,
    val city: String = AppConfig.DEFAULT_CITY,
    val enabledModuleIds: Set<String> = emptySet(),
    val musicFolderUri: String? = null,
    val downloadFolderUri: String? = null,
    val musicCacheMaxBytes: Long = AppConfig.MUSIC_CACHE_MAX_BYTES,
) {
    /** [enabledModuleIds] guarda los módulos DESHABILITADOS: vacío = todos activos. */
    fun isEnabled(id: String): Boolean = id !in enabledModuleIds
}

class SettingsStore(private val context: Context) {

    private val wakeWordKey = stringPreferencesKey("wake_word")
    private val languageKey = stringPreferencesKey("language_code")
    private val cityKey = stringPreferencesKey("city")
    private val enabledModulesKey = stringPreferencesKey("enabled_modules")
    private val musicFolderKey = stringPreferencesKey("music_folder_uri")
    private val downloadFolderKey = stringPreferencesKey("download_folder_uri")
    private val musicCacheMaxBytesKey = longPreferencesKey("music_cache_max_bytes")

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            wakeWord = prefs[wakeWordKey] ?: AppConfig.DEFAULT_WAKE_WORD,
            language = Language.fromCode(prefs[languageKey] ?: Language.ES.code),
            city = prefs[cityKey] ?: AppConfig.DEFAULT_CITY,
            enabledModuleIds = (prefs[enabledModulesKey] ?: "").split(",").filter { it.isNotBlank() }.toSet(),
            musicFolderUri = prefs[musicFolderKey],
            downloadFolderUri = prefs[downloadFolderKey],
            musicCacheMaxBytes = (prefs[musicCacheMaxBytesKey] ?: AppConfig.MUSIC_CACHE_MAX_BYTES)
                .coerceAtLeast(AppConfig.MIN_MUSIC_CACHE_BYTES),
        )
    }

    suspend fun fetch(): AppSettings = settings.first()

    suspend fun setMusicFolder(uri: String?) {
        context.dataStore.edit { prefs ->
            if (uri == null) prefs.remove(musicFolderKey) else prefs[musicFolderKey] = uri
        }
    }

    suspend fun setDownloadFolder(uri: String?) {
        context.dataStore.edit { prefs ->
            if (uri == null) prefs.remove(downloadFolderKey) else prefs[downloadFolderKey] = uri
        }
    }

    suspend fun setMusicCacheMaxBytes(bytes: Long) {
        context.dataStore.edit { prefs ->
            prefs[musicCacheMaxBytesKey] = bytes.coerceAtLeast(AppConfig.MIN_MUSIC_CACHE_BYTES)
        }
    }

    suspend fun setWakeWord(word: String) {
        context.dataStore.edit { it[wakeWordKey] = word.trim().lowercase() }
    }

    suspend fun setLanguage(language: Language) {
        context.dataStore.edit { it[languageKey] = language.code }
    }

    suspend fun setCity(city: String) {
        context.dataStore.edit { it[cityKey] = city.trim() }
    }

    suspend fun setModuleEnabled(id: String, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            val current = (prefs[enabledModulesKey] ?: "").split(",").filter { it.isNotBlank() }.toMutableSet()
            if (enabled) current.remove(id) else current.add(id)
            prefs[enabledModulesKey] = current.joinToString(",")
        }
    }
}
