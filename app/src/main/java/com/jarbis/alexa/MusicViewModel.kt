package com.jarbis.alexa

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.jarbis.alexa.music.LibrarySong
import com.jarbis.alexa.music.LocalMusicLibrary
import com.jarbis.alexa.music.MusicBridge
import com.jarbis.alexa.music.MusicPlayerService
import com.jarbis.alexa.music.ResolvedTrack
import com.jarbis.alexa.settings.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MusicUiState(
    val connected: Boolean = false,
    val title: String = "",
    val artist: String? = null,
    val artworkUri: Uri? = null,
    val hasTrack: Boolean = false,
    val playRequested: Boolean = false,
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val canPrevious: Boolean = false,
    val canNext: Boolean = false,
    val canSeek: Boolean = false,
    val buffering: Boolean = false,
    val searching: Boolean = false,
    val searched: Boolean = false,
    val results: List<ResolvedTrack> = emptyList(),
    val preparing: String? = null,
    val library: List<LibrarySong> = emptyList(),
    val folderUri: String? = null,
    val downloadFolderUri: String? = null,
    val cacheLimitBytes: Long = AppConfig.MUSIC_CACHE_MAX_BYTES,
    val cacheUsedBytes: Long = 0L,
    val loadingLibrary: Boolean = false,
    val message: String? = null,
)

