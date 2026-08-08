package com.jarbis.alexa.voice

import com.jarbis.alexa.AppConfig
import com.jarbis.alexa.commands.WakeGrammar
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer

/**
 * Detector del wake word usando Vosk con una gramática restringida que se
 * construye en tiempo real desde la configuración.
 */
class VoskWakeRecognizer(private val model: Model) {

    private var recognizer: Recognizer? = null
    private var wakeWords: List<String> = emptyList()

    fun start(wakeWords: List<String>) {
        this.wakeWords = wakeWords
        val grammar = WakeGrammar.grammar(wakeWords)
        recognizer = Recognizer(model, AppConfig.SAMPLE_RATE.toFloat(), grammar)
        recognizer?.reset()
    }

    fun reset() {
        recognizer?.reset()
    }

    fun stop() {
        recognizer?.close()
        recognizer = null
    }

    /** Procesa un fragmento; devuelve true si aparece el wake word. */
    fun accept(chunk: ShortArray): Boolean {
        val r = recognizer ?: return false
        if (r.acceptWaveForm(chunk, chunk.size)) {
            return matches(JSONObject(r.result).optString("text"))
        }
        return matches(JSONObject(r.getPartialResult()).optString("partial"))
    }

    private fun matches(text: String): Boolean {
        if (text.isBlank()) return false
        val tokens = text.lowercase().split(Regex("\\s+"))
        return tokens.any { it in wakeWords }
    }
}