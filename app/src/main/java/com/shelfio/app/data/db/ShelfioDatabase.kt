package com.shelfio.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.shelfio.app.data.model.Loan
import com.shelfio.app.data.model.SeriesCollectionCrossRef
import com.shelfio.app.data.model.Series
import com.shelfio.app.data.model.ShelfCollection
import com.shelfio.app.data.model.Volume

/**
 * Base de datos local (Room sobre SQLite). Si cambia el esquema, subir `version` y añadir una migración
 * a [MIGRATIONS]: nunca se usa migración destructiva para no perder la biblioteca del usuario.
 * El esquema de cada versión queda en app/schemas y las migraciones se prueban en MigrationTest.
 */
@Database(
    entities = [Series::class, Volume::class, ShelfCollection::class, SeriesCollectionCrossRef::class, Loan::class],
    version = 3,
    exportSchema = true,
)
abstract class ShelfioDatabase : RoomDatabase() {
    abstract fun dao(): ShelfioDao

    companion object {
        /** Versión 1 → 2: lista de deseos (prioridad, nota de compra y fecha de alta en los tomos). */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE volumes ADD COLUMN purchasePriority TEXT")
                db.execSQL("ALTER TABLE volumes ADD COLUMN purchaseNote TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE volumes ADD COLUMN wishlistAddedAt INTEGER")
                // Los tomos que ya estaban como «no lo tengo» entran en la lista con la fecha de hoy.
                db.execSQL("UPDATE volumes SET wishlistAddedAt = strftime('%s','now') * 1000 WHERE owned = 0")
            }
        }

        /** Versión 2 → 3: tabla de préstamos (SQL copiado del esquema 3.json que genera Room). */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `loans` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `volumeId` INTEGER NOT NULL, " +
                        "`person` TEXT NOT NULL, `lentAt` INTEGER NOT NULL, `dueAt` INTEGER, `returnedAt` INTEGER, `notes` TEXT NOT NULL, " +
                        "FOREIGN KEY(`volumeId`) REFERENCES `volumes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_loans_volumeId` ON `loans` (`volumeId`)")
            }
        }

        val MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)

        fun create(context: Context): ShelfioDatabase =
            Room.databaseBuilder(context, ShelfioDatabase::class.java, "shelfio.db")
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
