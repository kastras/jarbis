package com.jarbis.alexa.music

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** Entrada del índice de canciones cacheadas en local. */
@Serializable
data class CachedSong(
    val videoId: String,
    val fileName: String,
    val title: String,
    @SerialName("playCount") val playCount: Int = 0,
    @SerialName("lastPlayed") val lastPlayedEpochMillis: Long = 0L,
    val artist: String? = null,
    val artworkFileName: String? = null,
)

@Serializable
data class CacheIndex(val songs: MutableList<CachedSong> = mutableListOf())

/**
 * Índice persistente de la caché de música (JSON con kotlinx-serialization,
 * sin Room). Responsable de la cuota: cuando el total supera el máximo se
 * evictúan las canciones con menor playCount (desempate: lastPlayed más
 * antigua). La canción en reproducción nunca se evictúa.
 */
class CacheStore(
    private val rootDir: File,
    private val indexFile: File = File(rootDir, "index.json"),
    private val contentResolver: ContentResolver? = null,
) {

    private val json = Json { ignoreUnknownKeys = true }
    private var index: CacheIndex = load()

    private fun load(): CacheIndex {
        if (!indexFile.exists()) return CacheIndex()
        return try {
            json.decodeFromString(CacheIndex.serializer(), indexFile.readText())
        } catch (_: Exception) {
            CacheIndex()
        }
    }

    @Synchronized fun list(): List<CachedSong> = index.songs.toList()

    @Synchronized fun find(videoId: String): CachedSong? = index.songs.firstOrNull { it.videoId == videoId }

    @Synchronized fun size(): Int = index.songs.size

    /** Bytes ocupados por los ficheros cacheados que todavía existen. */
    @Synchronized fun totalBytes(): Long =
        index.songs.sumOf { song ->
            resourceSize(song.fileName) + (song.artworkFileName?.let(::resourceSize) ?: 0L)
        }

    fun uriFor(song: CachedSong): Uri = resourceUri(song.fileName)
    fun artworkUriFor(song: CachedSong): Uri? = song.artworkFileName?.let(::resourceUri)
    fun exists(song: CachedSong): Boolean = resourceExists(song.fileName)

    /** Registra (o actualiza) una canción recién descargada. */
    @Synchronized fun put(song: CachedSong) {
        val existing = index.songs.indexOfFirst { it.videoId == song.videoId }
        if (existing >= 0) index.songs[existing] = song else index.songs.add(song)
        persist()
    }

    @Synchronized fun replaceAll(songs: List<CachedSong>) {
        index = CacheIndex(songs.toMutableList())
        persist()
    }

    /** Actualiza una canción existente marcando que acaba de reproducirse. */
    @Synchronized fun recordPlayed(videoId: String, atEpochMillis: Long = System.currentTimeMillis()) {
        val idx = index.songs.indexOfFirst { it.videoId == videoId }
        if (idx < 0) return
        val song = index.songs[idx]
        index.songs[idx] = song.copy(playCount = song.playCount + 1, lastPlayedEpochMillis = atEpochMillis)
        persist()
    }

    /**
     * Búsqueda local difusa para "pon X": devuelve la canción cacheada cuyo
     * título se parece más a [query] (ver [FuzzyMatch]), tolerante a typos y
     * palabras parciales. Devuelve null si no hay ninguna parecida, para que
     * el controlador recurra a los orígenes remotos. El fallback a [mostPlayed]
     * es responsabilidad del llamante (offline).
     */
    @Synchronized fun bestLocalMatch(query: String): CachedSong? {
        if (query.isBlank()) return null
        val normalizedQuery = FuzzyMatch.normalize(query)
        val matches = index.songs.mapNotNull { song ->
            val score = FuzzyMatch.matchScore(song.title, normalizedQuery)
            if (score > 0) song to score else null
        }
        return matches.maxWithOrNull(
            compareBy({ it.second }, { it.first.playCount }, { it.first.lastPlayedEpochMillis })
        )?.first
    }

    /** Mejor canción cacheada (por playCount, desempate lastPlayed) para fallback offline. */
    @Synchronized fun mostPlayed(): CachedSong? =
        index.songs.maxWithOrNull(compareBy({ it.playCount }, { it.lastPlayedEpochMillis }))

    /**
     * Evictúa hasta encajar bajo [maxBytes]. Nunca toca [protectedVideoId]
     * (la canción que suena). Devuelve las entradas eliminadas.
     */
    @Synchronized fun evictToFit(maxBytes: Long, protectedVideoId: String? = null,
        protectedVideoIds: Set<String> = emptySet()): List<CachedSong> {
        val removed = mutableListOf<CachedSong>()
        var overflowing = totalBytes() > maxBytes
        while (overflowing) {
            val candidates = index.songs
                .filter { resourceExists(it.fileName) && it.videoId != protectedVideoId && it.videoId !in protectedVideoIds }
                .sortedWith(compareBy({ it.playCount }, { it.lastPlayedEpochMillis }))
            val victim = candidates.firstOrNull() ?: break
            removeFile(victim)
            index.songs.remove(victim)
            persist()
            removed.add(victim)
            overflowing = totalBytes() > maxBytes
        }
        return removed
    }

    private fun removeFile(song: CachedSong) {
        deleteResource(song.fileName)
        song.artworkFileName?.let(::deleteResource)
    }

    private fun resourceUri(name: String): Uri = if (name.startsWith("content://") || name.startsWith("file://")) {
        Uri.parse(name)
    } else Uri.fromFile(File(rootDir, name))

    private fun fileOf(name: String): File? = when {
        name.startsWith("content://") -> null
        name.startsWith("file://") -> Uri.parse(name).path?.let(::File)
        else -> File(rootDir, name)
    }

    private fun resourceExists(name: String): Boolean {
        val file = fileOf(name)
        if (file != null) return file.isFile
        return runCatching { contentResolver?.openAssetFileDescriptor(resourceUri(name), "r")?.use { true } ?: false }
            .getOrDefault(false)
    }

    private fun resourceSize(name: String): Long {
        val file = fileOf(name)
        if (file != null) return file.takeIf { it.isFile }?.length() ?: 0L
        return runCatching {
            contentResolver?.openAssetFileDescriptor(resourceUri(name), "r")?.use { descriptor ->
                descriptor.length.takeIf { it >= 0 } ?: querySize(resourceUri(name))
            } ?: 0L
        }.getOrDefault(0L)
    }

    private fun querySize(uri: Uri): Long = runCatching {
        contentResolver?.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0).coerceAtLeast(0L) else 0L
        } ?: 0L
    }.getOrDefault(0L)

    private fun deleteResource(name: String) {
        val file = fileOf(name)
        if (file != null) file.delete() else runCatching { contentResolver?.delete(resourceUri(name), null, null) }
    }

    @Synchronized fun remove(videoId: String) {
        val song = find(videoId) ?: return
        removeFile(song)
        index.songs.removeAll { it.videoId == videoId }
        persist()
    }

    private fun persist() {
        if (!rootDir.exists()) rootDir.mkdirs()
        indexFile.writeText(json.encodeToString(CacheIndex.serializer(), index))
    }
}
