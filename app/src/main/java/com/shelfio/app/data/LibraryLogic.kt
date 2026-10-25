package com.shelfio.app.data

import com.shelfio.app.data.model.ItemType
import com.shelfio.app.data.model.ReadingStatus
import com.shelfio.app.data.model.SeriesWithVolumes
import com.shelfio.app.data.model.Volume
import com.shelfio.app.data.model.VolumeWithSeries
import java.text.Normalizer

// Funciones puras sobre la biblioteca. Se usan en las pantallas y se prueban con tests unitarios.

/** Pasa a minúsculas y quita tildes para que «comic» encuentre «Cómic». */
fun normalizeForSearch(text: String): String =
    Normalizer.normalize(text.trim().lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")

/** Coincide si el texto aparece en título, autor, editorial, ISBN, título de tomo o ubicación. */
fun matchesSearch(item: SeriesWithVolumes, query: String): Boolean {
    val q = normalizeForSearch(query)
    if (q.isEmpty()) return true
    val series = item.series
    val fields = buildList {
        add(series.title)
        add(series.author)
        add(series.publisher)
        item.volumes.forEach {
            add(it.title)
            add(it.isbn)
            if (it.owned) add(it.location)
        }
    }
    return fields.any { normalizeForSearch(it).contains(q) }
}

fun filterLibrary(items: List<SeriesWithVolumes>, query: String, type: ItemType?): List<SeriesWithVolumes> =
    items.filter { (type == null || it.series.type == type) && matchesSearch(it, query) }
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

/** «Pendientes de compra»: tomos marcados como «no lo tengo». */
fun pendingPurchase(items: List<SeriesWithVolumes>): List<VolumeWithSeries> =
    items.flatMap { item ->
        item.volumes.filter { !it.owned }.map { VolumeWithSeries(it, item.series) }
    }.sortedWith(compareBy({ normalizeForSearch(it.series.title) }, { it.volume.number }))

fun countByType(items: List<SeriesWithVolumes>, type: ItemType): Int =
    items.count { it.series.type == type }

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
