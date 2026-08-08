package com.jarbis.alexa.commands

/** Construye la gramática JSON que Vosk usa para el "old word" (patrón puro, testable en JVM). */
object WakeGrammar {

    /**
     * Devuelve una lista JSON de palabras para la gramática de activación.
     * Ej. ["alexa"] -> `["alexa","[unk]"]` (el "[unk]" permite silencios/ruido).
     */
    fun grammar(words: List<String>): String {
        val quoted = words.joinToString(",") { "\"$it\"" }
        return if (words.isEmpty()) {
            "[\"[unk]\"]"
        } else {
            "[" + quoted + ",\"[unk]\"]"
        }
    }

    /** Valida que la palabra no rompa el JSON (sin comillas, espacios, etc.). */
    fun isValidWakeWord(word: String): Boolean =
        word.isNotBlank() && word.all { it.isLetter() || it.isDigit() }
}