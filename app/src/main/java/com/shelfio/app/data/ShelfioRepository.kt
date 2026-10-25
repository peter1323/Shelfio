package com.shelfio.app.data

import com.shelfio.app.data.db.ShelfioDao
import com.shelfio.app.data.model.CollectionWithCount
import com.shelfio.app.data.model.Series
import com.shelfio.app.data.model.SeriesWithVolumes
import com.shelfio.app.data.model.Volume
import kotlinx.coroutines.flow.Flow

/**
 * Punto único de acceso a los datos de la biblioteca. Los ViewModels hablan con el repositorio,
 * nunca con el DAO ni con la base de datos directamente.
 */
class ShelfioRepository(private val dao: ShelfioDao) {

    val library: Flow<List<SeriesWithVolumes>> = dao.observeLibrary()

    val collections: Flow<List<CollectionWithCount>> = dao.observeCollections()

    fun observeSeries(seriesId: Long): Flow<SeriesWithVolumes?> = dao.observeSeries(seriesId)

    suspend fun getSeries(seriesId: Long): Series? = dao.getSeries(seriesId)

    /** Al crear una serie se crea también su primer tomo. Devuelve el id de la serie nueva. */
    suspend fun createSeries(series: Series, firstVolume: Volume): Long =
        dao.insertSeriesWithFirstVolume(series, firstVolume)

    suspend fun updateSeries(series: Series) =
        dao.updateSeries(series.copy(updatedAt = System.currentTimeMillis()))

    suspend fun deleteSeries(series: Series) = dao.deleteSeries(series)
}
