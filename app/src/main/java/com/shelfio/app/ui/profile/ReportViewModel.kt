package com.shelfio.app.ui.profile

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shelfio.app.data.ShelfioRepository
import com.shelfio.app.data.model.SeriesWithVolumes
import com.shelfio.app.data.report.PdfReport
import com.shelfio.app.data.report.ReportOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

/** «Crear informe PDF» de Perfil › Ajustes: opciones, progreso y cancelar. */
class ReportViewModel(
    private val repository: ShelfioRepository,
    private val report: PdfReport,
) : ViewModel() {

    var options by mutableStateOf(ReportOptions())
    var showOptions by mutableStateOf(false)
    /** Progreso de 0 a 1 mientras se crea; null si no se está creando. */
    var progress by mutableStateOf<Float?>(null)
        private set
    private var job: Job? = null

    val defaultFileName: String get() = report.defaultFileName()

    fun saveTo(uri: Uri, onMessage: (String) -> Unit) = generate(onMessage) { items, collections ->
        report.writeTo(uri, items, collections, options) { progress = it }
        onMessage("Informe PDF guardado.")
    }

    fun share(onReady: (File) -> Unit, onMessage: (String) -> Unit) = generate(onMessage) { items, collections ->
        onReady(report.writeForSharing(items, collections, options) { progress = it })
    }

    fun cancel() {
        job?.cancel()
    }

    private fun generate(onMessage: (String) -> Unit, task: suspend (items: List<SeriesWithVolumes>, collections: Int) -> Unit) {
        if (job?.isActive == true) return
        showOptions = false
        progress = 0f
        job = viewModelScope.launch {
            try {
                task(repository.library.first(), repository.collections.first().size)
            } catch (e: CancellationException) {
                onMessage("Informe cancelado.")
                throw e
            } catch (e: Exception) {
                onMessage("No se pudo crear o guardar el informe. Inténtalo de nuevo.")
            } finally {
                progress = null
            }
        }
    }
}
