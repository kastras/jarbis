package com.jarbis.alexa

import java.util.Locale

/** Idiomas soportados con su modelo Vosk (offline) y locale de TTS. */
enum class Language(
    val code: String,
    val modelAsset: String,
    val ttsLocale: Locale,
) {
    ES(
        code = "es",
        modelAsset = "vosk-model-small-es-0.42.zip",
        ttsLocale = Locale("es", "ES"),
    ),
    EN(
        code = "en",
        modelAsset = "vosk-model-small-en-us-0.15.zip",
        ttsLocale = Locale.US,
    ),
    ;

    companion object {
        fun fromCode(code: String): Language =
            entries.firstOrNull { it.code == code } ?: ES
    }
}