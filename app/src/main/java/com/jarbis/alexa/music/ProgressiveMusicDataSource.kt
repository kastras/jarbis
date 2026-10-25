package com.jarbis.alexa.music

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import java.io.IOException

/** Media3 consume el mismo archivo que guarda la descarga; no abre otro HTTP. */
@androidx.annotation.OptIn(UnstableApi::class)
class ProgressiveMusicDataSource private constructor(
    private val cache: CacheManager,
) : BaseDataSource(true) {
    private var download: GrowingDownload? = null
    private var uri: Uri? = null
    private var position = 0L
    private var remaining = C.LENGTH_UNSET.toLong()
    @Volatile private var closed = true
    private var started = false

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        closed = false
        uri = dataSpec.uri
        val id = dataSpec.key ?: throw IOException("Canción remota sin identidad")
        val source = cache.openDownload(id, dataSpec.uri.toString())
        download = source
        val length = source.awaitLength { closed }
        position = dataSpec.position
        if (length >= 0 && position > length) throw IOException("Posición fuera del audio")
        remaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.length
            else if (length >= 0) length - position else C.LENGTH_UNSET.toLong()
        started = true
        transferStarted(dataSpec)
        return remaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val count = if (remaining < 0) length else minOf(length.toLong(), remaining).toInt()
        val read = download?.read(position, buffer, offset, count) { closed }
            ?: throw IOException("Fuente cerrada")
        if (read > 0) {
            position += read
            if (remaining >= 0) remaining -= read
            bytesTransferred(read)
        }
        return read
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        closed = true
        download?.wakeReaders()
        download = null
        uri = null
        if (started) { started = false; transferEnded() }
    }

    class Factory(context: Context, private val cache: CacheManager) : DataSource.Factory {
        private val local = DefaultDataSource.Factory(context)
        override fun createDataSource(): DataSource {
            val progressive = ProgressiveMusicDataSource(cache)
            val fallback = local.createDataSource()
            return object : DataSource {
                private var current: DataSource = fallback
                override fun addTransferListener(listener: androidx.media3.datasource.TransferListener) {
                    progressive.addTransferListener(listener)
                    fallback.addTransferListener(listener)
                }
                override fun open(dataSpec: DataSpec): Long {
                    val remote = dataSpec.key != null && dataSpec.uri.scheme in listOf("http", "https")
                    val cached = if (remote) cache.store().find(dataSpec.key!!)
                        ?.takeIf(cache::isAvailable)?.let(cache::uriFor) else null
                    current = if (remote && cached == null) progressive else fallback
                    return current.open(if (cached == null) dataSpec
                        else dataSpec.buildUpon().setUri(cached).build())
                }
                override fun read(buffer: ByteArray, offset: Int, length: Int) = current.read(buffer, offset, length)
                override fun getUri(): Uri? = current.uri
                override fun getResponseHeaders(): Map<String, List<String>> = current.responseHeaders
                override fun close() = current.close()
            }
        }
    }
}
