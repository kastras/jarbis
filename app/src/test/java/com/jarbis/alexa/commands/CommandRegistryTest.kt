package com.jarbis.alexa.commands

import com.jarbis.alexa.commands.impl.MusicCommand
import com.jarbis.alexa.commands.impl.WeatherCommand
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class CommandRegistryTest {

    private val registry = CommandRegistry(listOf(WeatherCommand(), MusicCommand()))

    @Test
    fun `frase de tiempo matchea el modulo weather`() {
        assertTrue(registry.matching("que tiempo hace", Locale.forLanguageTag("es")) is WeatherCommand)
    }

    @Test
    fun `frase en ingles de weather matchea en ingles`() {
        assertTrue(registry.matching("what is the weather like", Locale.US) is WeatherCommand)
    }

    @Test
    fun `frase de musica matchea el modulo music`() {
        assertTrue(registry.matching("pon musica de queen", Locale.forLanguageTag("es")) is MusicCommand)
    }

    @Test
    fun `frase generica no matchea ninguno`() {
        assertNull(registry.matching("hola como estas", Locale.forLanguageTag("es")))
    }

    @Test
    fun `es configurable filtrando modulos`() {
        val onlyWeather = CommandRegistry(listOf(WeatherCommand()))
        assertNull(onlyWeather.matching("pon musica de queen", Locale.forLanguageTag("es")))
    }

    @Test
    fun `busca con acentos normalizados en ambas lenguas`() {
        assertTrue(registry.matching("qué tiempo hace ahora", Locale.forLanguageTag("es")) is WeatherCommand)
        assertTrue(registry.matching("play a song", Locale.US) is MusicCommand)
    }
}