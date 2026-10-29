package com.shelfio.app.data.report

import com.shelfio.app.data.countByType
import com.shelfio.app.data.isOverdue
import com.shelfio.app.data.loanItems
import com.shelfio.app.data.model.ItemType
import com.shelfio.app.data.model.PublicationStatus
import com.shelfio.app.data.model.Series
import com.shelfio.app.data.model.SeriesWithVolumes
import com.shelfio.app.data.normalizeForSearch
import com.shelfio.app.data.wishlist
import com.shelfio.app.data.WishlistSort

/** Qué incluir en el informe. */
data class ReportOptions(
    /** false = solo lo que tengo; true = también la lista de deseos. */
    val includeWishlist: Boolean = false,
    val includeLocations: Boolean = true,
    val includeLoans: Boolean = true,
    val includeCollections: Boolean = true,
)

/** Una colección con las series que tiene dentro. */
data class ReportCollection(val name: String, val items: List<SeriesWithVolumes>)

/** Piezas del informe; PdfReport las dibuja en orden, pasando de página cuando no caben. */
sealed interface ReportBlock {
    /** Título de sección («Mi biblioteca», «Estantes»…), con un subtítulo opcional. */
    data class Heading(val text: String, val subtitle: String = "") : ReportBlock

    /** Subtítulo dentro de una sección (el nombre de cada colección). */
    data class SubHeading(val text: String) : ReportBlock

    /** Ficha de una serie: portada (real o generada) a la izquierda, título y líneas de datos. */
    data class SeriesCard(val series: Series, val volumeNumber: Int?, val lines: List<String>) : ReportBlock

    /** Fila de portadas con su título debajo, como una balda (para las colecciones). */
    data class CoverRow(val tiles: List<CoverTile>) : ReportBlock

    /** Texto suelto (por ejemplo, «No hay nada prestado.»). */
    data class Line(val text: String) : ReportBlock
}

/** Una portada de la balda: la serie y el número de su primer tomo (para la portada generada del manga). */
data class CoverTile(val series: Series, val volumeNumber: Int?)

/** Portadas por fila en las colecciones. */
const val COVERS_PER_ROW = 5

/** Totales para la portada del informe. */
data class ReportTotals(val books: Int, val mangas: Int, val comics: Int, val others: Int, val volumes: Int, val collections: Int)

/** Calcula los totales de la portada del informe. */
fun reportTotals(items: List<SeriesWithVolumes>, collections: Int) = ReportTotals(
    books = countByType(items, ItemType.BOOK),
    mangas = countByType(items, ItemType.MANGA),
    comics = countByType(items, ItemType.COMIC),
    others = countByType(items, ItemType.OTHER),
    volumes = items.sumOf { item -> item.volumes.count { it.owned } },
    collections = collections,
)

/**
 * Contenido del informe (sin dibujar): listado por series ordenado por título y, si se pide,
 * colecciones, lista de deseos y préstamos activos. Función pura para poder probarla.
 */
