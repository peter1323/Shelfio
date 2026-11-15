package com.shelfio.app.data

import com.shelfio.app.data.model.ItemType
import com.shelfio.app.data.model.PurchasePriority
import com.shelfio.app.data.model.Series
import com.shelfio.app.data.model.SeriesWithVolumes
import com.shelfio.app.data.model.Volume
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Calculadora de coste: precios, totales, filtros de la lista de deseos, presupuesto y formato. */
class CostTest {

    private fun volume(id: Long, seriesId: Long, number: Int, owned: Boolean, price: Int? = null, priority: PurchasePriority? = null) =
        Volume(id = id, seriesId = seriesId, number = number, owned = owned, priceCents = price, purchasePriority = priority)

    /** Manga a 8,50 €: tengo el 1; me faltan el 2 (precio de la serie) y el 3 (edición especial a 15 €). */
    private val manga = SeriesWithVolumes(
        Series(id = 1, title = "Saga", type = ItemType.MANGA, volumePriceCents = 850),
        listOf(
            volume(1, 1, 1, owned = true),
            volume(2, 1, 2, owned = false, priority = PurchasePriority.HIGH),
            volume(3, 1, 3, owned = false, price = 1500),
        ),
    )

    /** Cómic sin precio: me falta un tomo que no suma. */
    private val comic = SeriesWithVolumes(
        Series(id = 2, title = "Ciudad", type = ItemType.COMIC),
        listOf(volume(4, 2, 1, owned = true), volume(5, 2, 2, owned = false)),
    )

    private val library = listOf(manga, comic)

    @Test
    fun volumePrice_volumeBeatsSeries() {
        assertEquals(850, volumePrice(manga.series, manga.volumes[1]))
        assertEquals(1500, volumePrice(manga.series, manga.volumes[2]))
        assertNull(volumePrice(comic.series, comic.volumes[1]))
    }

    @Test
    fun pendingCost_sumsMissingVolumesAndCountsSeriesWithoutPrice() {
        // 8,50 + 15,00; el cómic no suma pero se avisa. Los tomos que ya tengo no cuentan.
        assertEquals(Cost(volumes = 3, cents = 2350, seriesWithoutPrice = 1), libraryCost(library, owned = false))
    }

    @Test
    fun collectionValue_sumsOwnedVolumes() {
        assertEquals(Cost(volumes = 2, cents = 850, seriesWithoutPrice = 1), libraryCost(library, owned = true))
    }

    @Test
    fun wishlistCost_followsFilters() {
        val cost = { type: ItemType?, priority: PurchasePriority? -> wishlistCost(wishlist(library, "", WishlistSort.TITLE, type, priority)) }
        assertEquals(Cost(3, 2350, 1), cost(null, null))
        assertEquals(Cost(2, 2350, 0), cost(ItemType.MANGA, null))
        assertEquals(Cost(1, 0, 1), cost(ItemType.COMIC, null))
        assertEquals(Cost(1, 850, 0), cost(null, PurchasePriority.HIGH))
        assertEquals(Cost(), cost(ItemType.BOOK, null))
    }

    @Test
    fun monthsToComplete_roundsUp() {
        assertEquals(11, monthsToComplete(41250, 4000))
        assertEquals(1, monthsToComplete(4000, 4000))
        assertEquals(2, monthsToComplete(4001, 4000))
        assertNull(monthsToComplete(41250, 0))
        assertNull(monthsToComplete(0, 4000))
        assertEquals("Con 40,00 €/mes la completas en 11 meses", budgetLine(41250, 4000))
    }

    @Test
    fun formatEuros_spanishFormat() {
        assertEquals("1.240,50 €", formatEuros(124050))
        assertEquals("0,05 €", formatEuros(5))
        assertEquals("1.000.000,00 €", formatEuros(100000000))
        assertEquals("Faltan 3 tomos · 23,50 €", missingLine(Cost(3, 2350, 1)))
        assertEquals("Falta 1 volumen · sin precio", missingLine(Cost(1, 0, 1), "volumen", "volúmenes"))
        assertEquals("3 series sin precio", withoutPriceLine(Cost(5, 0, 3)))
        assertNull(withoutPriceLine(Cost(5, 100, 0)))
    }

    @Test
    fun parseEuros_commaOrDot_noNegatives() {
        assertEquals(850, parseEuros("8,5"))
        assertEquals(850, parseEuros("8.50"))
        assertEquals(1200, parseEuros("12"))
        assertNull(parseEuros(""))
        assertNull(parseEuros("-5"))
        assertNull(parseEuros("1,2,3"))
        // Al escribir no se cuelan signos ni letras, y solo quedan dos decimales.
        assertEquals("5", cleanEuros("-5"))
        assertEquals("8,50", cleanEuros("8,509€"))
        assertEquals("1240,50", eurosText(124050))
        assertEquals("", eurosText(null))
    }
}
