package com.shelfio.app.ui.collections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import com.shelfio.app.data.SPINES_PER_SHELF
import com.shelfio.app.data.shelfSpines
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shelfio.app.data.ShelfFilters
import com.shelfio.app.data.ShelfSort
import com.shelfio.app.data.TrophyTier
import com.shelfio.app.data.matchesSearch
import com.shelfio.app.data.model.ItemType
import com.shelfio.app.data.parseSeriesTrophyId
import com.shelfio.app.data.shelfItems
import com.shelfio.app.ui.AppViewModelProvider
import com.shelfio.app.ui.components.ConfirmDialog
import com.shelfio.app.ui.components.CoverPhotoButtons
import com.shelfio.app.ui.components.EmptyState
import com.shelfio.app.ui.components.LoadingBox
import com.shelfio.app.ui.components.LocalShowMessage
import com.shelfio.app.ui.components.ScreenWithBack
import com.shelfio.app.ui.components.SearchField
import com.shelfio.app.ui.components.SeriesRow
import com.shelfio.app.ui.components.TextInputDialog
import com.shelfio.app.ui.theme.TextSecondary
import com.shelfio.app.ui.trophies.LocalUnlockedTrophies

/**
 * Dentro de un estante: foto arriba, buscador con filtros, orden y sus series.
 * «Añadir» mete series de la biblioteca o abre el buscador para una nueva ([onAddNew]) sin salir del estante.
 */
