package com.jarbis.alexa.music

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class PipedClientTest {

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

    private fun client(): PipedClient = PipedClient(instances = listOf(server.url("/").toString()))

    @Test
fun `search devuelve el primer video con audio de la respuesta`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"items":[
                        {"type":"channel","url":"/channel/UC1","title":"Queen"},
                        {"type":"stream","url":"/watch?v=abc123","title":"Bohemian Rhapsody","duration":355}
                    ]}"""
                ),
        )

        val result = client().search("queen")
        assertNotNull(result)

        assertEquals("Bohemian Rhapsody", result?.title)
    }

    @Test
    fun `audioStream elige el de mayor bitrate`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"title":"T","audioStreams":[
                        {"url":"http://l/m4a.m4a","format":"M4A","mimeType":"audio/mp4","bitrate":64000},
                        {"url":"http://l/opus.webm","format":"OPUS","mimeType":"audio/webm","bitrate":160000}
                    ]}"""
                ),
        )

        val stream = client().audioStream("abc")
        assertNotNull(stream)
        assertEquals(160000, stream?.bitrate)
    }

@Test
    fun `videoIdFrom extrae el id de urls watch y video`() {
        assertEquals("abc123", PipedClient.videoOf(PipedStreamItem(url = "/watch?v=abc123", title = "T")))
        assertEquals("rel1", PipedClient.videoOf(PipedStreamItem(url = "/video/rel1", title = "T")))
        assertNull(PipedClient.videoOf(PipedStreamItem(url = "/channel/UC1", title = "T")))
    }

    @Test
    fun `si la primera instancia cae prueba la segunda`() = runTest {
        val second = MockWebServer()
        second.start()

        try {
            server.enqueue(MockResponse().setResponseCode(500))
            second.enqueue(
                MockResponse()
                    .setBody("""{"items":[{"type":"stream","url":"/video/ok","title":"Backup"}]}"""),
            )

            val client = PipedClient(instances = listOf(server.url("/").toString(), second.url("/").toString()))
            val result = client.search("queen")

            assertNotNull(result)
            assertEquals("Backup", result?.title)
        } finally {
            second.shutdown()
        }
    }

    @Test
    fun `related devuelve musica parecida`() = runTest {
        server.enqueue(
            MockResponse()
                .setBody(
                    """{"relatedStreams":[
                        {"type":"stream","url":"/video/rel1","title":"Another One Bites the Dust","duration":200},
                        {"type":"stream","url":"/video/rel2","title":"We Will Rock You","duration":200}
                    ]}"""
                ),
        )

        val related = client().related("abc")
        assertEquals(2, related.size)
        assertEquals("rel1", PipedClient.videoOf(related[0]))
    }
}