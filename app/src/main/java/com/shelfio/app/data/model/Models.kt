package com.shelfio.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation

// MODELO: las clases de datos de Shelfio. Las marcadas con @Entity son tablas de Room.
// Modelo fijo serie → tomos: los datos comunes van en la serie y cada tomo guarda solo lo que cambia.

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

/** Estado de publicación de una serie (en mi idioma o en su país de origen). */
enum class PublicationStatus(val label: String) {
    UNKNOWN("Desconocido"),
    PUBLISHING("En publicación"),
    FINISHED("Finalizada"),
    PAUSED("Pausada"),
    CANCELLED("Cancelada"),
    UNCONFIRMED("Sin confirmar"),
}

/** Prioridad de compra en la lista de deseos (opcional). El orden de la enum es el de importancia. */
enum class PurchasePriority(val label: String) {
    HIGH("Alta"),
    MEDIUM("Media"),
    LOW("Baja"),
}

/** Lo que comparten todos los tomos de una serie. Un libro suelto es una serie con un tomo. */
@Entity(tableName = "series")
data class Series(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val author: String = "",
    val type: ItemType,
    val publisher: String = "",
    val language: String = "",
    /** Ruta del archivo de portada en el almacenamiento interno de la app, o null si no hay. */
    val coverPath: String? = null,
    val description: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    // Estado editorial (versión 4 de la base de datos). Lo rellena el usuario a mano; nunca se inventa.
    @ColumnInfo(defaultValue = "UNKNOWN") val statusInMyLanguage: PublicationStatus = PublicationStatus.UNKNOWN,
    @ColumnInfo(defaultValue = "UNKNOWN") val statusInOrigin: PublicationStatus = PublicationStatus.UNKNOWN,
    /** Tomos publicados en mi idioma, si se sabe. */
    val publishedInMyLanguage: Int? = null,
)

/** Lo propio de cada tomo. Al borrar la serie se borran sus tomos (CASCADE). */
@Entity(
    tableName = "volumes",
    foreignKeys = [ForeignKey(entity = Series::class, parentColumns = ["id"], childColumns = ["seriesId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("seriesId")],
)
data class Volume(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val seriesId: Long,
    val number: Int,
    val title: String = "",
    val isbn: String = "",
    val coverPath: String? = null,
    /** «Lo tengo». Los tomos con false forman «Pendientes de compra». */
    val owned: Boolean = true,
    val status: ReadingStatus = ReadingStatus.UNREAD,
    /** Dónde está guardado físicamente («Caja 3»). Solo tiene sentido si owned es true. */
    val location: String = "",
    val notes: String = "",
    // Lista de deseos (solo para tomos que no tengo). Añadido en la versión 2 de la base de datos.
    val purchasePriority: PurchasePriority? = null,
    @ColumnInfo(defaultValue = "") val purchaseNote: String = "",
    /** Cuándo entró en la lista de deseos. */
    val wishlistAddedAt: Long? = null,
)

/** Agrupación virtual creada por el usuario («Favoritos», «Fantasía»…). No es un sitio físico. */
@Entity(tableName = "collections")
data class ShelfCollection(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

/** Tabla de unión: una serie puede estar en varias colecciones y una colección tiene varias series. */
@Entity(
    tableName = "series_collections",
    primaryKeys = ["seriesId", "collectionId"],
    foreignKeys = [
        ForeignKey(entity = Series::class, parentColumns = ["id"], childColumns = ["seriesId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ShelfCollection::class, parentColumns = ["id"], childColumns = ["collectionId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("collectionId")],
)
data class SeriesCollectionCrossRef(
    val seriesId: Long,
    val collectionId: Long,
)

/**
 * Préstamo de un tomo. Solo se guarda el nombre o apodo de la persona (nada de teléfonos ni contactos).
 * Está activo mientras [returnedAt] sea null. Al borrar el tomo se borran sus préstamos (CASCADE).
 */
@Entity(
    tableName = "loans",
    foreignKeys = [ForeignKey(entity = Volume::class, parentColumns = ["id"], childColumns = ["volumeId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("volumeId")],
)
data class Loan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val volumeId: Long,
    val person: String,
    val lentAt: Long,
    /** Fecha prevista de devolución (opcional). */
    val dueAt: Long? = null,
    /** Fecha real de devolución; null = sigue prestado. */
    val returnedAt: Long? = null,
    val notes: String = "",
) {
    val isActive: Boolean get() = returnedAt == null
}

/** Una serie con todos sus tomos y los préstamos de esos tomos. Room la rellena sola gracias a @Relation. */
data class SeriesWithVolumes(
    @Embedded val series: Series,
    @Relation(parentColumn = "id", entityColumn = "seriesId")
    val volumes: List<Volume>,
    @Relation(
        parentColumn = "id",
        entityColumn = "volumeId",
        associateBy = Junction(Volume::class, parentColumn = "seriesId", entityColumn = "id"),
    )
    val loans: List<Loan> = emptyList(),
) {
    /** Préstamo activo de un tomo, si está prestado. */
    fun activeLoanOf(volume: Volume): Loan? = loans.firstOrNull { it.volumeId == volume.id && it.isActive }

    val sortedVolumes: List<Volume> get() = volumes.sortedBy { it.number }

    /** Un libro suelto no habla de «tomos». */
    val isSingleBook: Boolean get() = volumes.size <= 1

    val readCount: Int get() = volumes.count { it.status == ReadingStatus.READ }

    /** Progreso calculado: tomos leídos / tomos registrados. No se guarda en la base de datos. */
    val progress: Float get() = if (volumes.isEmpty()) 0f else readCount.toFloat() / volumes.size

    /** «Tengo X de Y publicados»: tomos que tengo con número dentro de los publicados. null si no se sabe Y. */
    val ownedOfPublished: Int?
        get() = series.publishedInMyLanguage?.let { published ->
            volumes.filter { it.owned && it.number in 1..published }.map { it.number }.toSet().size
        }

    /** Serie finalizada en mi idioma y tengo todos los tomos publicados. */
    val isCompleteCollection: Boolean
        get() = series.statusInMyLanguage == PublicationStatus.FINISHED &&
            series.publishedInMyLanguage.let { it != null && it > 0 && ownedOfPublished == it }

    /** Me faltan tomos: hay alguno en «no lo tengo» o tengo menos de los publicados. */
    val isMissingVolumes: Boolean
        get() = volumes.any { !it.owned } || series.publishedInMyLanguage.let { it != null && (ownedOfPublished ?: 0) < it }

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

/** Una colección con cuántas series tiene (resultado de una consulta, no es tabla). */
data class CollectionWithCount(
    @Embedded val collection: ShelfCollection,
    val seriesCount: Int,
)

/** Un tomo junto a su serie, para las listas de Inicio y la búsqueda por ubicación. */
data class VolumeWithSeries(
    val volume: Volume,
    val series: Series,
)
