package com.shelfio.app.ui.volume

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shelfio.app.data.CoverStorage
import com.shelfio.app.data.ShelfioRepository
import com.shelfio.app.data.model.ReadingStatus
import com.shelfio.app.data.model.SeriesWithVolumes
import com.shelfio.app.data.model.Volume
import com.shelfio.app.data.nextVolume
import com.shelfio.app.ui.navigation.Routes
import kotlinx.coroutines.launch

/** Datos editables de un tomo. Los números se guardan como texto mientras se escriben. */
data class VolumeFormState(
    val number: String,
    val title: String,
    val isbn: String,
    val owned: Boolean,
    val status: ReadingStatus,
    val location: String,
    val notes: String,
) {
    fun applyTo(volume: Volume) = volume.copy(
        number = number.toIntOrNull() ?: volume.number,
        title = title.trim(),
        isbn = isbn.trim(),
        owned = owned,
        status = status,
        location = if (owned) location.trim() else "",
        notes = notes.trim(),
    )

    companion object {
        fun from(volume: Volume) = VolumeFormState(
            number = volume.number.toString(),
            title = volume.title,
            isbn = volume.isbn,
            owned = volume.owned,
            status = volume.status,
            location = volume.location,
            notes = volume.notes,
        )
    }
}

/**
 * Crear o editar un tomo. Con volumeId = [Routes.NEW_VOLUME] es «Añadir siguiente tomo»:
 * el formulario llega con el número siguiente y solo hay que cambiar lo que sea distinto.
 */
class VolumeEditViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: ShelfioRepository,
    private val covers: CoverStorage,
) : ViewModel() {

    private val seriesId: Long = checkNotNull(savedStateHandle[Routes.ARG_SERIES_ID])
    private val volumeId: Long = checkNotNull(savedStateHandle[Routes.ARG_VOLUME_ID])
    val isNew: Boolean = volumeId == Routes.NEW_VOLUME

    /** Serie a la que pertenece el tomo (para títulos y portadas). null mientras carga. */
    var series by mutableStateOf<SeriesWithVolumes?>(null)
        private set

    /** Tomo tal como está guardado (o el nuevo sin guardar). */
    var volume by mutableStateOf<Volume?>(null)
        private set

    var form by mutableStateOf<VolumeFormState?>(null)

    /** Mensaje de error del formulario (por ejemplo, número repetido). */
    var error by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            val loadedSeries = repository.getSeries(seriesId) ?: return@launch
            val loadedVolume = if (isNew) nextVolume(seriesId, loadedSeries.volumes) else repository.getVolume(volumeId)
            series = loadedSeries
            volume = loadedVolume
            form = loadedVolume?.let(VolumeFormState::from)
        }
    }

    /** Un libro suelto no habla de tomos: se edita como «tu ejemplar». */
    val isSingleBook: Boolean get() = !isNew && series?.isSingleBook == true

    /** Solo se puede borrar un tomo si la serie tiene más; para quitar el último se borra la serie. */
    val canDelete: Boolean get() = !isNew && (series?.volumes?.size ?: 0) > 1

    fun save(onDone: () -> Unit) {
        val base = volume ?: return
        val edited = form?.applyTo(base) ?: return
        val number = form?.number?.toIntOrNull()
        val repeated = series?.volumes?.any { it.number == number && it.id != base.id } == true
        error = when {
            number == null || number <= 0 -> "Escribe un número de tomo válido."
            repeated -> "Ya tienes el tomo $number en esta serie."
            else -> null
        }
        if (error != null) return
        viewModelScope.launch {
            if (isNew) repository.addVolume(edited) else repository.updateVolume(edited)
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        val current = volume ?: return
        viewModelScope.launch {
            repository.deleteVolume(current)
            onDone()
        }
    }

    // La portada propia del tomo se guarda al momento (solo en tomos ya guardados).

    fun onCoverPhotoTaken(onResult: (Boolean) -> Unit) = viewModelScope.launch {
        val path = covers.saveCameraPhoto()
        if (path != null) setCover(path)
        onResult(path != null)
    }

    fun removeCover() = viewModelScope.launch { setCover(null) }

    private suspend fun setCover(path: String?) {
        val stored = repository.getVolume(volumeId) ?: return
        repository.updateVolume(stored.copy(coverPath = path))
        volume = volume?.copy(coverPath = path)
    }
}
