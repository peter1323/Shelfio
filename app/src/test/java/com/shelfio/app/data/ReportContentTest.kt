package com.shelfio.app.data

import com.shelfio.app.data.model.ItemType
import com.shelfio.app.data.model.Loan
import com.shelfio.app.data.model.PurchasePriority
import com.shelfio.app.data.model.ReadingStatus
import com.shelfio.app.data.model.Series
import com.shelfio.app.data.model.SeriesWithVolumes
import com.shelfio.app.data.model.Volume
import com.shelfio.app.data.report.ReportBlock
import com.shelfio.app.data.report.ReportOptions
import com.shelfio.app.data.report.formatNumbers
import com.shelfio.app.data.report.reportContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Las opciones del informe cambian su contenido. */
class ReportContentTest {

    private val dragon = SeriesWithVolumes(
        Series(id = 1, title = "Saga del Dragón", type = ItemType.MANGA, author = "Autora"),
        listOf(
            Volume(id = 1, seriesId = 1, number = 1, status = ReadingStatus.READ, location = "Caja 3"),
            Volume(id = 2, seriesId = 1, number = 2, location = "Caja 3"),
            Volume(id = 3, seriesId = 1, number = 3, owned = false, purchasePriority = PurchasePriority.HIGH),
        ),
        listOf(Loan(id = 1, volumeId = 1, person = "Ana", lentAt = 0)),
    )
    private val onlyWanted = SeriesWithVolumes(Series(id = 2, title = "Solo deseado", type = ItemType.BOOK), listOf(Volume(id = 4, seriesId = 2, number = 1, owned = false)))

    private fun text(options: ReportOptions) = reportContent(listOf(onlyWanted, dragon), options).joinToString("\n") {
        when (it) {
            is ReportBlock.Heading -> "# ${it.text}"
            is ReportBlock.SeriesTitle -> "## ${it.text}"
            is ReportBlock.Line -> it.text
            ReportBlock.Gap -> ""
        }
    }

    @Test
    fun onlyOwned_withoutExtras() {
        val report = text(ReportOptions(includeWishlist = false, includeLocations = false, includeLoans = false))
        assertTrue(report.contains("## Saga del Dragón"))
        assertTrue(report.contains("Tengo: tomos 1-2"))
        assertFalse("una serie que no tengo no sale en el listado", report.contains("Solo deseado"))
        assertFalse(report.contains("Caja 3"))
        assertFalse(report.contains("Lista de deseos"))
        assertFalse(report.contains("Préstamos"))
    }

    @Test
    fun allOptions_addWishlistLocationsAndLoans() {
        val report = text(ReportOptions(includeWishlist = true, includeLocations = true, includeLoans = true))
        assertTrue(report.contains("Ubicación: Caja 3 (tomos 1-2)"))
        assertTrue(report.contains("Tengo: tomos 1-2"))
        assertTrue(report.contains("# Lista de deseos"))
        assertTrue(report.contains("Tomo 3 · prioridad alta"))
        assertTrue(report.contains("## Solo deseado"))
        assertTrue(report.contains("# Préstamos activos"))
        assertTrue(report.contains("Saga del Dragón, tomo 1 → Ana"))
    }

    @Test
    fun seriesAreSortedByTitle() {
        val zeta = dragon.copy(series = dragon.series.copy(id = 9, title = "Zeta"))
        val alfa = dragon.copy(series = dragon.series.copy(id = 8, title = "Álfa"))
        val titles = reportContent(listOf(zeta, alfa), ReportOptions()).filterIsInstance<ReportBlock.SeriesTitle>().map { it.text }
        assertEquals(listOf("Álfa", "Zeta"), titles)
    }

    @Test
    fun formatNumbers_compressesRanges() {
        assertEquals("1-3, 5, 7-8", formatNumbers(listOf(8, 1, 2, 3, 5, 7)))
        assertEquals("4", formatNumbers(listOf(4)))
        assertEquals("-", formatNumbers(emptyList()))
    }
}
