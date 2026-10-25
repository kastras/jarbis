package com.jarbis.alexa.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jarbis.alexa.AssistantViewModel
import com.jarbis.alexa.MusicUiState
import com.jarbis.alexa.MusicViewModel
import com.jarbis.alexa.music.MusicSource

@Composable
fun MusicScreen(vm: MusicViewModel, assistant: AssistantViewModel,
    onEnableVoice: () -> Unit, onStartPlayback: () -> Unit) {
    val state by vm.state.collectAsState()
    var section by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(0) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(vm::chooseFolder)
    }
    val downloadFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(vm::chooseDownloadFolder)
    }
    var deleteTarget by remember { mutableStateOf<com.jarbis.alexa.music.LibrarySong?>(null) }
    LaunchedEffect(section) { if (section == 1) vm.refreshLibrary() }
    Column(Modifier.fillMaxSize().padding(top = 24.dp, bottom = 16.dp)) {
        Text("Jarbis", style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        TabRow(selectedTabIndex = section) {
            listOf("Buscar", "Mi música", "Configuración").forEachIndexed { index, label ->
                Tab(selected = section == index, onClick = { section = index }, text = { Text(label) })
            }
        }
        state.message?.let { message ->
            Card(Modifier.fillMaxWidth().padding(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(message)
                    TextButton(onClick = { vm.message(null) }) { Text("Cerrar") }
                }
            }
        }
        if (section == 2) {
            AssistantScreen(
                assistant,
                onEnableVoice,
                cacheLimitBytes = state.cacheLimitBytes,
                cacheUsedBytes = state.cacheUsedBytes,
                downloadFolderUri = state.downloadFolderUri,
                onCacheLimit = vm::setCacheLimitGiB,
                onChooseDownloadFolder = {
                    downloadFolderPicker.launch(state.downloadFolderUri?.let(android.net.Uri::parse))
                },
                onUseDefaultDownloadFolder = vm::useDefaultDownloadFolder,
            )
        } else {
            val visible = state.library.filter { filter == 0 || it.source ==
                if (filter == 1) MusicSource.FOLDER else MusicSource.DOWNLOADED }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { NowPlaying(state, vm::previous, vm::togglePlayback, vm::next, vm::stop, vm::seek) }
                if (section == 0) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = query, onValueChange = { query = it },
                                label = { Text("Canción o artista") }, singleLine = true,
                                modifier = Modifier.fillMaxWidth())
                            Button(onClick = { vm.search(query) }, enabled = query.isNotBlank()) { Text("Buscar en internet") }
                            if (state.searching) {
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                                Text("Buscando canciones…")
                            } else if (state.searched && state.results.isEmpty()) {
                                Text("No se encontraron canciones. Prueba otra búsqueda o comprueba la conexión.")
                            } else if (!state.searched) {
                                Text("Busca una canción o abre Mi música para escuchar tus archivos locales.")
                            }
                        }
                    }
                    items(state.results, key = { it.videoId }) { result ->
                        SongRow(result.title, if (state.preparing == result.videoId) "Descargando…" else "Internet",
                            enabled = state.connected && state.preparing == null) {
                            onStartPlayback()
                            vm.playResult(result)
                        }
                    }
                } else {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { folderPicker.launch(state.folderUri?.let(android.net.Uri::parse)) }) {
                                    Text(if (state.folderUri == null) "Elegir carpeta" else "Cambiar carpeta")
                                }
                                TextButton(onClick = vm::refreshLibrary) { Text("Actualizar") }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("Todas", "Carpeta", "Descargadas").forEachIndexed { index, label ->
                                    FilterChip(selected = filter == index, onClick = { filter = index }, label = { Text(label) })
                                }
                            }
                            if (state.loadingLibrary) LinearProgressIndicator(Modifier.fillMaxWidth())
                            if (!state.loadingLibrary && visible.isEmpty()) {
                                Text("No hay canciones aquí. Elige una carpeta o reproduce una canción de internet para descargarla.")
                            }
                            Text("Las descargas se conservan en la caché y pueden eliminarse al alcanzar su límite de disco.",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    items(visible, key = { "${it.source}:${it.track.uri}" }) { song ->
                        SongRow(song.track.title, if (song.source == MusicSource.FOLDER) "Carpeta" else "Descargada",
                            enabled = state.connected,
                            onDelete = if (song.source == MusicSource.DOWNLOADED) ({ deleteTarget = song }) else null) {
                                onStartPlayback()
                                vm.playLocal(visible, visible.indexOf(song))
                            }
                    }
                }
            }
        }
    }
    deleteTarget?.let { song ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Borrar descarga") },
            text = { Text("¿Quieres borrar «${song.track.title}» de las descargas? Si está sonando, se borrará al parar o cambiar de canción.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteDownloaded(song); deleteTarget = null }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
internal fun NowPlaying(state: MusicUiState, onPrevious: () -> Unit, onToggle: () -> Unit,
    onNext: () -> Unit, onStop: () -> Unit, onSeek: (Long) -> Unit) {
    var seeking by remember(state.title) { mutableStateOf<Float?>(null) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Reproductor", style = MaterialTheme.typography.titleMedium)
            key(state.artworkUri) { Artwork(state.artworkUri) }
            Text(state.title.ifBlank { "Elige una canción para empezar" }, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            state.artist?.takeIf { it.isNotBlank() }?.let {
                Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.buffering || state.preparing != null) LinearProgressIndicator(Modifier.fillMaxWidth())
            Slider(value = seeking ?: state.positionMs.toFloat().coerceAtMost(state.durationMs.toFloat()),
                onValueChange = { seeking = it },
                onValueChangeFinished = { seeking?.let { onSeek(it.toLong()) }; seeking = null },
                valueRange = 0f..state.durationMs.coerceAtLeast(1).toFloat(),
                enabled = state.canSeek && state.durationMs > 0,
                modifier = Modifier.semantics { contentDescription = "Progreso de reproducción" })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(timeLabel(state.positionMs), style = MaterialTheme.typography.bodySmall)
                Text(timeLabel(state.durationMs), style = MaterialTheme.typography.bodySmall)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevious, enabled = state.canPrevious, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Anterior")
                }
                FilledIconButton(onClick = onToggle, enabled = state.connected && state.hasTrack,
                    modifier = Modifier.size(64.dp)) {
                    Icon(if (state.playRequested) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (state.playRequested) "Pausar" else if (state.positionMs > 0) "Seguir" else "Reproducir",
                        modifier = Modifier.size(36.dp))
                }
                IconButton(onClick = onNext, enabled = state.canNext, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Siguiente")
                }
                IconButton(onClick = onStop, enabled = state.connected && (state.hasTrack || state.preparing != null),
                    modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Stop, contentDescription = "Parar")
                }
            }
            if (!state.connected) Text("Conectando con el reproductor…", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Artwork(uri: android.net.Uri?) {
    val context = LocalContext.current
    // Cada URI tiene su propio estado: una carga tardía nunca se aplica a otra canción.
    var bitmap by remember(uri) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(uri) {
        bitmap = withContext(Dispatchers.IO) {
            try {
                val localUri = if (uri?.scheme in listOf("http", "https")) {
                    val directory = java.io.File(context.cacheDir, "player_artwork")
                    val key = com.jarbis.alexa.music.ArtworkStore.key(uri.toString())
                    val cached = java.io.File(directory, "$key.jpg").takeIf { it.exists() }
                        ?: com.jarbis.alexa.music.ArtworkStore.download(uri.toString(), directory, uri.toString())
                    // Carátulas temporales del reproductor: como máximo cuatro JPEG acotados.
                    directory.listFiles()?.filter { it.extension == "jpg" && it != cached }
                        ?.sortedByDescending { it.lastModified() }?.drop(3)?.forEach { it.delete() }
                    cached?.let(android.net.Uri::fromFile)
                } else uri
                localUri?.let { context.contentResolver.openInputStream(it) }?.use {
                    BitmapFactory.decodeStream(it)?.asImageBitmap()
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                null
            }
        }
    }
    Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.widthIn(max = 280.dp).fillMaxWidth().aspectRatio(1f)
            .background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            bitmap?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                ?: Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SongRow(title: String, source: String, enabled: Boolean, onDelete: (() -> Unit)? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).clickable(enabled = enabled, onClick = onClick).padding(vertical = 10.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(source, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Borrar descarga de $title")
            }
        }
    }
    HorizontalDivider()
}

private fun timeLabel(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
