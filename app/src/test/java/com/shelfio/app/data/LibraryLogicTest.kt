package com.shelfio.app.data

import com.shelfio.app.data.model.ItemType
import com.shelfio.app.data.model.Loan
import com.shelfio.app.data.model.PublicationStatus
import com.shelfio.app.data.model.PurchasePriority
import com.shelfio.app.data.model.ReadingStatus
import com.shelfio.app.data.model.Series
import com.shelfio.app.data.model.SeriesWithVolumes
import com.shelfio.app.data.model.Volume
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pruebas de la lógica pura: búsqueda (incluida la ubicación), progreso, pendientes y «añadir siguiente tomo». */
class LibraryLogicTest {

    private fun series(id: Long, title: String, type: ItemType = ItemType.MANGA, author: String = "") =
        Series(id = id, title = title, type = type, author = author, createdAt = id, updatedAt = id)

    private fun volume(seriesId: Long, number: Int, status: ReadingStatus = ReadingStatus.UNREAD, owned: Boolean = true, location: String = "") =
        Volume(id = seriesId * 100 + number, seriesId = seriesId, number = number, status = status, owned = owned, location = location)

    private val dragon = SeriesWithVolumes(
        series(1, "Saga del Dragón", author = "Autora"),
        listOf(
            volume(1, 1, ReadingStatus.READ, location = "Estantería 2"),
            volume(1, 2, ReadingStatus.READ, location = "Caja 3"),
            volume(1, 3, ReadingStatus.READING, location = "Mesilla"),
            volume(1, 4, owned = false),
        ),
    )
    private val faro = SeriesWithVolumes(series(2, "El faro del norte", ItemType.BOOK), listOf(volume(2, 1, location = "Caja 3")))
    private val neon = SeriesWithVolumes(series(3, "Ciudad Neón", ItemType.COMIC), listOf(volume(3, 1)))
    private val library = listOf(dragon, faro, neon)

    // --- Búsqueda ---

    @Test
    fun search_ignoresCaseAndAccents() {
        assertEquals(listOf(dragon), filterLibrary(library, "dragon", null))
        assertEquals(listOf(neon), filterLibrary(library, "NEON", null))
    }

    @Test
    fun search_filtersByType() {
        assertEquals(listOf(faro), filterLibrary(library, "", ItemType.BOOK))
    }

    @Test
    fun search_findsSeriesByLocation() {
        assertEquals(listOf(faro, dragon), filterLibrary(library, "caja 3", null))
    }

    // --- Búsqueda por ubicación ---

    @Test
    fun volumesAtLocation_returnsOnlyVolumesStoredThere() {
        val found = volumesAtLocation(library, "Caja 3").map { it.series.id to it.volume.number }
        assertEquals(listOf(2L to 1, 1L to 2), found)
    }

    @Test
    fun volumesAtLocation_partialAndAccentInsensitive() {
        assertEquals(1, volumesAtLocation(library, "estanteria").size)
        assertEquals(1, volumesAtLocation(library, "mesi").size)
    }

    @Test
    fun volumesAtLocation_ignoresVolumesNotOwned() {
        val notOwnedWithLocation = SeriesWithVolumes(series(9, "X"), listOf(volume(9, 1, owned = false, location = "Caja 3")))
        assertTrue(volumesAtLocation(listOf(notOwnedWithLocation), "caja 3").isEmpty())
    }

    @Test
    fun volumesAtLocation_emptyQueryReturnsNothing() {
        assertTrue(volumesAtLocation(library, "  ").isEmpty())
    }

    // --- Progreso y estado calculados ---

    @Test
    fun progress_isReadVolumesOverRegistered() {
        assertEquals(0.5f, dragon.progress, 0.0001f)
        assertEquals(0f, neon.progress, 0.0001f)
    }

    @Test
    fun displayStatus_readingWhenAnyVolumeIsBeingRead() {
        assertEquals(ReadingStatus.READING, dragon.displayStatus)
    }

    @Test
    fun singleBook_whenOnlyOneVolume() {
        assertTrue(faro.isSingleBook)
        assertFalse(dragon.isSingleBook)
    }

    // --- Inicio ---

    @Test
    fun pendingPurchase_listsVolumesNotOwned() {
        assertEquals(listOf(1L to 4), pendingPurchase(library).map { it.series.id to it.volume.number })
    }

