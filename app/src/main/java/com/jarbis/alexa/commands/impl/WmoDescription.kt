package com.jarbis.alexa.commands.impl

import com.jarbis.alexa.commands.CommandModule

object WmoDescription {

    private val es = mapOf(
        0 to "cielo despejado",
        1 to "mayormente despejado",
        2 to "parcialmente nublado",
        3 to "nublado",
        45 to "niebla",
        48 to "niebla con escarcha",
        51 to "llovizna ligera",
        53 to "llovizna",
        55 to "llovizna densa",
        56 to "llovizna helada",
        57 to "llovizna helada densa",
        61 to "lluvia ligera",
        63 to "lluvia",
        65 to "lluvia fuerte",
        66 to "lluvia helada",
        67 to "lluvia helada fuerte",
        71 to "nevada ligera",
        73 to "nevada",
        75 to "nevada fuerte",
        77 to "granos de nieve",
        80 to "chubascos ligeros",
        81 to "chubascos",
        82 to "chubascos fuertes",
        85 to "chubascos de nieve",
        86 to "chubascos de nieve fuertes",
        95 to "tormenta",
        96 to "tormenta con granizo",
        99 to "tormenta con granizo fuerte",
    )

    private val en = mapOf(
        0 to "clear sky",
        1 to "mostly clear",
        2 to "partly cloudy",
        3 to "overcast",
        45 to "fog",
        48 to "rime fog",
        51 to "light drizzle",
        53 to "drizzle",
        55 to "dense drizzle",
        56 to "freezing drizzle",
        57 to "dense freezing drizzle",
        61 to "light rain",
        63 to "rain",
        65 to "heavy rain",
        66 to "freezing rain",
        67 to "heavy freezing rain",
        71 to "light snow",
        73 to "snow",
        75 to "heavy snow",
        77 to "snow grains",
        80 to "light showers",
        81 to "rain showers",
        82 to "heavy showers",
        85 to "snow showers",
        86 to "heavy snow showers",
        95 to "thunderstorm",
        96 to "thunderstorm with hail",
        99 to "thunderstorm with heavy hail",
    )

    fun describe(code: Int, language: String): String {
        val dict = if (language == "en") en else es
        return dict[code] ?: if (language == "en") "unknown weather" else "condiciones meteorológicas"
    }
}