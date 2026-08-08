package com.jarbis.alexa.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.jarbis.alexa.AppConfig
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Captura de micrófono en bucle a 16 kHz mono PCM16 y entrega chunks al
 * consumidor en un hilo propio.
 */
class MicInput {

    private val running = AtomicBoolean(false)
    private var thread: Thread? = null
    private var audioRecord: AudioRecord? = null

    @SuppressLint("MissingPermission")
    @Throws(IllegalStateException::class)
    fun start(onChunk: (ShortArray) -> Unit) {
        if (running.get()) return

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
        record.startRecording()
        running.set(true)

        thread = Thread {
            val buffer = ShortArray(minBuffer)
            while (running.get()) {
                val n = record.read(buffer, 0, buffer.size)
                if (n > 0) {
                    onChunk(buffer.copyOf(n))
                } else {
                    Thread.sleep(10)
                }
            }
        }.apply {
            name = "mic-input"
            start()
        }
    }

    @Synchronized
    fun stop() {
        running.set(false)
        thread?.join(500)
        thread = null
        audioRecord?.let {
            runCatching { it.stop() }
            it.release()
        }
        audioRecord = null
    }
}