@androidx.annotation.OptIn(UnstableApi::class)
class MusicViewModel(app: Application) : AndroidViewModel(app) {
    private val bridge = MusicBridge.state(app)
    private val settings = SettingsStore(app)
    private val library = LocalMusicLibrary(app, bridge.cache)
    private val mutableState = MutableStateFlow(MusicUiState())
    val state = mutableState.asStateFlow()
    private var controller: MediaController? = null
    private var searchJob: Job? = null
    private var selectionJob: Job? = null
    private var libraryJob: Job? = null
    private val connection = MediaController.Builder(app,
        SessionToken(app, ComponentName(app, MusicPlayerService::class.java))).buildAsync()
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) { updatePlayback() }
        override fun onPlayerError(error: PlaybackException) {
            val detail = generateSequence<Throwable>(error) { it.cause }
                .mapNotNull { it.message }
                .firstOrNull { it.contains("carpeta de descargas", ignoreCase = true) }
            message(detail ?: "No se pudo reproducir esta canción. Comprueba la conexión o que el archivo sigue disponible.")
        }
    }

    init {
        connection.addListener({
            try {
                controller = connection.get().also { it.addListener(listener) }
                updatePlayback()
            } catch (e: Exception) {
                message("No se pudo conectar con el reproductor.")
            }
        }, ContextCompat.getMainExecutor(app))
        viewModelScope.launch {
            while (true) {
                updatePlayback()
                delay(500)
            }
        }
        refreshLibrary()
        viewModelScope.launch { refreshStorageSettings() }
        viewModelScope.launch {
            bridge.cache.revision.collect { refreshLibrary() }
        }
    }

    private fun updatePlayback() {
        val player = controller ?: return
        mutableState.update { it.copy(
            connected = player.isConnected,
            title = player.mediaMetadata.title?.toString().orEmpty(),
            artist = player.mediaMetadata.artist?.toString(),
            artworkUri = player.mediaMetadata.artworkUri,
            hasTrack = player.currentMediaItem != null,
            playRequested = player.playWhenReady && player.playbackState != Player.STATE_ENDED,
            playing = player.isPlaying,
            buffering = player.playbackState == Player.STATE_BUFFERING,
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = player.duration.takeIf { duration -> duration != C.TIME_UNSET && duration > 0 } ?: 0,
            canPrevious = player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS),
            canNext = player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT),
            canSeek = player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM),
        ) }
    }

    fun search(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            mutableState.update { it.copy(results = emptyList(), searching = false, searched = false) }
            return
        }
        searchJob = viewModelScope.launch {
            mutableState.update { it.copy(searching = true, searched = false, results = emptyList(), message = null) }
            try {
                val results = withContext(Dispatchers.IO) { bridge.controller.searchResults(query.trim()) }
                ensureActive()
                mutableState.update { it.copy(results = results, searching = false, searched = true) }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                mutableState.update { it.copy(searching = false, searched = true, message = "No se pudo buscar. Comprueba la conexión a internet.") }
            }
        }
    }

    fun playResult(result: ResolvedTrack) {
        val player = controller?.takeIf { it.isConnected } ?: return
        selectionJob?.cancel()
        selectionJob = viewModelScope.launch {
            mutableState.update { it.copy(preparing = result.videoId, message = null) }
            try {
                val track = withContext(Dispatchers.IO) { bridge.controller.resolveSelection(result) }
                ensureActive()
                if (track == null) message("No se pudo obtener el audio de la canción seleccionada.") else {
                    player.setMediaItem(MusicPlayerService.mediaItem(track, remote = true))
                    player.prepare()
                    player.play()
                    refreshLibrary()
                }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { message("No se pudo preparar la canción.")
            } finally { mutableState.update { it.copy(preparing = null) } }
        }
    }

    fun playLocal(songs: List<LibrarySong>, index: Int) {
        if (index !in songs.indices) return
        val player = controller?.takeIf { it.isConnected } ?: return
        selectionJob?.cancel()
        mutableState.update { it.copy(message = null) }
        player.setMediaItems(songs.map { MusicPlayerService.mediaItem(it.track, remote = false) }, index, 0)
        player.prepare()
        player.play()
    }

    fun togglePlayback() {
        controller?.let {
            if (it.playWhenReady && it.playbackState != Player.STATE_ENDED) it.pause() else {
                if (it.playbackState == Player.STATE_ENDED) it.seekToDefaultPosition()
                it.prepare()
                it.play()
            }
        }
    }
    fun previous() { controller?.seekToPrevious() }
    fun stop() {
        selectionJob?.cancel()
        mutableState.update { it.copy(preparing = null) }
        controller?.stop()
    }
    fun next() { controller?.seekToNext() }
    fun seek(positionMs: Long) { controller?.seekTo(positionMs) }
    fun message(text: String?) { mutableState.update { it.copy(message = text) } }

    fun chooseFolder(uri: Uri) {
        viewModelScope.launch {
            try {
                val app = getApplication<Application>()
                app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val previous = settings.fetch().musicFolderUri
                settings.setMusicFolder(uri.toString())
                if (previous != null && previous != uri.toString()) {
                    runCatching { app.contentResolver.releasePersistableUriPermission(Uri.parse(previous), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                }
                refreshLibrary()
            } catch (e: SecurityException) {
                message("No se pudo guardar el acceso a esa carpeta. Elige otra carpeta.")
            }
        }
    }

    fun chooseDownloadFolder(uri: Uri) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            var persisted = false
            var previous: String? = null
            try {
                app.contentResolver.takePersistableUriPermission(uri, flags)
                persisted = true
                previous = settings.fetch().downloadFolderUri
                bridge.cache.migrateDownloads(uri)
                if (previous != null && previous != uri.toString()) {
                    runCatching { app.contentResolver.releasePersistableUriPermission(Uri.parse(previous), flags) }
                }
                refreshStorageSettings()
                refreshLibrary()
                message("Carpeta de descargas actualizada.")
            } catch (e: Exception) {
                if (persisted && previous != uri.toString()) {
                    runCatching { app.contentResolver.releasePersistableUriPermission(uri, flags) }
                }
                message(e.message ?: "No se pudo cambiar la carpeta de descargas. La ubicación anterior se conserva.")
            }
        }
    }

    fun useDefaultDownloadFolder() {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                val previous = settings.fetch().downloadFolderUri
                bridge.cache.migrateDownloads(null)
                previous?.let { old ->
                    runCatching { app.contentResolver.releasePersistableUriPermission(Uri.parse(old), flags) }
                }
                refreshStorageSettings()
                refreshLibrary()
                message("Las descargas usarán el almacenamiento de Jarbis.")
            } catch (e: Exception) {
                message(e.message ?: "No se pudo cambiar la carpeta. La ubicación anterior se conserva.")
            }
        }
    }

    fun setCacheLimitGiB(value: String) {
        val gib = value.replace(',', '.').toDoubleOrNull()
        if (gib == null || !gib.isFinite() || gib > Long.MAX_VALUE.toDouble() / (1024.0 * 1024 * 1024) ||
            gib * 1024 * 1024 * 1024 < AppConfig.MIN_MUSIC_CACHE_BYTES) {
            message("El límite mínimo es 0,25 GiB (256 MiB).")
            return
        }
        viewModelScope.launch {
            try {
                bridge.cache.setCacheLimit((gib * 1024 * 1024 * 1024).toLong())
                refreshStorageSettings()
                refreshLibrary()
            } catch (e: Exception) {
                message(e.message ?: "No se pudo guardar el límite de descargas.")
            }
        }
    }

    fun deleteDownloaded(song: LibrarySong) {
        if (song.source != com.jarbis.alexa.music.MusicSource.DOWNLOADED) return
        val deleted = bridge.cache.deleteDownloaded(song.track.videoId)
        if (deleted) message("Descarga eliminada: ${song.track.title}")
        else message("Se eliminará al parar o cambiar esta canción.")
        refreshLibrary()
    }

    private suspend fun refreshStorageSettings() {
        val configured = settings.fetch()
        if (configured.downloadFolderUri != null && !bridge.cache.isDownloadFolderAvailable()) {
            mutableState.update { it.copy(message = "La carpeta de descargas no está disponible. Vuelve a seleccionarla para continuar.") }
        }
        mutableState.update { it.copy(
            downloadFolderUri = configured.downloadFolderUri,
            cacheLimitBytes = configured.musicCacheMaxBytes,
            cacheUsedBytes = bridge.cache.store().totalBytes(),
        ) }
    }

    fun refreshLibrary() {
        libraryJob?.cancel()
        libraryJob = viewModelScope.launch {
            mutableState.update { it.copy(loadingLibrary = true) }
            try {
                val folder = settings.fetch().musicFolderUri
                val downloaded = library.downloaded()
                val configured = settings.fetch()
                mutableState.update { it.copy(
                    folderUri = folder,
                    downloadFolderUri = configured.downloadFolderUri,
                    cacheLimitBytes = bridge.cache.cacheLimitBytes(),
                    cacheUsedBytes = bridge.cache.store().totalBytes(),
                ) }
                val fromFolder = if (folder == null) emptyList() else try {
                    library.folder(Uri.parse(folder))
                } catch (e: CancellationException) { throw e
                } catch (e: Exception) {
                    message("No se puede leer la carpeta. Vuelve a seleccionarla para recuperar el acceso.")
                    emptyList()
                }
                mutableState.update { it.copy(library = (fromFolder + downloaded)
                    .sortedBy { song -> song.track.title.lowercase() }, loadingLibrary = false) }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                mutableState.update { it.copy(loadingLibrary = false, message = "No se pudo leer Mi música.") }
            }
        }
    }

    override fun onCleared() {
        controller?.removeListener(listener)
        MediaController.releaseFuture(connection)
        super.onCleared()
    }
}
