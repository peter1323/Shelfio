package com.shelfio.app.data.books

import com.shelfio.app.data.normalizeForSearch

/** Una línea de texto leída en la portada y su altura en píxeles (más alta = letra más grande). */
data class CoverLine(val text: String, val height: Int)

/**
 * Posibles títulos a partir del texto de una portada: primero las líneas con letra más grande
 * (el título suele ser lo más grande), sin repetidas y sin cosas que no son títulos
 * (números sueltos, códigos, precios o textos de una letra).
 */
fun titleCandidates(lines: List<CoverLine>): List<String> =
    lines.asSequence()
        .map { it.copy(text = it.text.replace(Regex("\\s+"), " ").trim()) }
        .filter { line -> line.text.count { it.isLetter() } >= 2 }
        .filterNot { line -> line.text.any { it == '€' || it == '$' } }
        .sortedByDescending { it.height }
        .map { it.text }
        .distinctBy { it.lowercase() }
        .take(8)
        .toList()

/**
 * De todos los títulos de una obra, el que más se parece a lo buscado (por ejemplo «La Nobleza de las Flores»
 * entre el romaji, el inglés, el japonés y los sinónimos). Si ninguno coincide, el primero (el principal).
 */
fun bestTitle(query: String, titles: List<String>): String {
    val q = normalizeForSearch(query)
    return titles.firstOrNull { normalizeForSearch(it) == q }
        ?: titles.firstOrNull { normalizeForSearch(it).contains(q) || (q.contains(normalizeForSearch(it)) && it.length > 3) }
        ?: titles.firstOrNull().orEmpty()
}

/** Quita resultados repetidos (misma fuente, mismo título y mismo autor; p. ej. varios registros del mismo tomo). */
fun withoutDuplicates(results: List<BookResult>): List<BookResult> =
    results.distinctBy { Triple(it.source, normalizeForSearch(it.title), normalizeForSearch(it.authors)) }
