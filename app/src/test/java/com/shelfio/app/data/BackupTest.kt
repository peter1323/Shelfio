package com.shelfio.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.shelfio.app.data.backup.BackupException
import com.shelfio.app.data.backup.BackupManager
import com.shelfio.app.data.db.ShelfioDatabase
import com.shelfio.app.data.model.ItemType
import com.shelfio.app.data.model.PublicationStatus
import com.shelfio.app.data.model.ReadingStatus
import com.shelfio.app.data.model.Series
import com.shelfio.app.data.model.SeriesWithVolumes
import com.shelfio.app.data.model.Volume
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Exportar → importar deja la biblioteca igual; un archivo inválido no cambia nada. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupTest {

    private lateinit var context: Context
    private val databases = mutableListOf<ShelfioDatabase>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() = databases.forEach { it.close() }

    /** Una «app» con su propia base de datos en memoria. */
    private fun newApp(): Pair<ShelfioRepository, BackupManager> {
        val db = Room.inMemoryDatabaseBuilder(context, ShelfioDatabase::class.java).allowMainThreadQueries().build()
        databases += db
        val covers = CoverStorage(context)
        return ShelfioRepository(db.dao(), covers) to BackupManager(context, db.dao(), covers)
    }

    private suspend fun fillLibrary(repository: ShelfioRepository) {
        val dragon = repository.createSeries(Series(title = "Saga del Dragón", type = ItemType.MANGA, author = "Autora"), Volume(seriesId = 0, number = 1, location = "Caja 3", status = ReadingStatus.READ))
        repository.addVolume(Volume(seriesId = dragon, number = 2, owned = false))
        val faro = repository.createSeries(Series(title = "El faro del norte", type = ItemType.BOOK, statusInMyLanguage = PublicationStatus.FINISHED, publishedInMyLanguage = 1), Volume(seriesId = 0, number = 1, isbn = "9780140328721"))
        val cover = File(context.filesDir, "covers/prueba.jpg").apply { parentFile?.mkdirs(); writeBytes(byteArrayOf(1, 2, 3)) }
        repository.setSeriesCover(repository.getSeries(faro)!!.series, cover.absolutePath)
        val favoritos = repository.createCollection("Favoritos")
        repository.setInCollection(dragon, favoritos, true)
        repository.lendVolume(repository.getSeries(dragon)!!.sortedVolumes.first(), "Ana", 123L)
    }

    /** Lo que importa comparar: todo menos la ruta exacta de la portada (cambia de nombre al importar). */
    private fun comparable(library: List<SeriesWithVolumes>) = library.map { item ->
        item.series.copy(coverPath = item.series.coverPath?.let { "con portada" }) to
            item.sortedVolumes.map { it.copy(coverPath = it.coverPath?.let { "con portada" }) }
    }

    @Test
    fun exportThenImportInFreshApp_keepsLibraryExactly() = runTest {
        val (original, originalBackup) = newApp()
        fillLibrary(original)
        val zip = ByteArrayOutputStream().also { originalBackup.writeZip(it) }.toByteArray()

        val (fresh, freshBackup) = newApp()
        val data = freshBackup.readZip(ByteArrayInputStream(zip))
        assertEquals(2, data.series.size)
        assertEquals(3, data.volumes.size)
        assertEquals(1, data.covers.size)
        freshBackup.restore(data)

        assertEquals(comparable(original.library.first()), comparable(fresh.library.first()))
        assertEquals(original.collections.first(), fresh.collections.first())
        assertEquals(original.library.first().flatMap { it.loans }, fresh.library.first().flatMap { it.loans })
        assertEquals("Ana", fresh.library.first().flatMap { it.loans }.single().person)
        val restoredCover = fresh.library.first().first { it.series.title == "El faro del norte" }.series.coverPath
        assertNotNull(restoredCover)
        assertArrayEquals(byteArrayOf(1, 2, 3), File(restoredCover!!).readBytes())
    }

    @Test
    fun importReplacesCurrentLibrary() = runTest {
        val (source, sourceBackup) = newApp()
        source.createSeries(Series(title = "Solo esta", type = ItemType.COMIC), Volume(seriesId = 0, number = 1))
        val zip = ByteArrayOutputStream().also { sourceBackup.writeZip(it) }.toByteArray()

        val (target, targetBackup) = newApp()
        fillLibrary(target)
        targetBackup.restore(targetBackup.readZip(ByteArrayInputStream(zip)))

        assertEquals(listOf("Solo esta"), target.library.first().map { it.series.title })
        assertEquals(emptyList<Any>(), target.collections.first())
    }

    @Test
    fun invalidFiles_areRejectedAndLibraryIsUntouched() = runTest {
        val (repository, backup) = newApp()
        fillLibrary(repository)
        val before = comparable(repository.library.first())

        val invalidFiles = listOf(
            "esto no es un zip".toByteArray(),
            zipWith("otra-cosa.txt" to "hola"),
            zipWith("datos.json" to "{ roto"),
            zipWith("datos.json" to """{"formatVersion": 99, "series": [], "volumes": [], "collections": [], "seriesCollections": []}"""),
            zipWith("datos.json" to """{"formatVersion": 1, "series": [], "volumes": [{"id": 1, "seriesId": 5, "number": 1, "owned": true, "status": "READ"}], "collections": [], "seriesCollections": []}"""),
            zipWith("datos.json" to """{"formatVersion": 1, "series": [{"id": 1, "title": "", "type": "BOOK"}], "volumes": [], "collections": [], "seriesCollections": []}"""),
            zipWith("datos.json" to """{"formatVersion": 1, "series": [{"id": 1, "title": "X", "type": "DISCO"}], "volumes": [], "collections": [], "seriesCollections": []}"""),
        )
        invalidFiles.forEach { bytes ->
            try {
                backup.readZip(ByteArrayInputStream(bytes))
                fail("Debería rechazar un archivo inválido")
            } catch (e: BackupException) {
                // Mensaje claro para el usuario
                assertNotNull(e.message)
            }
        }
        assertEquals(before, comparable(repository.library.first()))
    }

    /** Copia hecha con la Fase 7 (formatVersion 1): sin campos de lista de deseos. Debe seguir importándose. */
    @Test
    fun importsCopyFromFormatVersion1() = runTest {
        val (repository, backup) = newApp()
        val v1 = """
            {"formatVersion": 1, "appVersion": "0.1.0", "exportedAt": 1,
             "series": [{"id": 1, "title": "Saga del Dragón", "author": "", "type": "MANGA", "publisher": "", "language": "",
                         "coverFile": null, "description": "", "notes": "", "createdAt": 1, "updatedAt": 1}],
             "volumes": [{"id": 1, "seriesId": 1, "number": 1, "title": "", "isbn": "", "coverFile": null, "owned": true, "status": "READ", "location": "Caja 3", "notes": ""},
                         {"id": 2, "seriesId": 1, "number": 2, "title": "", "isbn": "", "coverFile": null, "owned": false, "status": "UNREAD", "location": "", "notes": ""}],
             "collections": [], "seriesCollections": []}
        """.trimIndent()
        backup.restore(backup.readZip(ByteArrayInputStream(zipWith("datos.json" to v1))))

        val volumes = repository.library.first().single().sortedVolumes
        assertEquals("Caja 3", volumes[0].location)
        assertNotNull(volumes[1].wishlistAddedAt)
        assertEquals(1, pendingPurchase(repository.library.first()).size)
    }

    private fun zipWith(vararg entries: Pair<String, String>): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
    }.toByteArray()
}
