package com.jarbis.alexa.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import com.jarbis.alexa.AppConfig
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Captura de micrófono en bucle a 16 kHz mono PCM16 y entrega chunks al
 * consumidor en un hilo propio.
 */
class MicInput {

    private val running = AtomicBoolean(false)
    private var thread: Thread? = null
    @Volatile private var audioRecord: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null

    @SuppressLint("MissingPermission")
    @Synchronized
    @Throws(IllegalStateException::class)
    fun start(onChunk: (ShortArray) -> Unit): EchoCancellationStatus {
        if (running.get()) return echoCancellationStatus()

        val minBuffer = AudioRecord.getMinBufferSize(
            AppConfig.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            AppConfig.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuffer,
        )
        check(record.state == AudioRecord.STATE_INITIALIZED) {
            "No se pudo abrir el micrófono"
        }
        audioRecord = record
        val available = runCatching { AcousticEchoCanceler.isAvailable() }.getOrDefault(false)
        val effect = if (available) runCatching {
            AcousticEchoCanceler.create(record.audioSessionId)?.also { it.enabled = true }
        }.getOrNull() else null
        echoCanceler = effect
        record.startRecording()
        running.set(true)

        thread = Thread {
            val buffer = ShortArray(minBuffer)
            while (running.get() && audioRecord === record) {
                val n = record.read(buffer, 0, buffer.size)
                if (n > 0 && running.get() && audioRecord === record) {
                    onChunk(buffer.copyOf(n))
                } else {
                    Thread.sleep(10)
                }
            }
        }.apply {
            name = "mic-input"
              start()
        }
        return EchoCancellationStatus(available, effect?.enabled == true)
    }

    private fun echoCancellationStatus(): EchoCancellationStatus {
        val available = runCatching { AcousticEchoCanceler.isAvailable() }.getOrDefault(false)
        return EchoCancellationStatus(available, echoCanceler?.enabled == true)
    }

    @Synchronized
    fun stop() {
        running.set(false)
        val record = audioRecord
        audioRecord = null
        // Desbloquea read antes de esperar al hilo; no entrega más chunks al cerrar Vosk.
        runCatching { record?.stop() }
        thread?.join(500)
        thread = null
        runCatching { echoCanceler?.release() }
        echoCanceler = null
        record?.release()
    }
}

data class EchoCancellationStatus(val available: Boolean, val enabled: Boolean)
