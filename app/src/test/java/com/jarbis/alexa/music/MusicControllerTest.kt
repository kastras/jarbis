package com.jarbis.alexa.music

import android.content.Context
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class MusicControllerTest {

    private lateinit var server: MockWebServer
    private lateinit var context: Context

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        context = RuntimeEnvironment.getApplication()
    }

    @After
    fun tearDown() {
        server.shutdown()
        context.getExternalFilesDir(null)?.deleteRecursively()
    }

    /**
     * Simula una canción ya descargada a caché escribiendo el fichero y el
     * índice directamente (sin red, determinista en Robolectric).
     */
    private fun seedCache(cache: CacheManager, videoId: String, title: String) {
        val entry = CachedSong(videoId = videoId, fileName = "$videoId.m4a", title = title, playCount = 1)
        cache.fileFor(entry).writeBytes(ByteArray(50))
        cache.store().put(entry)
    }

    @Test
    fun `resolve con todas las instancias caidas devuelve null sin lanzar`() = runTest {
        server.enqueue(MockResponse().setResponseCode(502))

        val controller = MusicController(
            cache = CacheManager(context),
            resolvers = listOf(
                PipedMusicResolver(PipedClient(instances = listOf(server.url("/").toString()))),
            ),
        )

        val track = controller.resolve("queen")
        assertNull(track)
    }

    @Test
    fun `resolve con match local estricto usa la cancion cacheada sin consultar origenes`() = runTest {
        val cache = CacheManager(context)
        seedCache(cache, "local1", "Imagine Dragons - Radioactive")
        val controller = MusicController(cache = cache, resolvers = emptyList())

        val track = controller.resolve("imagine dragons")

        assertNotNull(track)
        assertEquals("local1", track?.videoId)
    }

    @Test
    fun `resolve con busqueda no exacta usa la cancion cacheada sin consultar origenes`() = runTest {
        val cache = CacheManager(context)
        seedCache(cache, "local1", "The Rappers Avion")
        val spy = ResolverSpy()
        val controller = MusicController(cache = cache, resolvers = listOf(spy))

        val track = controller.resolve("rapers avion")

        assertNotNull(track)
        assertEquals("local1", track?.videoId)
        assertFalse("con búsqueda no exacta no debió consultar orígenes", spy.searchCalled)
    }

    @Test
    fun `resolve sin match local consulta NewPipe en vez de consumir la cancion cacheada`() = runTest {
        val cache = CacheManager(context)
        seedCache(cache, "local1", "Imagine Dragons - Radioactive")
        val spy = ResolverSpy()
        val controller = MusicController(cache = cache, resolvers = listOf(spy))

        // "coldplay" no coincide con la caché → debe llegar al resolver (NewPipe).
        val track = controller.resolve("coldplay yellow")

        assertTrue("resolve() debió consultar NewPipe", spy.searchCalled)
        // El spy no encuentra nada y no hay red: degrada a la canción más cacheada.
        assertNotNull(track)
        assertEquals("local1", track?.videoId)
    }

    @Test
    fun `resolve sin match local y sin red degrada a la cancion mas cacheada`() = runTest {
        val cache = CacheManager(context)
        seedCache(cache, "local1", "Imagine Dragons - Radioactive")
        val controller = MusicController(cache = cache, resolvers = listOf(FallingResolver()))

        val track = controller.resolve("coldplay something")

        assertNotNull(track)
        assertEquals("local1", track?.videoId)
    }

    /** Resolver de prueba que registra si se le pidió una búsqueda. */
    private class ResolverSpy : MusicResolver {
        var searchCalled = false
        override suspend fun search(query: String): ResolvedTrack? {
            searchCalled = true
            return null
        }

        override suspend fun audioUrlFor(videoId: String): String? = null
        override suspend fun related(videoId: String): List<ResolvedTrack> = emptyList()
    }

    /** Resolver que siempre falla (simula sin red). */
    private class FallingResolver : MusicResolver {
        override suspend fun search(query: String): ResolvedTrack? = null
        override suspend fun audioUrlFor(videoId: String): String? = null
        override suspend fun related(videoId: String): List<ResolvedTrack> = emptyList()
    }
}