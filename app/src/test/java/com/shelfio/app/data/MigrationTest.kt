package com.shelfio.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.shelfio.app.data.db.ShelfioDatabase
import com.shelfio.app.data.model.PublicationStatus
import com.shelfio.app.data.model.ReadingStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Migraciones de Room con datos reales: se crea una base de datos tal como era en la versión 1
 * (con el esquema guardado en app/schemas), se rellena y se abre con la app actual.
 * Room aplica las migraciones y comprueba que el esquema final es el esperado; si no, falla.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "migracion-test.db"

    /** Crea la base de datos de la versión [version] ejecutando el SQL de su esquema exportado. */
    private fun createDatabaseAtVersion(version: Int, fill: SQLiteDatabase.() -> Unit) {
        context.deleteDatabase(dbName)
        val schema = JSONObject(File("schemas/com.shelfio.app.data.db.ShelfioDatabase/$version.json").readText()).getJSONObject("database")
        val db = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(dbName).apply { parentFile?.mkdirs() }, null)
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val table = entity.getString("tableName")
            db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
            val indices = entity.optJSONArray("indices") ?: continue
            for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
        }
        val setup = schema.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
        db.fill()
        db.version = version
        db.close()
    }

    private fun openWithApp(): ShelfioDatabase =
        Room.databaseBuilder(context, ShelfioDatabase::class.java, dbName)
            .addMigrations(*ShelfioDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()

    @Test
    fun migrateFromVersion1ToLatest_keepsAllData() = runTest {
        createDatabaseAtVersion(1) {
            execSQL("INSERT INTO series (id, title, author, type, publisher, language, coverPath, description, notes, createdAt, updatedAt) VALUES (1, 'Saga del Dragón', 'Autora', 'MANGA', 'Editorial', 'español', NULL, 'Desc', 'Notas', 1000, 2000)")
            execSQL("INSERT INTO volumes (id, seriesId, number, title, isbn, coverPath, owned, status, location, notes) VALUES (10, 1, 1, '', '9780140328721', NULL, 1, 'READ', 'Caja 3', 'nota')")
            execSQL("INSERT INTO volumes (id, seriesId, number, title, isbn, coverPath, owned, status, location, notes) VALUES (11, 1, 2, '', '', NULL, 0, 'UNREAD', '', '')")
            execSQL("INSERT INTO collections (id, name) VALUES (5, 'Favoritos')")
            execSQL("INSERT INTO series_collections (seriesId, collectionId) VALUES (1, 5)")
        }

        val db = openWithApp()
        val library = db.dao().observeLibrary().first()
        val series = library.single()
        assertEquals("Saga del Dragón", series.series.title)
        assertEquals("Autora", series.series.author)
        assertEquals("Desc", series.series.description)
        assertEquals(PublicationStatus.UNKNOWN, series.series.statusInMyLanguage)
        assertNull(series.series.publishedInMyLanguage)
        assertEquals(emptyList<Any>(), series.loans)

        val owned = series.volumes.first { it.id == 10L }
        assertEquals("Caja 3", owned.location)
        assertEquals(ReadingStatus.READ, owned.status)
        assertEquals("9780140328721", owned.isbn)
        assertNull(owned.wishlistAddedAt)

        // El tomo que no tenía entra en la lista de deseos con fecha y sin prioridad.
        val missing = series.volumes.first { it.id == 11L }
        assertNotNull(missing.wishlistAddedAt)
        assertNull(missing.purchasePriority)
        assertEquals("", missing.purchaseNote)

        assertEquals(1, db.dao().observeCollections().first().single().seriesCount)
        db.close()
    }
}
