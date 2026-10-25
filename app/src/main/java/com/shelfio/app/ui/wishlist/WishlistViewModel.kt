package com.shelfio.app.ui.wishlist

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shelfio.app.data.ShelfioRepository
import com.shelfio.app.data.WishlistSort
import com.shelfio.app.data.model.SeriesWithVolumes
import com.shelfio.app.data.model.Volume
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Lista de deseos: búsqueda, orden y «Lo he comprado». */
class WishlistViewModel(private val repository: ShelfioRepository) : ViewModel() {

    /** Toda la biblioteca (null mientras carga); la pantalla saca de aquí los tomos que no tengo. */
    val library: StateFlow<List<SeriesWithVolumes>?> =
        repository.library.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    var query by mutableStateOf("")
    var sort by mutableStateOf(WishlistSort.PRIORITY)

    /** Tomo para el que se está preguntando dónde se va a guardar. */
    var buying by mutableStateOf<Volume?>(null)

    fun markBought(volume: Volume, location: String) = viewModelScope.launch {
        buying = null
        repository.markBought(volume, location)
    }
}
