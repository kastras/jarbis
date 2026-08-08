package com.jarbis.alexa.music

import android.content.Context
import com.jarbis.alexa.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/** Gestiona la caché local de música: descarga los streams de audio a
 * `externalFilesDir("music_cache")` (sin permiso de almacenamiento) y delega
 * la cuota/índice en [CacheStore]. El límite es [AppConfig.MUSIC_CACHE_MAX_BYTES].
 */
class CacheManager @JvmOverloads constructor(
    context: Context,
    private val rootDir: File = File(context.getExternalFilesDir(null) ?: context.filesDir, DIR_NAME),
) {

    private val store = CacheStore(rootDir)
    private val okHttp = OkHttpClient()

    fun store() = store

    /**
     * Descarga el stream y lo da de alta en la caché (playCount=1). Si ya
     * está cacheado, devuelve el fichero existente marcando la reproducción.
     * Devuelve el fichero listo para reproducir, o null si falla la descarga.
     */
    suspend fun obtain(videoId: String, title: String, audioUrl: String): File? {
        store.find(videoId)?.let { existing ->
            store.recordPlayed(videoId)
            val f = File(rootDir, existing.fileName)
            return f.takeIf { it.exists() }
        }

        return withContext(Dispatchers.IO) {
            try {
                val response = okHttp.newCall(Request.Builder().url(audioUrl).build()).execute()
                response.use { resp ->
                    if (!resp.isSuccessful) return@withContext null
                    val ext = extensionOf(audioUrl)
                    val file = File(rootDir, "$videoId$ext")
                    resp.body?.byteStream()?.use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    }
                    if (!file.exists() || file.length() == 0L) return@withContext null
                    store.put(CachedSong(videoId = videoId, fileName = file.name, title = title, playCount = 1))
                    store.evictToFit(AppConfig.MUSIC_CACHE_MAX_BYTES, protectedVideoId = videoId)
                    file
                }
            } catch (_: IOException) {
                null
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun extensionOf(url: String): String = when {
        url.contains(".m4a") -> ".m4a"
        url.contains(".opus") -> ".opus"
        url.contains(".webm") -> ".webm"
        else -> ".m4a"
    }

    /** Ruta del fichero cacheado de una entrada del índice. */
    fun fileFor(song: CachedSong): File {
        if (!rootDir.exists()) rootDir.mkdirs()
        return File(rootDir, song.fileName)
    }

    companion object {
        const val DIR_NAME = "music_cache"
    }
}