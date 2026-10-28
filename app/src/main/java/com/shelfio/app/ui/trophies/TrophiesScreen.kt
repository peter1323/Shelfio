package com.shelfio.app.ui.trophies

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.shelfio.app.data.APP_PLATINUM_ID
import com.shelfio.app.data.APP_TROPHIES
import com.shelfio.app.data.ReadCreditRules
import com.shelfio.app.data.TrophyTier
import com.shelfio.app.data.model.SeriesWithVolumes
import com.shelfio.app.data.model.UnlockedTrophy
import com.shelfio.app.data.parseSeriesTrophyId
import com.shelfio.app.data.platinumPhrase
import com.shelfio.app.data.trophyStats
import com.shelfio.app.ui.components.ScreenWithBack
import com.shelfio.app.ui.profile.formatDateTime
import com.shelfio.app.ui.theme.ShelfioCyan
import com.shelfio.app.ui.theme.TextSecondary

/**
 * Logros: el platino de Shelfio con su progreso, cuántos trofeos hay de cada categoría,
 * la lista de trofeos (con su avance) y los platinos de tus series.
 */
@Composable
fun TrophiesScreen(
    library: List<SeriesWithVolumes>,
    collections: Int,
    unlocked: List<UnlockedTrophy>,
    credited: Set<Long>,
    onBack: () -> Unit,
) = ScreenWithBack(title = "Logros", onBack = onBack) {
    val unlockedAt = unlocked.associate { it.id to it.unlockedAt }
    val seriesTiers = unlocked.mapNotNull { parseSeriesTrophyId(it.id)?.second }
    val stats = trophyStats(library, collections, credited, seriesTiers.count { it == TrophyTier.PLATINUM })
    val appDone = APP_TROPHIES.count { it.id in unlockedAt }
    // Platinos de series que siguen en la biblioteca, el más reciente primero.
    val platinums = unlocked.mapNotNull { trophy ->
        val (seriesId, tier) = parseSeriesTrophyId(trophy.id) ?: return@mapNotNull null
        if (tier != TrophyTier.PLATINUM) return@mapNotNull null
        library.firstOrNull { it.series.id == seriesId }?.let { it to trophy.unlockedAt }
    }.sortedByDescending { it.second }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    TrophyImage(TrophyTier.PLATINUM, 84.dp, locked = APP_PLATINUM_ID !in unlockedAt)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Platino de Shelfio", style = MaterialTheme.typography.titleMedium)
                        Text("$appDone de ${APP_TROPHIES.size} trofeos · ${appDone * 100 / APP_TROPHIES.size} %", color = TextSecondary)
                        Bar(appDone.toFloat() / APP_TROPHIES.size)
                    }
                }
            }
        }
        item {
            // Cuántos hay de cada categoría, sumando los de la app y los de las series.
            val allTiers = APP_TROPHIES.filter { it.id in unlockedAt }.map { it.tier } + seriesTiers +
                listOfNotNull(TrophyTier.PLATINUM.takeIf { APP_PLATINUM_ID in unlockedAt })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TrophyTier.entries.forEach { tier ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        TrophyImage(tier, 48.dp)
                        Text("${allTiers.count { it == tier }} ${tier.label.lowercase()}", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        item {
            Text(
                "Cada tomo leído cuenta una sola vez y, como mucho, ${ReadCreditRules.DAILY_LIMIT} al día. Un trofeo conseguido no se pierde aunque luego cambies algo.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        item { Text("Trofeos", style = MaterialTheme.typography.titleMedium) }
        items(APP_TROPHIES.sortedBy { it.tier }, key = { it.id }) { trophy ->
            val date = unlockedAt[trophy.id]
            val secret = trophy.hidden && date == null
            Card {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TrophyImage(trophy.tier, 48.dp, locked = date == null)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(if (secret) "Trofeo oculto" else trophy.name, style = MaterialTheme.typography.titleSmall)
                        Text(if (secret) "Sigue usando Shelfio para descubrirlo" else trophy.description, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        when {
                            date != null -> Text("Conseguido el ${formatDateTime(date)}", color = ShelfioCyan, style = MaterialTheme.typography.labelSmall)
                            !secret && trophy.goal > 1 -> {
                                val progress = trophy.progress(stats).coerceAtMost(trophy.goal)
                                Text("$progress de ${trophy.goal}", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                                Bar(progress.toFloat() / trophy.goal)
                            }
                        }
                    }
                }
            }
        }
        item { Text("Platinos de tus series", style = MaterialTheme.typography.titleMedium) }
        if (platinums.isEmpty()) {
            item {
                Text(
                    "Consigue el platino de una serie teniéndola completa, toda leída y finalizada. En la ficha de cada serie ves sus cuatro trofeos.",
                    color = TextSecondary,
                )
            }
        }
        items(platinums, key = { it.first.series.id }) { (item, date) ->
            Card {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TrophyImage(TrophyTier.PLATINUM, 48.dp)
                    Column(Modifier.weight(1f)) {
                        Text(item.series.title, style = MaterialTheme.typography.titleSmall)
                        Text(platinumPhrase(item.series.type), color = ShelfioCyan, fontStyle = FontStyle.Italic)
                        Text("${item.series.type.label} · ${formatDateTime(date)}", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(12.dp)) { content() }
}

/** Barra de progreso fina con el cian de la app. */
@Composable
private fun Bar(fraction: Float) {
    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(6.dp).clip(RoundedCornerShape(3.dp)).background(ShelfioCyan))
    }
}