    @Test
    fun readingNow_listsVolumesBeingRead() {
        assertEquals(listOf(3), readingNow(library).map { it.volume.number })
    }

    // --- Lista de deseos ---

    @Test
    fun pendingPurchase_highPriorityFirst() {
        val low = SeriesWithVolumes(series(5, "A primero por título"), listOf(volume(5, 1, owned = false).copy(purchasePriority = PurchasePriority.LOW)))
        val high = SeriesWithVolumes(series(6, "Z último por título"), listOf(volume(6, 1, owned = false).copy(purchasePriority = PurchasePriority.HIGH)))
        val order = pendingPurchase(listOf(low, high, dragon)).map { it.series.id }
        assertEquals(listOf(6L, 5L, 1L), order)
    }

    @Test
    fun wishlist_groupsBySeriesSortsAndSearches() {
        val a = SeriesWithVolumes(series(5, "Alfa"), listOf(
            volume(5, 1, owned = false).copy(purchasePriority = PurchasePriority.LOW, wishlistAddedAt = 300),
            volume(5, 2, owned = false).copy(purchaseNote = "segunda mano", wishlistAddedAt = 100),
        ))
        val b = SeriesWithVolumes(series(6, "Beta"), listOf(volume(6, 1, owned = false).copy(purchasePriority = PurchasePriority.HIGH, wishlistAddedAt = 200)))
        val items = listOf(a, b, neon)

        assertEquals(listOf(6L, 5L), wishlist(items, "", WishlistSort.PRIORITY).map { it.series.id })
        assertEquals(listOf(5L, 6L), wishlist(items, "", WishlistSort.TITLE).map { it.series.id })
        assertEquals(listOf(5L, 6L), wishlist(items, "", WishlistSort.DATE).map { it.series.id })
        // Dentro del grupo, por prioridad: el tomo con prioridad baja va antes que el que no tiene.
        assertEquals(listOf(1, 2), wishlist(items, "", WishlistSort.PRIORITY).first { it.series.id == 5L }.volumes.map { it.number })
        // La búsqueda mira también la nota de compra.
        val found = wishlist(items, "segunda", WishlistSort.TITLE)
        assertEquals(listOf(5L to listOf(2)), found.map { it.series.id to it.volumes.map { v -> v.number } })
    }

    @Test
    fun missingVolumeNumbers_doesNotDuplicateExisting() {
        assertEquals(listOf(5, 6), missingVolumeNumbers(dragon.volumes, 6))
        assertEquals(emptyList<Int>(), missingVolumeNumbers(dragon.volumes, 3))
    }

    @Test
    fun ownershipRules_keepWishlistAndLocationConsistent() {
        val bought = volume(1, 1, owned = true).copy(purchasePriority = PurchasePriority.HIGH, purchaseNote = "x", wishlistAddedAt = 5).withOwnershipRules(99)
        assertEquals(null, bought.purchasePriority)
        assertEquals("", bought.purchaseNote)
        assertEquals(null, bought.wishlistAddedAt)

        val wanted = volume(1, 1, owned = false, location = "Caja 3").withOwnershipRules(99)
        assertEquals("", wanted.location)
        assertEquals(99L, wanted.wishlistAddedAt)
        assertEquals(5L, wanted.copy(wishlistAddedAt = 5).withOwnershipRules(99).wishlistAddedAt)
    }

    // --- Estado editorial ---

    @Test
    fun publishedProgressAndCompleteCollection() {
        val finished = dragon.copy(series = dragon.series.copy(statusInMyLanguage = PublicationStatus.FINISHED, publishedInMyLanguage = 3))
        // Tiene 1, 2 y 3; el 4 no lo tiene pero ya no cuenta (solo hay 3 publicados).
        assertEquals(3, finished.ownedOfPublished)
        assertTrue(finished.isCompleteCollection)

        val moreAnnounced = finished.copy(series = finished.series.copy(publishedInMyLanguage = 5))
        assertEquals(3, moreAnnounced.ownedOfPublished)
        assertFalse(moreAnnounced.isCompleteCollection)
        assertTrue(moreAnnounced.isMissingVolumes)

        // Sin dato de publicados: no hay «X de Y» ni colección completa.
        assertNull(dragon.ownedOfPublished)
        assertFalse(dragon.isCompleteCollection)
        // Publicando: tengo todos los publicados pero no está finalizada.
        assertFalse(finished.copy(series = finished.series.copy(statusInMyLanguage = PublicationStatus.PUBLISHING)).isCompleteCollection)
    }

