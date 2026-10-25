package com.shelfio.app.ui.add

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shelfio.app.data.ShelfioRepository
import com.shelfio.app.ui.navigation.Routes
import com.shelfio.app.ui.series.SeriesFormState
import kotlinx.coroutines.launch

/**
 * Formulario de Añadir a mano. Si se llega desde el escáner sin resultados,
 * el ISBN viene ya rellenado en la ruta.
 */
class AddViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: ShelfioRepository,
) : ViewModel() {

    var form by mutableStateOf(SeriesFormState(isbn = savedStateHandle.get<String>(Routes.ARG_ISBN).orEmpty()))

    /** Crea la serie con su primer tomo, limpia el formulario y avisa con el id de la serie nueva. */
    fun save(onSaved: (seriesId: Long) -> Unit) {
        val toSave = form
        if (!toSave.isValid) return
        viewModelScope.launch {
            val seriesId = repository.createSeries(toSave.toSeries(), toSave.toFirstVolume())
            form = SeriesFormState()
            onSaved(seriesId)
        }
    }
}
