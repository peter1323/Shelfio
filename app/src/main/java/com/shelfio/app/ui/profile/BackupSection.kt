package com.shelfio.app.ui.profile

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shelfio.app.ui.AppViewModelProvider
import com.shelfio.app.ui.components.LocalShowMessage
import com.shelfio.app.ui.theme.TextSecondary
import java.io.File
import java.text.DateFormat
import java.util.Date

/** Bloque «Copia de seguridad» de Perfil › Ajustes. */
@Composable
fun BackupSection(viewModel: BackupViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val context = LocalContext.current
    val showMessage = LocalShowMessage.current

    // Selectores del sistema (Storage Access Framework): si el usuario cancela devuelven null y no pasa nada.
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) viewModel.export(uri, showMessage)
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.readForImport(uri, showMessage)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Copia de seguridad", style = MaterialTheme.typography.titleSmall)
        Text(
            viewModel.lastBackupAt?.let { "Última copia: ${formatDateTime(it)}" } ?: "Aún no has hecho ninguna copia.",
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
        FilledTonalButton(onClick = { saveLauncher.launch(viewModel.defaultFileName) }, enabled = !viewModel.isWorking, modifier = Modifier.fillMaxWidth()) {
            Text("Exportar copia de seguridad")
        }
        OutlinedButton(
            onClick = { viewModel.exportForSharing(onReady = { shareFile(context, it, "application/zip") }, onMessage = showMessage) },
            enabled = !viewModel.isWorking,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Compartir copia (Drive, correo…)") }
        OutlinedButton(
            onClick = { openLauncher.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed")) },
            enabled = !viewModel.isWorking,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Importar copia de seguridad") }
        if (viewModel.isWorking) LinearProgressIndicator(Modifier.fillMaxWidth())
    }

    viewModel.pendingImport?.let { data ->
        AlertDialog(
            onDismissRequest = viewModel::cancelImport,
            title = { Text("¿Importar esta copia?") },
            text = {
                Text(
                    "La copia tiene ${data.series.size} series, ${data.volumes.size} tomos, " +
                        "${data.collections.size} colecciones y ${data.covers.size} portadas.\n\n" +
                        "Se reemplazará TODA tu biblioteca actual por la de la copia. No se puede deshacer.",
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmImport(showMessage) }) { Text("Reemplazar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = viewModel::cancelImport) { Text("Cancelar") } },
        )
    }
}

/** Abre el diálogo de compartir del sistema con un archivo de la caché de la app. */
fun shareFile(context: Context, file: File, mimeType: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType(mimeType)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, null))
}

fun formatDateTime(time: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(time))
