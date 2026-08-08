package com.jarbis.alexa

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.jarbis.alexa.Language
import com.jarbis.alexa.voice.AssistantEngine
import com.jarbis.alexa.voice.UiState
import com.jarbis.alexa.voice.VoiceAssistantService
import kotlinx.coroutines.flow.StateFlow

class AssistantViewModel(app: Application) : AndroidViewModel(app) {

    private val engine: AssistantEngine = AlexaApp.engine(app)

    val uiState: StateFlow<UiState> = engine.uiState()

    fun start() = VoiceAssistantService.start(getApplication())
    fun stop() = VoiceAssistantService.stop(getApplication())

    fun setWakeWord(word: String) = engine.setWakeWord(word)
    fun setLanguage(language: Language) = engine.setLanguage(language)
    fun setCity(city: String) = engine.setCity(city)
    fun setModuleEnabled(id: String, enabled: Boolean) = engine.setModuleEnabled(id, enabled)
    fun simulateCommand(phrase: String) = engine.simulateCommand(phrase)

    override fun onCleared() {
        // el motor vive en la Application; no lo derribamos con la Activity
        super.onCleared()
    }
}