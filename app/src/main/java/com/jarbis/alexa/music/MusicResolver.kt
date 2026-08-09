package com.jarbis.alexa.music

/** Búsqueda resuelta en crudo (antes de descargar a caché). */
data class ResolvedTrack(val videoId: String, val title: String)

/**
 * Origen de "dónde sacar música": busca una canción, devuelve la URL de
 * audio reproducible de un vídeo y las canciones relacionadas para encadenar.
 * Cualquier método puede devolver null/vacío ante fallos; nunca debe lanzar.
 */
interface MusicResolver {
    suspend fun search(query: String): ResolvedTrack?

    suspend fun audioUrlFor(videoId: String): String?

    suspend fun related(videoId: String): List<ResolvedTrack>
}

/** Lista por defecto de orígenes: NewPipe primero, Piped como respaldo. */
object MusicResolvers {
    fun all(): List<MusicResolver> = listOf(
        NewPipeMusicResolver(),
        PipedMusicResolver(),
    )
}