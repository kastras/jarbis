package com.jarbis.alexa.commands.impl

import org.junit.Assert.assertEquals
import org.junit.Test

class MusicQueryParserTest {

    @Test
    fun `extrae la cancion tras pon musica de`() {
        assertEquals("queen", MusicQueryParser.extract("pon musica de queen", "es"))
    }

    @Test
    fun `extrae tras pon a secas con la cancion`() {
        assertEquals("abba", MusicQueryParser.extract("toca la cancion de abba", "es"))
    }

    @Test
    fun `quita la d previa si la frase solo es el activador`() {
        assertEquals("", MusicQueryParser.extract("pon musica", "es"))
    }

    @Test
    fun `en ingles extrae tras play some`() {
        assertEquals("jazz", MusicQueryParser.extract("play some jazz", "en"))
    }

    @Test
    fun `quita acentos y repetima espacios`() {
        assertEquals("imagine dragons", MusicQueryParser.extract("ponme canción de  imagine  dragons", "es"))
    }

    @Test
    fun `si no hay activador es devuelve la frase tal cual`() {
        assertEquals("voy a correr", MusicQueryParser.extract("voy a correr", "es"))
    }
}