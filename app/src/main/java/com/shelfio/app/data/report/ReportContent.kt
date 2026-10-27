package com.shelfio.app.data.report

import com.shelfio.app.data.countByType
import com.shelfio.app.data.isOverdue
import com.shelfio.app.data.loanItems
import com.shelfio.app.data.model.ItemType
import com.shelfio.app.data.model.PublicationStatus
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
)

/** Piezas del informe; PdfReport las dibuja en orden, pasando de página cuando no caben. */
sealed interface ReportBlock {
    data class Heading(val text: String) : ReportBlock
    data class SeriesTitle(val text: String) : ReportBlock
    data class Line(val text: String) : ReportBlock
    data object Gap : ReportBlock
}

/** Totales para la portada del informe. */
data class ReportTotals(val books: Int, val mangas: Int, val comics: Int, val others: Int, val volumes: Int, val collections: Int)

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
 * lista de deseos y préstamos activos. Función pura para poder probarla.
 */
fun reportContent(items: List<SeriesWithVolumes>, options: ReportOptions): List<ReportBlock> = buildList {
    add(ReportBlock.Heading("Mi colección"))
    items.filter { item -> item.volumes.any { it.owned } }
        .sortedBy { normalizeForSearch(it.series.title) }
        .forEach { item -> addAll(seriesBlocks(item, options)) }

    if (options.includeWishlist) {
        val groups = wishlist(items, "", WishlistSort.TITLE)
        add(ReportBlock.Heading("Lista de deseos"))
        if (groups.isEmpty()) add(ReportBlock.Line("No hay nada en la lista de deseos."))
        groups.forEach { group ->
            add(ReportBlock.SeriesTitle(group.series.title))
            group.volumes.forEach { volume ->
                val parts = listOf("${group.series.type.unitTitle} ${volume.number}", volume.purchasePriority?.let { "prioridad ${it.label.lowercase()}" }, volume.purchaseNote.ifBlank { null })
                add(ReportBlock.Line(parts.filterNotNull().joinToString(" · ")))
            }
            add(ReportBlock.Gap)
        }
    }

    if (options.includeLoans) {
        val active = loanItems(items).filter { it.loan.isActive }
        add(ReportBlock.Heading("Préstamos activos"))
        if (active.isEmpty()) add(ReportBlock.Line("No hay nada prestado."))
        active.forEach { loanItem ->
            val what = if (loanItem.singleBook) loanItem.series.title else "${loanItem.series.title}, ${loanItem.series.type.unit} ${loanItem.volume.number}"
            val late = if (isOverdue(loanItem.loan)) " · RETRASADO" else ""
            add(ReportBlock.Line(if (loanItem.loan.borrowed) "$what ← me lo prestó ${loanItem.loan.person}$late" else "$what → ${loanItem.loan.person}$late"))
        }
    }
}

private fun seriesBlocks(item: SeriesWithVolumes, options: ReportOptions): List<ReportBlock> = buildList {
    val series = item.series
    val owned = item.sortedVolumes.filter { it.owned }
    add(ReportBlock.SeriesTitle(series.title))
    add(ReportBlock.Line(listOf(series.type.label, series.author.ifBlank { null }).filterNotNull().joinToString(" · ")))
    if (!item.isSingleBook) add(ReportBlock.Line("Tengo: ${volumesLabel(owned.map { it.number }, series.type)}"))
    add(ReportBlock.Line("Lectura: ${item.displayStatus.label}" + if (item.isSingleBook) "" else " (${item.readCount} de ${item.volumes.size} leídos)"))
    if (options.includeLocations) {
        owned.filter { it.location.isNotBlank() }.groupBy { it.location.trim() }.forEach { (place, volumes) ->
            add(ReportBlock.Line(if (item.isSingleBook) "Ubicación: $place" else "Ubicación: $place (${volumesLabel(volumes.map { it.number }, series.type)})"))
        }
    }
    val editorial = listOfNotNull(
        series.statusInMyLanguage.takeIf { it != PublicationStatus.UNKNOWN }?.label,
        series.publishedInMyLanguage?.let { "$it publicados en mi idioma" },
    )
    if (editorial.isNotEmpty()) add(ReportBlock.Line("Estado editorial: ${editorial.joinToString(" · ")}"))
    if (item.isCompleteCollection) add(ReportBlock.Line("Colección completa"))
    add(ReportBlock.Gap)
}

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
