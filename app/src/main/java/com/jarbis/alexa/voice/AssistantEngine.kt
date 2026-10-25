package com.jarbis.alexa.voice

import android.content.Context
import com.jarbis.alexa.AppConfig
import com.jarbis.alexa.Language
import com.jarbis.alexa.commands.CommandContext
import com.jarbis.alexa.commands.CommandModule
import com.jarbis.alexa.commands.CommandRegistry
import com.jarbis.alexa.commands.DefaultModules
import com.jarbis.alexa.commands.TextNormalizer
import com.jarbis.alexa.commands.impl.UnknownCommand
import com.jarbis.alexa.music.MusicBridge
import com.jarbis.alexa.music.MusicPlayerService
import com.jarbis.alexa.music.PlaybackController
import com.jarbis.alexa.settings.AppSettings
import com.jarbis.alexa.settings.SettingsStore
import com.jarbis.alexa.tts.TtsManager
import com.jarbis.alexa.weather.OpenMeteoWeatherClient
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer

enum class Phase { IDLE, LOADING, WAKE, COMMAND, PROCESSING, SPEAKING, ERROR }

enum class LogKind { INFO, WAKE, INPUT, RESPONSE, ERROR }

data class LogEntry(val kind: LogKind, val text: String)

data class ModuleState(val id: String, val title: String, val enabled: Boolean)

data class UiState(
    val running: Boolean = false,
    val phase: Phase = Phase.IDLE,
    val wakeWord: String = AppConfig.DEFAULT_WAKE_WORD,
    val language: Language = Language.ES,
    val city: String = AppConfig.DEFAULT_CITY,
    val modules: List<ModuleState> = emptyList(),
    val logs: List<LogEntry> = emptyList(),
)

/**
 * Orquesta el asistente: micrófono en bucle, detección del wake word (Vosk),
 * ventana de comando, dispatch a módulos y respuesta por TTS.
 * 100% offline en el reconocimiento.
 */
class AssistantEngine(context: Context) {

    private val appContext = context.applicationContext
    private val settingsStore = SettingsStore(appContext)
    private val mic = MicInput()
    private val tts = TtsManager(appContext)
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
    private val weatherClient = OpenMeteoWeatherClient()
    private val musicBridge = MusicBridge.state(appContext)
    private val playbackController = PlaybackController(appContext)
    private val allModules: List<CommandModule> = DefaultModules.all
    private val TAG = "Jarbis"

    /** Red de seguridad: ninguna corrutina del motor puede tumbar la app. */
    private val uncaughtHandler = CoroutineExceptionHandler { _, throwable ->
        android.util.Log.e(TAG, "Corrutina no gestionada", throwable)
    }

