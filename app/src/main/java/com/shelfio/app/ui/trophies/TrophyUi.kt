package com.shelfio.app.ui.trophies

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shelfio.app.R
import com.shelfio.app.data.APP_PLATINUM_ID
import com.shelfio.app.data.APP_TROPHIES
import com.shelfio.app.data.TrophyTier
import com.shelfio.app.data.isLooseBook
import com.shelfio.app.data.model.SeriesWithVolumes
import com.shelfio.app.data.parseSeriesTrophyId
import com.shelfio.app.data.platinumPhrase
import com.shelfio.app.data.seriesTrophyHowTo
import com.shelfio.app.data.seriesTrophyId
import com.shelfio.app.ui.theme.ShelfioCyan
import com.shelfio.app.ui.theme.TextSecondary


/** Estilo de las imágenes de trofeo elegido en Perfil (1 a 4). */
val LocalTrophyStyle = compositionLocalOf { 1 }

/** Ids de los trofeos conseguidos, para enseñarlos en fichas y tarjetas sin pasarlos pantalla a pantalla. */
val LocalUnlockedTrophies = compositionLocalOf { emptySet<String>() }

/** Las cuatro opciones de diseño (docs/diseno/trofeos), reducidas a 256 px en res/drawable-nodpi. */
@DrawableRes
fun trophyDrawable(style: Int, tier: TrophyTier): Int = when (style) {
    2 -> when (tier) { TrophyTier.BRONZE -> R.drawable.trofeo_2_bronce; TrophyTier.SILVER -> R.drawable.trofeo_2_plata; TrophyTier.GOLD -> R.drawable.trofeo_2_oro; TrophyTier.PLATINUM -> R.drawable.trofeo_2_platino }
    3 -> when (tier) { TrophyTier.BRONZE -> R.drawable.trofeo_3_bronce; TrophyTier.SILVER -> R.drawable.trofeo_3_plata; TrophyTier.GOLD -> R.drawable.trofeo_3_oro; TrophyTier.PLATINUM -> R.drawable.trofeo_3_platino }
    4 -> when (tier) { TrophyTier.BRONZE -> R.drawable.trofeo_4_bronce; TrophyTier.SILVER -> R.drawable.trofeo_4_plata; TrophyTier.GOLD -> R.drawable.trofeo_4_oro; TrophyTier.PLATINUM -> R.drawable.trofeo_4_platino }
    else -> when (tier) { TrophyTier.BRONZE -> R.drawable.trofeo_1_bronce; TrophyTier.SILVER -> R.drawable.trofeo_1_plata; TrophyTier.GOLD -> R.drawable.trofeo_1_oro; TrophyTier.PLATINUM -> R.drawable.trofeo_1_platino }
}

/** Imagen del trofeo en el estilo elegido. Los no conseguidos salen en gris y apagados. */
@Composable
fun TrophyImage(tier: TrophyTier, size: Dp, locked: Boolean = false, style: Int = LocalTrophyStyle.current) {
    Image(
        painter = painterResource(trophyDrawable(style, tier)),
        contentDescription = "Trofeo de ${tier.label.lowercase()}" + if (locked) " (sin conseguir)" else "",
        colorFilter = if (locked) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null,
        modifier = Modifier.size(size).alpha(if (locked) 0.35f else 1f),
    )
}

/** Nombre, detalle y categoría de un trofeo para enseñarlo. */
data class TrophyView(val name: String, val detail: String, val tier: TrophyTier)

/** Nombre y descripción de un trofeo a partir de su id (de la app o de una serie). null si la serie ya no existe. */
fun describeTrophy(id: String, library: List<SeriesWithVolumes>): TrophyView? {
    if (id == APP_PLATINUM_ID) return TrophyView("Platino de Shelfio", "Todos los trofeos conseguidos", TrophyTier.PLATINUM)
    APP_TROPHIES.firstOrNull { it.id == id }?.let { return TrophyView(it.name, it.description, it.tier) }
    val (seriesId, tier) = parseSeriesTrophyId(id) ?: return null
    val item = library.firstOrNull { it.series.id == seriesId } ?: return null
    val series = item.series
    val detail = when (tier) {
        TrophyTier.BRONZE -> if (item.isLooseBook) "La mitad leída" else "Primer ${series.type.unit} leído"
        TrophyTier.SILVER -> if (item.isLooseBook) "Leído entero" else "La mitad leída"
        TrophyTier.GOLD -> "Colección completa"
        TrophyTier.PLATINUM -> platinumPhrase(series.type)
    }
    return TrophyView(series.title, detail, tier)
}

