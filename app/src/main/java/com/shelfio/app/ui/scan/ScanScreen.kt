package com.shelfio.app.ui.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DocumentScanner

import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.shelfio.app.data.books.BookResult
import com.shelfio.app.ui.AppViewModelProvider
import com.shelfio.app.ui.components.EmptyState
import com.shelfio.app.ui.components.LoadingBox
import com.shelfio.app.ui.components.LocalShowMessage
import com.shelfio.app.ui.components.ScreenWithBack
import com.shelfio.app.ui.components.hasCameraPermission
import com.shelfio.app.ui.components.rememberCameraPermissionRequest
import com.shelfio.app.ui.series.FormField
import com.shelfio.app.ui.series.LocationField
import com.shelfio.app.ui.series.OwnedSwitch
import com.shelfio.app.ui.series.SeriesFormFields
import com.shelfio.app.ui.series.StatusSelector
import com.shelfio.app.ui.series.TypeSelector
import com.shelfio.app.ui.theme.TextSecondary

/** Escanear o escribir un ISBN, ver los resultados y revisarlos antes de guardar. */
@Composable
fun ScanScreen(
    onBack: () -> Unit,
    onOpenSeries: (seriesId: Long) -> Unit,
    onManualEntry: (isbn: String) -> Unit,
    viewModel: ScanViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val showMessage = LocalShowMessage.current
    ScreenWithBack(title = "Escanear ISBN", onBack = onBack) {
        when (val step = viewModel.step) {
            ScanStep.Scanning -> ScanningStep(viewModel)
            ScanStep.Searching -> LoadingBox()
            is ScanStep.Choose -> ChooseStep(step.results, onSelect = viewModel::select)
            ScanStep.Review -> ReviewStep(
                viewModel = viewModel,
                onSaved = { seriesId ->
                    showMessage("Guardado en tu biblioteca.")
                    // Desde la ficha se vuelve a ella; desde Añadir/Inicio se abre la ficha nueva.
                    if (viewModel.fixedSeriesId != null) onBack() else onOpenSeries(seriesId)
                },
                onCompleted = { showMessage("Datos de la serie completados."); onBack() },
            )
            is ScanStep.NotFound -> ProblemStep(
                icon = Icons.Filled.SearchOff,
                title = "No se ha encontrado",
                message = "Google Books y Open Library no tienen el ISBN ${step.isbn}. Escanea la portada para buscarlo por título, o escribe los datos a mano: el ISBN ya va rellenado.",
                onCoverScan = viewModel::startCoverScan,
                onManual = { onManualEntry(step.isbn) },
                onSecondary = viewModel::scanAgain,
                secondaryLabel = "Escanear otro",
            )
            ScanStep.CoverScan -> CoverScanStep(viewModel, onError = { showMessage("No se pudo leer la portada. Prueba con más luz.") })
            is ScanStep.PickTitle -> PickTitleStep(step.candidates, viewModel)
            is ScanStep.Failed -> ProblemStep(
                icon = Icons.Filled.CloudOff,
                title = "No se pudo buscar",
                message = step.message,
                onCoverScan = viewModel::startCoverScan,
                onManual = { onManualEntry(step.isbn) },
                onSecondary = { viewModel.retry(step.isbn) },
                secondaryLabel = "Reintentar",
            )
        }
    }
}

