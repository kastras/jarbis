package com.jarbis.alexa.music

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

/** Resultado de búsqueda o elemento de relatedStreams de la API de Piped. */
@Serializable
data class PipedStreamItem(
    val url: String = "",
    val type: String = "stream",
    val title: String = "",
    val duration: Long = 0L,
    val uploaderName: String? = null,
    val thumbnail: String? = null,
    @SerialName("uploadedDate") val uploadedDate: String? = null,
)

@Serializable
data class PipedSearchResponse(val items: List<PipedStreamItem> = emptyList())

@Serializable
data class PipedRelatedResponse(@SerialName("relatedStreams") val related: List<PipedStreamItem> = emptyList())

/** Entrada de audio disponible para un vídeo (m4a/opus/webm). */
@Serializable
data class PipedAudioStream(
    val url: String = "",
    val format: String = "",
    @SerialName("mimeType") val mimeType: String = "",
    val bitrate: Int = 0,
)

@Serializable
data class PipedStreamsResponse(
    @SerialName("audioStreams") val audioStreams: List<PipedAudioStream> = emptyList(),
    val title: String = "",
)

/** Endpoints de la API pública de Piped (instancia configurada como baseUrl). */
interface PipedApi {
    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("filter") filter: String = "music",
    ): PipedSearchResponse

    @GET("streams/{videoId}")
    suspend fun streams(@Path("videoId") videoId: String): PipedStreamsResponse

    @GET("next/{videoId}")
    suspend fun next(@Path("videoId") videoId: String): PipedRelatedResponse
}

/**
 * Cliente de la API de Piped con failover entre instancias públicas: la
 * primera sana que responda gana; si cae, se prueba la siguiente.
 */
class PipedClient(
    private val instances: List<String> = DEFAULT_INSTANCES,
) {

    private val json = Json { ignoreUnknownKeys = true }
    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun apiFor(base: String): PipedApi {
        val retrofit = Retrofit.Builder()
            .baseUrl(if (base.endsWith("/")) base else "$base/")
            .client(okHttp)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        return retrofit.create(PipedApi::class.java)
    }

    /** Busca por nombre y devuelve el primer vídeo con audio, o null si nada sirve. */
    suspend fun search(query: String): PipedStreamItem? = tryInstances { api ->
        api.search(query).items.firstOrNull { it.type == "stream" && it.title.isNotBlank() }
    }

    suspend fun searchResults(query: String): List<PipedStreamItem> = tryInstances { api ->
        api.search(query).items.filter { it.type == "stream" && it.title.isNotBlank() }
            .takeIf { it.isNotEmpty() }
    } ?: emptyList()

    /** Stream audio mejor para un vídeo. */
    suspend fun audioStream(videoId: String): PipedAudioStream? = tryInstances { api ->
        val resp = api.streams(videoId)
        bestAudioStream(resp.audioStreams)
    }

    /** Canciones relacionadas para encadenar "música parecida". */
    suspend fun related(videoId: String): List<PipedStreamItem> = tryInstances { api ->
        api.next(videoId).related.filter { it.type == "stream" && it.title.isNotBlank() }
    } ?: emptyList()

    /** Recorre las instancias y devuelve el primer resultado útil. Nunca lanza:
     * si todas fallan (caídas, HTML, rate-limit…) se devuelve null y la llamada
     * de negocio responde de forma elegante. */
    private suspend fun <T> tryInstances(block: suspend (PipedApi) -> T?): T? {
        for (base in instances) {
            try {
                val result = block(apiFor(base))
                if (result != null) return result
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "Instancia $base no disponible (${e.javaClass.simpleName}: ${e.message})")
            }
        }
        return null
    }

    companion object {
        private const val TAG = "PipedClient"

        /**
         * Instancias públicas gestionadas por la comunidad de Piped. El orden
         * importa: primero las que siguen respondiendo hoy. La lista entera se
         * recorre en fallback; ninguna caída debe tumbar al asistente.
         */
        val DEFAULT_INSTANCES = listOf(
            "https://api.piped.private.coffee",
            "https://pipedapi.kavin.rocks",
            "https://pipedapi.adminforge.de",
            "https://phoenix.vern.cc",
        )

        /** Extrae el video id desde una url de retorno de Piped ("/watch?v=…", "/video/…" o id pelado). */
        fun videoOf(item: PipedStreamItem): String? {
            val url = item.url
            val id = when {
                url.contains("/watch?v=") -> url.substringAfter("/watch?v=").substringBefore("&")
                url.contains("/video/") -> url.substringAfter("/video/").substringBefore("/")
                url.contains("/") -> null
                else -> url
            }?.trim()
            return id?.takeIf { it.isNotBlank() }
        }

        /** Mejor stream de audio: prefiere m4a/opus, después el de mayor bitrate. */
        fun bestAudioStream(streams: List<PipedAudioStream>): PipedAudioStream? {
            val usable = streams.filter {
                it.url.isNotBlank() && (it.mimeType.contains("audio") || it.format.equals("m4a", true))
            }
            if (usable.isEmpty()) return null
            return usable.maxByOrNull { it.bitrate }
        }
    }
}
