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
import com.shelfio.app.ui.theme.ShelfioCyan
import com.shelfio.app.data.model.PurchasePriority
import com.shelfio.app.data.model.ItemType
import com.shelfio.app.data.withoutPriceLine
import com.shelfio.app.data.wishlistCost
import com.shelfio.app.data.volumePrice
import com.shelfio.app.data.seriesCost
import com.shelfio.app.data.missingLine
import com.shelfio.app.data.formatEuros
import com.shelfio.app.data.budgetLine
import com.shelfio.app.data.Cost
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

/**
 * Lista de deseos: todos los tomos que no tengo, agrupados por serie, con lo que cuesta completarla.
 * [monthlyBudgetCents] es el presupuesto al mes de Perfil (0 = sin presupuesto).
 */
@Composable
fun WishlistScreen(
    monthlyBudgetCents: Int,
    onBack: () -> Unit,
    onOpenVolume: (seriesId: Long, volumeId: Long) -> Unit,
    viewModel: WishlistViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val library by viewModel.library.collectAsStateWithLifecycle()

    ScreenWithBack(title = "Lista de deseos", onBack = onBack) {
        val items = library ?: return@ScreenWithBack LoadingBox()
        val groups = wishlist(items, viewModel.query, viewModel.sort, viewModel.type, viewModel.priority)
        // El total sale de lo que se ve: cambia con la búsqueda y los filtros.
        val total = wishlistCost(groups)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { SearchField(viewModel.query, { viewModel.query = it }, placeholder = "Buscar título, autor o nota") }
            if (total.volumes > 0) item { TotalCard(total, monthlyBudgetCents) }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Tipo:", color = TextSecondary, modifier = Modifier.align(Alignment.CenterVertically))
                    ItemType.entries.forEach { type ->
                        FilterChip(selected = viewModel.type == type, onClick = { viewModel.type = type.takeIf { viewModel.type != it } }, label = { Text(type.pluralLabel) })
                    }
                }
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Prioridad:", color = TextSecondary, modifier = Modifier.align(Alignment.CenterVertically))
                    PurchasePriority.entries.forEach { priority ->
                        FilterChip(selected = viewModel.priority == priority, onClick = { viewModel.priority = priority.takeIf { viewModel.priority != it } }, label = { Text(priority.label) })
                    }
                }
            }
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
                        title = if (viewModel.query.isBlank() && viewModel.type == null && viewModel.priority == null) "Tu lista de deseos está vacía" else "Sin resultados",
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
        // Subtotal de la serie: lo que cuesta lo que falta de ella.
        Text(missingLine(seriesCost(group.series, group.volumes), group.series.type.unit, group.series.type.units), color = ShelfioCyan, style = MaterialTheme.typography.labelLarge)
        group.volumes.forEach { volume ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onOpenVolume(volume) },
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VolumeCover(group.series, volume, Modifier.width(44.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${group.series.type.unitTitle} ${volume.number}", style = MaterialTheme.typography.titleSmall)
                    volumePrice(group.series, volume)?.let { Text(formatEuros(it), color = TextSecondary, style = MaterialTheme.typography.bodySmall) }
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

/** Total de la lista: tomos que faltan y su coste, aviso de series sin precio y meses con el presupuesto. */
@Composable
private fun TotalCard(total: Cost, monthlyBudgetCents: Int) {
    Column(Modifier.cardStyle(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(missingLine(total), style = MaterialTheme.typography.titleMedium)
        withoutPriceLine(total)?.let {
            Text("$it. Pon su precio en Editar para que cuente en el total.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        budgetLine(total.cents, monthlyBudgetCents)?.let { Text(it, color = ShelfioCyan, style = MaterialTheme.typography.bodyMedium) }
    }
}
