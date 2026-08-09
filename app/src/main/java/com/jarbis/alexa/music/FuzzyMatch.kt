package com.jarbis.alexa.music

import java.text.Normalizer

/**
 * Coincidencia difusa de títulos para búsqueda local "no exacta": acepta
 * substrings, prefijos y typos (distancia Levenshtein por palabra) de forma
 * que "rappers" encuentre "The Rappers Avion". Sin dependencias externas.
 */
object FuzzyMatch {

/** Normaliza a minúsculas sin acentos ni puntuación, unificando espacios. */
    fun normalize(s: String): String = Normalizer.normalize(s.trim().lowercase(), Normalizer.Form.NFD)
        .replace(Regex("[\\p{M}\\p{P}\\p{S}]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()

    /** Distancia Levenshtein con coste 1 por inserción/borrado/sustitución. */
    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in a.indices) {
            curr[0] = i + 1
            for (j in b.indices) {
                val cost = if (a[i] == b[j]) 0 else 1
                curr[j + 1] = minOf(curr[j] + 1, prev[j + 1] + 1, prev[j] + cost)
            }
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[b.length]
    }

    /**
     * Puntuación de coincidencia título↔consulta:
     *  - 3: la consulta es substring del título completo;
     *  - 2: cada palabra de la consulta es substring del título;
     *  - 1: cada palabra de la consulta se parece (prefijo/substring o typo a
     *       distancia [umbralTypos][typoDistance]) a alguna palabra del título;
     *  - 0: sin coincidencia (la caché no contiene nada parecido).
     */
    fun matchScore(title: String, query: String): Int {
        val t = normalize(title).split(' ').filter { it.isNotBlank() }
        val q = normalize(query).split(' ').filter { it.isNotBlank() }
        if (t.isEmpty() || q.isEmpty()) return 0

        val titleJoined = t.joinToString(" ")

        if (titleJoined.contains(q.joinToString(" "))) return 3
        if (q.all { titleJoined.contains(it) }) return 2
        if (q.all { queryWord -> t.any { typoTolerant(it, queryWord) } }) return 1
        return 0
    }

    /** ¿Una palabra del título encaja con una del query siendo tolerante a typos? */
    private fun typoTolerant(titleWord: String, queryWord: String): Boolean {
        if (titleWord == queryWord) return true
        // Palabras cortas: solo prefijo/subtring, jamás distancia (muy ruidoso).
        if (queryWord.length <= 3) return titleWord.contains(queryWord)
        if (titleWord.contains(queryWord)) return true
        return levenshtein(titleWord, queryWord) <= maxDistance(queryWord.length)
    }

    private fun maxDistance(wordLength: Int): Int = when {
        wordLength <= 4 -> 1
        wordLength <= 7 -> 2
        else -> 3
    }
}