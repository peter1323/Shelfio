package com.shelfio.app.ui.collections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shelfio.app.ui.AppViewModelProvider
import com.shelfio.app.ui.components.ConfirmDialog
import com.shelfio.app.ui.components.EmptyState
import com.shelfio.app.ui.components.LoadingBox
import com.shelfio.app.ui.components.TextInputDialog
import com.shelfio.app.ui.components.ScreenWithBack
import com.shelfio.app.ui.components.SeriesRow

@Composable
fun CollectionDetailScreen(
    onBack: () -> Unit,
    onOpenSeries: (Long) -> Unit,
    viewModel: CollectionDetailViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var renaming by rememberSaveable { mutableStateOf(false) }
    var askDelete by rememberSaveable { mutableStateOf(false) }
    val collection = state.collection

    // Al borrarla deja de existir: volver a la lista.
    LaunchedEffect(state.isLoading, collection) { if (!state.isLoading && collection == null) onBack() }

    ScreenWithBack(
        title = collection?.name.orEmpty(),
        onBack = onBack,
        actions = {
            IconButton(onClick = { renaming = true }) { Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = "Renombrar") }
            IconButton(onClick = { askDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Borrar colección") }
        },
    ) {
        if (collection == null) {
            LoadingBox()
            return@ScreenWithBack
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.series.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Filled.CollectionsBookmark,
                        title = "Colección vacía",
                        message = "Abre una serie y pulsa el icono de colecciones para añadirla aquí.",
                    )
                }
            }
            items(state.series, key = { it.series.id }) { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SeriesRow(item, onClick = { onOpenSeries(item.series.id) }, modifier = Modifier.weight(1f))
                    IconButton(onClick = { viewModel.removeSeries(item.series.id) }) {
                        Icon(Icons.Filled.RemoveCircleOutline, contentDescription = "Quitar de la colección")
                    }
                }
            }
        }
    }

    if (renaming) {
        TextInputDialog(
            title = "Renombrar colección",
            initialText = collection?.name.orEmpty(),
            confirmLabel = "Guardar",
            onConfirm = { viewModel.rename(it); renaming = false },
            onDismiss = { renaming = false },
        )
    }
    if (askDelete) {
        ConfirmDialog(
            title = "¿Borrar «${collection?.name}»?",
            message = "Solo se borra la agrupación. Las series siguen en tu biblioteca.",
            confirmLabel = "Borrar",
            onConfirm = { askDelete = false; viewModel.delete() },
            onDismiss = { askDelete = false },
        )
    }
}
