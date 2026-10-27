package com.shelfio.app.ui.components

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.shelfio.app.data.model.ItemType
import com.shelfio.app.data.model.Series
import com.shelfio.app.data.model.SeriesWithVolumes
import com.shelfio.app.data.model.Volume
import java.io.File

// Reglas de portada (ver plan, «Portadas»): se usa la primera que exista.
//   Serie: portada de la serie → portada generada (la foto de un tomo no pasa a la serie).
//   Tomo:  portada propia del tomo → portada generada (en todos los tipos).
//   Para dar la portada de la serie a todos los tomos, la ficha pregunta y les pone una copia a cada uno.

/** Portada de una serie en tarjetas y en la ficha. */
@Composable
fun SeriesCover(item: SeriesWithVolumes, modifier: Modifier = Modifier) {
    CoverImage(path = item.series.coverPath, modifier = modifier) {
        GeneratedCover(item.series.title, item.series.type, modifier, volumeNumber = item.sortedVolumes.firstOrNull()?.number)
    }
}

/** Portada de un tomo concreto. */
@Composable
fun VolumeCover(series: Series, volume: Volume, modifier: Modifier = Modifier) {
    CoverImage(path = volume.coverPath, modifier = modifier) {
        GeneratedCover(series.title, series.type, modifier, volumeNumber = volume.number)
    }
}

/** Muestra la imagen guardada en [path] o, si no hay, la portada generada que se pasa en [generated]. */
@Composable
private fun CoverImage(path: String?, modifier: Modifier, generated: @Composable () -> Unit) {
    if (path == null || !File(path).exists()) {
        generated()
        return
    }
    AsyncImage(
        model = File(path),
        contentDescription = "Portada",
        contentScale = ContentScale.Crop,
        modifier = modifier.aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)),
    )
}

/** Portada de un resultado de búsqueda (desde internet); si no trae, la generada. */
@Composable
fun RemoteCover(url: String?, title: String, type: ItemType, modifier: Modifier = Modifier) {
    if (url == null) {
        GeneratedCover(title, type, modifier)
        return
    }
    AsyncImage(
        model = url,
        contentDescription = "Portada",
        contentScale = ContentScale.Crop,
        modifier = modifier.aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)),
    )
}
