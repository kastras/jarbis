package com.jarbis.alexa

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ApplicationProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jarbis.alexa.music.MusicBridge
import com.jarbis.alexa.music.MusicPlayerService
import com.jarbis.alexa.music.MusicTrack
import com.jarbis.alexa.music.ResolvedTrack
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class MusicSessionTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var controller: MediaController
    private lateinit var audio: File
    private lateinit var activity: ActivityScenario<MainActivity>

    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)

    @Before fun connect() {
        activity = ActivityScenario.launch(MainActivity::class.java)
        audio = File(context.cacheDir, "session-test.wav")
        val samples = ByteArray(8000 * 2 * 10)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".toByteArray()).putInt(36 + samples.size).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(8000).putInt(16000)
            .putShort(2).putShort(16).put("data".toByteArray()).putInt(samples.size).array()
        audio.writeBytes(header + samples)
        lateinit var future: com.google.common.util.concurrent.ListenableFuture<MediaController>
        main {
            future = MediaController.Builder(context,
                SessionToken(context, ComponentName(context, MusicPlayerService::class.java))).buildAsync()
        }
        controller = future.get(10, TimeUnit.SECONDS)
    }

    private fun item(id: String, remote: Boolean = false) =
        MusicPlayerService.mediaItem(MusicTrack(id, id, Uri.fromFile(audio)), remote)

    private fun waitFor(predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            var satisfied = false
            main { satisfied = predicate() }
            if (satisfied) return
            Thread.sleep(50)
        }
        var diagnostic = ""
        main { diagnostic = "state=${controller.playbackState}, ready=${controller.playWhenReady}, suppression=${controller.playbackSuppressionReason}, next=${controller.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT)}, error=${controller.playerError}" }
        throw AssertionError("La sesión no alcanzó el estado esperado: $diagnostic")
    }

    @After fun release() {
        MusicBridge.state(context).nextResolver = null
        main { controller.stop(); controller.clearMediaItems(); controller.release() }
        activity.close()
        audio.delete()
    }

    @Test fun localQueueSupportsTransportAndSeekThroughSession() {
        main {
            controller.setMediaItems(listOf(item("first"), item("second")))
            controller.prepare()
            controller.play()
        }
        waitFor { controller.isPlaying }
        main { controller.seekToNext() }
        waitFor { controller.currentMediaItem?.mediaId == "second" }
        main { controller.pause() }
        waitFor { !controller.isPlaying }
        main {
            controller.seekTo(1500)
            assertEquals(1500L, controller.currentPosition)
            controller.seekToPreviousMediaItem()
        }
        waitFor { controller.currentMediaItem?.mediaId == "first" }
        main {
            assertTrue(controller.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))
            assertFalse(controller.hasPreviousMediaItem())
            controller.seekTo(9900)
            controller.play()
        }
        waitFor { controller.currentMediaItem?.mediaId == "second" }
        main { controller.seekTo(9900) }
        waitFor { controller.playbackState == Player.STATE_ENDED }
        main { assertEquals("second", controller.currentMediaItem?.mediaId) }
    }

    @Test fun remoteQueueExposesNextAndKeepsHistory() {
        MusicBridge.state(context).nextResolver = { MusicTrack("related", "related", Uri.fromFile(audio)) }
        main {
            controller.setMediaItem(item("remote", remote = true))
            controller.prepare()
            controller.play()
        }
        waitFor { controller.isPlaying && controller.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT) }
        main { controller.seekToNext() }
        waitFor { controller.currentMediaItem?.mediaId == "related" }
        main { controller.seekToPreviousMediaItem() }
        waitFor { controller.currentMediaItem?.mediaId == "remote" }
    }

    @Test fun pauseResumeAndStopKeepPositionIdentityAndQueue() {
        main {
            controller.setMediaItems(listOf(item("first"), item("second")))
            controller.prepare()
            controller.play()
        }
        waitFor { controller.isPlaying }
        main { controller.pause(); controller.seekTo(2300) }
        waitFor { !controller.isPlaying && controller.currentPosition in 2250..2350 }
        Thread.sleep(300)
        main { assertEquals(2300L, controller.currentPosition); controller.play() }
        waitFor { controller.isPlaying && controller.currentPosition >= 2300 }
        main { controller.stop() }
        waitFor { controller.playbackState == Player.STATE_IDLE && controller.currentPosition == 0L }
        Thread.sleep(300)
        main {
            assertFalse(controller.playWhenReady)
            assertEquals("first", controller.currentMediaItem?.mediaId)
            assertEquals(2, controller.mediaItemCount)
            assertEquals(0L, controller.currentPosition)
            controller.prepare(); controller.play()
        }
        waitFor { controller.isPlaying }
        main { assertEquals("first", controller.currentMediaItem?.mediaId) }
    }

    @Test fun stopRejectsLateRelatedEvenWhenResolverIgnoresCancellation() {
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        MusicBridge.state(context).nextResolver = {
            started.complete(Unit)
            withContext(NonCancellable) { finish.await() }
            MusicTrack("late", "late", Uri.fromFile(audio))
        }
        main { controller.setMediaItem(item("remote", true)); controller.prepare(); controller.play() }
        waitFor { controller.isPlaying }
        main { controller.seekToNext() }
        waitFor { started.isCompleted }
        main { controller.stop() }
        waitFor { controller.playbackState == Player.STATE_IDLE && !controller.playWhenReady }
        finish.complete(Unit)
        Thread.sleep(500)
        main {
            assertEquals("remote", controller.currentMediaItem?.mediaId)
            assertEquals(1, controller.mediaItemCount)
            assertEquals(0L, controller.currentPosition)
            assertFalse(controller.isPlaying)
        }
    }

    @Test fun progressivePlaybackStartsBeforeDownloadAndCompletedSongWorksOffline() {
        val cache = MusicBridge.state(context).cache
        val id = "progressive-session"
        cache.store().remove(id)
        // Un minuto de WAV, servido en unos 15s: suficiente para observar
        // reproducción real mientras el archivo sigue incompleto.
        val samples = ByteArray(8000 * 2 * 60)
        val header = audio.readBytes().copyOf(44)
        ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).putInt(4, 36 + samples.size)
            .putInt(40, samples.size)
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody(Buffer().write(header + samples))
                .throttleBody(16 * 1024, 250, TimeUnit.MILLISECONDS))
            val track = cache.remoteTrack(ResolvedTrack(id, "Progresiva", "Prueba"),
                server.url("/audio.wav").toString())
            try {
                main {
                    controller.setMediaItem(MusicPlayerService.mediaItem(track, true))
                    controller.prepare(); controller.play()
                }
                waitFor { controller.isPlaying }
                assertEquals("No debe haberse publicado aún", null, cache.store().find(id))
                assertEquals(1, server.requestCount)
                main { controller.pause() }
                val deadline = System.currentTimeMillis() + 20_000
                while (cache.store().find(id) == null && System.currentTimeMillis() < deadline) Thread.sleep(50)
                val saved = cache.store().find(id) ?: throw AssertionError("No terminó durante la pausa")
                assertEquals((header.size + samples.size).toLong(), cache.fileFor(saved).length())
                server.shutdown()
                // El historial remoto también reutiliza el archivo completo,
                // sin resolver ni abrir de nuevo su dirección de internet.
                main { controller.stop(); controller.prepare(); controller.seekTo(20_000); controller.play() }
                waitFor { controller.isPlaying && controller.currentPosition >= 20_000 }
                assertEquals(1, server.requestCount)
                main { controller.stop() }
                val local = cache.trackFor(saved)
                main {
                    controller.setMediaItem(MusicPlayerService.mediaItem(local, false))
                    controller.prepare(); controller.play()
                }
                waitFor { controller.isPlaying }
                main { assertEquals("Progresiva", controller.mediaMetadata.title.toString()) }
            } finally {
                main { controller.stop(); controller.clearMediaItems() }
                cache.store().remove(id)
            }
        }
    }

    @Test fun stopAndSwitchCancelPartialDownloadWithoutPublishingIt() {
        val cache = MusicBridge.state(context).cache
        val id = "progressive-cancel-session"
        cache.store().remove(id)
        val samples = ByteArray(8000 * 2 * 180)
        val header = audio.readBytes().copyOf(44)
        ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).putInt(4, 36 + samples.size)
            .putInt(40, samples.size)
        val root = File(context.getExternalFilesDir(null) ?: context.filesDir, "music_cache")
        fun hasPartial() = root.listFiles().orEmpty().any {
            it.name.startsWith("stream-$id-") && it.extension == "part"
        }
        MockWebServer().use { server ->
            server.start()
            repeat(2) {
                server.enqueue(MockResponse().setBody(Buffer().write(header + samples))
                    .throttleBody(16 * 1024, 250, TimeUnit.MILLISECONDS))
            }
            val track = cache.remoteTrack(ResolvedTrack(id, "Cancelación"), server.url("/audio.wav").toString())
            try {
                main {
                    controller.setMediaItem(MusicPlayerService.mediaItem(track, true))
                    controller.prepare(); controller.play()
                }
                waitFor { controller.isPlaying }
                assertTrue(hasPartial())
                main { controller.stop() }
                waitFor { !hasPartial() && controller.playbackState == Player.STATE_IDLE }
                assertEquals(null, cache.store().find(id))
                main {
                    assertEquals(id, controller.currentMediaItem?.mediaId)
                    assertEquals(0L, controller.currentPosition)
                    controller.prepare(); controller.play()
                }
                waitFor { controller.isPlaying }
                assertEquals(2, server.requestCount)
                assertTrue(hasPartial())
                main {
                    controller.setMediaItem(item("local-after-stream"))
                    controller.prepare(); controller.play()
                }
                waitFor { !hasPartial() && controller.isPlaying && controller.currentMediaItem?.mediaId == "local-after-stream" }
                assertEquals(null, cache.store().find(id))
            } finally {
                main { controller.stop(); controller.clearMediaItems() }
                cache.store().remove(id)
            }
        }
    }
}
