package com.shelfio.app.data.sample

import com.shelfio.app.data.model.ItemType
import com.shelfio.app.data.model.ReadingStatus
import com.shelfio.app.data.model.Series
import com.shelfio.app.data.model.SeriesWithVolumes
import com.shelfio.app.data.model.ShelfCollection
import com.shelfio.app.data.model.Volume

/**
 * Datos de prueba inventados (Fase 1). No son obras reales y no tienen portadas reales:
 * todas usan la portada generada. Solo viven en memoria.
 */
object SampleData {
    private const val DAY = 24 * 60 * 60 * 1000L
    private val now = System.currentTimeMillis()

    private fun series(id: Long, title: String, author: String, type: ItemType, daysAgo: Int) =
        Series(id = id, title = title, author = author, type = type, createdAt = now - daysAgo * DAY, updatedAt = now - daysAgo * DAY)

    private fun vol(seriesId: Long, number: Int, status: ReadingStatus, owned: Boolean = true, location: String = "") =
        Volume(id = seriesId * 100 + number, seriesId = seriesId, number = number, status = status, owned = owned, location = location)

    val library: List<SeriesWithVolumes> = listOf(
        SeriesWithVolumes(
            series(1, "Saga del Dragón", "Autora de ejemplo", ItemType.MANGA, 1),
            listOf(
                vol(1, 1, ReadingStatus.READ, location = "Estantería 2"),
                vol(1, 2, ReadingStatus.READ, location = "Estantería 2"),
                vol(1, 3, ReadingStatus.READING, location = "Mesilla"),
                vol(1, 4, ReadingStatus.UNREAD, owned = false),
            ),
        ),
        SeriesWithVolumes(
            series(2, "El faro del norte", "Autor de ejemplo", ItemType.BOOK, 3),
            listOf(vol(2, 1, ReadingStatus.READING, location = "Caja 3")),
        ),
        SeriesWithVolumes(
            series(3, "Ciudad Neón", "Equipo de ejemplo", ItemType.COMIC, 5),
            listOf(
                vol(3, 1, ReadingStatus.READ, location = "Caja 3"),
                vol(3, 2, ReadingStatus.UNREAD, location = "Caja 3"),
                vol(3, 3, ReadingStatus.UNREAD, owned = false),
            ),
        ),
        SeriesWithVolumes(
            series(4, "Jardines de ceniza", "Autor de ejemplo", ItemType.BOOK, 8),
            listOf(vol(4, 1, ReadingStatus.READ)),
        ),
        SeriesWithVolumes(
            series(5, "Patrulla Estelar", "Autora de ejemplo", ItemType.MANGA, 12),
            listOf(
                vol(5, 1, ReadingStatus.PAUSED, location = "Estante de arriba, segunda fila"),
                vol(5, 2, ReadingStatus.UNREAD, location = "Estante de arriba, segunda fila"),
            ),
        ),
    )

    val collections: List<ShelfCollection> = listOf(
        ShelfCollection(1, "Favoritos"),
        ShelfCollection(2, "Fantasía"),
    )
}
