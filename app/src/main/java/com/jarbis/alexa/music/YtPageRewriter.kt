package com.jarbis.alexa.music

/**
 * Adaptador del HTML de YouTube para NewPipeExtractor. Las páginas modernas de
 * m.youtube.com sirven `ytInitialData = '\x7b\x22...'` (JSON escapado dentro de
 * una cadena JS) en vez de `var ytInitialData = {...};`; el regex del extractor
 * no entiende esa forma y falla con "Could not get ytInitialData". Aquí se
 * unesca la cadena y se reescribe como objeto literal para que el extractor
 * pueda parsearlo.
 */
object YtPageRewriter {

    private val escapedForms = listOf("ytInitialData", "ytInitialPlayerResponse")

    /** Reescribe `[(var )?]NAME = '...cadenajson...'` a `var NAME = {...};`. */
    fun rewrite(html: String): String {
        if (!escapedForms.any { html.contains("$it = '") }) return html
        var out = html
        for (marker in escapedForms) {
            val pattern = Regex("(?:var\\s+)?" + marker + "\\s*=\\s*'((?:[^'\\\\]|\\\\.)*)';")
            out = pattern.replace(out) { match ->
                "var $marker = ${unescapeJs(match.groupValues[1])};"
            }
        }
        return out
    }

    /** Desescapa una cadena JS simple (\\xNN, \\uNNNN, \\n, \\t, \\', \\", \\\\ …). */
    fun unescapeJs(s: String): String {
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c != '\\' || i + 1 >= s.length) {
                sb.append(c)
                i++
                continue
            }
            i++
            when (val esc = s[i]) {
                'n' -> sb.append('\n')
                't' -> sb.append('\t')
                'r' -> sb.append('\r')
                'b' -> sb.append('\b')
                'f' -> sb.append('\u000C')
                '\\' -> sb.append('\\')
                '\'' -> sb.append('\'')
                '"' -> sb.append('"')
                '/' -> sb.append('/')
                'x' -> {
                    val hex = s.substring(i + 1, (i + 3).coerceAtMost(s.length))
                    i += 2
                    sb.append(hex.toIntOrNull(16)?.toChar() ?: esc)
                }
                'u' -> {
                    val hex = s.substring(i + 1, (i + 5).coerceAtMost(s.length))
                    i += 4
                    sb.append(hex.toIntOrNull(16)?.toChar() ?: esc)
                }
                else -> sb.append(esc)
            }
            i++
        }
        return sb.toString()
    }
}