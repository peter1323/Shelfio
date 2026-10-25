package com.shelfio.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.shelfio.app.data.model.CollectionWithCount
import com.shelfio.app.data.model.Series
import com.shelfio.app.data.model.SeriesWithVolumes
import com.shelfio.app.data.model.Volume
import kotlinx.coroutines.flow.Flow

/** Consultas SQL de Shelfio. Los Flow avisan solos cuando cambian los datos. Solo lo usa el repositorio. */
@Dao
interface ShelfioDao {

    // --- Series ---

    @Transaction
    @Query("SELECT * FROM series ORDER BY title COLLATE NOCASE")
    fun observeLibrary(): Flow<List<SeriesWithVolumes>>

    @Transaction
    @Query("SELECT * FROM series WHERE id = :seriesId")
    fun observeSeries(seriesId: Long): Flow<SeriesWithVolumes?>

    @Query("SELECT * FROM series WHERE id = :seriesId")
    suspend fun getSeries(seriesId: Long): Series?

    @Insert
    suspend fun insertSeries(series: Series): Long

    @Update
    suspend fun updateSeries(series: Series)

    @Delete
    suspend fun deleteSeries(series: Series)

    // --- Tomos ---

    @Insert
    suspend fun insertVolume(volume: Volume): Long

    /** Crea la serie y su primer tomo a la vez: o se guardan los dos o ninguno. */
    @Transaction
    suspend fun insertSeriesWithFirstVolume(series: Series, firstVolume: Volume): Long {
        val seriesId = insertSeries(series)
        insertVolume(firstVolume.copy(seriesId = seriesId))
        return seriesId
    }

    // --- Colecciones ---

    @Query(
        """
        SELECT c.*, COUNT(sc.seriesId) AS seriesCount
        FROM collections c LEFT JOIN series_collections sc ON sc.collectionId = c.id
        GROUP BY c.id ORDER BY c.name COLLATE NOCASE
        """,
    )
    fun observeCollections(): Flow<List<CollectionWithCount>>
}