@Composable
fun CollectionDetailScreen(
    onBack: () -> Unit,
    onOpenSeries: (Long) -> Unit,
    onAddNew: (shelfId: Long) -> Unit,
    viewModel: CollectionDetailViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val showMessage = LocalShowMessage.current
    var renaming by rememberSaveable { mutableStateOf(false) }
    var askDelete by rememberSaveable { mutableStateOf(false) }
    var editingCover by rememberSaveable { mutableStateOf(false) }
    var choosingSeriesCover by rememberSaveable { mutableStateOf(false) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var addMenu by remember { mutableStateOf(false) }
    var pickingFromLibrary by rememberSaveable { mutableStateOf(false) }
    val collection = state.collection

    // Al borrarlo deja de existir: volver a la lista.
    LaunchedEffect(state.isLoading, collection) { if (!state.isLoading && collection == null) onBack() }

    ScreenWithBack(
        title = collection?.name.orEmpty(),
        onBack = onBack,
        actions = {
            IconButton(onClick = { editingCover = true }) { Icon(Icons.Filled.Image, contentDescription = "Foto del estante") }
            IconButton(onClick = { renaming = true }) { Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = "Renombrar") }
            IconButton(onClick = { askDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Borrar estante") }
        },
    ) {
        if (collection == null) {
            LoadingBox()
            return@ScreenWithBack
        }
        val platinumSeries = LocalUnlockedTrophies.current.mapNotNull { id ->
            parseSeriesTrophyId(id)?.takeIf { it.second == TrophyTier.PLATINUM }?.first
        }.toSet()
        val shown = shelfItems(state.series, viewModel.query, viewModel.sort, viewModel.filters, state.addedAt, platinumSeries)

        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.fillMaxSize(),
                // Abajo deja sitio al botón «Añadir».
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    ShelfCover(
                        collection.coverPath,
                        state.series.sortedByDescending { state.addedAt[it.series.id] ?: 0L },
                        Modifier.clip(RoundedCornerShape(16.dp)).clickable { editingCover = true },
                    )
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SearchField(viewModel.query, { viewModel.query = it }, Modifier.weight(1f), placeholder = "Buscar en el estante")
                        IconButton(onClick = { showFilters = true }) {
                            BadgedBox(badge = { if (viewModel.filters.activeCount > 0) Badge { Text("${viewModel.filters.activeCount}") } }) {
                                Icon(Icons.Filled.FilterList, contentDescription = "Filtros")
                            }
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        SortChip(viewModel.sort) { viewModel.sort = it }
                        if (viewModel.filters.activeCount > 0) {
                            TextButton(onClick = { viewModel.filters = ShelfFilters() }) { Text("Quitar filtros") }
                        }
                    }
                }
                item { ViewToggle(viewModel.bookcase, viewModel::updateBookcase) }
                when {
                    state.series.isEmpty() -> item {
                        EmptyState(
                            icon = Icons.Filled.CollectionsBookmark,
                            title = "Estante vacío",
                            message = "Pulsa «Añadir» para meter series de tu biblioteca o una nueva.",
                        )
                    }
                    shown.isEmpty() -> item {
                        EmptyState(
                            icon = Icons.Filled.FilterList,
                            title = "Nada con estos filtros",
                            message = "Prueba con otra búsqueda o quita los filtros.",
                            action = { Button(onClick = { viewModel.filters = ShelfFilters(); viewModel.query = "" }) { Text("Quitar filtros") } },
                        )
                    }
                }
                if (viewModel.bookcase) {
                    // Un lomo por tomo que tengo, en baldas de SPINES_PER_SHELF.
                    items(shelfSpines(shown, platinumSeries).chunked(SPINES_PER_SHELF)) { shelf ->
                        BookcaseShelf(shelf, onOpenSeries)
                    }
                } else {
                    items(shown, key = { it.series.id }) { item ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SeriesRow(item, onClick = { onOpenSeries(item.series.id) }, modifier = Modifier.weight(1f))
                            IconButton(onClick = { viewModel.setInShelf(item.series.id, false) }) {
                                Icon(Icons.Filled.RemoveCircleOutline, contentDescription = "Quitar del estante")
                            }
                        }
                    }
                }
            }
            Box(Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
                ExtendedFloatingActionButton(
                    onClick = { addMenu = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Añadir") },
                )
                DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                    DropdownMenuItem(text = { Text("De mi biblioteca") }, onClick = { addMenu = false; pickingFromLibrary = true })
                    DropdownMenuItem(text = { Text("Nueva (buscar y guardar)") }, onClick = { addMenu = false; onAddNew(collection.id) })
                }
            }
        }
    }

    if (editingCover && collection != null) {
        val withCovers = state.series.filter { it.series.coverPath != null }
        AlertDialog(
            onDismissRequest = { editingCover = false },
            title = { Text("Foto del estante") },
            text = {
                CoverPhotoButtons(
                    hasRealCover = collection.coverPath != null,
                    onImage = { uri ->
                        editingCover = false
                        viewModel.onCoverImage(uri) { ok -> if (!ok) showMessage("No se pudo guardar la imagen.") }
                    },
                    onRemoveCover = { editingCover = false; viewModel.removeCover() },
                    onUseSeriesCover = if (withCovers.isEmpty()) null else ({ editingCover = false; choosingSeriesCover = true }),
                    seriesCoverLabel = "Portada de una serie",
                )
            },
            confirmButton = { TextButton(onClick = { editingCover = false }) { Text("Cerrar") } },
        )
    }
    if (choosingSeriesCover) {
        AlertDialog(
            onDismissRequest = { choosingSeriesCover = false },
            title = { Text("Elige la portada") },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.series.filter { it.series.coverPath != null }, key = { it.series.id }) { item ->
                        SeriesRow(item, onClick = { choosingSeriesCover = false; viewModel.useSeriesCover(item) })
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosingSeriesCover = false }) { Text("Cancelar") } },
        )
    }
    if (pickingFromLibrary) {
        LibraryPicker(
            state = state,
            onToggle = { seriesId, inShelf -> viewModel.setInShelf(seriesId, inShelf) },
            onDismiss = { pickingFromLibrary = false },
        )
    }
    if (showFilters) {
        FiltersSheet(viewModel.filters, onChange = { viewModel.filters = it }, onDismiss = { showFilters = false })
    }
    if (renaming) {
        TextInputDialog(
            title = "Renombrar estante",
            initialText = collection?.name.orEmpty(),
            confirmLabel = "Guardar",
            onConfirm = { viewModel.rename(it); renaming = false },
            onDismiss = { renaming = false },
        )
    }
    if (askDelete) {
        ConfirmDialog(
            title = "¿Borrar «${collection?.name}»?",
            message = "Solo se borra el estante. Las series siguen en tu biblioteca.",
            confirmLabel = "Borrar",
            onConfirm = { askDelete = false; viewModel.delete() },
            onDismiss = { askDelete = false },
        )
    }
}

