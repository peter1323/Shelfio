package com.shelfio.app.data

import com.shelfio.app.data.db.ShelfioDao
import com.shelfio.app.data.model.ReadCredit
import com.shelfio.app.data.model.ReadingStatus
import com.shelfio.app.data.model.SeriesWithVolumes
import com.shelfio.app.data.model.UnlockedTrophy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId

/**
 * Guarda los logros. Cada vez que cambia la biblioteca se llama a [check], que:
 *  1. da por leídos para los trofeos los tomos marcados como «Leído» que aún no contaron
 *     (cada tomo una sola vez y como mucho [ReadCreditRules.DAILY_LIMIT] al día);
 *  2. guarda con fecha los trofeos nuevos. Los guardados no se borran nunca.
 */
class TrophyManager(private val dao: ShelfioDao, private val now: () -> Long = System::currentTimeMillis) {

    val unlocked: Flow<List<UnlockedTrophy>> = dao.observeTrophies()

    val credited: Flow<Set<Long>> = dao.observeReadCredits().map { list -> list.map { it.volumeId }.toSet() }

    /** Revisa la biblioteca y devuelve los ids de los trofeos recién conseguidos (para avisar). */
    suspend fun check(library: List<SeriesWithVolumes>, collections: Int): List<String> {
        val credits = dao.allReadCredits()
        val creditedIds = credits.map { it.volumeId }.toSet()
        val pending = library.flatMap { it.volumes }.filter { it.status == ReadingStatus.READ && it.id !in creditedIds }
        val time = now()
        val newCredits = if (credits.isEmpty()) {
            // Primera vez: lo que ya estaba leído cuenta entero, sin límite (es tu historial, no una trampa).
            pending.map { ReadCredit(it.id, creditedAt = 0) }
        } else {
            val usedToday = credits.count { it.creditedAt >= startOfDay(time) }
            pending.take((ReadCreditRules.DAILY_LIMIT - usedToday).coerceAtLeast(0)).map { ReadCredit(it.id, time) }
        }
        if (newCredits.isNotEmpty()) dao.insertReadCredits(newCredits)

        val stored = dao.allTrophies().map { it.id }.toSet()
        val fresh = achievedTrophies(library, collections, creditedIds + newCredits.map { it.volumeId }, stored) - stored
        if (fresh.isNotEmpty()) dao.insertTrophies(fresh.map { UnlockedTrophy(it, time) })
        return fresh.toList()
    }

    private fun startOfDay(time: Long): Long {
        val zone = ZoneId.systemDefault()
        return Instant.ofEpochMilli(time).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
    }
}
