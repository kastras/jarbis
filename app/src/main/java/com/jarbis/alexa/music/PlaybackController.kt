package com.jarbis.alexa.music

import android.content.Context
import android.media.AudioManager

/**
 * Baja la música mientras el asistente habla (duck) y la restaura después,
 * sin pausar la escucha continua (Vosk) ni la reproducción. Pide el foco
 * [`AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`] sobre el stream de
 * música; al liberarlo la música vuelve a su volumen.
 */
class PlaybackController(context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val focusListener = AudioManager.OnAudioFocusChangeListener { _ -> }

    @Volatile
    private var ducking = false

    /** Baja la música mientras dura la respuesta hablada. */
    @Synchronized
    fun duck() {
        if (ducking) return
        val result = audioManager.requestAudioFocus(
            focusListener,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
        )
        ducking = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    /** Restaura el volumen de la música. */
    @Synchronized
    fun unDuck() {
        if (!ducking) return
        audioManager.abandonAudioFocus(focusListener)
        ducking = false
    }
}