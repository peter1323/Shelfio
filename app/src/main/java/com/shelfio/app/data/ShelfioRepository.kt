package com.shelfio.app.data

import com.shelfio.app.data.db.ShelfioDao
import com.shelfio.app.data.model.CollectionWithCount
import com.shelfio.app.data.model.Loan
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
        dao.insertSeriesWithFirstVolume(series, firstVolume.withOwnershipRules())

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

    suspend fun loansOfVolume(volumeId: Long): List<Loan> = dao.loansOfVolume(volumeId)

    // Todos los tomos pasan por withOwnershipRules: así «lo tengo», ubicación y lista de deseos siempre cuadran.

    suspend fun addVolume(volume: Volume): Long =
        dao.insertVolume(volume.withOwnershipRules()).also { touchSeries(volume.seriesId) }

    /** Guarda los cambios de un tomo. Si cambió la portada, borra el archivo anterior. */
    suspend fun updateVolume(volume: Volume) {
        val previousCover = dao.getVolume(volume.id)?.coverPath
        dao.updateVolume(volume.withOwnershipRules())
        if (previousCover != volume.coverPath) covers.delete(previousCover)
        touchSeries(volume.seriesId)
    }

    /** «Lo he comprado»: pasa a «lo tengo», sale de la lista de deseos y guarda dónde se va a guardar. */
    suspend fun markBought(volume: Volume, location: String) =
        updateVolume(volume.copy(owned = true, location = location.trim()))

    /** Crea de golpe, como «no lo tengo», los tomos que faltan del 1 al [upTo]. Devuelve cuántos ha creado. */
    suspend fun addMissingVolumes(seriesId: Long, upTo: Int): Int {
        val existing = dao.getSeriesWithVolumes(seriesId)?.volumes ?: return 0
        val missing = missingVolumeNumbers(existing, upTo)
        missing.forEach { dao.insertVolume(Volume(seriesId = seriesId, number = it, owned = false).withOwnershipRules()) }
        if (missing.isNotEmpty()) touchSeries(seriesId)
        return missing.size
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

    // --- Préstamos ---

    /**
     * Presta un tomo. Devuelve un mensaje de error si no se puede (no lo tengo o ya está prestado),
     * o null si se ha guardado.
     */
    suspend fun lendVolume(volume: Volume, person: String, dueAt: Long?): String? {
        val current = dao.getVolume(volume.id) ?: return "El tomo ya no existe."
        loanProblem(current, dao.loansOfVolume(current.id))?.let { return it }
        if (person.isBlank()) return "Escribe a quién se lo prestas."
        dao.insertLoan(Loan(volumeId = current.id, person = person.trim(), lentAt = System.currentTimeMillis(), dueAt = dueAt))
        touchSeries(current.seriesId)
        return null
    }

    /** «Marcar como devuelto»: guarda la fecha real y el préstamo pasa al historial. */
    suspend fun returnLoan(loan: Loan) {
        dao.updateLoan(loan.copy(returnedAt = System.currentTimeMillis()))
        dao.getVolume(loan.volumeId)?.let { touchSeries(it.seriesId) }
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
