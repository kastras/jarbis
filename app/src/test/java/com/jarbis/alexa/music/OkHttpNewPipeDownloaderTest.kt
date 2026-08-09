package com.jarbis.alexa.music

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.schabi.newpipe.extractor.downloader.Request

class OkHttpNewPipeDownloaderTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `ejecuta GET y expone codigo cabeceras y cuerpo`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"ok\":true}"),
        )

        val downloader = OkHttpNewPipeDownloader()
        val request = Request.newBuilder().get(server.url("/").toString()).build()
        val response = downloader.execute(request)

        assertEquals(200, response.responseCode())
        assertEquals("{\"ok\":true}", response.responseBody())
        assertTrue(response.responseHeaders()["Content-Type"].orEmpty().any { it.contains("application/json") })
    }

    @Test
    fun `lleva las cabeceras de la peticion al servidor`() = runTest {
        server.enqueue(MockResponse().setBody("{}"))

        val downloader = OkHttpNewPipeDownloader()
        val request = Request.newBuilder()
            .get(server.url("/").toString())
            .setHeader("X-Goog-Test", "hola")
            .build()
        downloader.execute(request)

        val recorded = server.takeRequest()
        assertEquals("hola", recorded.getHeader("X-Goog-Test"))
    }

    @Test
    fun `cliente sigue redirecciones`() = runTest {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/final"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("destino"))

        val downloader = OkHttpNewPipeDownloader()
        val response = downloader.execute(Request.newBuilder().get(server.url("/").toString()).build())

        assertEquals(200, response.responseCode())
        assertEquals("destino", response.responseBody())
    }
}