package com.jarbis.alexa.commands

import org.junit.Assert.assertEquals
import org.junit.Test

class TextNormalizerTest {

    @Test
    fun `normaliza minusculas y acentos`() {
        assertEquals("que tiempo hace", TextNormalizer.normalize("¿Qué TIEMPO haCe?"))
    }

    @Test
    fun `conserva los espacios internos`() {
        assertEquals("pon  la  cancion de abba", TextNormalizer.normalize("Pon  la  CANCIÓN de Abba"))
    }
}

class SettingsEnabledTest {

    @Test
    fun `modulos habilitados por defecto cuando el conjunto esta vacio`() {
        val s = com.jarbis.alexa.settings.AppSettings()
        assertEquals(true, s.isEnabled("music"))
        assertEquals(true, s.isEnabled("weather"))
    }

    @Test
    fun `modulo deshabilitado cuando aparece en el conjunto`() {
        val s = com.jarbis.alexa.settings.AppSettings(enabledModuleIds = setOf("music"))
        assertEquals(false, s.isEnabled("music"))
        assertEquals(true, s.isEnabled("weather"))
    }
}