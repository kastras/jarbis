package com.jarbis.alexa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import android.net.Uri
import android.provider.DocumentsContract
import java.util.Locale
import com.jarbis.alexa.AssistantViewModel
import com.jarbis.alexa.Language
import com.jarbis.alexa.voice.LogEntry
import com.jarbis.alexa.voice.LogKind
import com.jarbis.alexa.voice.Phase
import com.jarbis.alexa.voice.UiState
import kotlinx.coroutines.delay

@Composable
fun AssistantScreen(
    vm: AssistantViewModel,
    onEnableVoice: () -> Unit = { vm.start() },
    cacheLimitBytes: Long = 4L * 1024 * 1024 * 1024,
    cacheUsedBytes: Long = 0L,
    downloadFolderUri: String? = null,
    onCacheLimit: (String) -> Unit = {},
    onChooseDownloadFolder: () -> Unit = {},
    onUseDefaultDownloadFolder: () -> Unit = {},
) {
    val state by vm.uiState.collectAsState()

    var wakeField by remember(state.wakeWord) { mutableStateOf(state.wakeWord) }
    var cityField by remember(state.city) { mutableStateOf(state.city) }
    var cacheLimitField by remember(cacheLimitBytes) {
        mutableStateOf(String.format(Locale.US, "%.2f", cacheLimitBytes / (1024.0 * 1024 * 1024)))
    }

    LaunchedEffect(wakeField) {
        delay(400)
        val candidate = wakeField.trim()
        if (candidate.isNotBlank() && candidate != state.wakeWord) vm.setWakeWord(candidate)
    }
    LaunchedEffect(cityField) {
        delay(400)
        val candidate = cityField.trim()
        if (candidate.isNotBlank() && candidate != state.city) vm.setCity(candidate)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatusCard(state)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { if (state.running) vm.stop() else onEnableVoice() }) {
                Text(if (state.running) "Desactivar escucha por voz" else "Activar escucha por voz")
            }
        }

        ConfigCard(
            wakeWord = wakeField,
            onWakeChange = { wakeField = it },
            language = state.language,
            onLanguage = { vm.setLanguage(it) },
            city = cityField,
            onCityChange = { cityField = it },
        )

        ModulesCard(state.modules, vm)
        MusicStorageCard(
            cacheLimitField = cacheLimitField,
            onCacheLimitField = { cacheLimitField = it },
            cacheUsedBytes = cacheUsedBytes,
            downloadFolderUri = downloadFolderUri,
            onSaveLimit = { onCacheLimit(cacheLimitField) },
            onChooseFolder = onChooseDownloadFolder,
            onUseDefaultFolder = onUseDefaultDownloadFolder,
        )
        LogCard(state.logs)
    }
}

@Composable
private fun MusicStorageCard(
    cacheLimitField: String,
    onCacheLimitField: (String) -> Unit,
    cacheUsedBytes: Long,
    downloadFolderUri: String?,
    onSaveLimit: () -> Unit,
    onChooseFolder: () -> Unit,
    onUseDefaultFolder: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Almacenamiento de música", style = MaterialTheme.typography.titleMedium)
            Text("Ocupado: ${formatStorageSize(cacheUsedBytes)}", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                OutlinedTextField(
                    value = cacheLimitField,
                    onValueChange = { input ->
                        if (input.matches(Regex("[0-9]*([.,][0-9]*)?"))) onCacheLimitField(input)
                    },
                    label = { Text("Límite máximo (GiB)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onSaveLimit) { Text("Guardar") }
            }
            Text("Mínimo: 0,25 GiB. Al reducir el límite se borran las descargas menos usadas; la canción en reproducción se conserva hasta que termine.",
                style = MaterialTheme.typography.bodySmall)
            Text("Carpeta de descargas: ${downloadFolderUri?.let(::downloadFolderLabel) ?: "Almacenamiento de Jarbis"}",
                style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onChooseFolder) { Text(if (downloadFolderUri == null) "Elegir carpeta" else "Cambiar carpeta") }
                if (downloadFolderUri != null) TextButton(onClick = onUseDefaultFolder) { Text("Usar carpeta de Jarbis") }
            }
        }
    }
}

private fun downloadFolderLabel(value: String): String = runCatching {
    DocumentsContract.getTreeDocumentId(Uri.parse(value)).substringAfter(':').ifBlank { "Carpeta seleccionada" }
}.getOrDefault("Carpeta seleccionada")

private fun formatStorageSize(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> String.format(Locale.getDefault(), "%.2f GiB", bytes / (1024.0 * 1024 * 1024))
    else -> String.format(Locale.getDefault(), "%.0f MiB", bytes / (1024.0 * 1024))
}

@Composable
private fun StatusCard(state: UiState) {
    val (color, label) = statusOf(state)
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatusDot(color)
            Column {
                Text(label, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Palabra: «${state.wakeWord}» · idioma ${state.language.code.uppercase()}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun statusOf(state: UiState): Pair<Color, String> = when {
    !state.running -> Color(0xFF9E9E9E) to "Apagado"
    state.phase == Phase.LOADING -> Color(0xFF2196F3) to "Cargando modelo…"
    state.phase == Phase.WAKE -> Color(0xFF4CAF50) to "Escuchando… di «${state.wakeWord}»"
    state.phase == Phase.COMMAND -> Color(0xFFFFC107) to "Escuchando el comando…"
    state.phase == Phase.PROCESSING -> Color(0xFFFF9800) to "Procesando…"
    state.phase == Phase.SPEAKING -> Color(0xFF9C27B0) to "Respondiendo…"
    state.phase == Phase.ERROR -> Color(0xFFF44336) to "Error"
    else -> Color(0xFF9E9E9E) to "Idle"
}

@Composable
private fun StatusDot(color: Color) {
    Box(
        modifier = Modifier
            .size(16.dp)
            .background(color, CircleShape),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigCard(
    wakeWord: String,
    onWakeChange: (String) -> Unit,
    language: Language,
    onLanguage: (Language) -> Unit,
    city: String,
    onCityChange: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Configuración", style = MaterialTheme.typography.titleSmall)

            OutlinedTextField(
                value = wakeWord,
                onValueChange = onWakeChange,
                label = { Text("Palabra de activación") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Language.entries.forEach { lang ->
                    FilterChip(
                        selected = language == lang,
                        onClick = { onLanguage(lang) },
                        label = { Text(lang.code.uppercase()) },
                    )
                }
            }

            OutlinedTextField(
                value = city,
                onValueChange = onCityChange,
                label = { Text("Ciudad para el tiempo") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ModulesCard(modules: List<com.jarbis.alexa.voice.ModuleState>, vm: AssistantViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Módulos de comando", style = MaterialTheme.typography.titleMedium)
            modules.forEachIndexed { index, module ->
                if (index > 0) HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(module.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = module.enabled,
                        onCheckedChange = { vm.setModuleEnabled(module.id, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LogCard(logs: List<LogEntry>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Registro", style = MaterialTheme.typography.titleMedium)
            if (logs.isEmpty()) {
                Text("Sin eventos todavía", style = MaterialTheme.typography.bodySmall)
            } else {
                logs.take(20).forEach { entry ->
                    val color = when (entry.kind) {
                        LogKind.WAKE -> Color(0xFF4CAF50)
                        LogKind.INPUT -> Color(0xFF2196F3)
                        LogKind.RESPONSE -> Color(0xFF9C27B0)
                        LogKind.ERROR -> Color(0xFFF44336)
                        LogKind.INFO -> Color(0xFF757575)
                    }
                    Text(
                        text = entry.text,
                        color = color,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
