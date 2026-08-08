package com.jarbis.alexa.commands.impl

import com.jarbis.alexa.commands.CommandContext
import com.jarbis.alexa.commands.CommandModule

/** Se ejecuta cuando ningún módulo coincide con la frase. */
class UnknownCommand : CommandModule {
    override val id: String = "unknown"
    override val title: String = "(desconocido)"
    override val keywords: Map<String, List<String>> = emptyMap()

    override suspend fun execute(context: CommandContext, normalizedPhrase: String): String {
        return if (context.locale.language == "en") {
            "I didn't catch that. You can ask for: ${SUGGEST_EN}"
        } else {
            "No te he entendido. Prueba a decir: $SUGGEST_ES"
        }
    }

    private companion object {
        const val SUGGEST_ES = "¿qué tiempo hace?, o pon una canción"
        const val SUGGEST_EN = "the weather, or play a song"
    }
}