package com.shelfio.app

import android.app.Application
import com.shelfio.app.data.SettingsRepository
import com.shelfio.app.data.ShelfioRepository
import com.shelfio.app.data.db.ShelfioDatabase

/** Crea una sola vez la base de datos y los repositorios que comparte toda la app. */
class ShelfioApplication : Application() {
    val repository by lazy { ShelfioRepository(ShelfioDatabase.create(this).dao()) }
    val settings by lazy { SettingsRepository(this) }
}
