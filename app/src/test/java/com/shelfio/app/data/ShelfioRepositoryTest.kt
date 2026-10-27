package com.shelfio.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.shelfio.app.data.db.ShelfioDatabase
import com.shelfio.app.data.model.ItemType
import com.shelfio.app.data.model.ReadingStatus
import com.shelfio.app.data.model.Series
import com.shelfio.app.data.model.Volume
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Pruebas del repositorio contra una base de datos Room real, en memoria. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ShelfioRepositoryTest {

    private lateinit var db: ShelfioDatabase
    private lateinit var repository: ShelfioRepository
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, ShelfioDatabase::class.java).allowMainThreadQueries().build()
        repository = ShelfioRepository(db.dao(), CoverStorage(context))
    }

    @After
    fun tearDown() = db.close()

    private suspend fun createManga(title: String = "Saga del Dragón"): Long =
        repository.createSeries(Series(title = title, type = ItemType.MANGA), Volume(seriesId = 0, number = 1))

    /** Simula una portada guardada en disco. */
    private fun fakeCover(name: String) = File(context.filesDir, name).apply { writeText("x") }

    @Test
    fun createSeries_alsoCreatesFirstVolume() = runTest {
        val id = createManga()
        val saved = repository.getSeries(id)!!
        assertEquals("Saga del Dragón", saved.series.title)
        assertEquals(listOf(1), saved.volumes.map { it.number })
    }

    @Test
    fun addNextVolume_keepsCommonDataInSeries() = runTest {
        val id = createManga()
        repository.addVolume(nextVolume(id, repository.getSeries(id)!!.volumes))
        val saved = repository.getSeries(id)!!
        assertEquals(listOf(1, 2), saved.sortedVolumes.map { it.number })
        assertEquals(1, repository.library.first().size)
    }

    @Test
    fun updateSeries_isVisibleInLibrary() = runTest {
        val id = createManga()
        val series = repository.getSeries(id)!!.series
        repository.updateSeries(series.copy(author = "Autora"))
        assertEquals("Autora", repository.library.first().single().series.author)
    }

    @Test
    fun deleteSeries_removesVolumesAndCoverFiles() = runTest {
        val id = createManga()
        val seriesCover = fakeCover("serie.jpg")
        val volumeCover = fakeCover("tomo.jpg")
        val item = repository.getSeries(id)!!
        repository.updateSeries(item.series.copy(coverPath = seriesCover.path))
        repository.updateVolume(item.volumes.first().copy(coverPath = volumeCover.path))

        repository.deleteSeries(repository.getSeries(id)!!)

        assertNull(repository.getSeries(id))
        assertTrue(repository.library.first().isEmpty())
        assertFalse(seriesCover.exists())
        assertFalse(volumeCover.exists())
    }

    @Test
    fun setSeriesCover_replacesAndDeletesOldFile() = runTest {
        val id = createManga()
        val old = fakeCover("vieja.jpg")
        val new = fakeCover("nueva.jpg")
        repository.setSeriesCover(repository.getSeries(id)!!.series, old.path)
        repository.setSeriesCover(repository.getSeries(id)!!.series, new.path)
        assertEquals(new.path, repository.getSeries(id)!!.series.coverPath)
        assertFalse(old.exists())

        // Quitar portada: vuelve a null (se usará la generada) y se borra el archivo.
        repository.setSeriesCover(repository.getSeries(id)!!.series, null)
        assertNull(repository.getSeries(id)!!.series.coverPath)
        assertFalse(new.exists())
    }

    @Test
    fun locationAndStatus_persistAndAreSearchable() = runTest {
        val id = createManga()
        val volume = repository.getSeries(id)!!.volumes.first()
        repository.updateVolume(volume.copy(location = "Caja 3", status = ReadingStatus.READ))

        val library = repository.library.first()
        assertEquals("Caja 3", library.single().volumes.single().location)
        assertEquals(1, volumesAtLocation(library, "caja 3").size)
        assertEquals(1f, library.single().progress, 0.0001f)
    }

    @Test
    fun collections_countMembersAndDeletingKeepsSeries() = runTest {
        val dragon = createManga()
        val faro = createManga("El faro del norte")
        val favoritos = repository.createCollection("  Favoritos ")
        repository.setInCollection(dragon, favoritos, true)
        repository.setInCollection(faro, favoritos, true)
        repository.setInCollection(faro, favoritos, true) // repetir no duplica

        val summary = repository.collections.first().single()
        assertEquals("Favoritos", summary.collection.name)
        assertEquals(2, summary.seriesCount)
        assertEquals(listOf(favoritos), repository.observeCollectionIdsOfSeries(dragon).first())

        repository.setInCollection(dragon, favoritos, false)
        assertEquals(listOf(faro), repository.observeSeriesInCollection(favoritos).first().map { it.series.id })

        repository.deleteCollection(summary.collection)
        assertTrue(repository.collections.first().isEmpty())
        assertEquals(2, repository.library.first().size)
    }

    @Test
    fun loans_rulesAndHistory() = runTest {
        val id = createManga()
        repository.addVolume(Volume(seriesId = id, number = 2, owned = false))
        val (owned, notOwned) = repository.getSeries(id)!!.sortedVolumes

        assertEquals("Solo puedes prestar lo que tienes.", repository.lendVolume(notOwned, "Ana", null))
        // Lo que no es mío sí me lo pueden prestar.
        assertNull(repository.lendVolume(notOwned, "Marta", null, borrowed = true))
        assertTrue(repository.getSeries(id)!!.activeLoanOf(notOwned)!!.borrowed)
        assertNull(repository.lendVolume(owned, "Ana", null))
        assertEquals("Ya tiene un préstamo activo.", repository.lendVolume(owned, "Luis", null))

        val active = repository.getSeries(id)!!.activeLoanOf(owned)!!
        assertEquals("Ana", active.person)
        // La búsqueda encuentra el préstamo por la persona.
        assertEquals(1, filterLibrary(repository.library.first(), "ana", null).size)

        repository.returnLoan(active)
        val afterReturn = repository.getSeries(id)!!
        assertNull(afterReturn.activeLoanOf(owned))
        assertTrue(afterReturn.loans.first { it.person == "Ana" }.returnedAt != null)
        // Ya devuelto, se puede volver a prestar.
        assertNull(repository.lendVolume(owned, "Luis", null))
    }

    @Test
    fun deleteVolume_keepsSeriesAndOtherVolumes() = runTest {
        val id = createManga()
        repository.addVolume(Volume(seriesId = id, number = 2))
        val second = repository.getSeries(id)!!.volumes.first { it.number == 2 }
        repository.deleteVolume(second)
        assertEquals(listOf(1), repository.getSeries(id)!!.volumes.map { it.number })
    }
}
