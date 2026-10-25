package com.shelfio.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.shelfio.app.data.model.CollectionWithCount
import com.shelfio.app.data.model.Series
import com.shelfio.app.data.model.SeriesCollectionCrossRef
import com.shelfio.app.data.model.SeriesWithVolumes
import com.shelfio.app.data.model.ShelfCollection
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

    @Transaction
    @Query("SELECT * FROM series WHERE id = :seriesId")
    suspend fun getSeriesWithVolumes(seriesId: Long): SeriesWithVolumes?

    @Insert
    suspend fun insertSeries(series: Series): Long

    @Update
    suspend fun updateSeries(series: Series)

    @Delete
    suspend fun deleteSeries(series: Series)

    // --- Tomos ---

    @Query("SELECT * FROM volumes WHERE id = :volumeId")
    suspend fun getVolume(volumeId: Long): Volume?

    @Insert
    suspend fun insertVolume(volume: Volume): Long

    @Update
    suspend fun updateVolume(volume: Volume)

    @Delete
    suspend fun deleteVolume(volume: Volume)

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

    @Query("SELECT * FROM collections WHERE id = :collectionId")
    fun observeCollection(collectionId: Long): Flow<ShelfCollection?>

    @Transaction
    @Query(
        """
        SELECT s.* FROM series s JOIN series_collections sc ON sc.seriesId = s.id
        WHERE sc.collectionId = :collectionId ORDER BY s.title COLLATE NOCASE
        """,
    )
    fun observeSeriesInCollection(collectionId: Long): Flow<List<SeriesWithVolumes>>

    @Query("SELECT collectionId FROM series_collections WHERE seriesId = :seriesId")
    fun observeCollectionIdsOfSeries(seriesId: Long): Flow<List<Long>>

    @Insert
    suspend fun insertCollection(collection: ShelfCollection): Long

    @Update
    suspend fun updateCollection(collection: ShelfCollection)

    @Delete
    suspend fun deleteCollection(collection: ShelfCollection)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addToCollection(ref: SeriesCollectionCrossRef)

    @Delete
    suspend fun removeFromCollection(ref: SeriesCollectionCrossRef)
}
