package com.jarbis.alexa.music

import android.content.Context
import android.net.Uri
import android.media.MediaMetadataRetriever
import java.io.File
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

enum class MusicSource { FOLDER, DOWNLOADED }

data class LibrarySong(val track: MusicTrack, val source: MusicSource)

/** Lee las URI del selector de Android sin copiar ni modificar la carpeta. */
class LocalMusicLibrary(private val context: Context, private val cache: CacheManager) {
    suspend fun downloaded(): List<LibrarySong> = withContext(Dispatchers.IO) {
        cache.store().list().mapNotNull { song ->
            cache.isAvailable(song).takeIf { it }?.let {
                LibrarySong(cache.trackFor(song), MusicSource.DOWNLOADED)
            }
        }.sortedBy { it.track.title.lowercase() }
    }

    suspend fun folder(tree: Uri): List<LibrarySong> = withContext(Dispatchers.IO) {
        val result = mutableListOf<LibrarySong>()
        val pending = ArrayDeque<String>()
        val visited = mutableSetOf<String>()
        pending.add(DocumentsContract.getTreeDocumentId(tree))
        while (pending.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val parent = pending.removeFirst()
            if (!visited.add(parent)) continue
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent)
            val projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE)
            val cursor = context.contentResolver.query(children, projection, null, null, null)
                ?: throw java.io.IOException("No se puede leer la carpeta")
            cursor.use {
                while (it.moveToNext()) {
                    currentCoroutineContext().ensureActive()
                    val id = it.getString(0)
                    val name = it.getString(1) ?: id
                    val mime = it.getString(2).orEmpty()
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        pending.add(id)
                    } else if (mime.startsWith("audio/") || name.substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS) {
                        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                        result.add(LibrarySong(readMetadata(uri, name.substringBeforeLast('.')), MusicSource.FOLDER))
                    }
                }
            }
        }
        result.distinctBy { it.track.uri }.sortedBy { it.track.title.lowercase() }
    }

    internal fun readMetadata(uri: Uri, fallback: String): MusicTrack {
        var title = fallback
        var artist: String? = null
        var artwork: Uri? = null
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf(String::isNotBlank) ?: fallback
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf(String::isNotBlank)
            val directory = File(context.cacheDir, "folder_artwork")
            retriever.embeddedPicture?.let { bytes ->
                artwork = ArtworkStore.save(bytes, directory, uri.toString())?.let(Uri::fromFile)
            }
            // Caché regenerable de imágenes locales: como máximo 16 MiB.
            var size = directory.listFiles()?.sumOf { it.length() } ?: 0L
            directory.listFiles()?.sortedBy { it.lastModified() }?.forEach { file ->
                if (size > 16 * 1024 * 1024) { val length = file.length(); if (file.delete()) size -= length }
            }
        } catch (_: Exception) {
            // Los metadatos opcionales no impiden usar el archivo de audio.
        } finally { runCatching { retriever.release() } }
        return MusicTrack(uri.toString(), title, uri, artist, artwork)
    }

    companion object {
        private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "ogg", "opus", "flac", "wav", "webm")
    }
}
