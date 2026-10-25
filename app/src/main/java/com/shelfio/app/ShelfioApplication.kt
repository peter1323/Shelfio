package com.shelfio.app

import android.app.Application
import com.shelfio.app.data.CoverStorage
import com.shelfio.app.data.SettingsRepository
import com.shelfio.app.data.ShelfioRepository
import com.shelfio.app.data.db.ShelfioDatabase

/** Crea una sola vez la base de datos y los repositorios que comparte toda la app. */
class ShelfioApplication : Application() {
    val covers by lazy { CoverStorage(this) }
    val repository by lazy { ShelfioRepository(ShelfioDatabase.create(this).dao(), covers) }
    val settings by lazy { SettingsRepository(this) }
}
