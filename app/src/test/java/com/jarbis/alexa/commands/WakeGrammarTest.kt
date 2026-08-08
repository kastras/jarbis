package com.jarbis.alexa.commands.impl

import com.jarbis.alexa.commands.WakeGrammar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeGrammarTest {

    @Test
    fun `grammar para un wake word`() {
        assertEquals("[\"alexa\",\"[unk]\"]", WakeGrammar.grammar(listOf("alexa")))
    }

    @Test
    fun `gramatica para multiples palabras`() {
        assertEquals("[\"alexa\",\"hola\",\"[unk]\"]", WakeGrammar.grammar(listOf("alexa", "hola")))
    }

    @Test
    fun `lista vacia solo permite unicrono`() {
        assertEquals("[\"[unk]\"]", WakeGrammar.grammar(emptyList()))
    }

    @Test
    fun `palabras validas e invalidas`() {
        assertTrue(WakeGrammar.isValidWakeWord("alexa"))
        assertTrue(WakeGrammar.isValidWakeWord("alexa2"))
        assertFalse(WakeGrammar.isValidWakeWord(""))
        assertFalse(WakeGrammar.isValidWakeWord("ala\nxa"))
    }
}