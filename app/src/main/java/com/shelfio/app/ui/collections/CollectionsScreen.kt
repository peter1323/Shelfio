package com.shelfio.app.ui.collections

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.shelfio.app.data.model.CollectionWithCount
import com.shelfio.app.data.model.SeriesCollectionCrossRef
import com.shelfio.app.data.model.SeriesWithVolumes
import com.shelfio.app.ui.components.EmptyState
import com.shelfio.app.ui.components.MainTitle
import com.shelfio.app.ui.components.TextInputDialog
import com.shelfio.app.ui.theme.TextSecondary

/** Estantes: una tarjeta por estante con su foto (o las portadas de sus series), nombre y cuántas series tiene. */
@Composable
fun CollectionsScreen(
    collections: List<CollectionWithCount>,
    library: List<SeriesWithVolumes>,
    memberships: List<SeriesCollectionCrossRef>,
    onCreate: (name: String) -> Unit,
    onOpen: (Long) -> Unit,
    onOpenProfile: () -> Unit,
) {
    var creating by rememberSaveable { mutableStateOf(false) }
    val seriesById = library.associateBy { it.series.id }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            MainTitle("Estantes", onOpenProfile) {
                IconButton(onClick = { creating = true }) { Icon(Icons.Filled.Add, contentDescription = "Nuevo estante") }
            }
            Text("Agrupa tus series como quieras, por ejemplo «Favoritos» o «Fantasía». Una serie puede estar en varios.", color = TextSecondary)
        }
        if (collections.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Filled.CollectionsBookmark,
                    title = "Sin estantes",
                    message = "Crea un estante y añade series desde dentro o desde su ficha.",
                    action = { Button(onClick = { creating = true }) { Text("Crear estante") } },
                )
            }
        }
        items(collections, key = { it.collection.id }) { summary ->
            // Las últimas series añadidas son las que salen en la foto si no hay una elegida.
            val members = memberships.filter { it.collectionId == summary.collection.id }
                .sortedByDescending { it.addedAt }
                .mapNotNull { seriesById[it.seriesId] }
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { onOpen(summary.collection.id) },
            ) {
                ShelfCover(summary.collection.coverPath, members)
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(summary.collection.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (summary.seriesCount == 1) "1 serie" else "${summary.seriesCount} series",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }

    if (creating) {
        TextInputDialog(
            title = "Nuevo estante",
            initialText = "",
            confirmLabel = "Crear",
            onConfirm = { onCreate(it); creating = false },
            onDismiss = { creating = false },
        )
    }
}
