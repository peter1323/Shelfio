package com.shelfio.app.ui.series

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shelfio.app.data.CoverStorage
import com.shelfio.app.data.ShelfioRepository
import com.shelfio.app.data.model.CollectionWithCount
import com.shelfio.app.data.model.ReadingStatus
import com.shelfio.app.data.model.SeriesWithVolumes
import com.shelfio.app.data.model.Volume
import com.shelfio.app.ui.navigation.Routes
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Lo que muestra la ficha de una serie. */
data class SeriesDetailState(
    val isLoading: Boolean = true,
    /** null cuando ya no existe (por ejemplo, se acaba de borrar). */
    val item: SeriesWithVolumes? = null,
    val allCollections: List<CollectionWithCount> = emptyList(),
    val collectionIds: Set<Long> = emptySet(),
)

/** Ficha de serie: tomos, cambios rápidos de estado, portada y colecciones. */
class SeriesDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: ShelfioRepository,
    private val covers: CoverStorage,
) : ViewModel() {

    val seriesId: Long = checkNotNull(savedStateHandle[Routes.ARG_SERIES_ID])

    val state: StateFlow<SeriesDetailState> = combine(
        repository.observeSeries(seriesId),
        repository.collections,
        repository.observeCollectionIdsOfSeries(seriesId),
    ) { item, collections, ids ->
        SeriesDetailState(isLoading = false, item = item, allCollections = collections, collectionIds = ids.toSet())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SeriesDetailState())

    // --- Cambios rápidos en un tomo ---

    fun setStatus(volume: Volume, status: ReadingStatus) = viewModelScope.launch {
        repository.updateVolume(volume.copy(status = status))
    }

    /** «Lo tengo». El repositorio borra la ubicación al desmarcarlo y lo mete en la lista de deseos. */
    fun setOwned(volume: Volume, owned: Boolean) = viewModelScope.launch {
        repository.updateVolume(volume.copy(owned = owned))
    }

    /** «Añadir los tomos que me faltan hasta el N»: crea los que no existan como «no lo tengo». */
    fun addMissingVolumes(upTo: Int, onResult: (added: Int) -> Unit) = viewModelScope.launch {
        onResult(repository.addMissingVolumes(seriesId, upTo))
    }

    fun deleteVolume(volume: Volume) = viewModelScope.launch { repository.deleteVolume(volume) }

    // --- Portada de la serie ---

    /** Procesa la foto recién hecha y la pone como portada. Devuelve por [onResult] si ha ido bien. */
    fun onCoverPhotoTaken(onResult: (Boolean) -> Unit) = viewModelScope.launch {
        val series = state.value.item?.series ?: return@launch
        val path = covers.saveCameraPhoto()
        if (path != null) repository.setSeriesCover(series, path)
        onResult(path != null)
    }

    fun removeCover() = viewModelScope.launch {
        state.value.item?.series?.let { repository.setSeriesCover(it, null) }
    }

    // --- Colecciones ---

    fun setInCollection(collectionId: Long, inCollection: Boolean) = viewModelScope.launch {
        repository.setInCollection(seriesId, collectionId, inCollection)
    }

    fun createCollectionWithSeries(name: String) = viewModelScope.launch {
        val collectionId = repository.createCollection(name)
        repository.setInCollection(seriesId, collectionId, true)
    }
}
