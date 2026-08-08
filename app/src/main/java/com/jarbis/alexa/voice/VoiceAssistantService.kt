package com.jarbis.alexa.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.jarbis.alexa.AlexaApp
import com.jarbis.alexa.AppConfig
import com.jarbis.alexa.MainActivity
import androidx.core.app.NotificationCompat

/**
 * Servicio en primer plano que mantiene el micrófono (foregroundServiceType
 * "microphone") mientras el asistente está activo.
 */
class VoiceAssistantService : Service() {

    private val engine get() = (application as AlexaApp).engine

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(AppConfig.NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                engine.stop()
                stopSelf()
                return START_NOT_STICKY
            }
        }
        engine.start()
        return START_STICKY
    }

    override fun onDestroy() {
        if (engine.uiState().value.running) engine.stop()
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                AppConfig.CHANNEL_ID,
                "Asistente de voz",
                NotificationManager.IMPORTANCE_LOW,
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, VoiceAssistantService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, AppConfig.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Jarbis activo")
            .setContentText("Di la palabra de activación para dar órdenes")
            .setContentIntent(openIntent)
            .addAction(0, "Detener", stopIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.jarbis.alexa.action.STOP"

        fun start(context: Context) {
            val intent = Intent(context, VoiceAssistantService::class.java)
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, VoiceAssistantService::class.java).setAction(ACTION_STOP)
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }
    }
}