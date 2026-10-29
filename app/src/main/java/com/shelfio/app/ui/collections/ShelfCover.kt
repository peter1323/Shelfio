package com.shelfio.app.ui.collections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.shelfio.app.data.model.SeriesWithVolumes
import com.shelfio.app.ui.components.SeriesCover
import com.shelfio.app.ui.theme.ShelfioViolet
import java.io.File

/**
 * Foto de un estante a lo ancho: la elegida por el usuario o, si no hay, las portadas de sus primeras series
 * puestas en fila como en una balda. Si está vacío, el icono de estantes.
 */
@Composable
fun ShelfCover(coverPath: String?, members: List<SeriesWithVolumes>, modifier: Modifier = Modifier, height: Dp = 150.dp) {
    Box(
        modifier.fillMaxWidth().height(height).background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        when {
            coverPath != null && File(coverPath).exists() -> AsyncImage(
                model = File(coverPath),
                contentDescription = "Foto del estante",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            members.isNotEmpty() -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                members.take(4).forEach { SeriesCover(it, Modifier.height(height - 46.dp)) }
            }
            else -> Icon(Icons.Filled.CollectionsBookmark, contentDescription = null, tint = ShelfioViolet, modifier = Modifier.size(48.dp))
        }
    }
}
