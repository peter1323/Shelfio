package com.shelfio.app.ui.series

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shelfio.app.data.ShelfioRepository
import com.shelfio.app.data.model.Series
import com.shelfio.app.ui.navigation.Routes
import kotlinx.coroutines.launch

/** Editar o borrar los datos comunes de una serie. El id llega en la ruta de navegación. */
class EditSeriesViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: ShelfioRepository,
) : ViewModel() {

    private val seriesId: Long = checkNotNull(savedStateHandle[Routes.ARG_SERIES_ID])
    private var original: Series? = null

    /** Formulario con los datos actuales. null mientras carga. */
    var form by mutableStateOf<SeriesFormState?>(null)

    init {
        viewModelScope.launch {
            original = repository.getSeries(seriesId)
            form = original?.let(SeriesFormState::fromSeries)
        }
    }

    fun save(onDone: () -> Unit) {
        val base = original ?: return
        val edited = form ?: return
        viewModelScope.launch {
            repository.updateSeries(edited.toSeries(base))
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        val series = original ?: return
        viewModelScope.launch {
            repository.deleteSeries(series)
            onDone()
        }
    }
}