/** Aviso «Trofeo desbloqueado»: el mejor trofeo en grande y, si hay varios, la lista. */
@Composable
fun TrophyUnlockedDialog(ids: List<String>, library: List<SeriesWithVolumes>, onDismiss: () -> Unit) {
    val trophies = ids.mapNotNull { describeTrophy(it, library) }.sortedByDescending { it.tier }
    // Trofeos de series ya borradas no tienen qué enseñar: se cierra sin más.
    if (trophies.isEmpty()) {
        SideEffect { onDismiss() }
        return
    }
    val best = trophies.first()
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("¡Genial!") } },
        title = {
            Text(
                if (trophies.size == 1) "¡Trofeo desbloqueado!" else "¡${trophies.size} trofeos desbloqueados!",
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TrophyImage(best.tier, 140.dp)
                Text(best.name, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                // En el platino de una serie la frase va como en las consolas: en cursiva y en cian.
                Text(
                    best.detail,
                    color = if (best.tier == TrophyTier.PLATINUM) ShelfioCyan else TextSecondary,
                    fontStyle = if (best.tier == TrophyTier.PLATINUM) FontStyle.Italic else FontStyle.Normal,
                    textAlign = TextAlign.Center,
                )
                trophies.drop(1).take(5).forEach { other ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        TrophyImage(other.tier, 28.dp)
                        Text("${other.name} · ${other.detail}", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (trophies.size > 6) Text("y ${trophies.size - 6} más en Logros", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        },
    )
}

/**
 * Los trofeos de una serie (ficha): conseguidos en color, el resto apagados.
 * Al tocar uno se explica cómo se consigue o por qué ya lo tienes.
 */
@Composable
fun SeriesTrophiesRow(item: SeriesWithVolumes) {
    val unlocked = LocalUnlockedTrophies.current
    val ids = TrophyTier.entries.associateWith { seriesTrophyId(item.series.id, it) }
    var shown by remember { mutableStateOf<TrophyTier?>(null) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        // Un libro suelto solo puede ganar bronce y plata; un oro o platino de antes se sigue viendo.
        ids.filter { (tier, id) -> !item.isLooseBook || tier <= TrophyTier.SILVER || id in unlocked }
            .forEach { (tier, id) ->
                Box(Modifier.clip(RoundedCornerShape(8.dp)).clickable { shown = tier }) { TrophyImage(tier, 40.dp, locked = id !in unlocked) }
            }
    }
    shown?.let { tier ->
        val got = ids.getValue(tier) in unlocked
        AlertDialog(
            onDismissRequest = { shown = null },
            confirmButton = { TextButton(onClick = { shown = null }) { Text("Cerrar") } },
            title = { Text("Trofeo de ${tier.label.lowercase()}", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            text = {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TrophyImage(tier, 110.dp, locked = !got)
                    Text(if (got) "Conseguido" else "Sin conseguir", color = if (got) ShelfioCyan else TextSecondary, style = MaterialTheme.typography.titleSmall)
                    Text((if (got) "Lo conseguiste al cumplir esto: " else "Cómo conseguirlo: ") + seriesTrophyHowTo(item, tier), textAlign = TextAlign.Center)
                    if (got && tier == TrophyTier.PLATINUM) Text(platinumPhrase(item.series.type), color = ShelfioCyan, fontStyle = FontStyle.Italic)
                    if (!got && tier != TrophyTier.GOLD) {
                        Text("Cada tomo leído cuenta una sola vez y, como mucho, 20 al día.", color = TextSecondary, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                    }
                }
            },
        )
    }
}

/** Platino de la serie en la esquina de su portada (solo si lo tiene). */
@Composable
fun PlatinumBadge(seriesId: Long, size: Dp = 26.dp) {
    if (seriesTrophyId(seriesId, TrophyTier.PLATINUM) in LocalUnlockedTrophies.current) TrophyImage(TrophyTier.PLATINUM, size)
}

/** Selector del estilo de los trofeos (Perfil): las 4 opciones con su platino; la elegida, recuadrada. */
@Composable
fun TrophyStyleSelector(selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        (1..4).forEach { style ->
            Column(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .border(2.dp, if (style == selected) ShelfioCyan else Color.Transparent, RoundedCornerShape(12.dp))
                    .clickable { onSelect(style) }
                    .padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TrophyImage(TrophyTier.PLATINUM, 56.dp, style = style)
                Text("Opción $style", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
