package com.shelfio.app.data

import com.shelfio.app.data.model.ItemType
import com.shelfio.app.data.model.PublicationStatus
import com.shelfio.app.data.model.PurchasePriority
import com.shelfio.app.data.model.ReadingStatus
import com.shelfio.app.data.model.Series
import com.shelfio.app.data.model.Volume
import kotlinx.coroutines.flow.first

/**
 * Biblioteca de ejemplo para probar la app sin escribir nada (solo en la versión de pruebas).
 * Hay al menos 3 de cada tipo con opciones distintas: leídos y sin leer, ubicaciones, lista de deseos,
 * préstamos (al día, vencido y «me lo han prestado»), estados editoriales y otros títulos.
 * Todo va a la colección [DEMO_COLLECTION] para poder borrarlo de golpe.
 */
class DemoData(private val repository: ShelfioRepository) {

    /** Préstamo de ejemplo: [daysLeft] negativo = vencido; null = sin fecha. */
    private class DemoLoan(val number: Int, val person: String, val daysLeft: Int? = null, val borrowed: Boolean = false)

    private class DemoSeries(val series: Series, val volumes: List<Volume>, val loans: List<DemoLoan> = emptyList())

    /** Carga los ejemplos. Devuelve cuántas series ha creado (0 si ya estaban cargados). */
    suspend fun load(): Int {
        if (demoCollectionId() != null) return 0
        val collectionId = repository.createCollection(DEMO_COLLECTION)
        examples().forEach { demo ->
            val seriesId = repository.createSeries(demo.series, demo.volumes.first())
            demo.volumes.drop(1).forEach { repository.addVolume(it.copy(seriesId = seriesId)) }
            repository.setInCollection(seriesId, collectionId, true)
            val saved = repository.getSeries(seriesId) ?: return@forEach
            demo.loans.forEach { loan ->
                val volume = saved.volumes.first { it.number == loan.number }
                val now = System.currentTimeMillis()
                val dueAt = loan.daysLeft?.let { now + it * DAY }
                // Se presta dos semanas antes de la fecha de devolución (así el vencido tiene fechas lógicas).
                val lentAt = dueAt?.let { minOf(now, it - 14 * DAY) } ?: now
                repository.lendVolume(volume, loan.person, dueAt, loan.borrowed, lentAt)
            }
        }
        return examples().size
    }

    /** Borra las series de ejemplo y su colección. Devuelve cuántas ha borrado. */
    suspend fun remove(): Int {
        val collectionId = demoCollectionId() ?: return 0
        val items = repository.observeSeriesInCollection(collectionId).first()
        items.forEach { repository.deleteSeries(it) }
        repository.collections.first().first { it.collection.id == collectionId }.let { repository.deleteCollection(it.collection) }
        return items.size
    }

    private suspend fun demoCollectionId(): Long? =
        repository.collections.first().firstOrNull { it.collection.name == DEMO_COLLECTION }?.collection?.id

    // --- Atajos para escribir los ejemplos ---

    private fun owned(number: Int, status: ReadingStatus = ReadingStatus.UNREAD, location: String = "") =
        Volume(seriesId = 0, number = number, status = status, location = location)

    private fun wanted(number: Int, priority: PurchasePriority, note: String = "") =
        Volume(seriesId = 0, number = number, owned = false, purchasePriority = priority, purchaseNote = note)

