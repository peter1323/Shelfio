package com.shelfio.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Reglas de los logros y antitrampas: cada tomo cuenta una vez, límite diario y trofeos que no se pierden. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TrophiesTest {

    private lateinit var db: ShelfioDatabase
    private lateinit var repository: ShelfioRepository
    private var clock = 1_000_000_000_000L
    private lateinit var trophies: TrophyManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ShelfioDatabase::class.java).allowMainThreadQueries().build()
        repository = ShelfioRepository(db.dao(), CoverStorage(context))
        trophies = TrophyManager(db.dao()) { clock }
    }

    @After
    fun tearDown() = db.close()

    private fun volume(id: Long, number: Int, read: Boolean = false, owned: Boolean = true) =
        Volume(id = id, seriesId = 1, number = number, owned = owned, status = if (read) ReadingStatus.READ else ReadingStatus.UNREAD)

    private fun manga(vararg volumes: Volume, published: Int? = 3, status: PublicationStatus = PublicationStatus.FINISHED) = SeriesWithVolumes(
        Series(id = 1, title = "Saga", type = ItemType.MANGA, publishedInMyLanguage = published, statusInMyLanguage = status),
        volumes.toList(),
    )

    @Test
    fun seriesTiers_bronzeSilverGoldPlatinum() {
        val all = manga(volume(1, 1), volume(2, 2), volume(3, 3))
        assertEquals(setOf(TrophyTier.GOLD), seriesTrophyTiers(all, emptySet()))
        assertEquals(setOf(TrophyTier.BRONZE, TrophyTier.GOLD), seriesTrophyTiers(all, setOf(1L)))
        assertEquals(setOf(TrophyTier.BRONZE, TrophyTier.SILVER, TrophyTier.GOLD), seriesTrophyTiers(all, setOf(1L, 2L)))
        assertEquals(TrophyTier.entries.toSet(), seriesTrophyTiers(all, setOf(1L, 2L, 3L)))
        // Sin terminar de publicarse no hay platino aunque esté todo leído.
        val publishing = manga(volume(1, 1), volume(2, 2), volume(3, 3), status = PublicationStatus.PUBLISHING)
        assertFalse(TrophyTier.PLATINUM in seriesTrophyTiers(publishing, setOf(1L, 2L, 3L)))
        // Sin saber cuántos hay publicados no hay oro (no basta con apuntar un solo tomo).
        assertFalse(TrophyTier.GOLD in seriesTrophyTiers(manga(volume(1, 1), published = null), emptySet()))
    }

    @Test
    fun appPlatinum_needsEveryOtherTrophy() {
        val allButOne = APP_TROPHIES.drop(1).map { it.id }.toSet()
        assertFalse(APP_PLATINUM_ID in achievedTrophies(emptyList(), 0, emptySet(), allButOne))
        assertTrue(APP_PLATINUM_ID in achievedTrophies(emptyList(), 0, emptySet(), APP_TROPHIES.map { it.id }.toSet()))
    }

    @Test
    fun readCredits_countOnceAndAtMostTwentyPerDay() = runTest {
        // Lo que ya estaba leído al estrenar los logros cuenta entero.
        val seriesId = repository.createSeries(Series(title = "Saga", type = ItemType.MANGA), Volume(seriesId = 0, number = 1, status = ReadingStatus.READ))
        trophies.check(repository.library.first(), 0)
        assertEquals(1, db.dao().allReadCredits().size)

        // Marcar 30 de golpe: hoy solo cuentan 20.
        (2..31).forEach { repository.addVolume(Volume(seriesId = seriesId, number = it, status = ReadingStatus.READ)) }
        trophies.check(repository.library.first(), 0)
        assertEquals(21, db.dao().allReadCredits().size)

        // Desmarcar y volver a marcar uno ya contado no suma nada.
        val first = repository.getSeries(seriesId)!!.sortedVolumes.first()
        repository.updateVolume(first.copy(status = ReadingStatus.UNREAD))
        repository.updateVolume(first.copy(status = ReadingStatus.READ))
        trophies.check(repository.library.first(), 0)
        assertEquals(21, db.dao().allReadCredits().size)

        // Al día siguiente cuentan los que faltaban.
        clock += 24 * 60 * 60 * 1000L
        trophies.check(repository.library.first(), 0)
        assertEquals(31, db.dao().allReadCredits().size)
    }

    @Test
    fun trophies_areNeverLost() = runTest {
        val seriesId = repository.createSeries(Series(title = "Saga", type = ItemType.MANGA), Volume(seriesId = 0, number = 1, status = ReadingStatus.READ))
        val fresh = trophies.check(repository.library.first(), 0)
        assertTrue("app.first_read" in fresh)

        // Aunque se borre la serie, los trofeos siguen y no se vuelven a anunciar.
        repository.deleteSeries(repository.getSeries(seriesId)!!)
        assertEquals(emptyList<String>(), trophies.check(repository.library.first(), 0))
        assertTrue(db.dao().allTrophies().any { it.id == "app.first_read" })
    }
}
