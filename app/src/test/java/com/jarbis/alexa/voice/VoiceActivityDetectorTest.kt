package com.jarbis.alexa.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceActivityDetectorTest {

    private fun loudChunk(size: Int = 1600): ShortArray =
        ShortArray(size) { 1000 }

    private fun silentChunk(size: Int = 1600): ShortArray =
        ShortArray(size)

    @Test
    fun `silencio inicial no cuenta como voz`() {
        val vad = VoiceActivityDetector(threshold = 800)
        vad.accept(silentChunk())
        assertFalse(vad.hasSpeech)
        assertFalse(vad.isSilent(2000L, nowMs = 1000L))
    }

    @Test
    fun `chunk con energia marca voz y tiempo`() {
        val vad = VoiceActivityDetector(threshold = 800)
        vad.accept(loudChunk(), nowMs = 50_000L)
        assertTrue(vad.hasSpeech)
        assertEquals(50_000L, vad.lastSpeechMs)
    }

    @Test
    fun `isSilent true solo tras silencio sostenido despues de hablar`() {
        val vad = VoiceActivityDetector(threshold = 800)
        vad.accept(loudChunk(), nowMs = 0L)
        assertFalse(vad.isSilent(2000L, nowMs = 1999L))
        assertTrue(vad.isSilent(2000L, nowMs = 2000L))
    }

    @Test
    fun `chunk vacio devuelve null y no altera el estado`() {
        val vad = VoiceActivityDetector(threshold = 800)
        vad.accept(loudChunk(), nowMs = 0L)
        val rms = vad.accept(ShortArray(0), nowMs = 5000L)
        assertNull(rms)
        assertTrue(vad.hasSpeech)
        assertEquals(0L, vad.lastSpeechMs)
    }

    @Test
    fun `rms devuelto es correcto`() {
        val vad = VoiceActivityDetector(threshold = 800)
        val rms = vad.accept(ShortArray(4) { 100 }, nowMs = 0L)
        assertNotNull(rms)
        assertEquals(100, rms)
    }

    @Test
    fun `reset limpia voz y tiempo`() {
        val vad = VoiceActivityDetector(threshold = 800)
        vad.accept(loudChunk(), nowMs = 1000L)
        vad.reset(nowMs = 2000L)
        assertFalse(vad.hasSpeech)
        assertFalse(vad.isSilent(2000L, nowMs = 3000L))
    }
}