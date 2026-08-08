package com.jarbis.alexa.music

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.jarbis.alexa.AppConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

/**
 * Reproducción en segundo plano con Media3 (MediaSessionService,
 * foregroundServiceType="mediaPlayback"). No interfiere con la escucha
 * continua del asistente (Vosk). Al terminar cada canción resuelve la
 * siguiente "parecida" a través de [MusicBridge.nextResolver] y encadena.
 */
class MusicPlayerService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        MusicBridge.state(this)
        createChannel()
        val exo = ExoPlayer.Builder(this).build()
        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    val endedId = exo.currentMediaItem?.mediaId
                    if (!endedId.isNullOrBlank()) enqueueNext(endedId)
                }
            }
        })
        player = exo
        session = MediaSession.Builder(this, exo).build()
    }

    override fun onGetSession(controller: MediaSession.ControllerInfo): MediaSession? = session

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                player?.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        val track = intent.trackFor() ?: return START_STICKY
        play(track)
        return START_STICKY
    }

    private fun play(track: MusicTrack) {
        val exo = player ?: return
        val mediaItem = MediaItem.Builder()
            .setUri(track.uri)
            .setMediaId(track.videoId)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(track.title).build())
            .build()
        exo.setMediaItem(mediaItem)
        exo.prepare()
        exo.playWhenReady = true
        startForeground(AppConfig.MUSIC_NOTIFICATION_ID, buildNotification(track.title))
    }

    /** Terminó una canción: pide la siguiente "parecida" y encadena. */
    private fun enqueueNext(finishedId: String) {
        val resolver = MusicBridge.state(this).nextResolver ?: return
        scope.launch {
            val track = resolver(finishedId) ?: return@launch
            if (track.videoId != finishedId) play(track)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                AppConfig.MUSIC_CHANNEL_ID,
                "Música",
                NotificationManager.IMPORTANCE_LOW,
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String): Notification =
        NotificationCompat.Builder(this, AppConfig.MUSIC_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText("Reproduciendo")
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .build()

    override fun onDestroy() {
        scope.cancel()
        session?.release()
        player?.release()
        player = null
        session = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_VIDEO_ID = "video_id"
        const val EXTRA_FILE = "file_path"
        const val EXTRA_TITLE = "title"
        const val ACTION_PLAY = "com.jarbis.alexa.music.PLAY"
        const val ACTION_STOP = "com.jarbis.alexa.music.STOP"

        private fun Intent?.trackFor(): MusicTrack? {
            if (this == null || action != ACTION_PLAY) return null
            val title = getStringExtra(EXTRA_TITLE) ?: return null
            val filePath = getStringExtra(EXTRA_FILE)
            val uri = filePath?.let { File(it).takeIf { f -> f.exists() }?.let { f -> Uri.fromFile(f) } }
                ?: data
                ?: return null
            return MusicTrack(
                videoId = getStringExtra(EXTRA_VIDEO_ID) ?: uri.toString(),
                title = title,
                uri = uri,
            )
        }

        fun intentFor(context: Context, track: MusicTrack): Intent =
            Intent(context, MusicPlayerService::class.java)
                .setAction(ACTION_PLAY)
                .putExtra(EXTRA_VIDEO_ID, track.videoId)
                .putExtra(EXTRA_FILE, track.uri.path)
                .putExtra(EXTRA_TITLE, track.title)

        fun stop(context: Context) {
            val intent = Intent(context, MusicPlayerService::class.java).setAction(ACTION_STOP)
            try {
                context.startService(intent)
                context.stopService(intent)
            } catch (_: Exception) {
                // servicio aún no arrancado
            }
        }
    }
}