/** Cámara (si hay permiso) y campo para escribir el ISBN a mano. */
@Composable
private fun ScanningStep(viewModel: ScanViewModel) {
    var cameraAllowed by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val granted = hasCameraPermission() || cameraAllowed == true
    val requestCamera = rememberCameraPermissionRequest { cameraAllowed = it }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // El tipo decide dónde se busca por título si el ISBN no aparece (AniList para manga y novela ligera).
        Text("¿Qué vas a escanear?", style = MaterialTheme.typography.titleMedium)
        TypeSelector(viewModel.searchType) { viewModel.searchType = it }
        if (granted) {
            BarcodeScanner(
                onIsbn = viewModel::onScanned,
                modifier = Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(16.dp)),
            )
            Text("Apunta al código de barras de la contraportada.", color = TextSecondary)
        } else {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Leer el código de barras", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (cameraAllowed == false) "Sin permiso de cámara no se puede escanear. Escribe el ISBN abajo o da el permiso en Ajustes del móvil."
                    else "Shelfio usa la cámara solo para leer el ISBN mientras estás en esta pantalla. No guarda vídeo ni fotos.",
                    color = TextSecondary,
                )
                FilledTonalButton(onClick = requestCamera) {
                    Text("Usar la cámara")
                }
            }
        }

        Text("O escribe el ISBN:", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = viewModel.manualIsbn,
            onValueChange = { viewModel.manualIsbn = it },
            label = { Text("ISBN (10 o 13 dígitos)") },
            singleLine = true,
            isError = viewModel.manualError != null,
            supportingText = viewModel.manualError?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = viewModel::searchManual, enabled = viewModel.manualIsbn.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text("Buscar")
        }
    }
}

/** Varios resultados: el usuario elige cuál revisar. */
@Composable
private fun ChooseStep(results: List<BookResult>, onSelect: (BookResult) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Elige el resultado correcto", style = MaterialTheme.typography.titleMedium)
        results.forEach { result ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { onSelect(result) }
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(result.title, style = MaterialTheme.typography.titleMedium)
                listOf(result.authors, result.publisher).filter { it.isNotBlank() }.forEach { Text(it, color = TextSecondary) }
                Text("Fuente: ${result.source}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
        }
    }
}

/** Revisión editable antes de guardar. Nada se guarda hasta pulsar un botón. */
@Composable
private fun ReviewStep(viewModel: ScanViewModel, onSaved: (Long) -> Unit, onCompleted: () -> Unit) {
    val result = viewModel.selected ?: return
    val form = viewModel.form
    val target = viewModel.matches.firstOrNull { it.series.id == viewModel.targetSeriesId }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Revisa los datos", style = MaterialTheme.typography.headlineSmall)
        Text("Fuente: ${result.source}. Corrige lo que haga falta; los campos que no vengan se quedan vacíos.", color = TextSecondary)
        viewModel.duplicateWarning?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        // AniList da la obra con varios títulos (romaji, inglés, japonés, sinónimos en otros idiomas): el usuario elige.
        if (result.alternativeTitles.size > 1) {
            Text("Elige el título que quieres guardar", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                result.alternativeTitles.forEach { title ->
                    FilterChip(selected = form.title == title, onClick = { viewModel.form = form.copy(title = title) }, label = { Text(title) })
                }
            }
        }

        viewModel.coverBytes?.let { bytes ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AsyncImage(
                    model = bytes,
                    contentDescription = "Portada encontrada",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.width(90.dp).height(135.dp).clip(RoundedCornerShape(10.dp)),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = viewModel.useCover, onCheckedChange = { viewModel.useCover = it })
                    Text("Usar esta portada")
                }
            }
        }

        // ¿Serie nueva o tomo de una que ya tengo?
        if (viewModel.matches.isNotEmpty()) {
            Text("¿Dónde lo guardo?", style = MaterialTheme.typography.titleMedium)
            viewModel.matches.forEach { match ->
                TargetOption(
                    selected = viewModel.targetSeriesId == match.series.id,
                    label = "Como tomo de «${match.series.title}»",
                    onClick = { viewModel.chooseTarget(match.series.id) },
                )
            }
            if (viewModel.fixedSeriesId == null) {
                TargetOption(selected = viewModel.targetSeriesId == null, label = "Como serie nueva", onClick = { viewModel.chooseTarget(null) })
            }
        }

        if (target == null) {
            SeriesFormFields(form, { viewModel.form = it }, showFirstVolume = true)
        } else {
            // En una serie existente solo se pide lo que cambia en el tomo.
            FormField("Número de tomo", form.volumeNumber, { viewModel.form = form.copy(volumeNumber = it.filter(Char::isDigit)) }, keyboardType = KeyboardType.Number)
            FormField("ISBN", form.isbn, { viewModel.form = form.copy(isbn = it) }, keyboardType = KeyboardType.Number)
            OwnedSwitch(form.owned) { viewModel.form = form.copy(owned = it) }
            StatusSelector(form.status) { viewModel.form = form.copy(status = it) }
            if (form.owned) LocationField(form.location) { viewModel.form = form.copy(location = it) }
        }

        Button(
            onClick = { viewModel.save(onSaved) },
            enabled = form.isValid && !viewModel.isSaving,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (target == null) "Guardar" else "Añadir tomo ${form.volumeNumber}") }

        if (viewModel.fixedSeriesId != null) {
            OutlinedButton(onClick = { viewModel.completeSeries(onCompleted) }, enabled = !viewModel.isSaving, modifier = Modifier.fillMaxWidth()) {
                Text("Solo completar datos y portada que falten")
            }
        }
        OutlinedButton(onClick = viewModel::scanAgain, modifier = Modifier.fillMaxWidth()) { Text("Descartar y escanear otro") }
    }
}

