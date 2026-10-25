package com.shelfio.app.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shelfio.app.data.filterLibrary
import com.shelfio.app.data.model.ItemType
import com.shelfio.app.data.model.SeriesWithVolumes
import com.shelfio.app.data.volumesAtLocation
import com.shelfio.app.ui.components.EmptyState
import com.shelfio.app.ui.components.SearchField
import com.shelfio.app.ui.components.SectionHeader
import com.shelfio.app.ui.components.SeriesRow
import com.shelfio.app.ui.components.VolumeRow

/** Biblioteca: búsqueda, filtros por tipo y lista de series. La búsqueda y el filtro los guarda quien la llama. */
@Composable
fun LibraryScreen(
    items: List<SeriesWithVolumes>,
    query: String,
    onQueryChange: (String) -> Unit,
    typeFilter: ItemType?,
    onTypeFilterChange: (ItemType?) -> Unit,
    onOpenSeries: (Long) -> Unit,
    onAdd: () -> Unit,
) {
    val results = filterLibrary(items, query, typeFilter)
    val byLocation = volumesAtLocation(items.filter { typeFilter == null || it.series.type == typeFilter }, query)
    val singleBookIds = items.filter { it.isSingleBook }.map { it.series.id }.toSet()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Biblioteca", style = MaterialTheme.typography.headlineMedium) }
        item { SearchField(value = query, onValueChange = onQueryChange) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = typeFilter == null, onClick = { onTypeFilterChange(null) }, label = { Text("Todo") })
                ItemType.entries.forEach { type ->
                    FilterChip(selected = typeFilter == type, onClick = { onTypeFilterChange(type) }, label = { Text(type.pluralLabel) })
                }
            }
        }

        when {
            items.isEmpty() -> item {
                EmptyState(
                    icon = Icons.Filled.CollectionsBookmark,
                    title = "Aún no hay nada en tu biblioteca",
                    message = "Añade tu primer libro, manga o cómic a mano o escaneando su ISBN.",
                    action = { Button(onClick = onAdd) { Text("Añadir") } },
                )
            }
            results.isEmpty() && byLocation.isEmpty() -> item {
                EmptyState(
                    icon = Icons.Filled.SearchOff,
                    title = "Sin resultados",
                    message = "Prueba con otra palabra o cambia el filtro.",
                )
            }
            else -> {
                if (byLocation.isNotEmpty()) {
                    item { SectionHeader("Guardado en «${query.trim()}»") }
                    items(byLocation, key = { "l" + it.volume.id }) {
                        VolumeRow(it, singleBook = it.series.id in singleBookIds, showLocation = true, onClick = { onOpenSeries(it.series.id) })
                    }
                    if (results.isNotEmpty()) item { SectionHeader("Series") }
                }
                items(results, key = { it.series.id }) { SeriesRow(it, onClick = { onOpenSeries(it.series.id) }) }
            }
        }
    }
}
