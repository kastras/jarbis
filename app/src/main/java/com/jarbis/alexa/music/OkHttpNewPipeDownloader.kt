package com.jarbis.alexa.music

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as ExtractorRequest
import org.schabi.newpipe.extractor.downloader.Response as ExtractorResponse
import java.util.concurrent.TimeUnit

/** User-Agent tipo cliente Android real para que YouTube no rechace el tráfico. */
private const val DEFAULT_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

/**
 * Downloader de NewPipeExtractor sobre OkHttp. NewPipe necesita una
 * implementación de [Downloader] que hable por HTTP; las cabeceras que
 * construye el extractor (X-Goog, clientes de innertube…) viajan tal cual
 * en el Request y se reenvían sin tocar.
 */
class OkHttpNewPipeDownloader(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
) : Downloader() {

        private companion object {
            const val TAG = "OkHttpNpDownloader"

            fun isYouTubeHost(url: String): Boolean {
                val rest = url.lowercase().substringAfter("://")
                val host = rest.substringBefore("/")
                return host == "youtube.com" || host.endsWith(".youtube.com")
            }
        }

    override fun execute(request: ExtractorRequest): ExtractorResponse {
        val builder = okhttp3.Request.Builder().url(request.url())
        request.headers().forEach { (name, values) ->
            values.asReversed().forEach { builder.addHeader(name, it) }
        }
        if (request.headers()["User-Agent"].isNullOrEmpty()) {
            builder.header("User-Agent", DEFAULT_USER_AGENT)
        }
        // Sin la cookie CONSENT, YouTube redirige la página de reproducir a
        // consent.youtube.com (sin ytInitialData/ytInitialPlayerResponse) y la
        // extracción de audio falla con "Could not get ytInitialData".
        if (isYouTubeHost(request.url())) {
            builder.header("Cookie", "CONSENT=YES+cb.20240101-09-p0.en+FX+417")
        }

        val body = request.dataToSend()
        val method = request.httpMethod()
        if (body != null) {
            builder.method(method ?: "POST", body.toRequestBody())
        } else if (!method.isNullOrBlank() && method != "GET") {
            builder.method(method, null)
        }

        val resp = client.newCall(builder.build()).execute()
        return resp.use {
            val body = YtPageRewriter.rewrite(it.body?.string() ?: "")
            val url = it.request.url.toString()
            if (it.code >= 400 || url.contains("youtubei/v1") || url.contains("/results")) {
                val marker = when {
                    body.contains("ytInitialData") -> " HAS_INITIAL"
                    body.contains("streamingData") -> " HAS_STREAMING"
                    body.contains("\"url\"") -> " HAS_URL"
                    else -> " NO_YTINITIAL"
                }
                android.util.Log.w(TAG, "HTTP ${it.code}${marker} ${it.request.method} $url -> ${body.take(200)}")
            }
            ExtractorResponse(
                it.code,
                it.message,
                it.headers.toMultimap(),
                body,
                url,
            )
        }
    }
}