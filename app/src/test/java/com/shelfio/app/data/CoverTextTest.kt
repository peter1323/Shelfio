package com.shelfio.app.data

import com.shelfio.app.data.books.CoverLine
import com.shelfio.app.data.books.bestTitle
import com.shelfio.app.data.books.titleCandidates
import org.junit.Assert.assertEquals
import org.junit.Test

class CoverTextTest {

    @Test
    fun biggestTextFirst_withoutNoise() {
        val lines = listOf(
            CoverLine("Saka Mikami", 30),
            CoverLine("LA  NOBLEZA DE LAS FLORES", 120),
            CoverLine("3", 200), // número de tomo suelto
            CoverLine("8,95 €", 20), // precio
            CoverLine("la nobleza de las flores", 40), // repetido
            CoverLine("PANINI", 25),
        )
        assertEquals(listOf("LA NOBLEZA DE LAS FLORES", "Saka Mikami", "PANINI"), titleCandidates(lines))
    }

    @Test
    fun bestTitle_prefersTheOneSearched() {
        val titles = listOf("Kaoru Hana wa Rin to Saku", "The Fragrant Flower Blooms With Dignity", "薫る花は凛と咲く", "La Nobleza de las Flores", "A Nobreza das Flores")
        assertEquals("La Nobleza de las Flores", bestTitle("la nobleza de las flores", titles))
        assertEquals("La Nobleza de las Flores", bestTitle("LA NOBLEZA DE LAS FLORES 3", titles))
        // Sin coincidencia: el título principal.
        assertEquals("Kaoru Hana wa Rin to Saku", bestTitle("otra cosa", titles))
    }

    @Test
    fun emptyCover_givesNoCandidates() {
        assertEquals(emptyList<String>(), titleCandidates(emptyList()))
    }
}
