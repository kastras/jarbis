package com.jarbis.alexa.commands.impl

import org.junit.Assert.assertEquals
import org.junit.Test

class MusicQueryParserTest {

    @Test
    fun `extrae la cancion tras pon musica de`() {
        assertEquals("queen", MusicQueryParser.extract("pon musica de queen", "es"))
    }

    @Test
    fun `extrae tras toca a secas con la cancion`() {
        assertEquals("abba", MusicQueryParser.extract("toca la cancion de abba", "es"))
    }

    @Test
    fun `quita el texto si la frase solo es el activador`() {
        assertEquals("", MusicQueryParser.extract("pon musica", "es"))
    }

    @Test
    fun `en ingles extrae tras play some`() {
        assertEquals("jazz", MusicQueryParser.extract("play some jazz", "en"))
    }

    @Test
    fun `quita acentos y repetidos espacios`() {
        assertEquals("imagine dragons", MusicQueryParser.extract("ponme canción de  imagine  dragons", "es"))
    }

    @Test
    fun `si no hay activador es devuelve la frase tal cual`() {
        assertEquals("voy a correr", MusicQueryParser.extract("voy a correr", "es"))
    }

    @Test
    fun `busca queen extrae la consulta`() {
        assertEquals("queen", MusicQueryParser.extract("busca queen", "es"))
    }

    @Test
    fun `buscar la cancion de extrae el artista`() {
        assertEquals("coldplay", MusicQueryParser.extract("buscar la cancion de coldplay", "es"))
    }

    @Test
    fun `buscame de queen extrae el artista`() {
        assertEquals("queen", MusicQueryParser.extract("buscame musica de queen", "es"))
    }

    @Test
    fun `dame un tema de extrae el artista`() {
        assertEquals("mecano", MusicQueryParser.extract("dame un tema de mecano", "es"))
    }

    @Test
    fun `reproducir extrae la consulta`() {
        assertEquals("imagine dragons", MusicQueryParser.extract("reproducir imagine dragons", "es"))
    }

    @Test
    fun `quiero escuchar extrae la consulta`() {
        assertEquals("mecano", MusicQueryParser.extract("quiero escuchar mecano", "es"))
    }

    @Test
    fun `suena extrae la consulta`() {
        assertEquals("planetas", MusicQueryParser.extract("suena los planetas", "es"))
    }

    @Test
    fun `en ingles play me some extrae la consulta`() {
        assertEquals("drake", MusicQueryParser.extract("play me some drake", "en"))
    }

    @Test
    fun `en ingles search for extrae la consulta`() {
        assertEquals("taylor swift", MusicQueryParser.extract("search for taylor swift", "en"))
    }

    @Test
    fun `en ingles find extrae la consulta`() {
        assertEquals("coldplay", MusicQueryParser.extract("find coldplay", "en"))
    }

    @Test
    fun `dale play extrae la consulta`() {
        assertEquals("queen", MusicQueryParser.extract("dale play a queen", "es"))
    }

    @Test
    fun `pasame extrae la consulta`() {
        assertEquals("queen", MusicQueryParser.extract("pasame una cancion de queen", "es"))
    }

    @Test
    fun `echame extrae la consulta`() {
        assertEquals("shakira", MusicQueryParser.extract("echame un tema de shakira", "es"))
    }

    @Test
    fun `saca extrae la consulta`() {
        assertEquals("coldplay", MusicQueryParser.extract("saca coldplay", "es"))
    }

    @Test
    fun `dale a secas queda vacio`() {
        assertEquals("", MusicQueryParser.extract("dale play", "es"))
    }
}