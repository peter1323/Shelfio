package com.shelfio.app.ui.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shelfio.app.ui.AppViewModelProvider
import com.shelfio.app.ui.series.SeriesFormFields
import com.shelfio.app.ui.series.SeriesFormSaver
import com.shelfio.app.ui.series.SeriesFormState

/** Pantalla Añadir: escanear el ISBN o escribir los datos a mano. */
@Composable
fun AddScreen(
    onScan: () -> Unit,
    onSaved: (seriesId: Long) -> Unit,
    viewModel: AddViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    var form by rememberSaveable(stateSaver = SeriesFormSaver) { mutableStateOf(SeriesFormState()) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Añadir a tu colección", style = MaterialTheme.typography.headlineMedium)
        FilledTonalButton(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
            Text("Escanear ISBN", Modifier.padding(start = 8.dp))
        }
        Text("O escríbelo a mano:", color = MaterialTheme.colorScheme.onSurfaceVariant)
        SeriesFormFields(form, { form = it }, showFirstVolume = true)
        Button(
            onClick = {
                viewModel.save(form) { seriesId ->
                    form = SeriesFormState() // formulario limpio para el siguiente
                    onSaved(seriesId)
                }
            },
            enabled = form.isValid,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Guardar") }
    }
}
