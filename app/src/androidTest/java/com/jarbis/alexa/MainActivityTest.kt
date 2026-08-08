package com.jarbis.alexa

import android.Manifest
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.POST_NOTIFICATIONS,
    )

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun rendersCoreSections() {
        composeRule.onNodeWithText("Configuración").assertExists()
        composeRule.onNodeWithText("Palabra de activación").assertExists()
        composeRule.onNodeWithText("Módulos de comando").assertExists()
        composeRule.onNodeWithText("Tiempo").assertExists()
        composeRule.onNodeWithText("Música").assertExists()
    }

    @Test
    fun startsEngineAndSimulatesWeatherCommand() {
        composeRule.onNodeWithText("Arrancar").performClick()

        // El motor carga el modelo Vosk (de assets) y entra en modo escucha.
        // Primer arranque: la extracción completa (am/, graph/, conf/) puede tardar.
        composeRule.waitUntil(timeoutMillis = 180_000) {
            composeRule.onAllNodesWithText("Escuchando", substring = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Simular un comando de tiempo y comprobar que el pipeline llega al módulo.
        composeRule.onNodeWithText("Probar: tiempo").performClick()
        composeRule.waitUntil(timeoutMillis = 45_000) {
            composeRule.onAllNodesWithText("Oído", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitUntil(timeoutMillis = 45_000) {
            composeRule.onAllNodesWithText("weather", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }
}