package com.shelfio.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.shelfio.app.data.model.Loan
import com.shelfio.app.data.model.ReadCredit
import com.shelfio.app.data.model.SeriesCollectionCrossRef
import com.shelfio.app.data.model.Series
import com.shelfio.app.data.model.ShelfCollection
import com.shelfio.app.data.model.UnlockedTrophy
import com.shelfio.app.data.model.Volume

/**
 * Base de datos local (Room sobre SQLite). Si cambia el esquema, subir `version` y añadir una migración
 * a [MIGRATIONS]: nunca se usa migración destructiva para no perder la biblioteca del usuario.
 * El esquema de cada versión queda en app/schemas y las migraciones se prueban en MigrationTest.
 */
@Database(
    entities = [Series::class, Volume::class, ShelfCollection::class, SeriesCollectionCrossRef::class, Loan::class, UnlockedTrophy::class, ReadCredit::class],
    version = 9,
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

        /** Versión 3 → 4: estado editorial de las series (por defecto «desconocido»). */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE series ADD COLUMN statusInMyLanguage TEXT NOT NULL DEFAULT 'UNKNOWN'")
                db.execSQL("ALTER TABLE series ADD COLUMN statusInOrigin TEXT NOT NULL DEFAULT 'UNKNOWN'")
                db.execSQL("ALTER TABLE series ADD COLUMN publishedInMyLanguage INTEGER")
            }
        }

        /** Versión 4 → 5: otros títulos de la serie y préstamos «me lo han prestado». */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE series ADD COLUMN otherTitles TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE loans ADD COLUMN borrowed INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Versión 5 → 6: se guarda la portada del buscador para recuperarla al quitar una foto. */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE series ADD COLUMN originalCoverPath TEXT")
            }
        }

        /** Versión 6 → 7: logros (trofeos conseguidos y tomos que ya contaron como leídos). */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `trophies` (`id` TEXT NOT NULL, `unlockedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `read_credits` (`volumeId` INTEGER NOT NULL, `creditedAt` INTEGER NOT NULL, PRIMARY KEY(`volumeId`))")
            }
        }

        /** Versión 7 → 8: estantes con foto y fecha en que se añadió cada serie. */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE collections ADD COLUMN coverPath TEXT")
                db.execSQL("ALTER TABLE series_collections ADD COLUMN addedAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Versión 8 → 9: cuánto se ha leído de un tomo (barra de lectura). */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE volumes ADD COLUMN readLevel INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)

        fun create(context: Context): ShelfioDatabase =
            Room.databaseBuilder(context, ShelfioDatabase::class.java, "shelfio.db")
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
