package com.jarbis.alexa.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
) {
    /** [enabledModuleIds] guarda los módulos DESHABILITADOS: vacío = todos activos. */
    fun isEnabled(id: String): Boolean = id !in enabledModuleIds
}

class SettingsStore(private val context: Context) {

    private val wakeWordKey = stringPreferencesKey("wake_word")
    private val languageKey = stringPreferencesKey("language_code")
    private val cityKey = stringPreferencesKey("city")
    private val enabledModulesKey = stringPreferencesKey("enabled_modules")

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            wakeWord = prefs[wakeWordKey] ?: AppConfig.DEFAULT_WAKE_WORD,
            language = Language.fromCode(prefs[languageKey] ?: Language.ES.code),
            city = prefs[cityKey] ?: AppConfig.DEFAULT_CITY,
            enabledModuleIds = (prefs[enabledModulesKey] ?: "").split(",").filter { it.isNotBlank() }.toSet(),
        )
    }

    suspend fun fetch(): AppSettings = settings.first()

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