@Composable
private fun TargetOption(selected: Boolean, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}

/** Sin resultados o sin conexión: la entrada manual siempre funciona. */
@Composable
private fun ProblemStep(
    icon: ImageVector,
    title: String,
    message: String,
    onCoverScan: () -> Unit,
    onManual: () -> Unit,
    onSecondary: () -> Unit,
    secondaryLabel: String,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        EmptyState(icon = icon, title = title, message = message)
        Button(onClick = onCoverScan, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.DocumentScanner, contentDescription = null)
            Text("Escanear la portada", Modifier.padding(start = 8.dp))
        }
        FilledTonalButton(onClick = onManual, modifier = Modifier.fillMaxWidth()) { Text("Escribir los datos a mano") }
        OutlinedButton(onClick = onSecondary, modifier = Modifier.fillMaxWidth()) { Text(secondaryLabel) }
    }
}

/** Cámara para leer la portada (pide permiso si hace falta). */
@Composable
private fun CoverScanStep(viewModel: ScanViewModel, onError: () -> Unit) {
    var cameraAllowed by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val granted = hasCameraPermission() || cameraAllowed == true
    val requestCamera = rememberCameraPermissionRequest { cameraAllowed = it }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Enfoca la portada para que se lea bien el título y pulsa «Leer la portada».", color = TextSecondary)
        if (granted) {
            CoverTextReader(onLines = viewModel::onCoverLines, onError = onError)
        } else {
            FilledTonalButton(onClick = requestCamera, modifier = Modifier.fillMaxWidth()) { Text("Usar la cámara") }
            if (cameraAllowed == false) Text("Sin permiso de cámara no se puede leer la portada.", color = MaterialTheme.colorScheme.error)
        }
        OutlinedButton(onClick = { viewModel.onCoverLines(emptyList()) }, modifier = Modifier.fillMaxWidth()) { Text("Escribir el título yo") }
    }
}

/** Elegir o corregir el título leído y buscarlo. */
@Composable
private fun PickTitleStep(candidates: List<String>, viewModel: ScanViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("¿Cuál es el título?", style = MaterialTheme.typography.titleMedium)
        if (candidates.isEmpty()) Text("No se ha podido leer texto en la portada. Escribe el título.", color = TextSecondary)
        else Text("Toca el texto correcto o corrígelo.", color = TextSecondary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            candidates.forEach { line ->
                FilterChip(selected = viewModel.titleQuery == line, onClick = { viewModel.titleQuery = line }, label = { Text(line) })
            }
        }
        OutlinedTextField(
            value = viewModel.titleQuery,
            onValueChange = { viewModel.titleQuery = it },
            label = { Text("Título") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = viewModel::searchTitle, enabled = viewModel.titleQuery.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text("Buscar por título")
        }
        OutlinedButton(onClick = viewModel::startCoverScan, modifier = Modifier.fillMaxWidth()) { Text("Volver a leer la portada") }
        TextButton(onClick = viewModel::useTitleOnly, enabled = viewModel.titleQuery.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Usar este título sin buscar") }
    }
}
