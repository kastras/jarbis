package com.jarbis.alexa.music

import android.content.Context
import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.jarbis.alexa.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect
import com.jarbis.alexa.settings.SettingsStore
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Gestiona las descargas, el índice privado y la cuota de la música.
 */
class CacheManager @JvmOverloads constructor(
    context: Context,
    private val rootDir: File = File(context.getExternalFilesDir(null) ?: context.filesDir, DIR_NAME),
) {

    private val appContext = context.applicationContext
    private val resolver: ContentResolver = appContext.contentResolver
    private val settings = SettingsStore(appContext)
    private val store = CacheStore(rootDir, contentResolver = resolver)
    private val okHttp = OkHttpClient()
    private val downloadMutex = Mutex()
    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val streamingClient = OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).build()
    private val downloads = mutableMapOf<String, GrowingDownload>()
    private val activeFiles = java.util.concurrent.ConcurrentHashMap<String, File>()
    private val artworkJobs = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Job>()
    private val remoteTracks = mutableMapOf<String, MusicTrack>()
    private val mutableRevision = MutableStateFlow(0L)
    val revision = mutableRevision.asStateFlow()
    @Volatile private var maxCacheBytes = AppConfig.MUSIC_CACHE_MAX_BYTES
    @Volatile private var downloadTreeUri: String? = null
    private val pendingDeletes = mutableSetOf<String>()
    private val deferredMigrationCleanup = mutableMapOf<String, List<String>>()
    private val settingsReady = CountDownLatch(1)
    private val storageLock = Object()
    @Volatile private var migratingFolder = false

    init {
        // Solo hay un gestor compartido en producción. Un proceso nuevo nunca
        // trata como descargados los temporales que dejó un cierre abrupto.
        rootDir.listFiles()?.filter { it.name.endsWith(".part") || it.name.startsWith("active-") }?.forEach { it.delete() }
        downloadScope.launch {
            settings.settings.collect { value ->
                maxCacheBytes = value.musicCacheMaxBytes.coerceAtLeast(AppConfig.MIN_MUSIC_CACHE_BYTES)
                downloadTreeUri = value.downloadFolderUri
                settingsReady.countDown()
            }
        }
    }

    @Volatile var playingVideoId: String? = null
        set(value) {
            val previous = field
            field = value
            if (previous != value) {
                synchronized(pendingDeletes) {
                    if (previous != null && previous in pendingDeletes) {
                        pendingDeletes.remove(previous)
                        synchronized(storageLock) {
                            awaitFolderMigrationLocked()
                            store.remove(previous)
                        }
                        mutableRevision.update { it + 1 }
                    }
                }
                if (previous != null) {
                    deferredMigrationCleanup.remove(previous)?.forEach(::deleteResource)
                }
                enforceLimit()
            }
        }

    fun store() = store

    /** Resuelve identidad/metadatos sin iniciar todavía ninguna transferencia. */
    @Synchronized fun remoteTrack(selection: ResolvedTrack, url: String): MusicTrack {
        val track = MusicTrack(selection.videoId, selection.title, Uri.parse(url),
            selection.artist, selection.artworkUrl?.let(Uri::parse))
        remoteTracks[track.videoId] = track
        if (remoteTracks.size > 128) remoteTracks.keys.firstOrNull { it != track.videoId }?.let { remoteTracks.remove(it) }
        return track
    }

    @Synchronized internal fun openDownload(videoId: String, url: String): GrowingDownload =
        downloads.getOrPut(videoId) {
            ensureDownloadFolderAvailable()
            val track = remoteTracks[videoId] ?: MusicTrack(videoId, videoId, Uri.parse(url))
            val safeId = videoId.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(100)
            rootDir.mkdirs()
            val partial = File.createTempFile("stream-$safeId-", ".part", rootDir)
            GrowingDownload(streamingClient, downloadScope, url, partial, reserve = { bytes ->
                ensureDownloadFolderAvailable()
                synchronized(storageLock) {
                    awaitFolderMigrationLocked()
                    if (bytes > maxCacheBytes) throw IOException("Audio mayor que el límite de disco")
                    store.evictToFit(maxCacheBytes - bytes,
                        protectedVideoIds = setOfNotNull(playingVideoId))
                    if (store.totalBytes() + bytes > maxCacheBytes) {
                        throw IOException("No hay espacio dentro del límite de disco")
                    }
                }
            }, publish = { completed ->
                val published = synchronized(storageLock) {
                    awaitFolderMigrationLocked()
                    val result = publishAudio(completed, "$safeId${extensionOf(url)}", videoId)
                    try {
                        store.put(CachedSong(videoId, result.cacheName, track.title, playCount = 1,
                            lastPlayedEpochMillis = System.currentTimeMillis(), artist = track.artist))
                    } catch (e: Exception) {
                        result.activeFile.delete()
                        if (result.cacheName.startsWith("content://")) deleteDocument(result.cacheName)
                        throw e
                    }
                    result
                }
                mutableRevision.update { it + 1 }
                if (track.artworkUri != null) {
                    artworkJobs[videoId] = downloadScope.launch {
                          val cover = ArtworkStore.download(track.artworkUri.toString(), rootDir, videoId)
                          currentCoroutineContext().ensureActive()
                          val song = store.find(videoId)
                          if (song == null) cover?.delete() else {
                              val artworkName = cover?.let { runCatching { publishArtwork(it, videoId) }.getOrNull() }
                              if (downloadTreeUri != null) cover?.delete()
                              store.put(song.copy(artworkFileName = artworkName))
                              store.evictToFit(maxCacheBytes, videoId,
                                  setOfNotNull(playingVideoId))
                            mutableRevision.update { it + 1 }
                        }
                    }
                }
                published.activeFile
            }).also { it.start() }
        }

    /** Se llama en cambio de canción/parada; cerrar un lector por un seek no cancela. */
    fun cancelDownloadsExcept(videoId: String? = null) {
        val cancelled = synchronized(this) {
            val old = downloads.filterKeys { it != videoId }.toList()
            downloads.keys.removeAll { it != videoId }
            old
        }
        cancelled.forEach { (id, download) ->
            download.cancel()
            activeFiles.remove(id)?.delete()
        }
      artworkJobs.keys.filter { it != videoId }.forEach { artworkJobs.remove(it)?.cancel() }
    }

    /**
     * Descarga el stream y lo da de alta en la caché (playCount=1). Si ya
     * está cacheado, devuelve el fichero existente marcando la reproducción.
     * Devuelve el fichero listo para reproducir, o null si falla la descarga.
     */
    suspend fun obtain(videoId: String, title: String, audioUrl: String,
        artist: String? = null, artworkUrl: String? = null): File? = downloadMutex.withLock {
        val file = obtainLocked(videoId, title, audioUrl) ?: return@withLock null
        withContext(Dispatchers.IO) {
            val song = store.find(videoId) ?: return@withContext
            val artwork = song.artworkFileName ?: artworkUrl?.let { url ->
                ArtworkStore.download(url, rootDir, videoId)?.let { cover ->
                    try { publishArtwork(cover, videoId) } finally { if (downloadTreeUri != null) cover.delete() }
                }
            }
            store.put(song.copy(artist = artist ?: song.artist, artworkFileName = artwork))
      store.evictToFit(maxCacheBytes, videoId, setOfNotNull(playingVideoId))
        }
        file
    }

    private suspend fun obtainLocked(videoId: String, title: String, audioUrl: String): File? {
        store.find(videoId)?.let { existing ->
            if (store.exists(existing)) {
                store.recordPlayed(videoId)
                if (!existing.fileName.startsWith("content://")) return fileFor(existing)
                val active = File(rootDir, "active-$videoId-${System.nanoTime()}.m4a")
                copyResourceToFile(existing.fileName, active)
                activeFiles[videoId] = active
                return active
            }
            store.remove(videoId)
        }

        return withContext(Dispatchers.IO) {
            try {
                ensureDownloadFolderAvailable()
                val response = okHttp.newCall(Request.Builder().url(audioUrl).build()).execute()
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        Log.w(TAG, "Descarga de $videoId ($audioUrl) falló: HTTP ${resp.code}")
                        return@withContext null
                    }
                    val ext = extensionOf(audioUrl)
                    if (!rootDir.exists()) rootDir.mkdirs()
                    val file = File(rootDir, "$videoId$ext")
                    val partial = File(rootDir, "$videoId$ext.part")
                    try {
                        val body = resp.body ?: return@withContext null
                        body.byteStream().use { input ->
                            partial.outputStream().use { output ->
                                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    output.write(buffer, 0, count)
                                }
                            }
                        }
                        currentCoroutineContext().ensureActive()
                        if (partial.length() == 0L || !partial.renameTo(file)) return@withContext null
                    } finally {
                        partial.delete()
                    }
                    if (!file.exists() || file.length() == 0L) return@withContext null
                    val published = if (downloadTreeUri == null) PublishedAudio(file, file.name)
                        else publishAudio(file, file.name, videoId)
                    store.put(CachedSong(videoId = videoId, fileName = published.cacheName, title = title, playCount = 1))
                    if (published.activeFile != file) file.delete()
          store.evictToFit(maxCacheBytes, protectedVideoId = videoId,
                        protectedVideoIds = setOfNotNull(playingVideoId))
                    published.activeFile
                }
            } catch (e: IOException) {
                Log.w(TAG, "Descarga de $videoId falló (IOException): ${e.message} / ${e.cause?.message}")
                null
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "Descarga de $videoId ($audioUrl) falló: ${e.message}")
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
        require(!song.fileName.startsWith("content://")) { "La canción está fuera del almacenamiento privado" }
        if (!rootDir.exists()) rootDir.mkdirs()
        return if (song.fileName.startsWith("file://")) File(Uri.parse(song.fileName).path!!) else File(rootDir, song.fileName)
    }

    fun uriFor(song: CachedSong): Uri = store.uriFor(song)

    fun isAvailable(song: CachedSong): Boolean = store.exists(song)

    fun trackFor(song: CachedSong) = MusicTrack(song.videoId, song.title, uriFor(song),
        song.artist, store.artworkUriFor(song)?.takeIf { uri -> resourceExists(uri) })

    suspend fun setCacheLimit(bytes: Long) {
        val safe = bytes.coerceAtLeast(AppConfig.MIN_MUSIC_CACHE_BYTES)
        settings.setMusicCacheMaxBytes(safe)
        maxCacheBytes = safe
        enforceLimit()
    }

    fun cacheLimitBytes(): Long = maxCacheBytes

    fun isDownloadFolderAvailable(): Boolean = runCatching { ensureDownloadFolderAvailable() }.isSuccess

    fun deleteDownloaded(videoId: String): Boolean {
        if (playingVideoId == videoId) {
            synchronized(pendingDeletes) { pendingDeletes.add(videoId) }
            return false
        }
        synchronized(storageLock) {
            awaitFolderMigrationLocked()
            store.remove(videoId)
        }
        mutableRevision.update { it + 1 }
        return true
    }

    fun isPendingDelete(videoId: String): Boolean = synchronized(pendingDeletes) { videoId in pendingDeletes }

    fun enforceLimit() {
        val removed = synchronized(storageLock) {
            awaitFolderMigrationLocked()
            store.evictToFit(maxCacheBytes, protectedVideoIds = setOfNotNull(playingVideoId))
        }
        if (removed.isNotEmpty()) mutableRevision.update { it + 1 }
    }

    suspend fun migrateDownloads(treeUri: Uri?) = withContext(Dispatchers.IO) {
        synchronized(storageLock) {
            check(!migratingFolder) { "Ya se está cambiando la carpeta de descargas" }
            migratingFolder = true
        }
        val tree = treeUri?.toString()
        val songs = store.list()
        val created = mutableListOf<Uri>()
        val createdFiles = mutableListOf<File>()
        val replacements = mutableListOf<CachedSong>()
        var indexReplaced = false
        try {
            if (treeUri != null) {
                // Comprueba permiso persistente y escritura antes de mover la biblioteca.
                val probe = DocumentsContract.createDocument(resolver, treeUri, "application/octet-stream", ".jarbis-write-test")
                    ?: throw IOException("La carpeta elegida no permite guardar archivos")
                created += probe
                deleteDocument(probe.toString())
                created.remove(probe)
            } else rootDir.mkdirs()
            for (song in songs) {
                val audioName = if (treeUri != null) {
                    createDocument(treeUri, "${song.videoId}.${song.fileName.substringAfterLast('.', "m4a")}", "audio/mp4").also {
                        created += it
                        copyResourceToDocument(song.fileName, it)
                    }.toString()
                } else {
                    val extension = song.fileName.substringAfterLast('.', "m4a").takeIf { it.length in 2..5 } ?: "m4a"
                    val name = "${song.videoId}.$extension"
                    val file = File(rootDir, name)
                    copyResourceToFile(song.fileName, file)
                    createdFiles += file
                    file.name
                }
                val artName = song.artworkFileName?.let { old ->
                    if (treeUri != null) {
                        createDocument(treeUri, "${song.videoId}-cover.jpg", "image/jpeg").also {
                            created += it
                            copyResourceToDocument(old, it)
                        }.toString()
                    } else {
                        val file = File(rootDir, "${song.videoId}-cover.jpg")
                        copyResourceToFile(old, file)
                        createdFiles += file
                        file.name
                    }
                }
                replacements += song.copy(fileName = audioName, artworkFileName = artName)
            }
            store.replaceAll(replacements)
            indexReplaced = true
            try {
                settings.setDownloadFolder(tree)
            } catch (e: Exception) {
                store.replaceAll(songs)
                indexReplaced = false
                throw e
            }
            downloadTreeUri = tree
            songs.forEach { old ->
                val oldNames = listOfNotNull(old.fileName, old.artworkFileName)
                if (old.videoId == playingVideoId) deferredMigrationCleanup[old.videoId] = oldNames
                else oldNames.forEach(::deleteResource)
            }
            mutableRevision.update { it + 1 }
        } catch (e: Exception) {
            if (indexReplaced) store.replaceAll(songs)
            created.forEach { deleteDocument(it.toString()) }
            createdFiles.forEach { it.delete() }
            throw e
        } finally {
            synchronized(storageLock) {
                migratingFolder = false
                storageLock.notifyAll()
            }
        }
    }

    private data class PublishedAudio(val activeFile: File, val cacheName: String)

    private fun publishAudio(source: File, displayName: String, videoId: String): PublishedAudio {
        val tree = downloadTreeUri?.let(Uri::parse)
        if (tree == null) {
            val target = File(rootDir, displayName)
            if (!source.renameTo(target)) throw IOException("No se pudo guardar el audio")
            return PublishedAudio(target, target.name)
        }
        ensureDownloadFolderAvailable()
        val target = createDocument(tree, displayName, "audio/mp4")
        try {
            copyToDocument(source, target)
            val extension = displayName.substringAfterLast('.', "m4a")
            val activeCopy = File(rootDir, "active-${videoId}-${System.nanoTime()}.$extension")
            source.copyTo(activeCopy, overwrite = true)
            activeFiles[videoId] = activeCopy
            return PublishedAudio(activeCopy, target.toString())
        } catch (e: Exception) {
            deleteDocument(target.toString())
            throw e
        }
    }

    private fun publishArtwork(source: File, videoId: String): String {
        val tree = downloadTreeUri?.let(Uri::parse) ?: return source.name
        ensureDownloadFolderAvailable()
        val target = createDocument(tree, "$videoId-cover.jpg", "image/jpeg")
        try { copyToDocument(source, target) } catch (e: Exception) {
            deleteDocument(target.toString())
            throw e
        }
        return target.toString()
    }

    private fun ensureDownloadFolderAvailable() {
        if (!settingsReady.await(2, TimeUnit.SECONDS)) throw IOException("No se pudo leer la configuración de descargas")
        synchronized(storageLock) { awaitFolderMigrationLocked() }
        val tree = downloadTreeUri?.let(Uri::parse) ?: return
        try {
            resolver.query(tree, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)?.use { }
                ?: throw IOException("No se puede acceder a la carpeta de descargas")
        } catch (e: Exception) {
            throw IOException("La carpeta de descargas ya no está disponible. Vuelve a seleccionarla.", e)
        }
    }

    private fun awaitFolderMigrationLocked() {
        while (migratingFolder) {
            try { storageLock.wait() } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("Cambio de carpeta interrumpido", e)
            }
        }
    }

    private fun createDocument(tree: Uri, name: String, mimeType: String): Uri =
        DocumentsContract.createDocument(resolver, tree, mimeType, name)
            ?: throw IOException("No se pudo crear el archivo en la carpeta elegida")

    private fun copyToDocument(source: File, destination: Uri) {
        source.inputStream().use { input ->
            val output = resolver.openOutputStream(destination, "w")
                ?: throw IOException("No se pudo escribir en la carpeta elegida")
            output.use { input.copyTo(it) }
        }
    }

    private fun copyResourceToDocument(source: String, destination: Uri) {
        openResource(source).use { input ->
            val output = resolver.openOutputStream(destination, "w")
                ?: throw IOException("No se pudo copiar una descarga")
            output.use { input.copyTo(it) }
        }
    }

    private fun copyResourceToFile(source: String, destination: File) {
        val partial = File(destination.parentFile, "${destination.name}.part")
        try {
            openResource(source).use { input -> partial.outputStream().use { input.copyTo(it) } }
            if (!partial.renameTo(destination)) throw IOException("No se pudo mover una descarga al almacenamiento predeterminado")
        } finally { partial.delete() }
    }

    private fun openResource(name: String): InputStream = if (name.startsWith("content://")) {
        resolver.openInputStream(Uri.parse(name)) ?: throw IOException("No se puede leer un archivo descargado")
    } else fileFor(CachedSong("", name, "")).inputStream()

    private fun resourceExists(uri: Uri): Boolean = if (uri.scheme == "content") {
        runCatching { resolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false }.getOrDefault(false)
    } else File(uri.path.orEmpty()).isFile

    private fun deleteDocument(name: String) {
        runCatching { resolver.delete(Uri.parse(name), null, null) }
    }

    private fun deleteResource(name: String) {
        if (name.startsWith("content://")) deleteDocument(name) else fileFor(CachedSong("", name, "")).delete()
    }

    companion object {
        const val DIR_NAME = "music_cache"
        private const val TAG = "CacheManager"
    }
}
