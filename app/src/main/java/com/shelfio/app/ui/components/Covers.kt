package com.shelfio.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.shelfio.app.data.model.Series
import com.shelfio.app.data.model.SeriesWithVolumes
import com.shelfio.app.data.model.Volume

/** Portada de una serie en tarjetas. Por ahora siempre la generada. */
@Composable
fun SeriesCover(item: SeriesWithVolumes, modifier: Modifier = Modifier) {
    GeneratedCover(
        title = item.series.title,
        type = item.series.type,
        volumeNumber = item.sortedVolumes.firstOrNull()?.number,
        modifier = modifier,
    )
}

/** Portada de un tomo concreto. Por ahora siempre la generada. */
@Composable
fun VolumeCover(series: Series, volume: Volume, modifier: Modifier = Modifier) {
    GeneratedCover(title = series.title, type = series.type, volumeNumber = volume.number, modifier = modifier)
}
