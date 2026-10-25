package com.jarbis.alexa.music

/**
 * Mismo contrato de origen de música pero contra la API de Piped, como
 * respaldo cuando NewPipe falle. Reutiliza [PipedClient].
 */
class PipedMusicResolver(private val piped: PipedClient = PipedClient()) : MusicResolver {

    override suspend fun search(query: String): ResolvedTrack? {
        val item = piped.search(query) ?: return null
        val id = PipedClient.videoOf(item) ?: return null
        return ResolvedTrack(id, item.title, item.uploaderName, item.thumbnail)
    }

    override suspend fun audioUrlFor(videoId: String): String? =
        piped.audioStream(videoId)?.url?.takeIf { it.isNotBlank() }

    override suspend fun searchResults(query: String): List<ResolvedTrack> =
        piped.searchResults(query).mapNotNull { item ->
            PipedClient.videoOf(item)?.let { ResolvedTrack(it, item.title, item.uploaderName, item.thumbnail) }
        }.distinctBy { it.videoId }.take(30)

    override suspend fun related(videoId: String): List<ResolvedTrack> =
        piped.related(videoId).mapNotNull { item ->
            PipedClient.videoOf(item)?.takeIf { it.isNotBlank() }?.let { ResolvedTrack(it, item.title, item.uploaderName, item.thumbnail) }
        }
}
