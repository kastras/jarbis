package com.jarbis.alexa.music

import android.net.Uri

/** Pista resuelta y lista para reproducir (local o stream). */
data class MusicTrack(
    val videoId: String,
    val title: String,
    val uri: Uri,
)

/**
 * Orquesta el Módulo Música: resuelve una búsqueda a una pista reproducible,
 * usando primero la caché local y, si no, Piped API (con descarga a caché).
 * La cadena de "música parecida" la sigue el servicio de reproducción.
 */
class MusicController(private val cache: CacheManager, private val piped: PipedClient = PipedClient()) {

    val store: CacheStore get() = cache.store()

    /**
     * Resuelve "pon X": cacheado local directo; si no, búsqueda Piped +
     * descarga a caché. Devuelve la pista lista, o null si no se pudo.
     */
    suspend fun resolve(query: String): MusicTrack? {
        cache.store().bestLocalMatch(query)?.let { cached ->
            val f = cache.fileFor(cached)
            return MusicTrack(cached.videoId, cached.title, Uri.fromFile(f))
        }
        if (query.isNotBlank()) {
            val item = piped.search(query) ?: return null
            val videoId = PipedClient.videoOf(item) ?: return null
            val audio = piped.audioStream(videoId) ?: return null
            val file = cache.obtain(videoId, item.title, audio.url) ?: return null
            return MusicTrack(videoId, item.title, Uri.fromFile(file))
        }
        return null
    }

    /**
     * Pide la siguiente "parecida" para encadenar: `next/{videoId}` → primera
     * relacionada descargable. Sin red fallback a la mejor canción cacheada.
     */
    suspend fun nextLike(videoId: String): MusicTrack? {
        val related = try {
            piped.related(videoId)
        } catch (_: Exception) {
            emptyList()
        }
        for (item in related) {
            val id = PipedClient.videoOf(item) ?: continue
            val audio = try { piped.audioStream(id) } catch (_: Exception) { null } ?: continue
            val file = cache.obtain(id, item.title, audio.url) ?: continue
            return MusicTrack(id, item.title, Uri.fromFile(file))
        }
        // fallback offline: la canción mejor cacheada que no sea la actual
        val cached = store.mostPlayed()
        if (cached != null && cached.videoId != videoId) {
            val f = cache.fileFor(cached)
            return MusicTrack(cached.videoId, cached.title, Uri.fromFile(f))
        }
        return cache.store().list().firstOrNull { it.videoId != videoId }?.let {
            MusicTrack(it.videoId, it.title, Uri.fromFile(cache.fileFor(it)))
        }
    }
}