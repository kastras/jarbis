package com.jarbis.alexa.commands.impl

import com.jarbis.alexa.commands.CommandContext
import com.jarbis.alexa.commands.CommandModule
import kotlin.math.roundToInt

class WeatherCommand : CommandModule {
    override val id: String = "weather"
    override val title: String = "Tiempo"
    override val keywords: Map<String, List<String>> = mapOf(
        "es" to listOf("tiempo", "clima", "temperatura", "grados", "llover"),
        "en" to listOf("weather", "forecast", "temperature", "temp", "expectativas"),
    )

    override suspend fun execute(context: CommandContext, normalizedPhrase: String): String {
        val summary = context.weatherFor(context.city)
            ?: return if (context.locale.language == "en") {
                "I couldn't check the weather right now. Check your connection or the city name."
            } else {
                "No pude consultar el tiempo ahora mismo. Revisa tu conexión o el nombre de la ciudad."
            }

        val description = WmoDescription.describe(summary.wmoCode, context.locale.language)
        val temp = summary.temperatureC.roundToInt()
        return if (context.locale.language == "en") {
            "Right now it is $temp degrees in ${summary.city}, $description."
        } else {
            "Ahora en ${summary.city} hay $temp grados, $description."
        }
    }
}