/** Interruptor Lista / Estantería. */
@Composable
private fun ViewToggle(bookcase: Boolean, onChange: (Boolean) -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        SingleChoiceSegmentedButtonRow {
            listOf(false to "Lista", true to "Estantería").forEachIndexed { index, (value, label) ->
                SegmentedButton(
                    selected = bookcase == value,
                    onClick = { onChange(value) },
                    shape = SegmentedButtonDefaults.itemShape(index, 2),
                    icon = { Icon(if (value) Icons.Filled.LibraryBooks else Icons.AutoMirrored.Filled.ViewList, contentDescription = null) },
                    label = { Text(label) },
                )
            }
        }
    }
}

/** Chip «Ordenar» con su menú (título, autor y fecha de añadido, en los dos sentidos). */
@Composable
private fun SortChip(sort: ShelfSort, onChange: (ShelfSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        AssistChip(
            onClick = { open = true },
            label = { Text(sort.label) },
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Ordenar") },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ShelfSort.entries.forEach { option ->
                DropdownMenuItem(text = { Text(option.label) }, onClick = { onChange(option); open = false })
            }
        }
    }
}

/** Panel de filtros. Cada pareja (leídos / no leídos…) se excluye: tocar la marcada la quita. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FiltersSheet(filters: ShelfFilters, onChange: (ShelfFilters) -> Unit, onDismiss: () -> Unit) {
    // Abierto del todo, para que se vean todos los filtros y el botón «Ver resultados».
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Filtros", style = MaterialTheme.typography.titleLarge)
            FilterSection("Tipo") {
                ItemType.entries.forEach { type ->
                    FilterChip(
                        selected = filters.type == type,
                        onClick = { onChange(filters.copy(type = if (filters.type == type) null else type)) },
                        label = { Text(type.pluralLabel) },
                    )
                }
            }
            FilterSection("Lectura") {
                PairChips("Leídos", "No leídos", filters.read) { onChange(filters.copy(read = it)) }
            }
            FilterSection("Préstamos") {
                PairChips("Prestados", "No prestados", filters.lent) { onChange(filters.copy(lent = it)) }
            }
            FilterSection("Colección") {
                PairChips("Completas", "Me faltan", filters.complete) { onChange(filters.copy(complete = it)) }
            }
            FilterSection("Logros") {
                FilterChip(
                    selected = filters.platinumOnly,
                    onClick = { onChange(filters.copy(platinumOnly = !filters.platinumOnly)) },
                    label = { Text("Con platino") },
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { onChange(ShelfFilters()) }) { Text("Quitar filtros") }
                Button(onClick = onDismiss) { Text("Ver resultados") }
            }
        }
    }
}

@Composable
private fun FilterSection(title: String, chips: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = TextSecondary, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { chips() }
    }
}

/** Dos chips contrarios sobre un mismo valor: true, false o null (ninguno). */
@Composable
private fun PairChips(yes: String, no: String, value: Boolean?, onChange: (Boolean?) -> Unit) {
    FilterChip(selected = value == true, onClick = { onChange(if (value == true) null else true) }, label = { Text(yes) })
    FilterChip(selected = value == false, onClick = { onChange(if (value == false) null else false) }, label = { Text(no) })
}

/** «Añadir de mi biblioteca»: toda la biblioteca con buscador; marcar mete la serie en el estante, desmarcar la saca. */
@Composable
private fun LibraryPicker(state: CollectionDetailState, onToggle: (Long, Boolean) -> Unit, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val inShelf = state.series.map { it.series.id }.toSet()
    val results = state.library.filter { matchesSearch(it, query) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Añadir de mi biblioteca") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchField(query, { query = it }, placeholder = "Buscar título o autor")
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(results, key = { it.series.id }) { item ->
                        val checked = item.series.id in inShelf
                        Row(
                            Modifier.fillMaxWidth().clickable { onToggle(item.series.id, !checked) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = { onToggle(item.series.id, it) })
                            Column {
                                Text(item.series.title, style = MaterialTheme.typography.bodyLarge)
                                Text(item.series.type.label, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Hecho") } },
    )
}
