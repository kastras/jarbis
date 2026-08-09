package com.jarbis.alexa.voice

import com.jarbis.alexa.AppConfig
import kotlin.math.sqrt

/**
 * Detección de actividad de voz (VAD) ligera sobre PCM16 mono 16 kHz.
 * A diferencia del `Recognizer` (costoso), aquí se mide la energía RMS de cada
 * chunk para saber si se está hablando y cuándo hay silencio sostenido. Esto
 * permite cortar el comando al terminar la frase en vez de esperar siempre la
 * ventana dura.
 */
class VoiceActivityDetector(
    private val threshold: Int = AppConfig.VAD_RMS_THRESHOLD,
) {

    /** Si al menos un chunk superó el umbral (hubo algo que parecía voz). */
    var hasSpeech: Boolean = false
        private set

    /** Momento (epoch ms) de la última vez que se detectó voz. 0 si nunca. */
    var lastSpeechMs: Long = 0L
        private set

    /**
     * Procesa un chunk; actualiza [hasSpeech] y [lastSpeechMs]. Devuelve el
     * RMS computado (para logging) o null si [samples] está poblada vacío.
     */
    fun accept(samples: ShortArray, nowMs: Long = System.currentTimeMillis()): Int? {
        if (samples.isEmpty()) return null
        var sum = 0.0
        for (s in samples) sum += s.toDouble() * s
        val rms = (sqrt(sum / samples.size)).toInt()
        if (rms >= threshold) {
            hasSpeech = true
            lastSpeechMs = nowMs
        }
        return rms
    }

    /** True si pasaron [silenceMs] desde la última voz (tras haber hablado). */
    fun isSilent(silenceMs: Long, nowMs: Long = System.currentTimeMillis()): Boolean =
        hasSpeech && (nowMs - lastSpeechMs) >= silenceMs

    /** Reinicia el estado de voz (nuevo comando) sin tocar el umbral. */
    fun reset(nowMs: Long = System.currentTimeMillis()) {
        hasSpeech = false
        lastSpeechMs = nowMs
    }
}