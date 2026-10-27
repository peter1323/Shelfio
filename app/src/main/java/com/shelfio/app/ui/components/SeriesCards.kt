package com.shelfio.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shelfio.app.data.model.SeriesWithVolumes
import com.shelfio.app.data.model.VolumeWithSeries
import com.shelfio.app.ui.theme.TextSecondary

/** Fila de la Biblioteca: portada, título, autor, tipo, estado y progreso. */
@Composable
fun SeriesRow(item: SeriesWithVolumes, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SeriesCover(item, Modifier.width(64.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.series.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (item.series.author.isNotBlank()) {
                Text(item.series.author, color = TextSecondary, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TypeLabel(item.series.type)
                StatusLabel(item.displayStatus)
            }
            if (!item.isSingleBook) {
                Text(
                    "${item.readCount}/${item.volumes.size} ${item.series.type.units} leídos",
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            ProgressBar(item.progress)
        }
    }
}

/** Tarjeta vertical de Inicio («Últimos añadidos»). */
@Composable
fun SeriesTile(item: SeriesWithVolumes, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.width(108.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SeriesCover(item, Modifier.fillMaxWidth())
        Text(item.series.title, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(item.series.type.label, color = typeColor(item.series.type), style = MaterialTheme.typography.labelSmall)
    }
}

/** Fila compacta de un tomo con su serie (En lectura, Pendientes de compra, búsqueda por ubicación). */
@Composable
fun VolumeRow(
    item: VolumeWithSeries,
    singleBook: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showLocation: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VolumeCover(item.series, item.volume, Modifier.width(44.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.series.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val detail = buildList {
                if (!singleBook) add("${item.series.type.unitTitle} ${item.volume.number}")
                if (item.volume.title.isNotBlank()) add(item.volume.title)
                add(item.series.type.label)
            }.joinToString(" · ")
            Text(detail, color = TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            item.volume.purchasePriority?.takeIf { !item.volume.owned }?.let { PriorityLabel(it) }
            if (showLocation && item.volume.location.isNotBlank()) {
                LocationLabel(item.volume.location)
            }
        }
    }
}
