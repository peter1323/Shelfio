package com.shelfio.app.data.model

enum class ItemType(val label: String, val pluralLabel: String) {
    BOOK("Libro", "Libros"),
    MANGA("Manga", "Mangas"),
    COMIC("Cómic", "Cómics"),
}

enum class ReadingStatus(val label: String) {
    UNREAD("Sin leer"),
    READING("Leyendo"),
    READ("Leído"),
    PAUSED("Pausado"),
    ABANDONED("Abandonado"),
}

/** Lo que comparten todos los tomos de una serie. Un libro suelto es una serie con un tomo. */
data class Series(
    val id: Long = 0,
    val title: String,
    val author: String = "",
    val type: ItemType,
    val publisher: String = "",
    val language: String = "",
    val coverPath: String? = null,
    val description: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/** Lo propio de cada tomo. */
data class Volume(
    val id: Long = 0,
    val seriesId: Long,
    val number: Int,
    val title: String = "",
    val isbn: String = "",
    val coverPath: String? = null,
    val owned: Boolean = true,
    val status: ReadingStatus = ReadingStatus.UNREAD,
    val location: String = "",
    val notes: String = "",
)

/** Agrupación virtual creada por el usuario («Favoritos», «Fantasía»…). */
data class ShelfCollection(
    val id: Long = 0,
    val name: String,
)

data class SeriesWithVolumes(
    val series: Series,
    val volumes: List<Volume>,
) {
    val sortedVolumes: List<Volume> get() = volumes.sortedBy { it.number }

    /** Un libro suelto no habla de «tomos». */
    val isSingleBook: Boolean get() = volumes.size <= 1

    val readCount: Int get() = volumes.count { it.status == ReadingStatus.READ }

    /** Progreso calculado: tomos leídos / tomos registrados. No se guarda. */
    val progress: Float get() = if (volumes.isEmpty()) 0f else readCount.toFloat() / volumes.size

    /** Estado que se enseña en las tarjetas, deducido de los tomos. */
    val displayStatus: ReadingStatus
        get() = when {
            volumes.isEmpty() -> ReadingStatus.UNREAD
            volumes.all { it.status == ReadingStatus.READ } -> ReadingStatus.READ
            volumes.any { it.status == ReadingStatus.READING } -> ReadingStatus.READING
            volumes.any { it.status == ReadingStatus.PAUSED } -> ReadingStatus.PAUSED
            volumes.all { it.status == ReadingStatus.ABANDONED } -> ReadingStatus.ABANDONED
            readCount > 0 -> ReadingStatus.READING
            else -> ReadingStatus.UNREAD
        }
}

/** Un tomo junto a su serie, para las listas de Inicio y la búsqueda por ubicación. */
data class VolumeWithSeries(
    val volume: Volume,
    val series: Series,
)
