package com.shelfio.app.ui.series

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shelfio.app.data.model.ItemType
import com.shelfio.app.data.model.Loan
import com.shelfio.app.data.model.PublicationStatus
import com.shelfio.app.data.model.ReadingStatus
import com.shelfio.app.data.model.SeriesWithVolumes
import com.shelfio.app.data.model.Volume
import com.shelfio.app.ui.AppViewModelProvider
import com.shelfio.app.ui.components.ConfirmDialog
import com.shelfio.app.ui.components.CoverPhotoButtons
import com.shelfio.app.ui.components.LoadingBox
import com.shelfio.app.ui.components.LocalShowMessage
import com.shelfio.app.ui.components.LoanLabel
import com.shelfio.app.ui.components.LocationLabel
import com.shelfio.app.ui.components.TextInputDialog
import com.shelfio.app.ui.components.ProgressBar
import com.shelfio.app.ui.components.ScreenWithBack
import com.shelfio.app.ui.components.SeriesCover
import com.shelfio.app.ui.components.StatusLabel
import com.shelfio.app.ui.components.TypeLabel
import com.shelfio.app.ui.components.VolumeCover
import com.shelfio.app.ui.theme.ShelfioBlue
import com.shelfio.app.ui.theme.ShelfioCyan
import com.shelfio.app.ui.theme.TextSecondary

/**
 * Ficha de una serie: portada, datos comunes, progreso y lista de tomos.
 * Si la serie tiene un único tomo se muestra como un libro normal, sin hablar de «tomos».
 */
