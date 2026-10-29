package com.shelfio.app.ui.wishlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shelfio.app.ui.components.cardStyle
import com.shelfio.app.data.WishlistGroup
import com.shelfio.app.data.WishlistSort
import com.shelfio.app.data.model.Volume
import com.shelfio.app.data.wishlist
import com.shelfio.app.ui.AppViewModelProvider
import com.shelfio.app.ui.components.EmptyState
import com.shelfio.app.ui.components.LoadingBox
import com.shelfio.app.ui.components.PriorityLabel
import com.shelfio.app.ui.components.ScreenWithBack
import com.shelfio.app.ui.components.SearchField
import com.shelfio.app.ui.components.TextInputDialog
import com.shelfio.app.ui.components.VolumeCover
import com.shelfio.app.ui.theme.TextSecondary

/** Lista de deseos: todos los tomos que no tengo, agrupados por serie. */
@Composable
fun WishlistScreen(
    onBack: () -> Unit,
    onOpenVolume: (seriesId: Long, volumeId: Long) -> Unit,
    viewModel: WishlistViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val library by viewModel.library.collectAsStateWithLifecycle()

    ScreenWithBack(title = "Lista de deseos", onBack = onBack) {
        val items = library ?: return@ScreenWithBack LoadingBox()
        val groups = wishlist(items, viewModel.query, viewModel.sort)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { SearchField(viewModel.query, { viewModel.query = it }, placeholder = "Buscar título, autor o nota") }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Ordenar:", color = TextSecondary, modifier = Modifier.align(Alignment.CenterVertically))
                    WishlistSort.entries.forEach { sort ->
                        FilterChip(selected = viewModel.sort == sort, onClick = { viewModel.sort = sort }, label = { Text(sort.label) })
                    }
                }
            }
            if (groups.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Filled.ShoppingCart,
                        title = if (viewModel.query.isBlank()) "Tu lista de deseos está vacía" else "Sin resultados",
                        message = "Marca un tomo como «no lo tengo» o, en la ficha de una serie, añade los tomos que te faltan.",
                    )
                }
            }
            items(groups, key = { it.series.id }) { group ->
                WishlistGroupCard(
                    group = group,
                    onOpenVolume = { onOpenVolume(group.series.id, it.id) },
                    onBought = { viewModel.buying = it },
                )
            }
        }
    }

    viewModel.buying?.let { volume ->
        TextInputDialog(
            title = "¡Comprado!",
            initialText = "",
            label = "¿Dónde lo vas a guardar? (opcional)",
            confirmLabel = "Guardar",
            allowEmpty = true,
            onConfirm = { viewModel.markBought(volume, it) },
            onDismiss = { viewModel.buying = null },
        )
    }
}

/** Tarjeta de una serie en la lista de deseos con sus tomos pendientes y el botón «Comprado». */
@Composable
private fun WishlistGroupCard(group: WishlistGroup, onOpenVolume: (Volume) -> Unit, onBought: (Volume) -> Unit) {
    Column(
        Modifier.cardStyle(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(group.series.title, style = MaterialTheme.typography.titleMedium)
        group.volumes.forEach { volume ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onOpenVolume(volume) },
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VolumeCover(group.series, volume, Modifier.width(44.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${group.series.type.unitTitle} ${volume.number}", style = MaterialTheme.typography.titleSmall)
                    volume.purchasePriority?.let { PriorityLabel(it) }
                    if (volume.purchaseNote.isNotBlank()) {
                        Text(volume.purchaseNote, color = TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                FilledTonalButton(onClick = { onBought(volume) }) { Text("Lo he comprado") }
            }
        }
    }
}
