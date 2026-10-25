package com.shelfio.app.ui.volume

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shelfio.app.data.model.Loan
import com.shelfio.app.data.model.Volume
import com.shelfio.app.ui.AppViewModelProvider
import com.shelfio.app.ui.components.ConfirmDialog
import com.shelfio.app.ui.components.CoverPhotoButtons
import com.shelfio.app.ui.components.LoadingBox
import com.shelfio.app.ui.components.LoanLabel
import com.shelfio.app.ui.components.LocationLabel
import com.shelfio.app.ui.components.LocalShowMessage
import com.shelfio.app.ui.components.ScreenWithBack
import com.shelfio.app.ui.components.VolumeCover
import com.shelfio.app.ui.series.FormField
import com.shelfio.app.ui.series.LocationField
import com.shelfio.app.ui.series.OwnedSwitch
import com.shelfio.app.ui.series.PrioritySelector
import com.shelfio.app.ui.series.StatusSelector
import com.shelfio.app.ui.theme.TextSecondary

/** Crear («Añadir siguiente tomo») o editar un tomo. */
@Composable
fun VolumeEditScreen(
    onBack: () -> Unit,
    viewModel: VolumeEditViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val showMessage = LocalShowMessage.current
    var askDelete by rememberSaveable { mutableStateOf(false) }
    var lending by rememberSaveable { mutableStateOf(false) }
    val series = viewModel.series
    val volume = viewModel.volume
    val form = viewModel.form

    val title = when {
        viewModel.isNew -> "Nuevo tomo"
        viewModel.isSingleBook -> "Tu ejemplar"
        else -> "Tomo ${volume?.number ?: ""}"
    }

    ScreenWithBack(
        title = title,
        onBack = onBack,
        actions = {
            if (viewModel.canDelete) {
                IconButton(onClick = { askDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Borrar tomo") }
            }
        },
    ) {
        if (series == null || volume == null || form == null) {
            LoadingBox()
            return@ScreenWithBack
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                VolumeCover(series.series, volume, Modifier.width(96.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(series.series.title, style = MaterialTheme.typography.titleLarge)
                    if (series.series.author.isNotBlank()) Text(series.series.author, color = TextSecondary)
                }
            }
            if (viewModel.isNew) {
                Text("Los datos comunes (autor, editorial…) se copian de la serie. Cambia solo lo que sea distinto.", color = TextSecondary)
            } else {
                CoverPhotoButtons(
                    hasRealCover = volume.coverPath != null,
                    onPhotoTaken = { viewModel.onCoverPhotoTaken { ok -> if (!ok) showMessage("No se pudo guardar la foto.") } },
                    onRemoveCover = viewModel::removeCover,
                )
            }

            if (!viewModel.isSingleBook) {
                FormField("Número de tomo", form.number, { viewModel.form = form.copy(number = it.filter(Char::isDigit)) }, keyboardType = KeyboardType.Number)
                FormField("Título del tomo (opcional)", form.title, { viewModel.form = form.copy(title = it) })
            }
            FormField("ISBN", form.isbn, { viewModel.form = form.copy(isbn = it) }, keyboardType = KeyboardType.Number)
            OwnedSwitch(form.owned) { viewModel.form = form.copy(owned = it) }
            StatusSelector(form.status) { viewModel.form = form.copy(status = it) }
            if (form.owned) LocationField(form.location) { viewModel.form = form.copy(location = it) }
            if (!form.owned) {
                PrioritySelector(form.purchasePriority) { viewModel.form = form.copy(purchasePriority = it) }
                FormField("Nota de compra (ej.: edición con sobrecubierta)", form.purchaseNote, { viewModel.form = form.copy(purchaseNote = it) })
            }
            FormField("Notas", form.notes, { viewModel.form = form.copy(notes = it) }, singleLine = false)

            if (!viewModel.isNew) {
                LoanSection(
                    activeLoan = viewModel.activeLoan,
                    volume = volume,
                    onLend = { lending = true },
                    onReturn = { viewModel.returnLoan(); showMessage("Marcado como devuelto.") },
                )
            }
            viewModel.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = { viewModel.save(onBack) }, modifier = Modifier.fillMaxWidth()) {
                Text(if (viewModel.isNew) "Añadir tomo" else "Guardar cambios")
            }
        }
    }

    if (lending) {
        LendDialog(
            onConfirm = { person, dueAt ->
                lending = false
                viewModel.lend(person, dueAt, onError = showMessage)
            },
            onDismiss = { lending = false },
        )
    }

    if (askDelete) {
        ConfirmDialog(
            title = "¿Borrar el tomo ${volume?.number}?",
            message = "Se borrará solo este tomo. La serie y los demás tomos se quedan.",
            confirmLabel = "Borrar",
            onConfirm = { askDelete = false; viewModel.delete(onBack) },
            onDismiss = { askDelete = false },
        )
    }
}

/**
 * Préstamo del tomo: «Prestado a…» con su ubicación (para saber dónde devolverlo) y «Marcar como devuelto»,
 * o el botón «Prestar» si lo tengo y no está prestado.
 */
@Composable
private fun LoanSection(activeLoan: Loan?, volume: Volume, onLend: () -> Unit, onReturn: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Préstamo", style = MaterialTheme.typography.titleSmall)
        when {
            activeLoan != null -> {
                LoanLabel(activeLoan)
                Text(
                    "Desde el ${formatDay(activeLoan.lentAt)}" + (activeLoan.dueAt?.let { " · devolver antes del ${formatDay(it)}" } ?: ""),
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (volume.location.isNotBlank()) LocationLabel(volume.location)
                OutlinedButton(onClick = onReturn, modifier = Modifier.fillMaxWidth()) { Text("Marcar como devuelto") }
            }
            volume.owned -> OutlinedButton(onClick = onLend, modifier = Modifier.fillMaxWidth()) { Text("Prestar") }
            else -> Text("Solo puedes prestar tomos que tienes.", color = TextSecondary)
        }
    }
}
