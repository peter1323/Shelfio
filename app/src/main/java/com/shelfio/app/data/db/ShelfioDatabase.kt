package com.shelfio.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.shelfio.app.data.model.SeriesCollectionCrossRef
import com.shelfio.app.data.model.Series
import com.shelfio.app.data.model.ShelfCollection
import com.shelfio.app.data.model.Volume

/**
 * Base de datos local (Room sobre SQLite). Si cambia el esquema, subir `version` y añadir una migración:
 * nunca se usa migración destructiva para no perder la biblioteca del usuario.
 */
@Database(
    entities = [Series::class, Volume::class, ShelfCollection::class, SeriesCollectionCrossRef::class],
    version = 1,
    exportSchema = true,
)
abstract class ShelfioDatabase : RoomDatabase() {
    abstract fun dao(): ShelfioDao

    companion object {
        fun create(context: Context): ShelfioDatabase =
            Room.databaseBuilder(context, ShelfioDatabase::class.java, "shelfio.db").build()
    }
}
