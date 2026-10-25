package com.jarbis.alexa

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jarbis.alexa.voice.Phase
import com.jarbis.alexa.voice.VoiceAssistantService
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun opensPlayerWithoutStartingMicrophone() {
        composeRule.onNodeWithText("Reproductor").assertExists()
        composeRule.onNodeWithText("Canción o artista").assertExists()
        composeRule.onNodeWithText("Buscar en internet").assertExists()
        assertFalse(AlexaApp.engine(composeRule.activity).uiState().value.running)
    }

    @Test
    fun libraryOffersFolderAndDownloadedFilters() {
        composeRule.onNodeWithText("Mi música").performClick()
        composeRule.onNodeWithText("Elegir carpeta").assertExists()
        composeRule.onNodeWithText("Todas").assertExists()
        composeRule.onNodeWithText("Carpeta").performClick()
        composeRule.onNodeWithText("Descargadas").performClick()
        composeRule.onNodeWithText("Actualizar").assertExists()
    }

    @Test
    fun settingsRetainActivationAndIndependentModules() {
        composeRule.onNodeWithText("Configuración").performClick()
        composeRule.onNodeWithText("Activar escucha por voz").assertExists()
        composeRule.onNodeWithText("Palabra de activación").assertExists()
        composeRule.onNodeWithText("Módulos de comando").performScrollTo().assertExists()
        composeRule.onNodeWithText("Tiempo").assertExists()
        composeRule.onNodeWithText("Música").assertExists()
        composeRule.onNodeWithText("Almacenamiento de música").performScrollTo().assertExists()
        composeRule.onNodeWithText("Límite máximo (GiB)").assertExists()
        composeRule.onNodeWithText("Elegir carpeta").assertExists()
    }

    @Test
    fun voiceStartsOnlyWhenEnabledAndAcceptsActivationChange() {
        val activity = composeRule.activity
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.grantRuntimePermission(activity.packageName, Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) {
            automation.grantRuntimePermission(activity.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        val engine = AlexaApp.engine(activity)
        composeRule.onNodeWithText("Configuración").performClick()
        try {
            composeRule.onNodeWithText("Activar escucha por voz").performClick()
            composeRule.waitUntil(180_000) { engine.uiState().value.phase == Phase.WAKE }
            composeRule.waitUntil(10_000) {
                engine.uiState().value.logs.any { it.text.contains("Cancelación de eco acústico") }
            }
            composeRule.onNodeWithText("Palabra de activación").performTextReplacement("jarbis")
            composeRule.waitUntil(10_000) { engine.uiState().value.wakeWord == "jarbis" }
        } finally {
            VoiceAssistantService.stop(activity)
            composeRule.waitUntil(10_000) { !engine.uiState().value.running }
        }
    }
}
