package com.jarbis.alexa.music

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CancellationException

/** Pista resuelta y lista para reproducir (local o stream). */
data class MusicTrack(
    val videoId: String,
    val title: String,
    val uri: Uri,
    val artist: String? = null,
    val artworkUri: Uri? = null,
)

/**
 * Orquesta el Módulo Música: resuelve una búsqueda a una pista reproducible,
 * usando primero la caché local y, si no, los orígenes de música en orden
 * ([MusicResolvers]): NewPipe primero y Piped de respaldo. Devuelve la URL sin
 * esperar la descarga; el servicio reproduce y guarda una sola transferencia.
 */
class MusicController(
    private val cache: CacheManager,
    private val resolvers: List<MusicResolver> = MusicResolvers.all(),
) {

    val store: CacheStore get() = cache.store()

    /** Búsqueda manual: nunca sustituye los resultados por una canción de caché. */
    suspend fun searchResults(query: String): List<ResolvedTrack> {
        if (query.isBlank()) return emptyList()
        for (resolver in resolvers) {
            val results = resolver.searchResults(query).filter { it.videoId.isNotBlank() }
            if (results.isNotEmpty()) return results.distinctBy { it.videoId }
        }
        return emptyList()
    }

    /** Reproduce exactamente la selección, sin repetir la búsqueda por título. */
    suspend fun resolveSelection(selection: ResolvedTrack): MusicTrack? {
        store.find(selection.videoId)?.let { cached ->
            cache.isAvailable(cached).takeIf { it }?.let {
                return cache.trackFor(cached)
            }
        }
        for (resolver in resolvers) {
            val url = resolver.audioUrlFor(selection.videoId) ?: continue
            return cache.remoteTrack(selection, url)
        }
        return null
    }

    /**
     * Resuelve "pon X": primero la caché local SOLO si hay match real de título;
     * si no, busca en los orígenes (NewPipe primero, Piped de respaldo) y devuelve
     * una pista remota progresiva. Si no hay red ni match (offline total), cae a
     * la canción más cacheada como degradación. Devuelve null si no hay nada.
     * Nunca lanza: cualquier fallo de red vuelve null.
     */
    suspend fun resolve(query: String): MusicTrack? = try {
        if (query.isBlank()) {
            null
        } else {
            cache.store().bestLocalMatch(query)?.let { cached ->
                cache.trackFor(cached)
            } ?: resolveRemote(query)
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Log.w(TAG, "No se pudo resolver «$query»: ${e.message}")
        null
    }

    private suspend fun resolveRemote(query: String): MusicTrack? {
        for (resolver in resolvers) {
            val found = resolver.search(query) ?: continue
            if (found.videoId.isBlank()) continue
            val audioUrl = resolver.audioUrlFor(found.videoId) ?: continue
            return cache.remoteTrack(found, audioUrl)
        }
        // Todos los orígenes fallaron (sin red): degradación a lo cacheado más oído.
        return try {
            store.mostPlayed()?.let(cache::trackFor)
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
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    continue
                } ?: continue
                store.find(item.videoId)?.let { cached ->
                    if (cache.isAvailable(cached)) return cache.trackFor(cached)
                }
                return cache.remoteTrack(item, audioUrl)
            }
        }
        return try {
            val cached = store.mostPlayed()
            if (cached != null && cached.videoId != videoId) {
                cache.trackFor(cached)
            } else {
                cache.store().list().firstOrNull { it.videoId != videoId }?.let {
                    cache.trackFor(it)
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
