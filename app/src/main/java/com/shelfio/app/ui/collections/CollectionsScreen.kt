package com.shelfio.app.ui.collections

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.shelfio.app.data.model.CollectionWithCount
import com.shelfio.app.ui.components.MainTitle
import com.shelfio.app.ui.components.EmptyState
import com.shelfio.app.ui.components.TextInputDialog
import com.shelfio.app.ui.theme.ShelfioViolet
import com.shelfio.app.ui.theme.TextSecondary

@Composable
fun CollectionsScreen(
    collections: List<CollectionWithCount>,
    onCreate: (name: String) -> Unit,
    onOpen: (Long) -> Unit,
    onOpenProfile: () -> Unit,
) {
    var creating by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            MainTitle("Colecciones", onOpenProfile) {
                IconButton(onClick = { creating = true }) { Icon(Icons.Filled.Add, contentDescription = "Nueva colección") }
            }
            Text("Agrupaciones tuyas, como «Favoritos» o «Fantasía». Una serie puede estar en varias.", color = TextSecondary)
        }
        if (collections.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Filled.CollectionsBookmark,
                    title = "Sin colecciones",
                    message = "Crea una colección y añade series desde su ficha.",
                    action = { Button(onClick = { creating = true }) { Text("Crear colección") } },
                )
            }
        }
        items(collections, key = { it.collection.id }) { summary ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { onOpen(summary.collection.id) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.CollectionsBookmark, contentDescription = null, tint = ShelfioViolet, modifier = Modifier.size(28.dp))
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(summary.collection.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (summary.seriesCount == 1) "1 serie" else "${summary.seriesCount} series",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }

    if (creating) {
        TextInputDialog(
            title = "Nueva colección",
            initialText = "",
            confirmLabel = "Crear",
            onConfirm = { onCreate(it); creating = false },
            onDismiss = { creating = false },
        )
    }
}
