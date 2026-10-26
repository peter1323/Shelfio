package com.shelfio.app.ui.add

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shelfio.app.data.CoverStorage
import com.shelfio.app.data.ShelfioRepository
import com.shelfio.app.data.books.BookLookup
import com.shelfio.app.data.books.BookResult
import com.shelfio.app.data.books.LookupResult
import com.shelfio.app.ui.navigation.Routes
import com.shelfio.app.ui.series.SeriesFormState
import kotlinx.coroutines.launch

/**
 * Pantalla Añadir: buscador por título que rellena la ficha y formulario para escribirla o corregirla.
 * Si se llega desde el escáner sin resultados, el ISBN viene ya rellenado en la ruta.
 */
class AddViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: ShelfioRepository,
    private val lookup: BookLookup,
    private val covers: CoverStorage,
) : ViewModel() {

    var form by mutableStateOf(SeriesFormState(isbn = savedStateHandle.get<String>(Routes.ARG_ISBN).orEmpty()))

    // --- Buscador por título ---

    var query by mutableStateOf("")
    var isSearching by mutableStateOf(false)
        private set
    var results by mutableStateOf<List<BookResult>>(emptyList())
        private set
    /** Mensaje cuando no hay resultados o una fuente falla. */
    var searchMessage by mutableStateOf<String?>(null)
        private set
    /** Resultado con el que se rellenó la ficha (para enseñar sus otros títulos y guardar su portada). */
    var chosen by mutableStateOf<BookResult?>(null)
        private set
    var useCover by mutableStateOf(true)

    /** Busca por título en las fuentes que encajan con el tipo elegido (AniList para manga y novela ligera). */
    fun search() {
        val title = query.trim()
        if (title.isBlank() || isSearching) return
        isSearching = true
        searchMessage = null
        viewModelScope.launch {
            when (val result = lookup.searchByTitle(title, form.type)) {
                is LookupResult.Found -> results = result.results
                LookupResult.NotFound -> { results = emptyList(); searchMessage = "No se ha encontrado. Prueba con otro título o escribe la ficha a mano." }
                is LookupResult.Failed -> { results = emptyList(); searchMessage = result.message }
            }
            isSearching = false
        }
    }

    /** Rellena la ficha con el resultado elegido. Lo que ya estaba escrito y no viene en el resultado se mantiene. */
    fun choose(result: BookResult) {
        chosen = result
        useCover = true
        form = form.copy(
            title = result.title,
            author = result.authors.ifBlank { form.author },
            publisher = result.publisher.ifBlank { form.publisher },
            language = result.language.ifBlank { form.language },
            description = result.description.ifBlank { form.description },
            isbn = form.isbn.ifBlank { result.isbn },
            statusInOrigin = result.originStatus ?: form.statusInOrigin,
        )
        results = emptyList()
    }

    /** Crea la serie con su primer tomo (y la portada del resultado, si se quiere), limpia todo y avisa con el id. */
    fun save(onSaved: (seriesId: Long) -> Unit) {
        val toSave = form
        if (!toSave.isValid) return
        val coverUrl = chosen?.coverUrl?.takeIf { useCover }
        viewModelScope.launch {
            val coverPath = coverUrl?.let { lookup.downloadImage(it) }?.let { covers.saveDownloaded(it) }
            val seriesId = repository.createSeries(toSave.toSeries().copy(coverPath = coverPath), toSave.toFirstVolume())
            toSave.wishlistUpTo.toIntOrNull()?.let { repository.addMissingVolumes(seriesId, it) }
            form = SeriesFormState()
            query = ""
            chosen = null
            onSaved(seriesId)
        }
    }
}
