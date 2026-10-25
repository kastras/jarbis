package com.jarbis.alexa.music

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler
import org.schabi.newpipe.extractor.search.SearchExtractor
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/**
 * Resolutor de música con NewPipeExtractor (sin servidor de terceros): extrae
 * directamente de YouTube búsqueda de música (filtro `music_songs`), streams
 * de audio y relacionados. Cualquier fallo vuelve null/vacío sin lanzar.
 */
class NewPipeMusicResolver : MusicResolver {

    override suspend fun search(query: String): ResolvedTrack? = searchResults(query).firstOrNull()

    override suspend fun searchResults(query: String): List<ResolvedTrack> = withContext(Dispatchers.IO) {
        runCatching {
            val service = youtubeService()
            val handler: SearchQueryHandler =
                service.getSearchQHFactory().fromQuery(query, listOf(MUSIC_FILTER), "")
            val extractor: SearchExtractor = service.getSearchExtractor(handler)
            // fetchPage() dispara la petición y rellena initialData; sin ella
            // getInitialPage() devuelve null (NPE) porque solo se rellena en onFetchPage().
            extractor.fetchPage()
            extractor.getInitialPage().getItems().mapNotNull { item ->
                youtubeIdOf(item.url)?.let { ResolvedTrack(it, item.name,
                    (item as? StreamInfoItem)?.uploaderName?.takeIf(String::isNotBlank),
                    item.thumbnails.lastOrNull()?.url) }
            }.distinctBy { it.videoId }.take(30)
        }.getOrElse { e ->
            if (e is CancellationException) throw e
            Log.w(TAG, "Búsqueda NewPipe «$query» falló", e)
            emptyList()
        }
    }

    override suspend fun audioUrlFor(videoId: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val info = StreamInfo.getInfo(youtubeService(), watchUrl(videoId))
            val best = bestAudioStream(info.audioStreams)
            if (best == null) {
                Log.w(TAG, "Audio de $videoId: sin streams de audio (${info.audioStreams.size} en total, ${info.videoStreams.size} vídeo)")
            }
            best?.content
        }.getOrElse { e ->
            if (e is CancellationException) throw e
            Log.w(TAG, "Audio de $videoId falló: ${e.message}", e)
            null
        }
    }

    override suspend fun related(videoId: String): List<ResolvedTrack> = withContext(Dispatchers.IO) {
        runCatching {
            val info = StreamInfo.getInfo(youtubeService(), watchUrl(videoId))
            info.relatedItems.mapNotNull { item ->
                youtubeIdOf(item.url)?.takeIf { it.isNotBlank() }?.let { ResolvedTrack(it, item.name,
                    (item as? StreamInfoItem)?.uploaderName?.takeIf(String::isNotBlank),
                    item.thumbnails.lastOrNull()?.url) }
            }
        }.getOrElse { e ->
            if (e is CancellationException) throw e
            Log.w(TAG, "Relacionadas de $videoId fallaron: ${e.message}")
            emptyList()
        }
    }

    private fun youtubeService(): StreamingService = NewPipe.getService(YOUTUBE_SERVICE_ID)

    /** Mejor stream de audio: prefiere el de mayor bitrate conocido. */
    private fun bestAudioStream(streams: List<AudioStream>): AudioStream? =
        streams.maxByOrNull { it.averageBitrate.let { b -> if (b > 0) b else Int.MIN_VALUE } }

    companion object {
        private const val TAG = "NewPipeMusicResolver"
        private const val YOUTUBE_SERVICE_ID = 0
        private const val MUSIC_FILTER = "music_songs"

        private fun watchUrl(videoId: String) = "https://www.youtube.com/watch?v=$videoId"

        /** Extrae el id de un url de YouTube del tipo watch/shorts/youtu.be. */
        fun youtubeIdOf(url: String?): String? {
            if (url.isNullOrBlank()) return null
            val id = when {
                url.contains("/watch?v=") -> url.substringAfter("/watch?v=").substringBefore("&")
                url.contains("/shorts/") -> url.substringAfter("/shorts/").substringBefore("?")
                url.contains("youtu.be/") -> url.substringAfter("youtu.be/").substringBefore("?")
                else -> url.substringAfterLast('/').substringBefore('?')
            }?.trim()
            return id?.takeIf { it.isNotBlank() && it.length >= 8 }
        }
    }
}
