package com.jarbis.alexa

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jarbis.alexa.music.ArtworkStore
import com.jarbis.alexa.music.CacheManager
import com.jarbis.alexa.music.CachedSong
import com.jarbis.alexa.music.LocalMusicLibrary
import com.jarbis.alexa.music.MusicPlayerService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(AndroidJUnit4::class)
class ArtworkTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun embeddedArtworkAndMetadataAreReadWithoutChangingAudio() {
        val audio = File(context.cacheDir, "embedded-cover.mp3")
        InstrumentationRegistry.getInstrumentation().context.assets.open("embedded-cover.mp3").use {
            audio.outputStream().use { output -> it.copyTo(output) }
        }
        try {
            val before = audio.readBytes()
            val track = LocalMusicLibrary(context, CacheManager(context)).readMetadata(Uri.fromFile(audio), "Fallback")
            assertEquals("Canción sintética", track.title)
            assertEquals("Artista de prueba", track.artist)
            assertNotNull(track.artworkUri)
            assertNotNull(BitmapFactory.decodeFile(track.artworkUri!!.path))
            assertArrayEquals(before, audio.readBytes())
            val metadata = MusicPlayerService.mediaItem(track, false).mediaMetadata
            assertEquals(track.artist, metadata.artist)
            assertEquals(track.artworkUri, metadata.artworkUri)
        } finally { audio.delete() }
    }

    @Test fun downloadedArtworkSurvivesReopeningAndIsRemovedWithAudio() = runBlocking {
        val root = File(context.cacheDir, "offline-artwork-test").apply { mkdirs() }
        try {
            val bitmap = Bitmap.createBitmap(1800, 1800, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.BLUE)
            val bytes = ByteArrayOutputStream().apply { bitmap.compress(Bitmap.CompressFormat.PNG, 100, this) }.toByteArray()
            bitmap.recycle()
            val cover = ArtworkStore.save(bytes, root, "song")!!
            val decoded = BitmapFactory.decodeFile(cover.path)
            assertTrue(decoded.width <= 512 && decoded.height <= 512)
            decoded.recycle()
            assertTrue(cover.length() <= 256 * 1024)
            assertNull(ArtworkStore.save("broken".toByteArray(), root, "broken"))
            File(root, "song.mp3").writeBytes(byteArrayOf(1))
            CacheManager(context, root).store().put(CachedSong("song", "song.mp3", "Title",
                artist = "Artist", artworkFileName = cover.name))
            // Nueva instancia e índice leído de disco; sin ninguna URL de red.
            val reopened = CacheManager(context, root)
            val songs = LocalMusicLibrary(context, reopened).downloaded()
            assertEquals("Artist", songs.single().track.artist)
            assertEquals(Uri.fromFile(cover), songs.single().track.artworkUri)
            reopened.store().remove("song")
            assertFalse(cover.exists())
        } finally { root.deleteRecursively() }
    }
}
