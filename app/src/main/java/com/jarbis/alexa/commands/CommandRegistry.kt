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
 * Descubre módulos (ampliable: añade uno al lista y ya funciona).
 * Hace match por subcadena normalizada de la frase reconocida.
 */
class CommandRegistry(private val modules: List<CommandModule>) {

    val all: List<CommandModule> get() = modules

    fun matching(rawPhrase: String, locale: Locale): CommandModule? {
        val phrase = TextNormalizer.normalize(rawPhrase)
        return modules.firstOrNull { module ->
            module.keywords[locale.language]
                ?.any { phrase.contains(TextNormalizer.normalize(it)) } == true
        }
    }
}

/** Registro por defecto de la app con los módulos actuales. */
object DefaultModules {
    val all: List<CommandModule> = listOf(
        WeatherCommand(),
        MusicCommand(),
    )
}