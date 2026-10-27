package com.shelfio.app.ui.charts

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shelfio.app.data.ChartMetric
import com.shelfio.app.data.chartValue
import com.shelfio.app.data.model.ItemType
import com.shelfio.app.data.model.SeriesWithVolumes
import com.shelfio.app.data.readShare
import com.shelfio.app.ui.components.MainTitle
import com.shelfio.app.ui.components.typeColor
import com.shelfio.app.ui.theme.TextSecondary

/**
 * Gráficas: compara los tipos (libros, mangas, cómics y otros) en barras.
 * Se elige qué comparar (lo que tengo, leídos…) y qué tipos; con dos tipos se ve la diferencia
 * y con uno solo se compara consigo mismo (lo que tengo frente a lo que me falta).
 */
@Composable
fun ChartsScreen(items: List<SeriesWithVolumes>, onOpenProfile: () -> Unit) {
    var metric by rememberSaveable { mutableStateOf(ChartMetric.OWNED) }
    var types by rememberSaveable { mutableStateOf(ItemType.entries.toSet()) }
    val shown = ItemType.entries.filter { it in types }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MainTitle("Gráficas", onOpenProfile)

        ChartCard("¿Qué tipos comparo?") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ItemType.entries.forEach { type ->
                    FilterChip(
                        selected = type in types,
                        // Siempre queda al menos un tipo marcado.
                        onClick = { types = if (type in types) (types - type).ifEmpty { types } else types + type },
                        label = { Text(type.pluralLabel) },
                    )
                }
            }
        }

        val single = shown.singleOrNull()
        if (single != null) {
            // Un solo tipo: se compara consigo mismo (lo que tengo frente a lo que me falta, leídos…).
            ChartCard("${single.pluralLabel} por dentro") {
                Text("Tomos y volúmenes de ${single.pluralLabel.lowercase()}", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                val color = typeColor(single)
                val metrics = listOf(ChartMetric.OWNED, ChartMetric.WANTED, ChartMetric.READ, ChartMetric.READING)
                // Cada barra con un tono distinto del color del tipo para distinguirlas.
                val bars = metrics.mapIndexed { index, m -> Bar(m.label, chartValue(items, single, m), color.copy(alpha = 1f - index * 0.2f)) }
                BarChart(bars)
                Text(ownedVsWanted(bars[0].value, bars[1].value), style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            ChartCard("Comparar") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ChartMetric.entries.forEach { option ->
                        FilterChip(selected = metric == option, onClick = { metric = option }, label = { Text(option.label) })
                    }
                }
                Text(metric.description, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                val values = shown.map { it to chartValue(items, it, metric) }
                BarChart(values.map { (type, value) -> Bar(type.pluralLabel, value, typeColor(type)) })
                Text(comparison(values), style = MaterialTheme.typography.bodyMedium)
            }
        }

        ChartCard("¿De qué he leído más?") {
            Text("Leídos de los que tengo o he leído (no cuenta lo que solo quiero comprar).", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            val shares = shown.map { it to readShare(items, it) }
            shares.forEach { (type, share) ->
                ProgressBar(
                    label = type.pluralLabel,
                    detail = "${(share.fraction * 100).toInt()} % · ${share.read} de ${share.total}",
                    fraction = share.fraction,
                    color = typeColor(type),
                )
            }
            val best = shares.filter { it.second.total > 0 }.maxByOrNull { it.second.fraction }
            Text(
                when {
                    best == null -> "Todavía no hay nada leído."
                    shares.size == 1 -> "Has leído el ${(best.second.fraction * 100).toInt()} % de tus ${best.first.pluralLabel.lowercase()}."
                    else -> "Lo que más has leído: ${best.first.pluralLabel.lowercase()} (${(best.second.fraction * 100).toInt()} %)."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** Frase bajo las barras: con dos tipos, la diferencia; con más, cuál gana. */
private fun comparison(values: List<Pair<ItemType, Int>>): String {
    if (values.all { it.second == 0 }) return "Todavía no hay datos."
    val sorted = values.sortedByDescending { it.second }
    val (first, firstValue) = sorted.first()
    if (values.size == 2) {
        val (second, secondValue) = sorted[1]
        return if (firstValue == secondValue) "Empate: ${firstValue} de cada uno."
        else "${first.pluralLabel}: ${firstValue - secondValue} más que ${second.pluralLabel.lowercase()}."
    }
    return "Más: ${first.pluralLabel.lowercase()} ($firstValue)."
}

/** Con un solo tipo: cuánto tengo frente a lo que me falta. */
private fun ownedVsWanted(owned: Int, wanted: Int): String = when {
    owned + wanted == 0 -> "Todavía no hay datos."
    wanted == 0 -> "Tienes todo lo que has registrado ($owned)."
    else -> "Tienes $owned y te faltan $wanted: tienes el ${owned * 100 / (owned + wanted)} %."
}

/** Una barra: nombre, valor y color. */
private data class Bar(val label: String, val value: Int, val color: Color)

private val BAR_MAX_HEIGHT = 170.dp

/** Barras verticales; la más alta ocupa toda la altura. */
@Composable
private fun BarChart(bars: List<Bar>) {
    val max = bars.maxOfOrNull { it.value }?.coerceAtLeast(1) ?: 1
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        bars.forEach { (label, value, color) ->
            // La barra crece con una pequeña animación al cambiar lo que se compara.
            val fraction by animateFloatAsState(value.toFloat() / max, label = "barra")
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(value.toString(), style = MaterialTheme.typography.titleMedium)
                Box(
                    Modifier
                        .padding(vertical = 4.dp)
                        .fillMaxWidth(0.7f)
                        .height(BAR_MAX_HEIGHT * fraction.coerceAtLeast(0.02f))
                        .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                        .background(color),
                )
                Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
            }
        }
    }
}

/** Barra horizontal de porcentaje con su nombre y detalle. */
@Composable
private fun ProgressBar(label: String, detail: String, fraction: Float, color: Color) {
    val animated by animateFloatAsState(fraction, label = "porcentaje")
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Text(detail, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            Box(Modifier.fillMaxWidth(animated).fillMaxHeight().clip(RoundedCornerShape(7.dp)).background(color))
        }
    }
}

@Composable
private fun ChartCard(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}
