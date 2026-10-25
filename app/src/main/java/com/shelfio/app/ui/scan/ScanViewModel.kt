package com.shelfio.app.ui.scan

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
import com.shelfio.app.data.findMatchingSeries
import com.shelfio.app.data.findVolumeByIsbn
import com.shelfio.app.data.model.SeriesWithVolumes

import com.shelfio.app.data.nextVolume
import com.shelfio.app.data.normalizeIsbn
import com.shelfio.app.ui.navigation.Routes
import com.shelfio.app.ui.series.SeriesFormState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Pasos del escaneo: leer ISBN → buscar → (elegir resultado) → revisar y guardar. */
sealed interface ScanStep {
    data object Scanning : ScanStep
    data object Searching : ScanStep
    data class Choose(val results: List<BookResult>) : ScanStep
    data object Review : ScanStep
    data class NotFound(val isbn: String) : ScanStep
    data class Failed(val isbn: String, val message: String) : ScanStep
}

/**
 * Escanear o escribir un ISBN, buscar sus datos y guardarlos solo tras revisarlos.
 * Si se abre desde la ficha de una serie (seriesId en la ruta), permite completar sus datos o añadir un tomo.
 */
class ScanViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: ShelfioRepository,
    private val lookup: BookLookup,
    private val covers: CoverStorage,
) : ViewModel() {

    /** Serie desde la que se abrió el escáner, o null si se abrió desde Añadir/Inicio. */
    val fixedSeriesId: Long? = savedStateHandle.get<Long>(Routes.ARG_SERIES_ID)?.takeIf { it > 0 }

    var step by mutableStateOf<ScanStep>(ScanStep.Scanning)
        private set

    /** ISBN escrito a mano y su error de validación. */
    var manualIsbn by mutableStateOf("")
    var manualError by mutableStateOf<String?>(null)
        private set

    // --- Estado de la pantalla de revisión ---

    var selected by mutableStateOf<BookResult?>(null)
        private set
    /** Datos editables antes de guardar (serie + primer tomo). */
    var form by mutableStateOf(SeriesFormState())
    /** Series existentes que parecen la misma obra. */
    var matches by mutableStateOf<List<SeriesWithVolumes>>(emptyList())
        private set
    /** null = crear serie nueva; si no, id de la serie a la que se añade el tomo. */
    var targetSeriesId by mutableStateOf<Long?>(null)
        private set
    /** Portada descargada para enseñarla y guardarla si el usuario quiere. */
    var coverBytes by mutableStateOf<ByteArray?>(null)
        private set
    var useCover by mutableStateOf(true)
    /** Aviso si ese ISBN ya está en la biblioteca. */
    var duplicateWarning by mutableStateOf<String?>(null)
        private set
    var isSaving by mutableStateOf(false)
        private set

    private var library: List<SeriesWithVolumes> = emptyList()

    /** El escáner llama aquí con cada lectura; solo cuenta mientras se está escaneando. */
    fun onScanned(isbn: String) {
        if (step == ScanStep.Scanning) search(isbn)
    }

    fun searchManual() {
        val isbn = normalizeIsbn(manualIsbn)
        manualError = if (isbn == null) "Ese ISBN no es válido. Revisa los 10 o 13 dígitos." else null
        if (isbn != null) search(isbn)
    }

    fun retry(isbn: String) = search(isbn)

    fun scanAgain() {
        step = ScanStep.Scanning
        selected = null
        coverBytes = null
    }

    private fun search(isbn: String) {
        step = ScanStep.Searching
        viewModelScope.launch {
            library = repository.library.first()
            when (val result = lookup.searchByIsbn(isbn)) {
                is LookupResult.Found ->
                    if (result.results.size == 1) select(result.results.first())
                    else step = ScanStep.Choose(result.results)
                LookupResult.NotFound -> step = ScanStep.NotFound(isbn)
                is LookupResult.Failed -> step = ScanStep.Failed(isbn, result.message)
            }
        }
    }

    /** Prepara la revisión con un resultado elegido. */
    fun select(result: BookResult) {
        selected = result
        form = SeriesFormState(
            title = result.title,
            author = result.authors,
            publisher = result.publisher,
            language = result.language,
            description = result.description,
            isbn = result.isbn,
        )
        matches = if (fixedSeriesId != null) library.filter { it.series.id == fixedSeriesId }
        else findMatchingSeries(library, result.title)
        duplicateWarning = findVolumeByIsbn(library, result.isbn)?.let {
            "Ya tienes este ISBN en «${it.series.title}» (tomo ${it.volume.number})."
        }
        chooseTarget(matches.firstOrNull()?.series?.id)
        coverBytes = null
        useCover = true
        step = ScanStep.Review
        result.coverUrl?.let { url -> viewModelScope.launch { coverBytes = lookup.downloadImage(url) } }
    }

    /** Elige entre serie nueva (null) o añadir como tomo de una existente; propone el número siguiente. */
    fun chooseTarget(seriesId: Long?) {
        targetSeriesId = seriesId
        val target = library.firstOrNull { it.series.id == seriesId }
        form = form.copy(volumeNumber = (target?.let { nextVolume(it.series.id, it.volumes).number } ?: 1).toString())
    }

    /** Guarda lo revisado: serie nueva con su tomo, o tomo nuevo en la serie elegida. */
    fun save(onSaved: (seriesId: Long) -> Unit) {
        if (isSaving || !form.isValid) return
        isSaving = true
        viewModelScope.launch {
            val coverPath = savedCoverPath()
            val targetId = targetSeriesId
            val seriesId = if (targetId == null) {
                repository.createSeries(form.toSeries().copy(coverPath = coverPath), form.toFirstVolume())
            } else {
                repository.addVolume(form.toFirstVolume().copy(seriesId = targetId, coverPath = coverPath))
                targetId
            }
            isSaving = false
            onSaved(seriesId)
        }
    }

    /** Solo desde la ficha: rellena los datos vacíos de la serie (y la portada si no tiene). Nunca pisa lo escrito. */
    fun completeSeries(onDone: () -> Unit) {
        val target = matches.firstOrNull() ?: return
        if (isSaving) return
        isSaving = true
        viewModelScope.launch {
            val series = target.series
            val coverPath = if (series.coverPath == null) savedCoverPath() else null
            repository.updateSeries(
                series.copy(
                    author = series.author.ifBlank { form.author.trim() },
                    publisher = series.publisher.ifBlank { form.publisher.trim() },
                    language = series.language.ifBlank { form.language.trim() },
                    description = series.description.ifBlank { form.description.trim() },
                    coverPath = series.coverPath ?: coverPath,
                ),
            )
            isSaving = false
            onDone()
        }
    }

    private suspend fun savedCoverPath(): String? =
        coverBytes?.takeIf { useCover }?.let { covers.saveDownloaded(it) }
}

