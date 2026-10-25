package com.jarbis.alexa

/** Parámetros de la app. El wake word (y más) son configurables y persistentes en DataStore. */
object AppConfig {
    const val DEFAULT_WAKE_WORD = "alexa"
    const val DEFAULT_CITY = "Madrid"
    const val SAMPLE_RATE = 16000
    const val COMMAND_WINDOW_MS = 4000L
    /** Silencio (en voz) que da por terminado el comando antes de la ventana dura. */
    const val SILENCE_END_MS = 2000L
    /** Umbral de RMS (0..32767) por encima del cual se considera voz activa. */
    const val VAD_RMS_THRESHOLD = 800
    /** Volumen de la música mientras el asistente escucha/responde (duck). */
    const val MUSIC_DUCK_VOLUME = 0.2f
    const val CHANNEL_ID = "assistant-voice"
    const val NOTIFICATION_ID = 1
    const val MUSIC_CHANNEL_ID = "music-playback"
    const val MUSIC_NOTIFICATION_ID = 2
    /** Cuota inicial de disco para las descargas musicales (4 GiB). */
    const val MUSIC_CACHE_MAX_BYTES = 4L * 1024 * 1024 * 1024
    const val MIN_MUSIC_CACHE_BYTES = 256L * 1024 * 1024
}
