package com.jarbis.alexa.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CacheStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun song(videoId: String, bytes: Int = 100): CachedSong =
        CachedSong(videoId = videoId, fileName = "$videoId.m4a", title = videoId)

    private fun CacheStore.withFile(videoId: String, bytes: Int): CacheStore {
        File(tmp.root, "$videoId.m4a").writeBytes(ByteArray(bytes))
        put(song(videoId))
        return this
    }

    @Test
    fun `registra y persiste en el indice json`() {
        val root = tmp.root
        val store = CacheStore(root)
        store.withFile("a1", 10)
        store.withFile("b2", 20)

        val reloaded = CacheStore(root)
        assertEquals(2, reloaded.size())
        assertNotNull(reloaded.find("a1"))
        assertNotNull(reloaded.find("b2"))
    }

    @Test
    fun `totalBytes suma solo ficheros existentes`() {
        val store = CacheStore(tmp.root)
        store.withFile("a1", 10)
        store.withFile("b2", 20)
        store.remove("b2")

        assertEquals(10L, store.totalBytes())
    }

    @Test
    fun `recordPlayed incrementa el contador y actualiza la ultima visita`() {
        val store = CacheStore(tmp.root)
        store.withFile("a1", 10)
        store.recordPlayed("a1", atEpochMillis = 1000)
        store.recordPlayed("a1", atEpochMillis = 2000)

        val song = store.find("a1")!!
        assertEquals(2, song.playCount)
        assertEquals(2000L, song.lastPlayedEpochMillis)
    }

    @Test
    fun `evictToFit borra los de menor playCount primero`() {
        val root = tmp.root
        val store = CacheStore(root)
        store.withFile("a1", 10).withFile("b2", 10)
        store.recordPlayed("a1", atEpochMillis = 1000) // a1 se juega más

        store.evictToFit(maxBytes = 15L)

        assertFalse(File(root, "b2.m4a").exists())
        assertTrue(File(root, "a1.m4a").exists())
    }

    @Test
    fun `al empatar en playCount evict la mas antigua`() {
        val root = tmp.root
        val store = CacheStore(root)
        store.withFile("a1", 10).withFile("b2", 10)
        store.recordPlayed("a1", atEpochMillis = 1000)
        store.recordPlayed("b2", atEpochMillis = 2000)

        store.evictToFit(maxBytes = 15)

        // ambas tienen playCount=1; se borra la con lastPlayed más antigua (a1)
        assertFalse(File(root, "a1.m4a").exists())
        assertTrue(File(root, "b2.m4a").exists())
    }

    @Test
    fun `evict nunca toca la cancion protegida en reproduccion`() {
        val root = tmp.root
        val store = CacheStore(root)
        store.withFile("current", 20)
        store.withFile("old", 5)

        store.evictToFit(maxBytes = 6, protectedVideoId = "current")

        assertTrue(File(root, "current.m4a").exists())
        assertFalse(File(root, "old.m4a").exists())
    }

    @Test
    fun `bestLocalMatch encuentra por titulo aunque tenga acentos`() {
        val store = CacheStore(tmp.root)
        store.withFile("xx", 10)
        store.put(CachedSong("yy", "yy.m4a", "Imagine Dragons - Radioactive"))
        store.recordPlayed("yy", atEpochMillis = 5)

        val match = store.bestLocalMatch("imagine dragons")
        assertNotNull(match)
        assertEquals("yy", match?.videoId)
    }

    @Test
    fun `mostPlayed devuelve la cancion mas afinada`() {
        val store = CacheStore(tmp.root)
        store.withFile("a1", 10)
        store.withFile("b2", 10)
        store.recordPlayed("b2", atEpochMillis = 1)
        store.recordPlayed("a1", atEpochMillis = 2)
        store.recordPlayed("a1", atEpochMillis = 3)

        val best = store.mostPlayed()
        assertNotNull(best)
        assertEquals("a1", best?.videoId)
    }
}