package com.shelfio.app.data.books

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
