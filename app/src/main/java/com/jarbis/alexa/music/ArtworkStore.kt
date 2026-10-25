package com.jarbis.alexa.music

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Imágenes opcionales: entrada acotada, JPEG de 512 px y publicación atómica. */
object ArtworkStore {
    private const val MAX_INPUT = 4 * 1024 * 1024
    private const val MAX_IMAGE = 256 * 1024
    private val client = OkHttpClient.Builder().callTimeout(8, TimeUnit.SECONDS).build()

    fun key(id: String): String = MessageDigest.getInstance("SHA-256")
        .digest(id.toByteArray()).joinToString("") { "%02x".format(it) }

    suspend fun download(url: String, directory: File, id: String): File? = try {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body ?: return null
            if (body.contentLength() > MAX_INPUT) return null
            val bytes = body.byteStream().use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > MAX_INPUT) return null
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            currentCoroutineContext().ensureActive()
            save(bytes, directory, id)
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        null
    }

    fun save(bytes: ByteArray, directory: File, id: String): File? {
        if (bytes.size > MAX_INPUT) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 512) inSampleSize *= 2
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        val output = ByteArrayOutputStream()
        try { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output) } finally { bitmap.recycle() }
        if (output.size() > MAX_IMAGE) return null
        directory.mkdirs()
        val file = File(directory, "${key(id)}.jpg")
        val partial = File(directory, "${file.name}.part")
        try {
            partial.writeBytes(output.toByteArray())
            return file.takeIf { partial.renameTo(it) }
        } finally { partial.delete() }
    }
}