    private fun examples(): List<DemoSeries> = listOf(
        // --- Libros ---
        DemoSeries(
            Series(title = "El nombre del viento", author = "Patrick Rothfuss", type = ItemType.BOOK, publisher = "Plaza & Janés", language = "Español",
                otherTitles = "The Name of the Wind", statusInOrigin = PublicationStatus.PAUSED, statusInMyLanguage = PublicationStatus.PAUSED),
            listOf(owned(1, ReadingStatus.READ, "Estantería salón")),
        ),
        DemoSeries(
            Series(title = "1984", author = "George Orwell", type = ItemType.BOOK, publisher = "Debolsillo", language = "Español",
                statusInOrigin = PublicationStatus.FINISHED, statusInMyLanguage = PublicationStatus.FINISHED),
            listOf(owned(1, ReadingStatus.READING, "Mesilla")),
            listOf(DemoLoan(1, "Ana", daysLeft = 7)),
        ),
        DemoSeries(
            Series(title = "Cien años de soledad", author = "Gabriel García Márquez", type = ItemType.BOOK, language = "Español"),
            listOf(wanted(1, PurchasePriority.HIGH, "Edición conmemorativa")),
        ),
        DemoSeries(
            Series(title = "Dune", author = "Frank Herbert", type = ItemType.BOOK, publisher = "Nova", language = "Español"),
            listOf(Volume(seriesId = 0, number = 1, owned = false, status = ReadingStatus.READING)),
            listOf(DemoLoan(1, "Luis", daysLeft = 20, borrowed = true)),
        ),
        // --- Manga ---
        DemoSeries(
            Series(title = "One Piece", author = "Eiichiro Oda", type = ItemType.MANGA, publisher = "Planeta Cómic", language = "Español",
                otherTitles = "ワンピース", statusInOrigin = PublicationStatus.PUBLISHING, statusInMyLanguage = PublicationStatus.PUBLISHING, publishedInMyLanguage = 108),
            listOf(
                owned(1, ReadingStatus.READ, "Caja 1"), owned(2, ReadingStatus.READ, "Caja 1"), owned(3, ReadingStatus.READ, "Caja 1"),
                owned(4, ReadingStatus.READING, "Caja 1"), owned(5, location = "Caja 1"),
                wanted(6, PurchasePriority.MEDIUM), wanted(7, PurchasePriority.LOW, "Segunda mano"),
            ),
            listOf(DemoLoan(2, "Marta", daysLeft = -3)),
        ),
        DemoSeries(
            Series(title = "Death Note", author = "Tsugumi Ohba", type = ItemType.MANGA, publisher = "Norma", language = "Español",
                statusInOrigin = PublicationStatus.FINISHED, statusInMyLanguage = PublicationStatus.FINISHED, publishedInMyLanguage = 12),
            (1..12).map { owned(it, ReadingStatus.READ, "Estantería manga") },
        ),
        DemoSeries(
            Series(title = "Hunter x Hunter", author = "Yoshihiro Togashi", type = ItemType.MANGA, publisher = "Panini", language = "Español",
                statusInOrigin = PublicationStatus.PAUSED, statusInMyLanguage = PublicationStatus.PAUSED, publishedInMyLanguage = 37),
            listOf(owned(1, ReadingStatus.READ), owned(2, ReadingStatus.PAUSED), owned(3), Volume(seriesId = 0, number = 4, owned = false)),
            listOf(DemoLoan(4, "Carlos", borrowed = true)),
        ),
        DemoSeries(
            Series(title = "Frieren", author = "Kanehito Yamada", type = ItemType.MANGA, publisher = "Norma", language = "Español",
                otherTitles = "Sousou no Frieren\n葬送のフリーレン", statusInOrigin = PublicationStatus.PUBLISHING, statusInMyLanguage = PublicationStatus.UNCONFIRMED),
            listOf(owned(1, location = "Caja 2")),
        ),
        // --- Cómics ---
        DemoSeries(
            Series(title = "Watchmen", author = "Alan Moore", type = ItemType.COMIC, publisher = "ECC", language = "Español",
                statusInOrigin = PublicationStatus.FINISHED, statusInMyLanguage = PublicationStatus.FINISHED, publishedInMyLanguage = 1),
            listOf(owned(1, ReadingStatus.READ, "Estantería cómics")),
        ),
        DemoSeries(
            Series(title = "Saga", author = "Brian K. Vaughan", type = ItemType.COMIC, publisher = "Planeta Cómic", language = "Español",
                statusInOrigin = PublicationStatus.PUBLISHING, statusInMyLanguage = PublicationStatus.PUBLISHING, publishedInMyLanguage = 11),
            listOf(owned(1, ReadingStatus.READ), owned(2, ReadingStatus.READ), owned(3, ReadingStatus.READING), wanted(4, PurchasePriority.HIGH)),
        ),
        DemoSeries(
            Series(title = "The Sandman", author = "Neil Gaiman", type = ItemType.COMIC, publisher = "ECC", language = "Español",
                statusInOrigin = PublicationStatus.FINISHED, statusInMyLanguage = PublicationStatus.CANCELLED),
            listOf(owned(1, ReadingStatus.READ), owned(2, ReadingStatus.ABANDONED)),
            listOf(DemoLoan(1, "Ana")),
        ),
        // --- Otros ---
        DemoSeries(
            Series(title = "Atlas del mundo", author = "National Geographic", type = ItemType.OTHER, language = "Español"),
            listOf(owned(1, location = "Estudio")),
        ),
        DemoSeries(
            Series(title = "Muy Interesante nº 500", type = ItemType.OTHER, publisher = "Zinet Media", language = "Español"),
            listOf(owned(1, ReadingStatus.READ)),
        ),
        DemoSeries(
            Series(title = "Guía de Japón", author = "Lonely Planet", type = ItemType.OTHER, language = "Español"),
            listOf(wanted(1, PurchasePriority.MEDIUM, "Antes del viaje")),
        ),
    )

    companion object {
        const val DEMO_COLLECTION = "Datos de prueba"
        private const val DAY = 24 * 60 * 60 * 1000L
    }
}
