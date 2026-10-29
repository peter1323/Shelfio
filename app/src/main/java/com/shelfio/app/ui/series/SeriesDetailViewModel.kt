package com.shelfio.app.ui.series

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shelfio.app.data.CoverStorage
import com.shelfio.app.data.ShelfioRepository
import com.shelfio.app.data.model.CollectionWithCount
import com.shelfio.app.data.model.PurchasePriority
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

    fun setStatus(volume: Volume, status: ReadingStatus, readLevel: Int = volume.readLevel) = viewModelScope.launch {
        repository.updateVolume(volume.copy(status = status, readLevel = readLevel))
    }

    /** «Lo tengo». El repositorio borra la ubicación al desmarcarlo y lo mete en la lista de deseos. */
    fun setOwned(volume: Volume, owned: Boolean) = viewModelScope.launch {
        repository.updateVolume(volume.copy(owned = owned))
    }

    /** Prioridad de compra de un tomo que no tengo. */
    fun setPriority(volume: Volume, priority: PurchasePriority?) = viewModelScope.launch {
        repository.updateVolume(volume.copy(purchasePriority = priority))
    }

    /** «Añadir los tomos que me faltan hasta el N»: crea los que no existan como «no lo tengo». */
    fun addMissingVolumes(upTo: Int, onResult: (added: Int) -> Unit) = viewModelScope.launch {
        onResult(repository.addMissingVolumes(seriesId, upTo))
    }

    fun deleteVolume(volume: Volume) = viewModelScope.launch { repository.deleteVolume(volume) }

    // --- Portada de la serie ---

    /** Portada recién guardada que espera respuesta a «¿solo la portada o también los tomos?». */
    var pendingCover by mutableStateOf<String?>(null)
        private set

    /** Guarda la imagen (foto o galería) en [pendingCover] para preguntar si va solo en la portada o también en los tomos. */
    fun onCoverImage(uri: Uri, onResult: (Boolean) -> Unit) = viewModelScope.launch {
        val path = covers.saveImage(uri)
        if (path != null) pendingCover = path
        onResult(path != null)
    }

    /** Respuesta a la pregunta: solo la portada de la serie, o también una copia en cada tomo. */
    fun applyPendingCover(toAllVolumes: Boolean) = viewModelScope.launch {
        val path = pendingCover ?: return@launch
        pendingCover = null
        val item = state.value.item ?: return@launch
        if (toAllVolumes) repository.setCoverForSeriesAndVolumes(item, path) else repository.setSeriesCover(item.series, path)
    }

    fun removeCover() = viewModelScope.launch {
        state.value.item?.series?.let { repository.removeSeriesCover(it) }
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
