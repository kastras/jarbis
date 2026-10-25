package com.jarbis.alexa

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.core.content.ContextCompat
import com.jarbis.alexa.ui.MusicScreen

class MainActivity : ComponentActivity() {
    private val assistant: AssistantViewModel by viewModels()
    private val music: MusicViewModel by viewModels()
    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            assistant.start()
            requestNotifications()
        } else music.message("Activa el permiso del micrófono para usar la escucha por voz.")
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    MusicScreen(music, assistant, onEnableVoice = ::enableVoice,
                        onStartPlayback = ::requestNotifications)
                }
            }
        }
    }

    private fun enableVoice() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            assistant.start()
            requestNotifications()
        } else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