    @Test
    fun collectionFilters() {
        val complete = SeriesWithVolumes(series(7, "Completa").copy(statusInMyLanguage = PublicationStatus.FINISHED, publishedInMyLanguage = 1), listOf(volume(7, 1)))
        val publishing = SeriesWithVolumes(series(8, "Sigue saliendo").copy(statusInMyLanguage = PublicationStatus.PUBLISHING, publishedInMyLanguage = 2), listOf(volume(8, 1)))
        val items = listOf(complete, publishing, dragon, neon)
        assertEquals(listOf(7L), filterLibrary(items, "", null, CollectionFilter.COMPLETE).map { it.series.id })
        assertEquals(listOf(1L, 8L), filterLibrary(items, "", null, CollectionFilter.MISSING).map { it.series.id })
        assertEquals(listOf(8L), filterLibrary(items, "", null, CollectionFilter.PUBLISHING).map { it.series.id })
    }

    // --- Préstamos ---

    @Test
    fun loanProblem_bothDirections() {
        val owned = volume(1, 1)
        assertEquals("Solo puedes prestar lo que tienes.", loanProblem(owned.copy(owned = false), emptyList()))
        assertEquals("Ya tiene un préstamo activo.", loanProblem(owned, listOf(Loan(volumeId = owned.id, person = "Ana", lentAt = 0))))
        // «Me lo han prestado»: solo en lo que no es mío.
        assertNull(loanProblem(owned.copy(owned = false), emptyList(), borrowed = true))
        assertEquals("Si es tuyo no te lo han prestado. Desmarca «Lo tengo» primero.", loanProblem(owned, emptyList(), borrowed = true))
        assertNull(loanProblem(owned, listOf(Loan(volumeId = owned.id, person = "Ana", lentAt = 0, returnedAt = 5))))
    }

    @Test
    fun isOverdue_afterTheWholeDueDay() {
        val day = 24 * 60 * 60 * 1000L
        val loan = Loan(volumeId = 1, person = "Ana", lentAt = 0, dueAt = 10 * day)
        assertFalse(isOverdue(loan, now = 10 * day + 5))
        assertTrue(isOverdue(loan, now = 11 * day))
        assertFalse(isOverdue(loan.copy(returnedAt = 1), now = 20 * day))
        assertFalse(isOverdue(loan.copy(dueAt = null), now = 20 * day))
    }

    @Test
    fun loanItems_activeFirstAndFilterByPerson() {
        val withLoans = dragon.copy(loans = listOf(
            Loan(id = 1, volumeId = 101, person = "Luis", lentAt = 1, returnedAt = 2),
            Loan(id = 2, volumeId = 102, person = "Ana", lentAt = 3),
        ))
        val loans = loanItems(listOf(withLoans))
        assertEquals(listOf(2L, 1L), loans.map { it.loan.id })
        assertEquals(listOf(1L), filterLoans(loans, "luis").map { it.loan.id })
    }

    // --- Añadir siguiente tomo ---

    @Test
    fun nextVolume_usesNextNumberAndLastLocation() {
        val ownedOnly = dragon.volumes.filter { it.owned }
        val next = nextVolume(1, ownedOnly)
        assertEquals(4, next.number)
        assertEquals("Mesilla", next.location)
        assertEquals(1L, next.seriesId)
        assertEquals(0L, next.id)
    }

    @Test
    fun nextVolume_ofEmptySeriesIsOne() {
        assertEquals(1, nextVolume(5, emptyList()).number)
    }

    // --- Escaneo ---

    @Test
    fun findMatchingSeries_matchesTitleContainedInResult() {
        assertEquals(listOf(dragon), findMatchingSeries(library, "Saga del dragón 5"))
        assertTrue(findMatchingSeries(library, "Otra cosa").isEmpty())
    }

    @Test
    fun findVolumeByIsbn_findsDuplicates() {
        val withIsbn = SeriesWithVolumes(series(7, "Con ISBN"), listOf(volume(7, 1).copy(isbn = "978-0-14-032872-1")))
        assertEquals(7L, findVolumeByIsbn(listOf(withIsbn), "9780140328721")?.series?.id)
        assertNull(findVolumeByIsbn(library, "9780140328721"))
    }
}
