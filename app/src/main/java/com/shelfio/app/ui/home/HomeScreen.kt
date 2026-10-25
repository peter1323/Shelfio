package com.shelfio.app.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.shelfio.app.R
import com.shelfio.app.data.countByType
import com.shelfio.app.data.model.ItemType
import com.shelfio.app.data.model.SeriesWithVolumes
import com.shelfio.app.data.pendingPurchase
import com.shelfio.app.data.readingNow
import com.shelfio.app.data.recentlyAdded
import com.shelfio.app.ui.components.CounterCard
import com.shelfio.app.ui.components.EmptyState
import com.shelfio.app.ui.components.SearchField
import com.shelfio.app.ui.components.SectionHeader
import com.shelfio.app.ui.components.SeriesTile
import com.shelfio.app.ui.components.VolumeRow
import com.shelfio.app.ui.theme.ShelfioBlue
import com.shelfio.app.ui.theme.ShelfioCyan
import com.shelfio.app.ui.theme.ShelfioViolet
import com.shelfio.app.ui.theme.TextSecondary

@Composable
fun HomeScreen(
    items: List<SeriesWithVolumes>,
    collectionsCount: Int,
    userName: String,
    onSearch: (String) -> Unit,
    onOpenSeries: (Long) -> Unit,
    onOpenLibrary: (ItemType?) -> Unit,
    onOpenCollections: () -> Unit,
    onAdd: () -> Unit,
    onScan: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val singleBookIds = items.filter { it.isSingleBook }.map { it.series.id }.toSet()
    val reading = readingNow(items)
    val pending = pendingPurchase(items)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(48.dp))
                Text("Shelfio", style = MaterialTheme.typography.titleLarge)
            }
        }
        item {
            Text(if (userName.isBlank()) "¡Hola!" else "¡Hola, $userName!", style = MaterialTheme.typography.headlineMedium)
            Text("Aquí tienes un resumen de tu colección.", color = TextSecondary)
        }
        item {
            SearchField(
                value = query,
                onValueChange = { query = it },
                onSearch = { if (query.isNotBlank()) onSearch(query) },
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CounterCard(Icons.AutoMirrored.Filled.MenuBook, "Libros", countByType(items, ItemType.BOOK), ShelfioBlue, Modifier.weight(1f)) { onOpenLibrary(ItemType.BOOK) }
                CounterCard(Icons.Filled.AutoStories, "Mangas", countByType(items, ItemType.MANGA), ShelfioViolet, Modifier.weight(1f)) { onOpenLibrary(ItemType.MANGA) }
                CounterCard(Icons.Filled.ChatBubble, "Cómics", countByType(items, ItemType.COMIC), ShelfioCyan, Modifier.weight(1f)) { onOpenLibrary(ItemType.COMIC) }
                CounterCard(Icons.Filled.CollectionsBookmark, "Colecciones", collectionsCount, ShelfioViolet, Modifier.weight(1f), onOpenCollections)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAdd, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("Añadir", Modifier.padding(start = 6.dp))
                }
                FilledTonalButton(onClick = onScan, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
                    Text("Escanear ISBN", Modifier.padding(start = 6.dp))
                }
            }
        }

        if (items.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Filled.CollectionsBookmark,
                    title = "Tu biblioteca está vacía",
                    message = "Pulsa «Añadir» para meter tu primer libro, manga o cómic, o escanea su código de barras.",
                )
            }
            return@LazyColumn
        }

        item { SectionHeader("Últimos añadidos", actionLabel = "Ver todo", onAction = { onOpenLibrary(null) }) }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(recentlyAdded(items), key = { it.series.id }) { SeriesTile(it, onClick = { onOpenSeries(it.series.id) }) }
            }
        }

        item { SectionHeader("En lectura") }
        if (reading.isEmpty()) {
            item { HomeHint(Icons.Filled.Timelapse, "No estás leyendo nada ahora mismo. Marca un tomo como «Leyendo» y aparecerá aquí.") }
        } else {
            items(reading, key = { "r" + it.volume.id }) {
                VolumeRow(it, singleBook = it.series.id in singleBookIds, onClick = { onOpenSeries(it.series.id) })
            }
        }

        item { SectionHeader("Pendientes de compra") }
        if (pending.isEmpty()) {
            item { HomeHint(Icons.Filled.ShoppingCart, "Nada pendiente. Los tomos marcados como «no lo tengo» aparecen aquí.") }
        } else {
            items(pending, key = { "p" + it.volume.id }) {
                VolumeRow(it, singleBook = it.series.id in singleBookIds, onClick = { onOpenSeries(it.series.id) })
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun HomeHint(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
        Column(Modifier.padding(start = 10.dp)) { Text(text, color = TextSecondary, style = MaterialTheme.typography.bodyMedium) }
    }
}
