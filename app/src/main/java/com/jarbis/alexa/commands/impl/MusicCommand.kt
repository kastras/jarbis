package com.jarbis.alexa.commands.impl

import com.jarbis.alexa.commands.CommandContext
import com.jarbis.alexa.commands.CommandModule
import com.jarbis.alexa.commands.TextNormalizer

/**
 * Módulo de música. Ante una búsqueda ("pon/busca/reproduce X") reproduce la
 * consulta en segundo plano (in-app, Media3 + NewPipe/Piped) y encadena
 * canciones parecidas. También responde a comandos de control de la
 * reproducción actual (siguiente, parar, volumen) con una respuesta.
 */
class MusicCommand : CommandModule {

    override val id: String = "music"
    override val title: String = "Música"
    override val keywords: Map<String, List<String>> = mapOf(
        // Palabras que enrutan al módulo (dispatch por límite de palabra).
        "es" to listOf(
            "pon", "toca", "ponme", "reproduce", "reproducir",
            "musica", "cancion", "canciones", "canción",
            "busca", "buscar", "buscame", "dame", "suena", "escucha", "quiero",
            "dale", "play", "pasame", "echame", "saca",
            "siguiente", "siguiente cancion", "siguiente canción",
            "para", "parar", "detener", "detente", "stop",
            "volumen", "sonido", "sube", "baja",
        ),
        "en" to listOf(
            "play", "put", "turn", "music", "song", "songs",
            "search", "find", "next", "skip", "stop", "pause", "volume", "louder", "quieter",
        ),
    )

    private val defaultQuery = mapOf(
        "es" to "música",
        "en" to "music",
    )

    override suspend fun execute(context: CommandContext, normalizedPhrase: String): String {
        MusicControl.command(normalizedPhrase)?.let { control ->
            return respondControl(control, context)
        }

        val query = MusicQueryParser.extract(normalizedPhrase, context.locale.language)
            .ifBlank { defaultQuery[context.locale.language] ?: "music" }
        return if (context.locale.language == "en") {
            context.playMusic(query)?.let { "Playing: $it." } ?: "I could not find music."
        } else {
            context.playMusic(query)?.let { "Reproduciendo: $it." } ?: "No pude encontrar música."
        }
    }

    private suspend fun respondControl(control: MusicControl.Command, context: CommandContext): String {
        val ok = when (control) {
            MusicControl.Command.NEXT -> context.nextSong()
            MusicControl.Command.STOP -> context.stopMusic()
            MusicControl.Command.VOLUME_UP -> context.volumeUp()
            MusicControl.Command.VOLUME_DOWN -> context.volumeDown()
        }
        if (!ok) {
            return if (context.locale.language == "en") {
                "There is no music playing right now."
            } else {
                "No hay música sonando ahora."
            }
        }
        return if (context.locale.language == "en") {
            when (control) {
                MusicControl.Command.NEXT -> "Next song."
                MusicControl.Command.STOP -> "Music stopped."
                MusicControl.Command.VOLUME_UP -> "Volume up."
                MusicControl.Command.VOLUME_DOWN -> "Volume down."
            }
        } else {
            when (control) {
                MusicControl.Command.NEXT -> "Siguiente canción."
                MusicControl.Command.STOP -> "Música parada."
                MusicControl.Command.VOLUME_UP -> "Subo el volumen."
                MusicControl.Command.VOLUME_DOWN -> "Bajo el volumen."
            }
        }
    }
}

/**
 * Detecta y clasifica órdenes de control de la reproducción dentro de la
 * frase normalizada, ANTES de interpretarla como nueva búsqueda.
 */
object MusicControl {

    enum class Command { NEXT, STOP, VOLUME_UP, VOLUME_DOWN }

