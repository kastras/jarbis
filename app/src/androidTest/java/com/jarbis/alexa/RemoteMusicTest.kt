package com.jarbis.alexa

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jarbis.alexa.music.CacheManager
import com.jarbis.alexa.music.MusicController
import com.jarbis.alexa.music.MusicPlayerService
import com.jarbis.alexa.music.ProgressiveMusicDataSource
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Prueba de red real, opt-in: -Pandroid.testInstrumentationRunnerArguments.remoteMusic=true. */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(UnstableApi::class)
class RemoteMusicTest {
    @Test fun searchAndDownloadSelection() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("remoteMusic") == "true")
        val query = args.getString("musicQuery") ?: "estop"
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = File(context.cacheDir, "remote-music-test")
        root.deleteRecursively()
        val cache = CacheManager(context, root)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = ActivityScenario.launch(MainActivity::class.java)
        lateinit var player: ExoPlayer
        instrumentation.runOnMainSync {
            player = ExoPlayer.Builder(context).setMediaSourceFactory(
                DefaultMediaSourceFactory(ProgressiveMusicDataSource.Factory(context, cache))).build()
        }
        try {
            val controller = MusicController(cache)
            val results = controller.searchResults(query)
            Log.i("RemoteMusicTest", "Consulta=$query resultados=${results.take(3)}")
            assertTrue("Sin resultados para $query", results.isNotEmpty())
            for (selection in results.take(2)) {
                val start = System.currentTimeMillis()
                val track = controller.resolveSelection(selection)
                assertNotNull("No se pudo obtener audio ${selection.videoId}: ${selection.title}", track)
                val resolved = System.currentTimeMillis()
                instrumentation.runOnMainSync {
                    player.setMediaItem(MusicPlayerService.mediaItem(track!!, false))
                    player.prepare(); player.play()
                }
                var playing = false
                val deadline = System.currentTimeMillis() + 90_000
                while (!playing && System.currentTimeMillis() < deadline) {
                    instrumentation.runOnMainSync {
                        player.playerError?.let { throw AssertionError("Error reproduciendo ${selection.title}", it) }
                        playing = player.isPlaying
                    }
                    if (!playing) Thread.sleep(50)
                }
                assertTrue("No empezó a reproducir ${selection.title}", playing)
                Log.i("RemoteMusicTest", "Sonando ${selection.title}: resolución=${resolved - start}ms, " +
                    "inicio=${System.currentTimeMillis() - start}ms, completa=${cache.store().find(selection.videoId) != null}")
                instrumentation.runOnMainSync { player.pause() }
                val finishDeadline = System.currentTimeMillis() + 180_000
                while (cache.store().find(selection.videoId) == null && System.currentTimeMillis() < finishDeadline) Thread.sleep(100)
                val saved = cache.store().find(selection.videoId)
                assertNotNull("No se completó ${selection.title}", saved)
                val audio = cache.fileFor(saved!!)
                assertTrue("Audio vacío", audio.length() > 0)
                Log.i("RemoteMusicTest", "Descargada ${selection.title}: ${audio.length()} bytes")
                instrumentation.runOnMainSync { player.stop(); player.clearMediaItems() }
                cache.cancelDownloadsExcept()
            }
        } finally {
            instrumentation.runOnMainSync { player.release() }
            cache.cancelDownloadsExcept()
            activity.close()
            root.deleteRecursively()
        }
    }
}
