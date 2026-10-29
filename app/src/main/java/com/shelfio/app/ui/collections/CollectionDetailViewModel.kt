package com.shelfio.app.ui.collections

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shelfio.app.data.CoverStorage
import com.shelfio.app.data.ShelfFilters
import com.shelfio.app.data.ShelfSort
import com.shelfio.app.data.ShelfioRepository
import com.shelfio.app.data.model.SeriesWithVolumes
import com.shelfio.app.data.model.ShelfCollection
import com.shelfio.app.ui.navigation.Routes
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CollectionDetailState(
    val isLoading: Boolean = true,
    /** null cuando el estante ya no existe (se acaba de borrar). */
    val collection: ShelfCollection? = null,
    val series: List<SeriesWithVolumes> = emptyList(),
    /** Cuándo entró cada serie (id → fecha), para ordenar por «Último añadido». */
    val addedAt: Map<Long, Long> = emptyMap(),
    /** Toda la biblioteca, para «Añadir de mi biblioteca». */
    val library: List<SeriesWithVolumes> = emptyList(),
)

/** Dentro de un estante: sus series con búsqueda, orden y filtros; foto; añadir, quitar, renombrar y borrar. */
class CollectionDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: ShelfioRepository,
    private val covers: CoverStorage,
) : ViewModel() {

    val collectionId: Long = checkNotNull(savedStateHandle[Routes.ARG_COLLECTION_ID])

    val state: StateFlow<CollectionDetailState> = combine(
        repository.observeCollection(collectionId),
        repository.observeSeriesInCollection(collectionId),
        repository.seriesCollections,
        repository.library,
    ) { collection, series, refs, library ->
        CollectionDetailState(
            isLoading = false,
            collection = collection,
            series = series,
            addedAt = refs.filter { it.collectionId == collectionId }.associate { it.seriesId to it.addedAt },
            library = library,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CollectionDetailState())

    // Búsqueda, orden y filtros: se mantienen mientras el estante esté abierto.
    var query by mutableStateOf("")
    var sort by mutableStateOf(ShelfSort.TITLE_ASC)
    var filters by mutableStateOf(ShelfFilters())

    fun rename(name: String) = viewModelScope.launch {
        state.value.collection?.let { repository.renameCollection(it, name) }
    }

    fun delete() = viewModelScope.launch {
        state.value.collection?.let { repository.deleteCollection(it) }
    }

    fun setInShelf(seriesId: Long, inShelf: Boolean) = viewModelScope.launch {
        repository.setInCollection(seriesId, collectionId, inShelf)
    }

    // --- Foto del estante ---

    /** Guarda la imagen elegida (cámara o galería) como foto del estante. */
    fun onCoverImage(uri: Uri, onResult: (Boolean) -> Unit) = viewModelScope.launch {
        val path = covers.saveImage(uri)
        state.value.collection?.let { if (path != null) repository.setCollectionCover(it, path) }
        onResult(path != null)
    }

    /** Usa una copia de la portada de una serie (copia: así no se pierde si luego se borra la serie). */
    fun useSeriesCover(item: SeriesWithVolumes) = viewModelScope.launch {
        val copy = item.series.coverPath?.let { covers.copy(it) } ?: return@launch
        state.value.collection?.let { repository.setCollectionCover(it, copy) }
    }

    fun removeCover() = viewModelScope.launch {
        state.value.collection?.let { repository.setCollectionCover(it, null) }
    }
}
