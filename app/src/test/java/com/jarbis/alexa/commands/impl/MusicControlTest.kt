package com.jarbis.alexa.commands.impl

import com.jarbis.alexa.commands.impl.MusicControl.Command
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MusicControlTest {

    @Test
    fun `siguiente cancion es NEXT`() {
        assertEquals(Command.NEXT, MusicControl.command("siguiente cancion"))
    }

    @Test
    fun `siguiente a secas es NEXT`() {
        assertEquals(Command.NEXT, MusicControl.command("siguiente"))
    }

    @Test
    fun `pasa cancion es NEXT`() {
        assertEquals(Command.NEXT, MusicControl.command("pasa cancion"))
    }

    @Test
    fun `pasa a secas no es control`() {
        assertNull(MusicControl.command("pasa"))
    }

    @Test
    fun `para la musica es STOP`() {
        assertEquals(Command.STOP, MusicControl.command("para la musica"))
    }

    @Test
    fun `para en solitario es STOP`() {
        assertEquals(Command.STOP, MusicControl.command("para"))
    }

    @Test
    fun `parar es STOP`() {
        assertEquals(Command.STOP, MusicControl.command("parar"))
    }

    @Test
    fun `stop en ingles es STOP`() {
        assertEquals(Command.STOP, MusicControl.command("stop"))
    }

    @Test
    fun `para manana no es control`() {
        assertNull(MusicControl.command("para manana"))
    }

    @Test
    fun `sube el volumen es VOLUME_UP`() {
        assertEquals(Command.VOLUME_UP, MusicControl.command("sube el volumen"))
    }

    @Test
    fun `baja el volumen es VOLUME_DOWN`() {
        assertEquals(Command.VOLUME_DOWN, MusicControl.command("baja el volumen"))
    }

    @Test
    fun `stop the music en ingles es STOP`() {
        assertEquals(Command.STOP, MusicControl.command("stop the music"))
    }

    @Test
    fun `next song en ingles es NEXT`() {
        assertEquals(Command.NEXT, MusicControl.command("next song"))
    }

    @Test
    fun `turn up the volume en ingles es VOLUME_UP`() {
        assertEquals(Command.VOLUME_UP, MusicControl.command("turn up the volume"))
    }

    @Test
    fun `pause en ingles es STOP`() {
        assertEquals(Command.STOP, MusicControl.command("pause"))
    }
}