fun reportContent(items: List<SeriesWithVolumes>, collections: List<ReportCollection>, options: ReportOptions): List<ReportBlock> = buildList {
    val owned = items.filter { item -> item.volumes.any { it.owned } }.sortedBy { normalizeForSearch(it.series.title) }
    add(ReportBlock.Heading("Mi biblioteca", "${owned.size} series y libros que tengo"))
    owned.forEach { add(seriesCard(it, seriesLines(it, options))) }

    if (options.includeCollections) {
        add(ReportBlock.Heading("Estantes", "${collections.size} estantes"))
        if (collections.isEmpty()) add(ReportBlock.Line("Todavía no hay estantes."))
        collections.sortedBy { normalizeForSearch(it.name) }.forEach { collection ->
            add(ReportBlock.SubHeading("${collection.name} · ${collection.items.size}"))
            if (collection.items.isEmpty()) add(ReportBlock.Line("Vacía."))
            collection.items.sortedBy { normalizeForSearch(it.series.title) }
                .map { CoverTile(it.series, it.sortedVolumes.firstOrNull()?.number) }
                .chunked(COVERS_PER_ROW)
                .forEach { add(ReportBlock.CoverRow(it)) }
        }
    }

    if (options.includeWishlist) {
        val groups = wishlist(items, "", WishlistSort.TITLE)
        add(ReportBlock.Heading("Lista de deseos", "${groups.sumOf { it.volumes.size }} pendientes de compra"))
        if (groups.isEmpty()) add(ReportBlock.Line("No hay nada en la lista de deseos."))
        groups.forEach { group ->
            val lines = group.volumes.map { volume ->
                listOfNotNull("${group.series.type.unitTitle} ${volume.number}", volume.purchasePriority?.let { "prioridad ${it.label.lowercase()}" }, volume.purchaseNote.ifBlank { null })
                    .joinToString(" · ")
            }
            add(ReportBlock.SeriesCard(group.series, group.volumes.firstOrNull()?.number, lines))
        }
    }

    if (options.includeLoans) {
        val active = loanItems(items).filter { it.loan.isActive }
        add(ReportBlock.Heading("Préstamos activos", "${active.size} préstamos"))
        if (active.isEmpty()) add(ReportBlock.Line("No hay nada prestado."))
        active.forEach { loanItem ->
            val who = if (loanItem.loan.borrowed) "me lo prestó ${loanItem.loan.person}" else "prestado a ${loanItem.loan.person}"
            // En un libro suelto basta con quién; en una serie se dice qué tomo.
            val what = if (loanItem.singleBook) who.replaceFirstChar { it.uppercase() } else "${loanItem.series.type.unitTitle} ${loanItem.volume.number} · $who"
            val late = if (isOverdue(loanItem.loan)) " · RETRASADO" else ""
            add(ReportBlock.SeriesCard(loanItem.series, loanItem.volume.number, listOf(what + late)))
        }
    }
}

/** Ficha de una serie con la portada de su primer tomo. */
private fun seriesCard(item: SeriesWithVolumes, lines: List<String>) =
    ReportBlock.SeriesCard(item.series, item.sortedVolumes.firstOrNull()?.number, lines)

/** Datos completos de una serie para «Mi biblioteca». */
private fun seriesLines(item: SeriesWithVolumes, options: ReportOptions): List<String> = buildList {
    val series = item.series
    val owned = item.sortedVolumes.filter { it.owned }
    addAll(summaryLines(item))
    if (!item.isSingleBook) add("Tengo: ${volumesLabel(owned.map { it.number }, series.type)}")
    if (options.includeLocations) {
        owned.filter { it.location.isNotBlank() }.groupBy { it.location.trim() }.forEach { (place, volumes) ->
            add(if (item.isSingleBook) "Ubicación: $place" else "Ubicación: $place (${volumesLabel(volumes.map { it.number }, series.type)})")
        }
    }
    val editorial = listOfNotNull(
        series.statusInOrigin.takeIf { it != PublicationStatus.UNKNOWN }?.let { "original ${it.label.lowercase()}" },
        series.statusInMyLanguage.takeIf { it != PublicationStatus.UNKNOWN }?.let { "en mi idioma ${it.label.lowercase()}" },
        series.publishedInMyLanguage?.let { "$it publicados" },
    )
    if (editorial.isNotEmpty()) add("Estado editorial: ${editorial.joinToString(" · ")}")
    if (item.isCompleteCollection) add("Colección completa")
}

/** Autor y lectura. El tipo no hace falta: va en la etiqueta de color de la ficha. */
private fun summaryLines(item: SeriesWithVolumes): List<String> = listOfNotNull(
    item.series.author.ifBlank { null },
    "Lectura: ${item.displayStatus.label}" + if (item.isSingleBook) "" else " (${item.readCount} de ${item.volumes.size} leídos)",
)

/** 1, 2, 3, 5, 7, 8 → «1-3, 5, 7-8». */
fun formatNumbers(numbers: List<Int>): String {
    val sorted = numbers.distinct().sorted()
    if (sorted.isEmpty()) return "-"
    val ranges = mutableListOf<IntRange>()
    var start = sorted.first()
    var end = start
    sorted.drop(1).forEach { n ->
        if (n == end + 1) end = n else { ranges += start..end; start = n; end = n }
    }
    ranges += start..end
    return ranges.joinToString(", ") { if (it.first == it.last) "${it.first}" else "${it.first}-${it.last}" }
}

/** «tomo 3» o «tomos 1-3, 5». */
private fun volumesLabel(numbers: List<Int>, type: ItemType): String =
    if (numbers.distinct().size == 1) "${type.unit} ${numbers.first()}" else "${type.units} ${formatNumbers(numbers)}"
