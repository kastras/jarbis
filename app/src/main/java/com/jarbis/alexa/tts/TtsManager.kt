package com.jarbis.alexa.tts

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** TTS con voz es/en configurable + tono de alerta al despertar. */
class TtsManager(context: Context) : TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private val tts: TextToSpeech = TextToSpeech(appContext, this)
    private val ready = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentLanguage: Locale = Locale.getDefault()

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            ready.set(true)
            applyLanguage()
        }
    }

    fun setLanguage(locale: Locale) {
        currentLanguage = locale
        if (ready.get()) applyLanguage()
    }

    private fun applyLanguage() {
        tts.language = currentLanguage
    }

    /** Habla [text] y llama [onDone] cuando termina. */
    fun speak(text: String, onDone: () -> Unit) {
        if (!ready.get()) {
            mainHandler.post(onDone)
            return
        }
        val utteranceId = UUID.randomUUID().toString()
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                mainHandler.post(onDone)
            }

            override fun onError(utteranceId: String?) {
                mainHandler.post(onDone)
            }
        })
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    /** Tono corto para avisar que el asistente está escuchando al comando. */
    fun beep() {
        val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 90)
        tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 140)
        Thread {
            try {
                Thread.sleep(220)
            } catch (_: InterruptedException) {
            }
            tone.release()
        }.start()
    }

    fun stop() {
        if (ready.get()) tts.stop()
    }

    fun shutdown() {
        ready.set(false)
        tts.stop()
        tts.shutdown()
    }
}