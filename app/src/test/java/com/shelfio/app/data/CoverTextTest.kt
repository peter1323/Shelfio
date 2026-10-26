package com.shelfio.app.data

import com.shelfio.app.data.books.CoverLine
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
    fun emptyCover_givesNoCandidates() {
        assertEquals(emptyList<String>(), titleCandidates(emptyList()))
    }
}
