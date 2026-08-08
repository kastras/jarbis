package com.jarbis.alexa.commands.impl

import com.jarbis.alexa.commands.CommandContext
import com.jarbis.alexa.commands.CommandModule
import com.jarbis.alexa.commands.TextNormalizer

/**
 * Módulo de música: ante "pon/toca/reproduce X" reproduce la búsqueda en
 * segundo plano (in-app, Media3 + Piped API) y encadena canciones parecidas.
 */
class MusicCommand : CommandModule {

    override val id: String = "music"
    override val title: String = "Música"
    override val keywords: Map<String, List<String>> = mapOf(
        "es" to listOf("pon", "toca", "ponme", "reproduce", "musica", "canción", "canciones", "cancion", "música"),
        "en" to listOf("play", "put", "music", "song", "songs", "turn"),
    )

    private val defaultQuery = mapOf(
        "es" to "música",
        "en" to "music",
    )

    override suspend fun execute(context: CommandContext, normalizedPhrase: String): String {
        val query = MusicQueryParser.extract(normalizedPhrase, context.locale.language)
            .ifBlank { defaultQuery[context.locale.language] ?: "music" }
        return if (context.locale.language == "en") {
            context.playMusic(query)?.let { "Playing: $it." } ?: "I could not find music."
        } else {
            context.playMusic(query)?.let { "Reproduciendo: $it." } ?: "No pude encontrar música."
        }
    }
}

object MusicQueryParser {

    /** Palabras de mando que abren la frase (por idioma). */
    private val triggers = mapOf(
        "es" to listOf("reproduce", "reproducir", "ponme", "pon", "toca", "busca"),
        "en" to listOf("play some", "put on", "play", "turn on"),
    )

    /** Ruido residual que se quita del comienzo de la consulta. */
    private val noise = mapOf(
        "es" to listOf("musica", "música", "canción", "canciones", "cancion", "la", "el", "de", "en", "youtube"),
        "en" to listOf("music", "songs", "song", "the", "on", "de", "some", "youtube"),
    )

    /**
     * Extrae la consulta a buscar de una frase reconocida (normalizada).
     * "ponmusica de imagine dragons" -> "imagine dragons"
     */
    fun extract(normalizedPhrase: String, language: String): String {
        var trimmed = normalizedPhrase.trim()
        val triggerList = triggers[language] ?: emptyList()
        for (trigger in triggerList) {
            if (trimmed == trigger) {
                trimmed = ""
                break
            }
            if (trimmed.startsWith("$trigger ")) {
                trimmed = trimmed.removePrefix("$trigger ").trim()
                break
            }
        }

        val noiseList = noise[language] ?: emptyList()
        var changed = true
        while (changed) {
            changed = false
            for (word in noiseList) {
                if (trimmed == word) {
                    trimmed = ""
                    changed = true
                } else if (trimmed.startsWith("$word ")) {
                    trimmed = trimmed.removePrefix("$word ").trim()
                    changed = true
                }
                if (!changed && trimmed.isBlank()) break
            }
        }
        return TextNormalizer.normalize(trimmed)
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}