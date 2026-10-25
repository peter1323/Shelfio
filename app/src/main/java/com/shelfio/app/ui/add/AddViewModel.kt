package com.shelfio.app.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shelfio.app.data.ShelfioRepository
import com.shelfio.app.ui.series.SeriesFormState
import kotlinx.coroutines.launch

/** Guarda una serie nueva (con su primer tomo) escrita a mano en Añadir. */
class AddViewModel(private val repository: ShelfioRepository) : ViewModel() {

    /** Crea la serie y avisa con su id para poder abrirla. */
    fun save(form: SeriesFormState, onSaved: (Long) -> Unit) {
        viewModelScope.launch {
            val seriesId = repository.createSeries(form.toSeries(), form.toFirstVolume())
            onSaved(seriesId)
        }
    }
}
