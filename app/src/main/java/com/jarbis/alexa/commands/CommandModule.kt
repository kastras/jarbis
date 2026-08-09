package com.jarbis.alexa.commands

import java.util.Locale

/** Resultado resumido del tiempo en una ciudad. */
data class WeatherSummary(
    val city: String,
    val temperatureC: Double,
    val apparentC: Double,
    val wmoCode: Int,
)

/** Servicios de los que dependen los módulos de comando. */
interface CommandContext {
    val locale: Locale
    /** Ciudad configurada para consultas de tiempo. */
    val city: String
    suspend fun weatherFor(city: String): WeatherSummary?
    /** Reproduce la búsqueda pedida y devuelve el título, o null si falló. */
    suspend fun playMusic(query: String): String?

    // ---- Control de reproducción de música ----
    /** Salta a la siguiente canción "parecida"; false si no hay música. */
    suspend fun nextSong(): Boolean
    /** Detiene la reproducción; false si no había música. */
    suspend fun stopMusic(): Boolean
    /** Sube el volumen de la música. */
    suspend fun volumeUp(): Boolean
    /** Baja el volumen de la música. */
    suspend fun volumeDown(): Boolean
}

/**
 * Un módulo de comando: intercambiable y ampliable. Para añadir uno nuevo,
 * clona esta interfaz (p.ej. [com.jarbis.alexa.commands.impl.WeatherCommand])
 * y añádelo a [CommandRegistry.MODULES].
 */
interface CommandModule {
    val id: String
    val title: String
    /** Palabras clave por código de idioma ("es" / "en"). Se matchean como subcadena normalizada. */
    val keywords: Map<String, List<String>>

    /** Devuelve la frase que dirá el asistente. El contexto ya resuelve servicios. */
    suspend fun execute(context: CommandContext, normalizedPhrase: String): String
}