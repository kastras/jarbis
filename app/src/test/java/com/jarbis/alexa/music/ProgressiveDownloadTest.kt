package com.jarbis.alexa.music

import android.content.Context
import androidx.media3.datasource.DataSpec
import androidx.media3.common.util.UnstableApi
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@androidx.annotation.OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
class ProgressiveDownloadTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    private fun eventually(check: () -> Boolean) {
        val end = System.currentTimeMillis() + 5000
        while (!check() && System.currentTimeMillis() < end) Thread.sleep(20)
        assertTrue(check())
    }

    @Test fun `lectores y saltos comparten transferencia y solo el archivo completo es offline`() {
        val root = File(context.cacheDir, "progressive-complete").apply { deleteRecursively(); mkdirs() }
        val cache = CacheManager(context, root)
        MockWebServer().use { server ->
            server.start()
            val bytes = "abcdefgh".repeat(16 * 1024).toByteArray()
            server.enqueue(MockResponse().setBody(okio.Buffer().write(bytes))
                .throttleBody(8192, 100, TimeUnit.MILLISECONDS))
            val url = server.url("/audio.m4a").toString()
            cache.remoteTrack(ResolvedTrack("shared", "Compartida", "Artista"), url)
            val factory = ProgressiveMusicDataSource.Factory(context, cache)
            val first = factory.createDataSource()
            val second = factory.createDataSource()
            try {
                val spec = DataSpec.Builder().setUri(url).setKey("shared").build()
                assertEquals(bytes.size.toLong(), first.open(spec))
                val prefix = ByteArray(100)
                assertEquals(100, first.read(prefix, 0, prefix.size))
                assertArrayEquals(bytes.copyOf(100), prefix)
                assertNull(cache.store().find("shared"))
                // Saltar más allá de lo recibido espera al mismo productor.
                second.open(spec.buildUpon().setPosition((bytes.size - 100).toLong()).build())
                val tail = ByteArray(100)
                assertEquals(100, second.read(tail, 0, tail.size))
                eventually { cache.store().find("shared") != null }
                assertArrayEquals(bytes.takeLast(100).toByteArray(), tail)
                assertEquals(1, server.requestCount)
                assertArrayEquals(bytes, cache.fileFor(cache.store().find("shared")!!).readBytes())
                assertEquals("Artista", CacheManager(context, root).store().find("shared")?.artist)
                first.close()
                first.open(spec)
                assertEquals(100, first.read(prefix, 0, prefix.size))
                assertEquals(1, server.requestCount)
            } finally {
                first.close(); second.close(); cache.cancelDownloadsExcept()
            }
        }
        root.deleteRecursively()
    }

    @Test fun `cancelar libera lectores bloqueados y limpia el temporal sin publicar`() {
        val root = File(context.cacheDir, "progressive-cancel").apply { deleteRecursively(); mkdirs() }
        val cache = CacheManager(context, root)
        val executor = Executors.newSingleThreadExecutor()
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody("x".repeat(128 * 1024))
                .throttleBody(1024, 500, TimeUnit.MILLISECONDS))
            val source = ProgressiveMusicDataSource.Factory(context, cache).createDataSource()
            try {
                source.open(DataSpec.Builder().setUri(server.url("/audio").toString())
                    .setKey("cancelled").setPosition(100_000).build())
                val reader = executor.submit<Boolean> {
                    try { source.read(ByteArray(10), 0, 10); false } catch (_: IOException) { true }
                }
                Thread.sleep(100)
                assertFalse(reader.isDone)
                cache.cancelDownloadsExcept()
                assertTrue(reader.get(3, TimeUnit.SECONDS))
                eventually { root.listFiles().orEmpty().none { it.extension == "part" } }
                assertNull(cache.store().find("cancelled"))
            } finally {
                source.close(); cache.cancelDownloadsExcept(); executor.shutdownNow()
            }
        }
        root.deleteRecursively()
    }

    @Test fun `respuesta truncada o HTTP fallido nunca se publica ni bloquea al lector`() {
        val root = File(context.cacheDir, "progressive-errors").apply { deleteRecursively(); mkdirs() }
        val cache = CacheManager(context, root)
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody("x".repeat(64 * 1024))
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY))
            server.enqueue(MockResponse().setResponseCode(403))
            repeat(2) { index ->
                val source = ProgressiveMusicDataSource.Factory(context, cache).createDataSource()
                try {
                    source.open(DataSpec.Builder().setUri(server.url("/audio").toString()).setKey("broken$index").build())
                    val buffer = ByteArray(8192)
                    while (source.read(buffer, 0, buffer.size) >= 0) { }
                    fail("Debe fallar sin publicar audio incompleto")
                } catch (_: IOException) {
                    assertNull(cache.store().find("broken$index"))
                } finally { source.close(); cache.cancelDownloadsExcept() }
            }
            eventually { root.listFiles().orEmpty().none { it.extension == "part" } }
        }
        root.deleteRecursively()
    }

    @Test fun `recuperacion limpia parciales y rechaza audio mayor que la cuota`() {
        val root = File(context.cacheDir, "progressive-quota").apply { deleteRecursively(); mkdirs() }
        File(root, "abandoned.m4a.part").writeText("incompleto")
        val cache = CacheManager(context, root)
        assertFalse(File(root, "abandoned.m4a.part").exists())
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody("audio").setHeader("Content-Length",
                com.jarbis.alexa.AppConfig.MUSIC_CACHE_MAX_BYTES + 1))
            val source = ProgressiveMusicDataSource.Factory(context, cache).createDataSource()
            try {
                source.open(DataSpec.Builder().setUri(server.url("/oversized").toString()).setKey("huge").build())
                fail("Debe rechazar el tamaño antes de reproducir")
            } catch (_: IOException) {
                assertNull(cache.store().find("huge"))
            } finally { source.close(); cache.cancelDownloadsExcept() }
            eventually { root.listFiles().orEmpty().none { it.extension == "part" } }
        }
        root.deleteRecursively()
    }
}
