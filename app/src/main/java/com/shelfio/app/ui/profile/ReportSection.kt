package com.shelfio.app.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shelfio.app.ui.AppViewModelProvider
import com.shelfio.app.ui.components.LocalShowMessage

/** Bloque «Informe PDF» de Perfil › Ajustes. */
@Composable
fun ReportSection(viewModel: ReportViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val context = LocalContext.current
    val showMessage = LocalShowMessage.current
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) viewModel.saveTo(uri, showMessage) else showMessage("No se ha guardado el informe.")
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Informe PDF", style = MaterialTheme.typography.titleSmall)
        val progress = viewModel.progress
        if (progress == null) {
            FilledTonalButton(onClick = { viewModel.showOptions = true }, modifier = Modifier.fillMaxWidth()) { Text("Crear informe PDF") }
        } else {
            Text("Creando informe… ${(progress * 100).toInt()} %")
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = viewModel::cancel) { Text("Cancelar") }
        }
    }

    if (viewModel.showOptions) {
        val options = viewModel.options
        AlertDialog(
            onDismissRequest = { viewModel.showOptions = false },
            title = { Text("Crear informe PDF") },
            text = {
                Column {
                    OptionRow("Incluir la lista de deseos (si no, solo lo que tengo)", options.includeWishlist) { viewModel.options = options.copy(includeWishlist = it) }
                    OptionRow("Incluir ubicaciones", options.includeLocations) { viewModel.options = options.copy(includeLocations = it) }
                    OptionRow("Incluir préstamos activos", options.includeLoans) { viewModel.options = options.copy(includeLoans = it) }
                    OptionRow("Incluir estantes y lo que hay en cada uno", options.includeCollections) { viewModel.options = options.copy(includeCollections = it) }
                }
            },
            confirmButton = { TextButton(onClick = { saveLauncher.launch(viewModel.defaultFileName) }) { Text("Guardar…") } },
            dismissButton = {
                TextButton(onClick = { viewModel.share(onReady = { shareFile(context, it, "application/pdf") }, onMessage = showMessage) }) { Text("Compartir") }
            },
        )
    }
}

@Composable
private fun OptionRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label)
    }
}
