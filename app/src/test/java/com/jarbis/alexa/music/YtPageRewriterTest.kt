package com.jarbis.alexa.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YtPageRewriterTest {

    @Test
    fun `reescribe la forma escapada de ytInitialData a objeto literal`() {
        // Fragmento equivalente al que sirve m.youtube.com (JSON escapado).
        val html = "<html><script>window.cfg={}; ytInitialData = '\\x7b\\x22a\\x22:\\x7b\\x22b\\x22:\\x5b1,2\\x5d\\x7d\\x7d';</script></html>"

        val out = YtPageRewriter.rewrite(html)

        assertTrue(out.contains("var ytInitialData = {\"a\":{\"b\":[1,2]}};"))
    }

    @Test
    fun `no altera la forma ya clasica de objeto literal`() {
        val html = "<script>var ytInitialData = {\"responseContext\":{\"ok\":true}};</script>"

        val out = YtPageRewriter.rewrite(html)

        assertEquals(html, out)
    }

    @Test
    fun `tambien reescribe ytInitialPlayerResponse`() {
        val html = "ytInitialPlayerResponse = '\\x7b\\x22ok\\x22:\\x22si\\x22\\x7d';"

        val out = YtPageRewriter.rewrite(html)

        assertTrue(out.contains("var ytInitialPlayerResponse = {\"ok\":\"si\"};"))
    }

    @Test
    fun `desescapa secuencias comunes de JS`() {
        val out = YtPageRewriter.unescapeJs("a\\n\\t\\\"\\x22\\u0041\\\\b")

        assertEquals("a\n\t\"\"A\\b", out)
    }
}