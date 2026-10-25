package com.jarbis.alexa.music

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.io.RandomAccessFile

/** Una transferencia, varios lectores (incluidos los saltos de Media3).
 * Solo los bytes escritos se anuncian a los lectores; EOF significa archivo completo.
 */
internal class GrowingDownload(
    private val client: OkHttpClient,
    private val scope: CoroutineScope,
    private val url: String,
    private val partial: File,
    private val reserve: (Long) -> Unit,
    private val publish: (File) -> File,
) {
    private val monitor = Object()
    private var file = partial
    private var available = 0L
    private var length = -1L
    private var headers = false
    private var complete = false
    private var failure: IOException? = null
    private var call: Call? = null

    fun start() {
        scope.launch {
            try {
                val request = client.newCall(Request.Builder().url(url).build())
                synchronized(monitor) {
                    checkFailure()
                    call = request
                }
                request.execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Descarga: HTTP ${response.code}")
                    val body = response.body ?: throw IOException("Respuesta de audio vacía")
                    val expected = body.contentLength()
                    reserve(expected.coerceAtLeast(0))
                    partial.parentFile?.mkdirs()
                    partial.outputStream().use { output ->
                        synchronized(monitor) {
                            checkFailure()
                            length = expected
                            headers = true
                            monitor.notifyAll()
                        }
                        body.byteStream().use { input ->
                            val buffer = ByteArray(32 * 1024)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                synchronized(monitor) {
                                    checkFailure()
                                    reserve(available + count)
                                    output.write(buffer, 0, count)
                                    available += count
                                    monitor.notifyAll()
                                }
                            }
                        }
                    }
                    synchronized(monitor) {
                        checkFailure()
                        if (available == 0L || (expected >= 0 && available != expected)) {
                            throw IOException("Descarga de audio incompleta")
                        }
                        file = publish(partial)
                        length = available
                        complete = true
                        monitor.notifyAll()
                    }
                }
            } catch (e: Exception) {
                synchronized(monitor) {
                    if (failure == null) failure = e as? IOException ?: IOException("Descarga interrumpida", e)
                    monitor.notifyAll()
                }
            } finally {
                partial.delete()
            }
        }
    }

    fun awaitLength(closed: () -> Boolean): Long = synchronized(monitor) {
        while (!headers) {
            checkReader(closed)
            waitForBytes()
        }
        checkReader(closed)
        length
    }

    fun read(position: Long, buffer: ByteArray, offset: Int, count: Int,
        closed: () -> Boolean): Int = synchronized(monitor) {
        while (position >= available && !complete) {
            checkReader(closed)
            waitForBytes()
        }
        checkReader(closed)
        if (position >= available) return@synchronized -1
        RandomAccessFile(file, "r").use { input ->
            input.seek(position)
            input.read(buffer, offset, minOf(count.toLong(), available - position).toInt())
        }
    }

    fun cancel() {
        val request = synchronized(monitor) {
            if (!complete && failure == null) failure = IOException("Descarga cancelada")
            monitor.notifyAll()
            call
        }
        request?.cancel()
    }

    fun wakeReaders() = synchronized(monitor) { monitor.notifyAll() }

    private fun checkFailure() { failure?.let { throw it } }
    private fun checkReader(closed: () -> Boolean) {
        if (closed()) throw InterruptedIOException("Lectura cerrada")
        checkFailure()
    }
    private fun waitForBytes() {
        try { monitor.wait() } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw InterruptedIOException("Lectura interrumpida")
        }
    }
}
