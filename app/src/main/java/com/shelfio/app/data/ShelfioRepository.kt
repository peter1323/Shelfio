package com.shelfio.app.data

import com.shelfio.app.data.db.ShelfioDao
import com.shelfio.app.data.model.CollectionWithCount
import com.shelfio.app.data.model.Series
import com.shelfio.app.data.model.SeriesCollectionCrossRef
import com.shelfio.app.data.model.SeriesWithVolumes
import com.shelfio.app.data.model.ShelfCollection
import com.shelfio.app.data.model.Volume
import kotlinx.coroutines.flow.Flow

/**
 * Punto único de acceso a los datos de la biblioteca. Los ViewModels hablan con el repositorio,
 * nunca con el DAO ni con la base de datos directamente.
 * También se encarga de borrar los archivos de portada que dejan de usarse.
 */
class ShelfioRepository(
    private val dao: ShelfioDao,
    private val covers: CoverStorage,
) {

    // --- Series ---

    val library: Flow<List<SeriesWithVolumes>> = dao.observeLibrary()

    fun observeSeries(seriesId: Long): Flow<SeriesWithVolumes?> = dao.observeSeries(seriesId)

    suspend fun getSeries(seriesId: Long): SeriesWithVolumes? = dao.getSeriesWithVolumes(seriesId)

    /** Al crear una serie se crea también su primer tomo. Devuelve el id de la serie nueva. */
    suspend fun createSeries(series: Series, firstVolume: Volume): Long =
        dao.insertSeriesWithFirstVolume(series, firstVolume)

    suspend fun updateSeries(series: Series) =
        dao.updateSeries(series.copy(updatedAt = System.currentTimeMillis()))

    /** Borra la serie, sus tomos (CASCADE) y todos sus archivos de portada. */
    suspend fun deleteSeries(item: SeriesWithVolumes) {
        dao.deleteSeries(item.series)
        covers.delete(item.series.coverPath)
        item.volumes.forEach { covers.delete(it.coverPath) }
    }

    /** Pone o quita (null) la portada real de la serie y borra el archivo anterior. */
    suspend fun setSeriesCover(series: Series, newPath: String?) {
        updateSeries(series.copy(coverPath = newPath))
        if (series.coverPath != newPath) covers.delete(series.coverPath)
    }

    // --- Tomos ---

    suspend fun getVolume(volumeId: Long): Volume? = dao.getVolume(volumeId)

    suspend fun addVolume(volume: Volume): Long = dao.insertVolume(volume).also { touchSeries(volume.seriesId) }

    /** Guarda los cambios de un tomo. Si cambió la portada, borra el archivo anterior. */
    suspend fun updateVolume(volume: Volume) {
        val previousCover = dao.getVolume(volume.id)?.coverPath
        dao.updateVolume(volume)
        if (previousCover != volume.coverPath) covers.delete(previousCover)
        touchSeries(volume.seriesId)
    }

    suspend fun deleteVolume(volume: Volume) {
        dao.deleteVolume(volume)
        covers.delete(volume.coverPath)
        touchSeries(volume.seriesId)
    }

    /** Marca la serie como actualizada (sirve para ordenar «En lectura» por lo último tocado). */
    private suspend fun touchSeries(seriesId: Long) {
        dao.getSeriesWithVolumes(seriesId)?.let { updateSeries(it.series) }
    }

    // --- Colecciones ---

    val collections: Flow<List<CollectionWithCount>> = dao.observeCollections()

    fun observeCollection(collectionId: Long): Flow<ShelfCollection?> = dao.observeCollection(collectionId)

    fun observeSeriesInCollection(collectionId: Long): Flow<List<SeriesWithVolumes>> =
        dao.observeSeriesInCollection(collectionId)

    fun observeCollectionIdsOfSeries(seriesId: Long): Flow<List<Long>> = dao.observeCollectionIdsOfSeries(seriesId)

    suspend fun createCollection(name: String): Long = dao.insertCollection(ShelfCollection(name = name.trim()))

    suspend fun renameCollection(collection: ShelfCollection, name: String) =
        dao.updateCollection(collection.copy(name = name.trim()))

    /** Borra la colección. Las series no se tocan: solo dejan de estar agrupadas. */
    suspend fun deleteCollection(collection: ShelfCollection) = dao.deleteCollection(collection)

    suspend fun setInCollection(seriesId: Long, collectionId: Long, inCollection: Boolean) {
        val ref = SeriesCollectionCrossRef(seriesId, collectionId)
        if (inCollection) dao.addToCollection(ref) else dao.removeFromCollection(ref)
    }
}
