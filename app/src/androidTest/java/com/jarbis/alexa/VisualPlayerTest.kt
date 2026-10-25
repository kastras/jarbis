package com.jarbis.alexa

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jarbis.alexa.ui.NowPlaying
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VisualPlayerTest {
    @get:Rule val compose = createComposeRule()

    @Test fun narrowPlayerWithLargeTextKeepsTransportAccessible() {
        var stops = 0
        val state = mutableStateOf(MusicUiState(connected = true, hasTrack = true,
            title = "Un título muy largo que debe truncarse sin desplazar ningún control del reproductor",
            artist = "Artista", positionMs = 2300, canNext = true))
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                MaterialTheme {
                    Box(Modifier.width(280.dp)) {
                        NowPlaying(state.value, {}, {}, {}, { stops++ }, {})
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("Anterior").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithContentDescription("Seguir").assertIsDisplayed()
        compose.onNodeWithContentDescription("Siguiente").assertIsDisplayed()
        compose.onNodeWithContentDescription("Parar").assertIsDisplayed().performClick()
        assertEquals(1, stops)
        compose.runOnIdle { state.value = state.value.copy(playRequested = true) }
        compose.onNodeWithContentDescription("Pausar").assertIsDisplayed()
    }
}
