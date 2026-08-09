package com.jarbis.alexa.commands

import com.jarbis.alexa.commands.impl.MusicCommand
import com.jarbis.alexa.commands.impl.WeatherCommand
import java.util.Locale

object TextNormalizer {
    private val combiningMarks = Regex("[\\p{M}\\p{P}\\p{S}]")

    /** Minúsculas sin acentos ni puntuación, para cruces robustos. */
    fun normalize(text: String): String =
        java.text.Normalizer.normalize(text.trim().lowercase(), java.text.Normalizer.Form.NFD)
            .replace(combiningMarks, "")
}

/**
 * Descubre módulos (ampliable: añade uno y ya funciona).
 * Hace match por palabra completa normalizada (no subcadena, para que
 * "compone" no dispare "pon"); si varias palabras coinciden, la más larga
 * gana ("tiempo para mañana" → Tiempo por "tiempo" vs "para"). Las keywords
 * con espacios ("siguiente cancion") matchean como frase exacta.
 */
class CommandRegistry(private val modules: List<CommandModule>) {

    val all: List<CommandModule> get() = modules

    fun matching(rawPhrase: String, locale: Locale): CommandModule? {
        val phraseTokens = TextNormalizer.normalize(rawPhrase)
            .replace(Regex("\\s+"), " ")
            .trim()
            .split(" ")
            .filter { it.isNotBlank() }

        var best: CommandModule? = null
        var bestLength = -1
        for (module in modules) {
            val keywords = module.keywords[locale.language] ?: continue
            for (keyword in keywords) {
                val normalized = TextNormalizer.normalize(keyword).trim()
                if (normalized.isBlank()) continue
                if (!phraseTokens.containsWordPhrase(normalized)) continue
                if (normalized.length > bestLength) {
                    bestLength = normalized.length
                    best = module
                }
            }
        }
        return best
    }
}

/** Matcheo por palabra completa o frase exacta entre tokens normalizados. */
fun List<String>.containsWordPhrase(needle: String): Boolean {
    val target = needle.trim().replace(Regex("\\s+"), " ")
    val targetTokens = target.split(" ").filter { it.isNotBlank() }
    if (targetTokens.isEmpty() || size < targetTokens.size) return false
    for (start in 0..(size - targetTokens.size)) {
        if (slice(start until start + targetTokens.size) == targetTokens) return true
    }
    return false
}

/** Registro por defecto de la app con los módulos actuales. */
object DefaultModules {
    val all: List<CommandModule> = listOf(
        WeatherCommand(),
        MusicCommand(),
    )
}