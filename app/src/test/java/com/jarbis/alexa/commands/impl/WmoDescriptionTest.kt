package com.jarbis.alexa.commands.impl

import org.junit.Assert.assertEquals
import org.junit.Test

class WmoDescriptionTest {

    @Test
    fun `descripcion en espanol`() {
        assertEquals("cielo despejado", WmoDescription.describe(0, "es"))
        assertEquals("lluvia", WmoDescription.describe(63, "es"))
        assertEquals("tormenta", WmoDescription.describe(95, "es"))
    }

    @Test
    fun `descripcion en ingles`() {
        assertEquals("clear sky", WmoDescription.describe(0, "en"))
        assertEquals("rain", WmoDescription.describe(63, "en"))
        assertEquals("thunderstorm", WmoDescription.describe(95, "en"))
    }

    @Test
    fun `codigo desconocido cae a generico`() {
        assertEquals("condiciones meteorológicas", WmoDescription.describe(999, "es"))
        assertEquals("unknown weather", WmoDescription.describe(999, "en"))
    }
}