package com.shelfio.app.ui.collections

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    /** null cuando la colección ya no existe (se acaba de borrar). */
    val collection: ShelfCollection? = null,
    val series: List<SeriesWithVolumes> = emptyList(),
)

/** Detalle de una colección: sus series, renombrar, borrar y quitar series. */
class CollectionDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: ShelfioRepository,
) : ViewModel() {

    private val collectionId: Long = checkNotNull(savedStateHandle[Routes.ARG_COLLECTION_ID])

    val state: StateFlow<CollectionDetailState> = combine(
        repository.observeCollection(collectionId),
        repository.observeSeriesInCollection(collectionId),
    ) { collection, series -> CollectionDetailState(isLoading = false, collection = collection, series = series) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CollectionDetailState())

    fun rename(name: String) = viewModelScope.launch {
        state.value.collection?.let { repository.renameCollection(it, name) }
    }

    fun delete() = viewModelScope.launch {
        state.value.collection?.let { repository.deleteCollection(it) }
    }

    fun removeSeries(seriesId: Long) = viewModelScope.launch {
        repository.setInCollection(seriesId, collectionId, inCollection = false)
    }
}
