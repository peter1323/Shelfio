package com.shelfio.app.data.books

import com.shelfio.app.data.normalizeForSearch

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