    private val nextStrong = setOf("siguiente", "adelante", "next")
    private val nextSoft = setOf("pasa", "otra", "cambia", "saltate", "skip")
    private val musicNouns = setOf(
        "musica", "música", "canción", "canciones", "cancion", "sonido", "tema",
        "music", "song", "songs",
    )
    private val stopExplicit = setOf("parar", "detener", "detente", "stop", "pause", "quit")
    private val volumeUpWords = setOf("sube", "subir", "arriba", "alto", "fuerte", "levanta", "louder", "raise", "up")
    private val volumeDownWords = setOf("baja", "bajar", "abajo", "bajo", "suave", "quieter", "lower", "down")

    /**
     * Devuelve el comando de control si la frase lo expresa; si no, null.
     * Recibe la frase normalizada (sin acentos, minúsculas). El volumen se
     * activa solo ante "volumen"/"sonido"; "para" solo como parar si es la
     * frase entera o va seguida de "la música" (no "para mañana"); "pasa" y
     * "otra" solo si van acompañadas de un nombre de música ("pasa canción").
     */
    fun command(normalizedPhrase: String): Command? {
        val normalized = normalizedPhrase.trim().lowercase()
        val words = normalized.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return null

        if (words.any { it == "volumen" || it == "sonido" || it == "volume" }) {
            val up = words.any { it in volumeUpWords } ||
                ((words.contains("mas") || words.contains("more")) &&
                    (words.contains("alto") || words.contains("fuerte") || words.contains("loud") || words.contains("high")))
            val down = words.any { it in volumeDownWords } ||
                ((words.contains("mas") || words.contains("more")) && words.contains("suave"))
            return if (down && !up) Command.VOLUME_DOWN else Command.VOLUME_UP
        }

        if (words.any { it in nextStrong } ||
            (words.any { it in nextSoft } && words.any { it in musicNouns })
        ) return Command.NEXT

        if (normalized == "para" || normalized.startsWith("para la musica") || normalized.startsWith("para la cancion")) {
            return Command.STOP
        }
        if (words.any { it in stopExplicit }) return Command.STOP

        return null
    }
}

object MusicQueryParser {

    /** Palabras de mando que abren la frase (por idioma), de más larga a más corta. */
    private val triggers = mapOf(
        "es" to listOf(
            "quiero escuchar", "reproducir", "reproduce", "buscame", "buscar", "ponme",
            "escuchame", "dale play", "pasame", "echame", "suena", "dame", "busca", "saca", "toca", "pon", "dale",
        ),
        "en" to listOf(
            "play me some", "play me", "play some", "put on some", "put on",
            "search for", "play", "search", "find", "listen to", "turn on",
        ),
    )

    /** Ruido residual que se quita del comienzo de la consulta. */
    private val noise = mapOf(
        "es" to listOf(
            "musica", "música", "canción", "canciones", "cancion",
            "la", "el", "los", "las", "de", "en", "youtube",
            "un", "una", "unos", "unas", "algo", "tema", "play", "a",
        ),
        "en" to listOf(
            "music", "songs", "song", "the", "some", "a", "an", "of", "on", "for", "me", "youtube",
        ),
    )

    /**
     * Extrae la consulta a buscar de una frase reconocida (normalizada).
     * "ponmusica de imagine dragons" -> "imagine dragons"
     */
    fun extract(normalizedPhrase: String, language: String): String {
        var trimmed = normalizedPhrase.trim()
        val triggerList = triggers[language] ?: emptyList()
        for (trigger in triggerList) {
            if (trimmed == trigger) {
                trimmed = ""
                break
            }
            if (trimmed.startsWith("$trigger ")) {
                trimmed = trimmed.removePrefix("$trigger ").trim()
                break
            }
        }

        val noiseList = noise[language] ?: emptyList()
        var changed = true
        while (changed) {
            changed = false
            for (word in noiseList) {
                if (trimmed.isBlank()) break
                if (trimmed == word) {
                    trimmed = ""
                    changed = true
                } else if (trimmed.startsWith("$word ")) {
                    trimmed = trimmed.removePrefix("$word ").trim()
                    changed = true
                }
            }
        }
        return TextNormalizer.normalize(trimmed)
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}