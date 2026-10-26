package com.shelfio.app.data

import com.shelfio.app.data.books.BookResult
import com.shelfio.app.data.books.bestTitle
import com.shelfio.app.data.books.withoutDuplicates
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchResultsTest {

    @Test
    fun bestTitle_prefersTheOneSearched() {
        val titles = listOf("Kaoru Hana wa Rin to Saku", "The Fragrant Flower Blooms With Dignity", "薫る花は凛と咲く", "La Nobleza de las Flores", "A Nobreza das Flores")
        assertEquals("La Nobleza de las Flores", bestTitle("la nobleza de las flores", titles))
        assertEquals("La Nobleza de las Flores", bestTitle("LA NOBLEZA DE LAS FLORES 3", titles))
        // Sin coincidencia: el título principal.
        assertEquals("Kaoru Hana wa Rin to Saku", bestTitle("otra cosa", titles))
    }

    @Test
    fun withoutDuplicates_keepsOnePerSourceTitleAndAuthor() {
        fun result(source: String, title: String) = BookResult("", title, "Aoi Miura", "", "", "", null, source)
        val results = listOf(
            result("Open Library", "La caja azul nº 01"),
            result("Open Library", "La caja azul nº 01"),
            result("Open Library", "La Caja Azul Nº 01"),
            result("AniList", "La caja azul"),
        )
        assertEquals(listOf("Open Library", "AniList"), withoutDuplicates(results).map { it.source })
    }
}
