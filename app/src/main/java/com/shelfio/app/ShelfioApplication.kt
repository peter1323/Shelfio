package com.shelfio.app

import android.app.Application
import com.shelfio.app.data.CoverStorage
import com.shelfio.app.data.SettingsRepository
import com.shelfio.app.data.ShelfioRepository
import com.shelfio.app.data.backup.BackupManager
import com.shelfio.app.data.books.BookLookup
import com.shelfio.app.data.db.ShelfioDatabase
import com.shelfio.app.data.report.PdfReport

/** Crea una sola vez la base de datos y los repositorios que comparte toda la app. */
class ShelfioApplication : Application() {
    private val database by lazy { ShelfioDatabase.create(this) }
    val covers by lazy { CoverStorage(this) }
    val repository by lazy { ShelfioRepository(database.dao(), covers) }
    val backup by lazy { BackupManager(this, database.dao(), covers) }
    val pdfReport by lazy { PdfReport(this) }
    val settings by lazy { SettingsRepository(this) }
    val bookLookup by lazy { BookLookup() }
}
