package com.shelfio.app.data

import com.shelfio.app.data.model.ItemType
import com.shelfio.app.data.model.Loan
import com.shelfio.app.data.model.PublicationStatus
import com.shelfio.app.data.model.PurchasePriority
import com.shelfio.app.data.model.ReadingStatus
import com.shelfio.app.data.model.Series
import com.shelfio.app.data.model.SeriesWithVolumes
import com.shelfio.app.data.model.Volume
import com.shelfio.app.data.model.VolumeWithSeries
import java.text.Normalizer

// Funciones puras sobre la biblioteca. Se usan en las pantallas y se prueban con tests unitarios.

/** Pasa a minúsculas y quita tildes para que «comic» encuentre «Cómic». */
fun normalizeForSearch(text: String): String =
    Normalizer.normalize(text.trim().lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")

/** Coincide si el texto aparece en título, autor, editorial, ISBN, título de tomo, ubicación o a quién se prestó. */
fun matchesSearch(item: SeriesWithVolumes, query: String): Boolean {
    val q = normalizeForSearch(query)
    if (q.isEmpty()) return true
    val series = item.series
    val fields = buildList {
        add(series.title)
        add(series.author)
        add(series.publisher)
        series.otherTitles.lines().forEach { add(it) }
        item.volumes.forEach {
            add(it.title)
            add(it.isbn)
            if (it.owned) add(it.location)
        }
        item.loans.filter { it.isActive }.forEach { add(it.person) }
    }
    return fields.any { normalizeForSearch(it).contains(q) }
}

/** Filtros de Biblioteca según el estado de la colección (Fase 10). */
enum class CollectionFilter(val label: String, val matches: (SeriesWithVolumes) -> Boolean) {
    COMPLETE("Completas", { it.isCompleteCollection }),
    MISSING("Me faltan", { it.isMissingVolumes }),
    PUBLISHING("En publicación", { it.series.statusInMyLanguage == PublicationStatus.PUBLISHING }),
}

fun filterLibrary(items: List<SeriesWithVolumes>, query: String, type: ItemType?, collectionFilter: CollectionFilter? = null): List<SeriesWithVolumes> =
    items.filter { (type == null || it.series.type == type) && (collectionFilter?.matches?.invoke(it) ?: true) && matchesSearch(it, query) }
        .sortedBy { normalizeForSearch(it.series.title) }

/** Tomos que tengo cuya ubicación contiene el texto buscado («caja 3»). */
fun volumesAtLocation(items: List<SeriesWithVolumes>, query: String): List<VolumeWithSeries> {
    val q = normalizeForSearch(query)
    if (q.isEmpty()) return emptyList()
    return items.flatMap { item ->
        item.volumes
            .filter { it.owned && it.location.isNotBlank() && normalizeForSearch(it.location).contains(q) }
            .map { VolumeWithSeries(it, item.series) }
    }.sortedWith(compareBy({ normalizeForSearch(it.series.title) }, { it.volume.number }))
}

fun recentlyAdded(items: List<SeriesWithVolumes>, limit: Int = 10): List<SeriesWithVolumes> =
    items.sortedByDescending { it.series.createdAt }.take(limit)

fun readingNow(items: List<SeriesWithVolumes>): List<VolumeWithSeries> =
    items.flatMap { item ->
        item.volumes.filter { it.status == ReadingStatus.READING }.map { VolumeWithSeries(it, item.series) }
    }.sortedByDescending { it.series.updatedAt }

/** «Pendientes de compra»: tomos marcados como «no lo tengo», primero los de prioridad alta. */
fun pendingPurchase(items: List<SeriesWithVolumes>): List<VolumeWithSeries> =
    items.flatMap { item ->
        item.volumes.filter { !it.owned }.map { VolumeWithSeries(it, item.series) }
    }.sortedWith(compareBy({ priorityRank(it.volume) }, { normalizeForSearch(it.series.title) }, { it.volume.number }))

/** Alta = 0, media = 1, baja = 2, sin prioridad = 3 (al final). */
private fun priorityRank(volume: Volume): Int = volume.purchasePriority?.ordinal ?: PurchasePriority.entries.size

enum class WishlistSort(val label: String) { PRIORITY("Prioridad"), DATE("Fecha"), TITLE("Título") }

/** Un grupo de la lista de deseos: una serie con los tomos que me faltan. */
data class WishlistGroup(val series: Series, val volumes: List<Volume>)

/**
 * Lista de deseos: tomos que no tengo agrupados por serie, filtrados por [query] (título, autor o nota de compra)
 * y ordenados por prioridad (más alta primero), fecha (más reciente primero) o título.
 */
fun wishlist(items: List<SeriesWithVolumes>, query: String, sort: WishlistSort): List<WishlistGroup> {
    val q = normalizeForSearch(query)
    val groups = items.mapNotNull { item ->
        val missing = item.volumes.filter { !it.owned }.filter { volume ->
            q.isEmpty() || listOf(item.series.title, item.series.author, volume.purchaseNote, volume.title)
                .any { normalizeForSearch(it).contains(q) }
        }
        if (missing.isEmpty()) null else WishlistGroup(item.series, missing)
    }
    val byTitle = compareBy<WishlistGroup> { normalizeForSearch(it.series.title) }
    return when (sort) {
        WishlistSort.PRIORITY -> groups
            .map { it.copy(volumes = it.volumes.sortedWith(compareBy(::priorityRank, Volume::number))) }
            .sortedWith(compareBy<WishlistGroup> { priorityRank(it.volumes.first()) }.then(byTitle))
        WishlistSort.DATE -> groups
            .map { it.copy(volumes = it.volumes.sortedByDescending { v -> v.wishlistAddedAt ?: 0 }) }
            .sortedByDescending { it.volumes.first().wishlistAddedAt ?: 0 }
        WishlistSort.TITLE -> groups.map { it.copy(volumes = it.volumes.sortedBy(Volume::number)) }.sortedWith(byTitle)
    }
}

/** Números de tomo que faltan entre 1 y [upTo] (los que ya existen no se repiten). */
fun missingVolumeNumbers(volumes: List<Volume>, upTo: Int): List<Int> {
    val existing = volumes.map { it.number }.toSet()
    return (1..upTo).filter { it !in existing }
}

/**
 * Mantiene coherente un tomo según «lo tengo»: si lo tengo no está en la lista de deseos;
 * si no lo tengo no tiene ubicación y entra en la lista con fecha [now] (si no la tenía ya).
 */
fun Volume.withOwnershipRules(now: Long = System.currentTimeMillis()): Volume =
    if (owned) copy(purchasePriority = null, purchaseNote = "", wishlistAddedAt = null)
    else copy(location = "", wishlistAddedAt = wishlistAddedAt ?: now)

fun countByType(items: List<SeriesWithVolumes>, type: ItemType): Int =
    items.count { it.series.type == type }

/**
 * Series de la biblioteca que parecen la misma obra que [title] (un título contiene al otro).
 * Sirve para ofrecer «añadir como tomo» al escanear, por ejemplo «Saga del Dragón 4» → «Saga del Dragón».
 */
fun findMatchingSeries(items: List<SeriesWithVolumes>, title: String): List<SeriesWithVolumes> {
    val wanted = normalizeForSearch(title)
    if (wanted.isEmpty()) return emptyList()
    return items.filter {
        val existing = normalizeForSearch(it.series.title)
        existing.isNotEmpty() && (wanted.contains(existing) || existing.contains(wanted))
    }
}

/** Tomo de la biblioteca que ya tiene ese ISBN, para avisar de duplicados. */
fun findVolumeByIsbn(items: List<SeriesWithVolumes>, isbn: String): VolumeWithSeries? =
    items.firstNotNullOfOrNull { item ->
        item.volumes.firstOrNull { normalizeIsbn(it.isbn) == isbn }?.let { VolumeWithSeries(it, item.series) }
    }

/** Tomo nuevo para «Añadir siguiente tomo»: número siguiente y la ubicación del último tomo. */
fun nextVolume(seriesId: Long, volumes: List<Volume>): Volume {
    val last = volumes.maxByOrNull { it.number }
    return Volume(
        seriesId = seriesId,
        number = (last?.number ?: 0) + 1,
        owned = true,
        status = ReadingStatus.UNREAD,
        location = last?.location.orEmpty(),
    )
}

// --- Préstamos ---

/** Un préstamo con su tomo y su serie, para las listas de préstamos. */
data class LoanItem(val loan: Loan, val volume: Volume, val series: Series, val singleBook: Boolean)

private const val DAY_MS = 24 * 60 * 60 * 1000L

/** Todos los préstamos de la biblioteca: activos primero (por fecha prevista), luego el historial (más reciente primero). */
fun loanItems(items: List<SeriesWithVolumes>): List<LoanItem> =
    items.flatMap { item ->
        item.loans.mapNotNull { loan ->
            item.volumes.firstOrNull { it.id == loan.volumeId }?.let { LoanItem(loan, it, item.series, item.isSingleBook) }
        }
    }.sortedWith(compareBy<LoanItem> { !it.loan.isActive }.thenBy { it.loan.dueAt ?: Long.MAX_VALUE }.thenByDescending { it.loan.returnedAt ?: it.loan.lentAt })

/** Filtra préstamos por nombre de la persona o título de la serie. */
fun filterLoans(loans: List<LoanItem>, query: String): List<LoanItem> {
    val q = normalizeForSearch(query)
    return if (q.isEmpty()) loans else loans.filter { normalizeForSearch(it.loan.person).contains(q) || normalizeForSearch(it.series.title).contains(q) }
}

/** Retrasado: sigue prestado y ya pasó el día previsto de devolución entero. */
fun isOverdue(loan: Loan, now: Long = System.currentTimeMillis()): Boolean =
    loan.isActive && loan.dueAt != null && now >= loan.dueAt + DAY_MS

/** Por qué no se puede prestar un tomo, o null si se puede. */
fun loanProblem(volume: Volume, loansOfVolume: List<Loan>, borrowed: Boolean = false): String? = when {
    !borrowed && !volume.owned -> "Solo puedes prestar lo que tienes."
    // Si me lo prestan no es mío: se registra en un tomo marcado como «no lo tengo».
    borrowed && volume.owned -> "Si es tuyo no te lo han prestado. Desmarca «Lo tengo» primero."
    loansOfVolume.any { it.isActive } -> "Ya tiene un préstamo activo."
    else -> null
}

// --- Gráficas ---

/** Qué se compara en la pantalla Gráficas. Cuenta tomos y volúmenes, salvo [SERIES]. */
enum class ChartMetric(val label: String, val description: String) {
    OWNED("Tengo", "Tomos y volúmenes que tengo"),
    READ("Leídos", "Tomos y volúmenes leídos"),
    READING("Leyendo", "Tomos y volúmenes que estoy leyendo"),
    WANTED("Pendientes", "Pendientes de compra"),
    SERIES("Series", "Series y libros registrados"),
}

/** Valor de [metric] para un tipo. */
fun chartValue(items: List<SeriesWithVolumes>, type: ItemType, metric: ChartMetric): Int {
    val ofType = items.filter { it.series.type == type }
    val volumes = ofType.flatMap { it.volumes }
    return when (metric) {
        ChartMetric.OWNED -> volumes.count { it.owned }
        ChartMetric.READ -> volumes.count { it.status == ReadingStatus.READ }
        ChartMetric.READING -> volumes.count { it.status == ReadingStatus.READING }
        ChartMetric.WANTED -> volumes.count { !it.owned }
        ChartMetric.SERIES -> ofType.size
    }
}

/** Cuánto he leído de un tipo: leídos de los que tengo o he leído (lo que solo quiero comprar no cuenta). */
data class ReadShare(val read: Int, val total: Int) {
    val fraction: Float get() = if (total == 0) 0f else read.toFloat() / total
}

fun readShare(items: List<SeriesWithVolumes>, type: ItemType): ReadShare {
    val volumes = items.filter { it.series.type == type }.flatMap { it.volumes }.filter { it.owned || it.status == ReadingStatus.READ }
    return ReadShare(read = volumes.count { it.status == ReadingStatus.READ }, total = volumes.size)
}

// --- Estantes ---

/** Orden de las series dentro de un estante. */
enum class ShelfSort(val label: String) {
    TITLE_ASC("Título (A-Z)"),
    TITLE_DESC("Título (Z-A)"),
    AUTHOR_ASC("Autor (A-Z)"),
    AUTHOR_DESC("Autor (Z-A)"),
    NEWEST("Último añadido"),
    OLDEST("Primero añadido"),
}

/**
 * Filtros de un estante; null = sin filtrar. Cada pareja va con su contraria en el mismo campo
 * (leídos / no leídos, prestados / no prestados, completas / me faltan), así nunca se piden las dos a la vez.
 */
data class ShelfFilters(
    val type: ItemType? = null,
    val read: Boolean? = null,
    val lent: Boolean? = null,
    val complete: Boolean? = null,
    val platinumOnly: Boolean = false,
) {
    val activeCount: Int get() = listOfNotNull(type, read, lent, complete).size + if (platinumOnly) 1 else 0
}

/** Leída entera (todos sus tomos en «Leído»). */
private fun SeriesWithVolumes.isRead() = displayStatus == ReadingStatus.READ

/** Algo de la serie está prestado por mí ahora mismo (lo que me prestan no cuenta). */
private fun SeriesWithVolumes.isLent() = loans.any { it.isActive && !it.borrowed }

/**
 * Series de un estante buscadas, filtradas y ordenadas. [addedAt] dice cuándo entró cada serie (por id)
 * y [platinumSeries] qué series tienen su platino. Función pura, se prueba en LibraryLogicTest.
 */
fun shelfItems(
    items: List<SeriesWithVolumes>,
    query: String,
    sort: ShelfSort,
    filters: ShelfFilters,
    addedAt: Map<Long, Long> = emptyMap(),
    platinumSeries: Set<Long> = emptySet(),
): List<SeriesWithVolumes> {
    val filtered = items.filter { item ->
        matchesSearch(item, query) &&
            (filters.type == null || item.series.type == filters.type) &&
            (filters.read == null || item.isRead() == filters.read) &&
            (filters.lent == null || item.isLent() == filters.lent) &&
            (filters.complete == null || (if (filters.complete) item.isCompleteCollection else item.isMissingVolumes)) &&
            (!filters.platinumOnly || item.series.id in platinumSeries)
    }
    val byTitle = compareBy<SeriesWithVolumes> { normalizeForSearch(it.series.title) }
    // Sin autor van siempre al final, en los dos sentidos.
    val noAuthorLast = compareBy<SeriesWithVolumes> { it.series.author.isBlank() }
    val comparator = when (sort) {
        ShelfSort.TITLE_ASC -> byTitle
        ShelfSort.TITLE_DESC -> byTitle.reversed()
        ShelfSort.AUTHOR_ASC -> noAuthorLast.thenBy { normalizeForSearch(it.series.author) }.then(byTitle)
        ShelfSort.AUTHOR_DESC -> noAuthorLast.thenByDescending { normalizeForSearch(it.series.author) }.then(byTitle)
        ShelfSort.NEWEST -> compareByDescending<SeriesWithVolumes> { addedAt[it.series.id] ?: 0L }.then(byTitle)
        ShelfSort.OLDEST -> compareBy<SeriesWithVolumes> { addedAt[it.series.id] ?: 0L }.then(byTitle)
    }
    return filtered.sortedWith(comparator)
}

/** Lomos por balda en la vista Estantería. */
const val SPINES_PER_SHELF = 10

/**
 * Un lomo de la estantería: un tomo que está en casa. [lent] = lo he prestado yo; [borrowed] = me lo han prestado
 * (no es mío); [platinum] = su serie tiene el platino.
 */
data class ShelfSpine(val item: SeriesWithVolumes, val volume: Volume, val lent: Boolean, val borrowed: Boolean, val platinum: Boolean)

/**
 * Lomos de la vista Estantería: un lomo por cada tomo que tengo o que me han prestado, los de una serie seguidos
 * y en orden, respetando el orden de [items]. La marca de platino va solo en el primer lomo de la serie.
 */
fun shelfSpines(items: List<SeriesWithVolumes>, platinumSeries: Set<Long> = emptySet()): List<ShelfSpine> =
    items.flatMap { item ->
        item.sortedVolumes.mapNotNull { volume ->
            val loan = item.activeLoanOf(volume)
            val borrowed = loan?.borrowed == true
            if (!volume.owned && !borrowed) return@mapNotNull null
            ShelfSpine(item, volume, lent = loan != null && !borrowed, borrowed = borrowed, platinum = false)
        }.mapIndexed { index, spine -> spine.copy(platinum = index == 0 && item.series.id in platinumSeries) }
    }
