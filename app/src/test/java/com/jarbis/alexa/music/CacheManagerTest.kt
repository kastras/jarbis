package com.jarbis.alexa.music

import android.content.Context
import android.graphics.Bitmap
import okio.Buffer
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class CacheManagerTest {
    @Test fun `remote metadata and cover persist and failed image leaves audio usable`() = runBlocking {
        val context: Context = RuntimeEnvironment.getApplication()
        val root = File(context.cacheDir, "remote-cover").apply { mkdirs() }
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        val image = ByteArrayOutputStream().apply { bitmap.compress(Bitmap.CompressFormat.PNG, 100, this) }.toByteArray()
        bitmap.recycle()
        try {
            MockWebServer().use { server ->
                server.start()
                server.enqueue(MockResponse().setBody("audio"))
                server.enqueue(MockResponse().setBody(Buffer().write(image)))
                val cache = CacheManager(context, root)
                assertTrue(cache.obtain("remote", "Remote", server.url("/audio").toString(), "Artist",
                    server.url("/cover").toString())!!.exists())
                val reopened = CacheManager(context, root)
                val track = reopened.trackFor(reopened.store().find("remote")!!)
                assertEquals("Artist", track.artist)
                assertTrue(File(track.artworkUri!!.path!!).isFile)
                server.enqueue(MockResponse().setBody("audio"))
                server.enqueue(MockResponse().setResponseCode(500))
                assertTrue(cache.obtain("no-image", "No image", server.url("/audio").toString(),
                    artworkUrl = server.url("/broken").toString())!!.exists())
                assertNull(cache.store().find("no-image")!!.artworkFileName)
            }
        } finally { root.deleteRecursively() }
    }

    @Test fun `old index loads and artwork participates in quota and eviction`() {
        val context: Context = RuntimeEnvironment.getApplication()
        val root = File(context.cacheDir, "artwork-quota").apply { mkdirs() }
        File(root, "index.json").writeText("""{"songs":[{"videoId":"old","title":"Old","fileName":"old.m4a"}]}""")
        File(root, "old.m4a").writeBytes(ByteArray(10))
        val store = CacheStore(root)
        assertNull(store.find("old")?.artist)
        assertNull(store.find("old")?.artworkFileName)
        File(root, "cover.jpg").writeBytes(ByteArray(20))
        store.put(store.find("old")!!.copy(artist = "Artist", artworkFileName = "cover.jpg"))
        assertEquals("Artist", CacheStore(root).find("old")?.artist)
        assertEquals(30L, store.totalBytes())
        assertEquals(1, store.evictToFit(15).size)
        assertFalse(File(root, "cover.jpg").exists())
        assertFalse(File(root, "old.m4a").exists())
    }

    @Test
    fun `cancelar una descarga no publica ni deja un archivo incompleto`() = runBlocking {
        val context: Context = RuntimeEnvironment.getApplication()
        val root = File(context.cacheDir, "cancel-download").apply { mkdirs() }
        val cache = CacheManager(context, root)
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody("a".repeat(128 * 1024))
                .throttleBody(8192, 100, TimeUnit.MILLISECONDS))
            val download = async(Dispatchers.IO) { cache.obtain("cancelled", "Cancelada", server.url("/audio.m4a").toString()) }
            withTimeout(5000) {
                while (root.listFiles().orEmpty().none { it.extension == "part" }) delay(10)
            }
            download.cancelAndJoin()
            assertTrue(cache.store().list().isEmpty())
            assertFalse(File(root, "cancelled.m4a").exists())
            assertTrue(root.listFiles().orEmpty().none { it.extension == "part" })
        }
    }

    @Test
    fun `borrar la descarga en reproduccion espera hasta que deje de sonar`() {
        val context: Context = RuntimeEnvironment.getApplication()
        val root = File(context.cacheDir, "deferred-delete").apply { mkdirs() }
        val cache = CacheManager(context, root)
        val audio = File(root, "playing.m4a").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        cache.store().put(CachedSong("playing", audio.name, "En reproducción"))

        cache.playingVideoId = "playing"
        assertFalse(cache.deleteDownloaded("playing"))
        assertTrue(audio.exists())
        assertTrue(cache.isPendingDelete("playing"))

        cache.playingVideoId = null
        assertNull(cache.store().find("playing"))
        assertFalse(audio.exists())
        root.deleteRecursively()
    }
}
