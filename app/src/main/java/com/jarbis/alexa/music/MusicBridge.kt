package com.jarbis.alexa.music

import android.content.Context

/**
 * Mantiene el estado compartido de Música entre el motor del asistente y el
 * [MusicPlayerService]: caché, resolución de canciones y el resolver de
 * "música parecida" para encadenar al terminar cada tema.
 */
object MusicBridge {

    @Volatile
    private var instance: MusicBridgeState? = null

    fun state(context: Context): MusicBridgeState {
        val existing = instance
        if (existing != null) return existing
        val created = MusicBridgeState(context.applicationContext)
        instance = created
        return created
    }

    fun replace(state: MusicBridgeState) {
        instance = state
    }
}

class MusicBridgeState(private val appContext: Context) {

    val cache: CacheManager by lazy { CacheManager(appContext) }
    val controller: MusicController by lazy { MusicController(cache) }
    val client: PipedClient by lazy { PipedClient() }

    /** Si hay algo reproduciéndose ahora mismo (lo marca el servicio). */
    @Volatile
    var isPlaying: Boolean = false

    /** Resolver de "música parecida" que inyecta el motor del asistente. */
    @Volatile
    var nextResolver: (suspend (String) -> MusicTrack?)? = null

    /** Resuelve una búsqueda hablada a una pista reproducible. */
    suspend fun resolve(query: String): MusicTrack? = controller.resolve(query)
}