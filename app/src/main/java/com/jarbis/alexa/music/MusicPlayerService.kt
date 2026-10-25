package com.jarbis.alexa.music

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.jarbis.alexa.AppConfig
import com.jarbis.alexa.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.io.File

/** Una sola sesión para voz, pantalla, notificación y controles del sistema. */
@androidx.annotation.OptIn(UnstableApi::class)
class MusicPlayerService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var nextJob: Job? = null
    private var generation = 0L
    private val bridge get() = MusicBridge.state(this)

    override fun onCreate() {
        super.onCreate()
        bridge.hasSession = true
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(ProgressiveMusicDataSource.Factory(this, bridge.cache)))
            .setLoadControl(DefaultLoadControl.Builder()
                .setBufferDurationsMs(15_000, 50_000, 1_000, 2_000).build())
            .build().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            setHandleAudioBecomingNoisy(true)
        }
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { bridge.isPlaying = isPlaying }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                generation++
                nextJob?.cancel()
                bridge.cache.playingVideoId = mediaItem?.mediaId
                bridge.cache.cancelDownloadsExcept(mediaItem?.mediaId)
                val uri = mediaItem?.localConfiguration?.uri
                if (uri?.scheme in listOf("http", "https")) {
                    bridge.cache.remoteTrack(ResolvedTrack(mediaItem!!.mediaId,
                        mediaItem.mediaMetadata.title?.toString() ?: mediaItem.mediaId,
                        mediaItem.mediaMetadata.artist?.toString(),
                        mediaItem.mediaMetadata.artworkUri?.toString()), uri.toString())
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && isRemote()) enqueueRelated()
            }

            override fun onPlayerError(error: PlaybackException) {
                bridge.isPlaying = false
                bridge.cache.cancelDownloadsExcept()
            }
        })
        // La cola remota conserva el historial y expone «siguiente» incluso antes
        // de resolver la próxima canción. La cola local usa los saltos de ExoPlayer.
        val sessionPlayer = SessionPlayer()
        session = MediaSession.Builder(this, sessionPlayer)
            .setSessionActivity(PendingIntent.getActivity(this, 0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .build()
        scope.launch {
            bridge.cache.revision.collect {
                val item = player.currentMediaItem ?: return@collect
                val saved = bridge.cache.store().find(item.mediaId) ?: return@collect
                val artwork = bridge.cache.trackFor(saved).artworkUri ?: return@collect
                if (artwork != item.mediaMetadata.artworkUri) {
                    player.replaceMediaItem(player.currentMediaItemIndex, item.buildUpon()
                        .setMediaMetadata(item.mediaMetadata.buildUpon().setArtworkUri(artwork).build()).build())
                }
            }
        }
        // MediaSessionService genera la notificación MediaStyle y los controles
        // de bloqueo a partir del estado real del reproductor.
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    private inner class SessionPlayer : ForwardingSimpleBasePlayer(player) {
        override fun handleStop(): com.google.common.util.concurrent.ListenableFuture<*> {
            cancelRelated()
            player.pause()
            player.stop()
            player.seekTo(0)
            bridge.cache.cancelDownloadsExcept()
            return com.google.common.util.concurrent.Futures.immediateVoidFuture()
        }

        override fun handleSetPlayWhenReady(playWhenReady: Boolean): com.google.common.util.concurrent.ListenableFuture<*> {
            if (!playWhenReady) cancelRelated()
            return super.handleSetPlayWhenReady(playWhenReady)
        }

        override fun getState(): State {
            val state = super.getState()
            val commands = state.availableCommands.buildUpon().apply {
                if (isRemote()) {
                    add(Player.COMMAND_SEEK_TO_NEXT)
                    add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                }
            }.build()
            return state.buildUpon().setAvailableCommands(commands).build()
        }

        override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): com.google.common.util.concurrent.ListenableFuture<*> {
            cancelRelated()
            if (seekCommand == Player.COMMAND_SEEK_TO_NEXT || seekCommand == Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM) {
                this@MusicPlayerService.next()
                return com.google.common.util.concurrent.Futures.immediateVoidFuture()
            }
            return super.handleSeek(mediaItemIndex, positionMs, seekCommand)
        }
    }

    private fun cancelRelated() {
        generation++
        nextJob?.cancel()
        nextJob = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> intent.trackFor()?.let { track ->
                nextJob?.cancel()
                player.setMediaItem(mediaItem(track, remote = true))
                player.prepare()
                player.play()
            }
            ACTION_NEXT -> next()
            ACTION_STOP -> {
                generation++
                nextJob?.cancel()
                player.stop()
                player.clearMediaItems()
                bridge.cache.cancelDownloadsExcept()
                bridge.isPlaying = false
                stopSelf()
            }
            ACTION_DUCK -> player.volume = AppConfig.MUSIC_DUCK_VOLUME
            ACTION_UNDUCK -> player.volume = 1f
            else -> return super.onStartCommand(intent, flags, startId)
        }
        return START_NOT_STICKY
    }

    private fun isRemote(): Boolean =
        player.currentMediaItem?.mediaMetadata?.extras?.getBoolean(EXTRA_REMOTE) == true

    private fun next() {
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
        } else if (isRemote()) {
            enqueueRelated()
        }
    }

    private fun enqueueRelated() {
        if (nextJob?.isActive == true) return
        val id = player.currentMediaItem?.mediaId ?: return
        val requestGeneration = generation
        nextJob = scope.launch {
            val resolver = bridge.nextResolver
            val track = if (resolver != null) resolver(id) else bridge.controller.nextLike(id)
            ensureActive()
            if (requestGeneration != generation || !isRemote()) return@launch
            if (track == null || track.videoId == id) return@launch
            player.addMediaItem(mediaItem(track, remote = true))
            player.seekToNextMediaItem()
            player.prepare()
            player.play()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        bridge.isPlaying = false
        bridge.hasSession = false
        bridge.cache.playingVideoId = null
        bridge.cache.cancelDownloadsExcept()
        session?.release()
        player.release()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_VIDEO_ID = "video_id"
        const val EXTRA_FILE = "file_path"
        const val EXTRA_TITLE = "title"
        const val EXTRA_ARTIST = "artist"
        const val EXTRA_ARTWORK = "artwork"
        const val EXTRA_REMOTE = "remote_autoplay"
        const val ACTION_PLAY = "com.jarbis.alexa.music.PLAY"
        const val ACTION_NEXT = "com.jarbis.alexa.music.NEXT"
        const val ACTION_STOP = "com.jarbis.alexa.music.STOP"
        const val ACTION_DUCK = "com.jarbis.alexa.music.DUCK"
        const val ACTION_UNDUCK = "com.jarbis.alexa.music.UNDUCK"

        fun mediaItem(track: MusicTrack, remote: Boolean): MediaItem = MediaItem.Builder()
            .setUri(track.uri).setMediaId(track.videoId)
            .setCustomCacheKey(if (track.uri.scheme in listOf("http", "https")) track.videoId else null)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(track.title).setArtist(track.artist)
                .setArtworkUri(track.artworkUri)
                .setIsPlayable(true).setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .setExtras(Bundle().apply { putBoolean(EXTRA_REMOTE, remote) }).build())
            .build()

        private fun Intent.trackFor(): MusicTrack? {
            val uri = data ?: getStringExtra(EXTRA_FILE)?.let { path ->
                File(path).takeIf { it.exists() }?.let(Uri::fromFile)
            } ?: return null
            return MusicTrack(getStringExtra(EXTRA_VIDEO_ID) ?: uri.toString(),
                getStringExtra(EXTRA_TITLE) ?: return null, uri,
                getStringExtra(EXTRA_ARTIST), getStringExtra(EXTRA_ARTWORK)?.let(Uri::parse))
        }

        fun intentFor(context: Context, track: MusicTrack): Intent =
            Intent(context, MusicPlayerService::class.java).setAction(ACTION_PLAY)
                .setData(track.uri).putExtra(EXTRA_VIDEO_ID, track.videoId)
                .putExtra(EXTRA_TITLE, track.title)
                .putExtra(EXTRA_ARTIST, track.artist).putExtra(EXTRA_ARTWORK, track.artworkUri?.toString())

        fun stop(context: Context) = sendAction(context, ACTION_STOP)
        fun next(context: Context) = sendAction(context, ACTION_NEXT)
        fun duck(context: Context) = sendAction(context, ACTION_DUCK)
        fun unduck(context: Context) = sendAction(context, ACTION_UNDUCK)

        private fun sendAction(context: Context, action: String) {
            // Evita crear un servicio vacío al detener o aplicar duck sin música.
            if (!MusicBridge.state(context).hasSession) return
            try {
                context.startService(Intent(context, MusicPlayerService::class.java).setAction(action))
            } catch (_: IllegalStateException) {
                // No hay reproducción iniciada desde la aplicación visible.
            }
        }
    }
}
