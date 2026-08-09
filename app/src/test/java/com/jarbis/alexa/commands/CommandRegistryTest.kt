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

    @Test
    fun `busca queen enruta a musica`() {
        assertTrue(registry.matching("busca queen", Locale.forLanguageTag("es")) is MusicCommand)
    }

    @Test
    fun `siguiente cancion enruta a musica`() {
        assertTrue(registry.matching("siguiente cancion", Locale.forLanguageTag("es")) is MusicCommand)
    }

    @Test
    fun `para la musica enruta a musica`() {
        assertTrue(registry.matching("para la musica", Locale.forLanguageTag("es")) is MusicCommand)
    }

    @Test
    fun `sube el volumen enruta a musica`() {
        assertTrue(registry.matching("sube el volumen", Locale.forLanguageTag("es")) is MusicCommand)
    }

    @Test
    fun `baja el volumen enruta a musica`() {
        assertTrue(registry.matching("baja el volumen", Locale.forLanguageTag("es")) is MusicCommand)
    }

    @Test
    fun `match por palabra completa evita falsos positivos`() {
        assertNull(registry.matching("ese disco lo compone un artista", Locale.forLanguageTag("es")))
        assertNull(registry.matching("me respondió otra cosa", Locale.forLanguageTag("es")))
    }

    @Test
    fun `tiempo para manana gana weather sobre para`() {
        assertTrue(registry.matching("tiempo para mañana", Locale.forLanguageTag("es")) is WeatherCommand)
    }

    @Test
    fun `palabra mas larga gana cuando hay varias`() {
        assertTrue(registry.matching("pon musica de queen", Locale.forLanguageTag("es")) is MusicCommand)
    }
}