package com.jarbis.alexa.music

import android.net.Uri
import android.util.Log

/** Pista resuelta y lista para reproducir (local o stream). */
data class MusicTrack(
    val videoId: String,
    val title: String,
    val uri: Uri,
)

/**
 * Orquesta el Módulo Música: resuelve una búsqueda a una pista reproducible,
 * usando primero la caché local y, si no, los orígenes de música en orden
 * ([MusicResolvers]): NewPipe primero y Piped de respaldo. Cada origen descarga
 * el audio a la caché. La cadena de "música parecida" la sigue el servicio.
 */
class MusicController(
    private val cache: CacheManager,
    private val resolvers: List<MusicResolver> = MusicResolvers.all(),
) {

    val store: CacheStore get() = cache.store()

    /**
     * Resuelve "pon X": primero la caché local SOLO si hay match real de título;
     * si no, busca en los orígenes (NewPipe primero, Piped de respaldo), descarga
     * a caché y devuelve la pista. Si no hay red ni match (offline total), cae a
     * la canción más cacheada como degradación. Devuelve null si no hay nada.
     * Nunca lanza: cualquier fallo de red vuelve null.
     */
    suspend fun resolve(query: String): MusicTrack? = try {
        if (query.isBlank()) {
            null
        } else {
            cache.store().bestLocalMatch(query)?.let { cached ->
                MusicTrack(cached.videoId, cached.title, Uri.fromFile(cache.fileFor(cached)))
            } ?: resolveRemote(query)
        }
    } catch (e: Exception) {
        Log.w(TAG, "No se pudo resolver «$query»: ${e.message}")
        null
    }

    private suspend fun resolveRemote(query: String): MusicTrack? {
        for (resolver in resolvers) {
            val found = resolver.search(query) ?: continue
            if (found.videoId.isBlank()) continue
            val audioUrl = resolver.audioUrlFor(found.videoId) ?: continue
            val file = cache.obtain(found.videoId, found.title, audioUrl) ?: continue
            return MusicTrack(found.videoId, found.title, Uri.fromFile(file))
        }
        // Todos los orígenes fallaron (sin red): degradación a lo cacheado más oído.
        return try {
            store.mostPlayed()?.let { MusicTrack(it.videoId, it.title, Uri.fromFile(cache.fileFor(it))) }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Pide la siguiente "parecida" para encadenar: recorre los orígenes y usa la
     * primera relacionada descargable. Sin resultado, fallback offline: la
     * canción mejor cacheada que no sea la actual. Nunca lanza.
     */
    suspend fun nextLike(videoId: String): MusicTrack? {
        for (resolver in resolvers) {
            for (item in resolver.related(videoId)) {
                if (item.videoId.isBlank() || item.videoId == videoId) continue
                val audioUrl = try {
                    resolver.audioUrlFor(item.videoId)
                } catch (_: Exception) {
                    continue
                } ?: continue
                val file = cache.obtain(item.videoId, item.title, audioUrl) ?: continue
                return MusicTrack(item.videoId, item.title, Uri.fromFile(file))
            }
        }
        return try {
            val cached = store.mostPlayed()
            if (cached != null && cached.videoId != videoId) {
                MusicTrack(cached.videoId, cached.title, Uri.fromFile(cache.fileFor(cached)))
            } else {
                cache.store().list().firstOrNull { it.videoId != videoId }?.let {
                    MusicTrack(it.videoId, it.title, Uri.fromFile(cache.fileFor(it)))
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val TAG = "MusicController"
    }
}