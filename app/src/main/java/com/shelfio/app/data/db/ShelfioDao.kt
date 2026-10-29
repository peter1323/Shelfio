package com.shelfio.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.shelfio.app.data.model.CollectionWithCount
import com.shelfio.app.data.model.Loan
import com.shelfio.app.data.model.ReadCredit
import com.shelfio.app.data.model.Series
import com.shelfio.app.data.model.SeriesCollectionCrossRef
import com.shelfio.app.data.model.SeriesWithVolumes
import com.shelfio.app.data.model.ShelfCollection
import com.shelfio.app.data.model.UnlockedTrophy
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

    /** Qué series hay en cada estante y cuándo se añadieron (portadas de las tarjetas y orden por fecha). */
    @Query("SELECT * FROM series_collections")
    fun observeSeriesCollections(): Flow<List<SeriesCollectionCrossRef>>

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

    // --- Préstamos ---

    @Insert
    suspend fun insertLoan(loan: Loan): Long

    @Update
    suspend fun updateLoan(loan: Loan)

    @Query("SELECT * FROM loans WHERE volumeId = :volumeId")
    suspend fun loansOfVolume(volumeId: Long): List<Loan>

    // --- Copia de seguridad ---

    @Query("SELECT * FROM series")
    suspend fun allSeries(): List<Series>

    @Query("SELECT * FROM volumes")
    suspend fun allVolumes(): List<Volume>

    @Query("SELECT * FROM collections")
    suspend fun allCollections(): List<ShelfCollection>

    @Query("SELECT * FROM series_collections")
    suspend fun allSeriesCollections(): List<SeriesCollectionCrossRef>

    @Query("DELETE FROM series")
    suspend fun deleteAllSeries()

    @Query("DELETE FROM collections")
    suspend fun deleteAllCollections()

    @Insert
    suspend fun insertSeries(series: List<Series>)

    @Insert
    suspend fun insertVolumes(volumes: List<Volume>)

    @Insert
    suspend fun insertCollections(collections: List<ShelfCollection>)

    @Insert
    suspend fun insertSeriesCollections(refs: List<SeriesCollectionCrossRef>)

    @Insert
    suspend fun insertLoans(loans: List<Loan>)

    @Query("SELECT * FROM loans")
    suspend fun allLoans(): List<Loan>

    // --- Logros ---

    @Query("SELECT * FROM trophies ORDER BY unlockedAt")
    fun observeTrophies(): Flow<List<UnlockedTrophy>>

    @Query("SELECT * FROM trophies")
    suspend fun allTrophies(): List<UnlockedTrophy>

    /** IGNORE: un trofeo que ya estaba conserva su fecha original. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTrophies(trophies: List<UnlockedTrophy>)

    @Query("SELECT * FROM read_credits")
    fun observeReadCredits(): Flow<List<ReadCredit>>

    @Query("SELECT * FROM read_credits")
    suspend fun allReadCredits(): List<ReadCredit>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertReadCredits(credits: List<ReadCredit>)

    @Query("DELETE FROM trophies")
    suspend fun deleteAllTrophies()

    @Query("DELETE FROM read_credits")
    suspend fun deleteAllReadCredits()

    /**
     * Sustituye toda la biblioteca (y los logros) por la de una copia, conservando los ids originales.
     * Es una transacción: si algo falla no se cambia nada. Borrar series y colecciones borra
     * en cascada sus tomos, préstamos y relaciones.
     */
    @Transaction
    suspend fun replaceAll(
        series: List<Series>,
        volumes: List<Volume>,
        collections: List<ShelfCollection>,
        seriesCollections: List<SeriesCollectionCrossRef>,
        loans: List<Loan>,
        trophies: List<UnlockedTrophy>,
        readCredits: List<ReadCredit>,
    ) {
        deleteAllSeries()
        deleteAllCollections()
        deleteAllTrophies()
        deleteAllReadCredits()
        insertSeries(series)
        insertVolumes(volumes)
        insertCollections(collections)
        insertSeriesCollections(seriesCollections)
        insertLoans(loans)
        insertTrophies(trophies)
        insertReadCredits(readCredits)
    }
}