@Composable
fun SeriesDetailScreen(
    onBack: () -> Unit,
    onEditSeries: (seriesId: Long) -> Unit,
    onOpenVolume: (seriesId: Long, volumeId: Long) -> Unit,
    onAddNextVolume: (seriesId: Long) -> Unit,
    viewModel: SeriesDetailViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val showMessage = LocalShowMessage.current
    var showCollections by rememberSaveable { mutableStateOf(false) }
    // null = diálogo cerrado; texto = número propuesto para «Añadir los tomos que me faltan».
    var askMissingUpTo by rememberSaveable { mutableStateOf<String?>(null) }
    // Tomo que se va a borrar tras mantenerlo pulsado (null = sin diálogo).
    var deleting by remember { mutableStateOf<Volume?>(null) }
    val item = state.item
    // «tomo» o «volumen» según el tipo (para los textos de los diálogos).
    val unitType = item?.series?.type ?: ItemType.MANGA

    // Si la serie desaparece (se ha borrado desde Editar), se vuelve atrás.
    LaunchedEffect(state.isLoading, item) { if (!state.isLoading && item == null) onBack() }

    ScreenWithBack(
        title = item?.series?.title.orEmpty(),
        onBack = onBack,
        actions = {
            IconButton(onClick = { showCollections = true }) { Icon(Icons.Filled.CollectionsBookmark, contentDescription = "Colecciones") }
            IconButton(onClick = { onEditSeries(viewModel.seriesId) }) { Icon(Icons.Filled.Edit, contentDescription = "Editar") }
        },
    ) {
        if (item == null) {
            LoadingBox()
            return@ScreenWithBack
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SeriesHeader(item) }
            item {
                CoverPhotoButtons(
                    hasRealCover = item.series.coverPath != null,
                    onPhotoTaken = { viewModel.onCoverPhotoTaken { ok -> if (!ok) showMessage("No se pudo guardar la foto.") } },
                    onRemoveCover = viewModel::removeCover,
                )
            }
            item.ownedOfPublished?.let { owned -> item { PublishedProgress(item, owned) } }
            item { SeriesInfo(item) }

            if (item.isSingleBook) {
                item.volumes.firstOrNull()?.let { volume ->
                    item { Text("Tu ejemplar", style = MaterialTheme.typography.titleMedium) }
                    item {
                        VolumeQuickControls(
                            volume = volume,
                            activeLoan = item.activeLoanOf(volume),
                            onStatus = { viewModel.setStatus(volume, it) },
                            onOwned = { viewModel.setOwned(volume, it) },
                        )
                    }
                    item {
                        OutlinedButton(onClick = { onOpenVolume(item.series.id, volume.id) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Editar, ubicación y préstamo")
                        }
                    }
                }
            } else {
                item { Text("${item.series.type.unitsTitle} (${item.volumes.size})", style = MaterialTheme.typography.titleMedium) }
                items(item.sortedVolumes, key = { it.id }) { volume ->
                    // Deslizar a la derecha edita, a la izquierda borra (con confirmación); mantener pulsado también borra.
                    SwipeActions(onEdit = { onOpenVolume(item.series.id, volume.id) }, onDelete = { deleting = volume }) {
                        VolumeListRow(
                            item = item,
                            volume = volume,
                            onClick = { onOpenVolume(item.series.id, volume.id) },
                            onLongClick = { deleting = volume },
                            onStatus = { viewModel.setStatus(volume, it) },
                            onOwned = { viewModel.setOwned(volume, it) },
                        )
                    }
                }
            }
            item {
                Button(onClick = { onAddNextVolume(item.series.id) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("Añadir siguiente ${item.series.type.unit}", Modifier.padding(start = 6.dp))
                }
            }
            item {
                OutlinedButton(onClick = { askMissingUpTo = item.series.publishedInMyLanguage?.toString().orEmpty() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Añadir los ${item.series.type.units} que me faltan")
                }
            }
        }
    }

    askMissingUpTo?.let { initial ->
        TextInputDialog(
            title = "Añadir los ${unitType.units} que me faltan",
            initialText = initial,
            label = "Hasta el ${unitType.unit} número",
            confirmLabel = "Añadir",
            numeric = true,
            onConfirm = { text ->
                askMissingUpTo = null
                text.toIntOrNull()?.let { upTo ->
                    viewModel.addMissingVolumes(upTo) { added ->
                        showMessage(if (added == 0) "No faltaba ninguno." else "Añadidos $added ${unitType.units} a tu lista de deseos.")
                    }
                }
            },
            onDismiss = { askMissingUpTo = null },
        )
    }

    deleting?.let { volume ->
        ConfirmDialog(
            title = "¿Borrar el ${unitType.unit} ${volume.number}?",
            message = "Se borrará solo este ${unitType.unit}. La serie y los demás se quedan.",
            confirmLabel = "Borrar",
            onConfirm = { deleting = null; viewModel.deleteVolume(volume) },
            onDismiss = { deleting = null },
        )
    }

    if (showCollections) {
        CollectionsDialog(
            state = state,
            onToggle = viewModel::setInCollection,
            onCreate = viewModel::createCollectionWithSeries,
            onDismiss = { showCollections = false },
        )
    }
}

@Composable
private fun SeriesHeader(item: SeriesWithVolumes) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        SeriesCover(item, Modifier.width(130.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(item.series.title, style = MaterialTheme.typography.headlineSmall)
            if (item.series.author.isNotBlank()) Text(item.series.author, color = TextSecondary)
            TypeLabel(item.series.type)
            StatusLabel(item.displayStatus)
            if (!item.isSingleBook) {
                Text("${item.readCount} de ${item.volumes.size} ${item.series.type.units} leídos", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                ProgressBar(item.progress)
            }
        }
    }
}

/** Datos comunes. Solo se enseñan los que tienen valor: los desconocidos quedan vacíos. */
@Composable
private fun SeriesInfo(item: SeriesWithVolumes) {
    val series = item.series
    val rows = listOf(
        "Editorial" to series.publisher,
        "Idioma" to series.language,
        "Estado en mi idioma" to series.statusInMyLanguage.takeIf { it != PublicationStatus.UNKNOWN }?.label.orEmpty(),
        "Estado en su país de origen" to series.statusInOrigin.takeIf { it != PublicationStatus.UNKNOWN }?.label.orEmpty(),
        "Descripción" to series.description,
        "Otros títulos" to series.otherTitles.lines().filter { it.isNotBlank() }.joinToString(" · "),
        "Notas" to series.notes,
    )
        .filter { it.second.isNotBlank() }
    if (rows.isEmpty()) return
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        rows.forEach { (label, value) ->
            Column {
                Text(label, color = TextSecondary, style = MaterialTheme.typography.labelMedium)
                Text(value, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** «Lo tengo» y estado de lectura con un toque (para el libro suelto). */
@Composable
private fun VolumeQuickControls(volume: Volume, activeLoan: Loan?, onStatus: (ReadingStatus) -> Unit, onOwned: (Boolean) -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (volume.owned) "Lo tengo" else "No lo tengo (pendiente de compra)", Modifier.weight(1f))
            Switch(checked = volume.owned, onCheckedChange = onOwned)
        }
        activeLoan?.let { LoanLabel(it) }
        if (volume.owned && volume.location.isNotBlank()) LocationLabel(volume.location)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(ReadingStatus.UNREAD, ReadingStatus.READING, ReadingStatus.READ).forEach { status ->
                FilterChip(selected = volume.status == status, onClick = { onStatus(status) }, label = { Text(status.label) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(ReadingStatus.PAUSED, ReadingStatus.ABANDONED).forEach { status ->
                FilterChip(selected = volume.status == status, onClick = { onStatus(status) }, label = { Text(status.label) })
            }
        }
    }
}

/** Fila de un tomo: portada, número, «lo tengo» y estado (con menú para cambiarlo con un toque). */
@Composable
private fun VolumeListRow(
    item: SeriesWithVolumes,
    volume: Volume,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onStatus: (ReadingStatus) -> Unit,
    onOwned: (Boolean) -> Unit,
) {
    var statusMenu by rememberSaveable { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            // Tocar abre el tomo; mantener pulsado pregunta si borrarlo.
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VolumeCover(item.series, volume, Modifier.width(48.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                listOf("${item.series.type.unitTitle} ${volume.number}", volume.title).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box {
                Box(Modifier.clip(RoundedCornerShape(8.dp)).clickable { statusMenu = true }.padding(vertical = 4.dp)) {
                    StatusLabel(volume.status)
                }
                DropdownMenu(expanded = statusMenu, onDismissRequest = { statusMenu = false }) {
                    ReadingStatus.entries.forEach { status ->
                        DropdownMenuItem(text = { Text(status.label) }, onClick = { onStatus(status); statusMenu = false })
                    }
                }
            }
            item.activeLoanOf(volume)?.let { LoanLabel(it) }
            if (volume.owned && volume.location.isNotBlank()) LocationLabel(volume.location)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Checkbox(checked = volume.owned, onCheckedChange = onOwned)
            Text(if (volume.owned) "Lo tengo" else "Pendiente", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
    }
}

/** Marcar en qué colecciones está la serie, o crear una nueva con ella dentro. */
@Composable
private fun CollectionsDialog(
    state: SeriesDetailState,
    onToggle: (collectionId: Long, inCollection: Boolean) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var creating by rememberSaveable { mutableStateOf(false) }
    if (creating) {
        TextInputDialog(
            title = "Nueva colección",
            initialText = "",
            confirmLabel = "Crear",
            onConfirm = { onCreate(it); creating = false },
            onDismiss = { creating = false },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Colecciones") },
        text = {
            Column {
                if (state.allCollections.isEmpty()) Text("Aún no tienes colecciones.", color = TextSecondary)
                state.allCollections.forEach { (collection) ->
                    val checked = collection.id in state.collectionIds
                    Row(
                        Modifier.fillMaxWidth().clickable { onToggle(collection.id, !checked) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = { onToggle(collection.id, it) })
                        Text(collection.name)
                    }
                }
                TextButton(onClick = { creating = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("Nueva colección", Modifier.padding(start = 6.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Hecho") } },
    )
}

/** «Tengo X de Y publicados» con barra, o «Colección completa» si está finalizada y la tengo entera. */
@Composable
private fun PublishedProgress(item: SeriesWithVolumes, owned: Int) {
    val published = item.series.publishedInMyLanguage ?: return
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (item.isCompleteCollection) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Verified, contentDescription = null, tint = ShelfioCyan)
                Text("Colección completa", style = MaterialTheme.typography.titleMedium, color = ShelfioCyan, modifier = Modifier.padding(start = 8.dp))
            }
        }
        Text("Tengo $owned de $published publicados")
        ProgressBar(if (published > 0) owned.toFloat() / published else 0f)
    }
}

/**
 * Fila que se puede deslizar: hacia la derecha muestra «Editar», hacia la izquierda «Borrar».
 * La fila vuelve siempre a su sitio; la acción la decide la pantalla (borrar pide confirmación).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeActions(onEdit: () -> Unit, onDelete: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> onEdit()
                SwipeToDismissBoxValue.EndToStart -> onDelete()
                SwipeToDismissBoxValue.Settled -> Unit
            }
            false
        },
    )
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            val editing = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            Row(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (editing) ShelfioBlue else MaterialTheme.colorScheme.error)
                    .padding(horizontal = 20.dp),
                horizontalArrangement = if (editing) Arrangement.Start else Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (editing) Icons.Filled.Edit else Icons.Filled.Delete, contentDescription = null, tint = Color.White)
                Text(if (editing) "Editar" else "Borrar", color = Color.White, modifier = Modifier.padding(horizontal = 8.dp))
            }
        },
    ) { content() }
}