    private val lifetimeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + uncaughtHandler)
    private var runScope: CoroutineScope? = null

    private var model: Model? = null
    private var wakeDetector: VoskWakeRecognizer? = null
    private var currentSettings: AppSettings = AppSettings()

    @Volatile private var phase: Phase = Phase.IDLE
    private val commandBuffer = mutableListOf<ShortArray>()
    private val vad = VoiceActivityDetector()
    private val logs = ArrayList<LogEntry>()

    private val uiState = MutableStateFlow(UiState())

    init {
        lifetimeScope.launch {
            settingsStore.settings.collect { newSettings ->
                val languageChanged = currentSettings.language != newSettings.language
                val wakeChanged = currentSettings.wakeWord != newSettings.wakeWord
                currentSettings = newSettings
                tts.setLanguage(newSettings.language.ttsLocale)
                pushState()
                if (runScope != null) {
                    if (wakeChanged) applyWakeWord()
                    if (languageChanged) restart()
                }
            }
        }
    }

    fun uiState(): StateFlow<UiState> = uiState

    fun start() {
        if (runScope != null) return
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + uncaughtHandler)
        runScope = scope
        phase = Phase.LOADING
        pushState()
        musicBridge.nextResolver = { finishedId -> musicBridge.controller.nextLike(finishedId) }
        scope.launch { boot() }
    }

    private suspend fun boot() {
        try {
            val settings = settingsStore.fetch()
            currentSettings = settings
            tts.setLanguage(settings.language.ttsLocale)
            log(LogKind.INFO, "Cargando modelo ${settings.language.modelAsset}…")
            val modelPath = ModelLoader.ensure(appContext, settings.language.modelAsset)
            android.util.Log.d(TAG, "model unpacked at $modelPath")
            val loaded = Model(modelPath)
            android.util.Log.d(TAG, "model opened from $modelPath")
            model = loaded
            wakeDetector = VoskWakeRecognizer(loaded)
            beginWakeLoop()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "boot error", e)
            phase = Phase.ERROR
            log(LogKind.ERROR, "Fallo al arrancar: ${e.message}")
            pushState()
        }
    }

    private fun beginWakeLoop() {
        try {
            wakeDetector?.start(listOf(currentSettings.wakeWord))
            phase = Phase.WAKE
            log(LogKind.INFO, "Escuchando… di «${currentSettings.wakeWord}»")
            val echo = mic.start { chunk -> handleChunk(chunk) }
            when {
                echo.enabled -> log(LogKind.INFO, "Cancelación de eco acústico disponible y activa")
                echo.available -> log(LogKind.ERROR, "Cancelación de eco acústico disponible, pero no se pudo activar")
                else -> log(LogKind.INFO, "Cancelación de eco acústico no disponible en este dispositivo")
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "mic start error", e)
            phase = Phase.ERROR
            log(LogKind.ERROR, "Micrófono: ${e.message}")
            pushState()
        }
    }

    private fun handleChunk(chunk: ShortArray) {
        when (phase) {
            Phase.WAKE -> {
                if (wakeDetector?.accept(chunk) == true) onWakeTriggered()
            }
            Phase.COMMAND -> {
                synchronized(commandBuffer) {
                    commandBuffer.add(chunk)
                    vad.accept(chunk)
                }
            }
            else -> Unit
        }
    }

    private fun onWakeTriggered() {
        if (phase != Phase.WAKE) return
        synchronized(commandBuffer) { commandBuffer.clear() }
        vad.reset()
        phase = Phase.COMMAND
        playbackController.duck()
        MusicPlayerService.duck(appContext)
        log(LogKind.WAKE, "Despertado por «${currentSettings.wakeWord}»")
        tts.beep()
        runScope?.launch {
            // Escucha hasta detectar silencio tras la voz, o la ventana dura.
            val start = System.currentTimeMillis()
            var done = false
            while (!done && System.currentTimeMillis() - start < AppConfig.COMMAND_WINDOW_MS) {
                val silent = synchronized(commandBuffer) { vad.isSilent(AppConfig.SILENCE_END_MS) }
                if (silent) {
                    log(LogKind.INFO, "Comando terminado por silencio (${vad.lastSpeechMs})")
                    done = true
                } else {
                    delay(100)
                }
            }
            if (!vad.hasSpeech) {
                log(LogKind.INFO, "Sin voz detectada en la ventana de comando")
            }
            if (phase == Phase.COMMAND) finalizeCommand()
        }
    }

    /** Prueba rápida desde la UI sin necesidad de hablar. */
    fun simulateCommand(phrase: String) {
        runScope?.launch {
            phase = Phase.PROCESSING
            pushState()
            processRecognized(phrase)
        }
    }

    private suspend fun finalizeCommand() {
        if (phase != Phase.COMMAND) return
        phase = Phase.PROCESSING
        pushState()

        val chunks: List<ShortArray> = synchronized(commandBuffer) { commandBuffer.toList() }
        synchronized(commandBuffer) { commandBuffer.clear() }

        val currentModel = model ?: run { processRecognized(""); return }
        if (chunks.isEmpty()) {
            processRecognized("")
            return
        }

        val recognizer = try {
            Recognizer(currentModel, AppConfig.SAMPLE_RATE.toFloat())
        } catch (e: Exception) {
            log(LogKind.ERROR, "Reconocedor: ${e.message}")
            processRecognized("")
            return
        }
        try {
            chunks.forEach { recognizer.acceptWaveForm(it, it.size) }
            val text = JSONObject(recognizer.getFinalResult()).optString("text")
            processRecognized(text)
        } finally {
            recognizer.close()
        }
    }

    private suspend fun processRecognized(rawText: String) {
        val phrase = TextNormalizer.normalize(rawText)
        log(LogKind.INPUT, "Oído: «$phrase»")
        val locale = currentSettings.language.ttsLocale

        val enabledModules = allModules.filter { currentSettings.isEnabled(it.id) }
        val module = CommandRegistry(enabledModules).matching(phrase, locale) ?: UnknownCommand()

        val response = module.execute(commandContext, phrase)
        log(LogKind.RESPONSE, "[${module.id}] $response")
        phase = Phase.SPEAKING
        pushState()
        tts.speak(response) { afterSpeaking() }
    }

    private val commandContext = object : CommandContext {
        override val locale get() = currentSettings.language.ttsLocale
        override val city get() = currentSettings.city
        override suspend fun weatherFor(city: String) = weatherClient.currentWeather(city)
        override suspend fun playMusic(query: String): String? = runCatching {
            musicBridge.resolve(query)
        }.getOrNull()?.also { track ->
            runCatching {
                MusicPlayerService.intentFor(appContext, track).let { intent ->
                    androidx.core.content.ContextCompat.startForegroundService(appContext, intent)
                }
            }.onFailure { e ->
                android.util.Log.e(TAG, "No se pudo arrancar la reproducción", e)
            }
        }?.title

        override suspend fun nextSong(): Boolean {
            if (!musicBridge.isPlaying) return false
            MusicPlayerService.next(appContext)
            return true
        }

        override suspend fun stopMusic(): Boolean {
            val wasPlaying = musicBridge.isPlaying
            MusicPlayerService.stop(appContext)
            return wasPlaying
        }

        override suspend fun volumeUp(): Boolean {
            audioManager.adjustStreamVolume(
                android.media.AudioManager.STREAM_MUSIC,
                android.media.AudioManager.ADJUST_RAISE,
                0,
            )
            return true
        }

        override suspend fun volumeDown(): Boolean {
            audioManager.adjustStreamVolume(
                android.media.AudioManager.STREAM_MUSIC,
                android.media.AudioManager.ADJUST_LOWER,
                0,
            )
            return true
        }
    }

    private fun afterSpeaking() {
        if (runScope != null && (phase == Phase.SPEAKING || phase == Phase.IDLE)) {
            // La respuesta ha terminado: la música vuelve a su volumen.
            playbackController.unDuck()
            MusicPlayerService.unduck(appContext)
            phase = Phase.WAKE
            wakeDetector?.reset()
            pushState()
        }
    }

    private fun applyWakeWord() {
        if (runScope == null || model == null) return
        wakeDetector?.stop()
        wakeDetector = VoskWakeRecognizer(model!!)
        wakeDetector?.start(listOf(currentSettings.wakeWord))
    }

    fun setWakeWord(word: String) { lifetimeScope.launch { settingsStore.setWakeWord(word) } }
    fun setLanguage(language: Language) { lifetimeScope.launch { settingsStore.setLanguage(language) } }
    fun setCity(city: String) { lifetimeScope.launch { settingsStore.setCity(city) } }
    fun setModuleEnabled(id: String, enabled: Boolean) { lifetimeScope.launch { settingsStore.setModuleEnabled(id, enabled) } }

    private fun restart() {
        if (runScope != null) {
            runScope?.cancel()
            runScope = null
        }
        mic.stop()
        start()
    }

    fun stop() {
        runScope?.cancel()
        runScope = null
        mic.stop()
        wakeDetector?.stop()
        wakeDetector = null
        model?.close()
        model = null
        playbackController.unDuck()
        MusicPlayerService.unduck(appContext)
        phase = Phase.IDLE
        log(LogKind.INFO, "Asistente detenido")
    }

    fun destroy() {
        stop()
        lifetimeScope.cancel()
        tts.shutdown()
    }

    private fun log(kind: LogKind, text: String) {
        logs.add(0, LogEntry(kind, text))
        if (logs.size > 40) logs.removeAt(logs.size - 1)
        pushState()
    }

    private fun pushState() {
        uiState.value = UiState(
            running = runScope != null,
            phase = phase,
            wakeWord = currentSettings.wakeWord,
            language = currentSettings.language,
            city = currentSettings.city,
            modules = allModules.map { ModuleState(it.id, it.title, currentSettings.isEnabled(it.id)) },
            logs = logs.toList(),
        )
    }
}
