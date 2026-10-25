package com.shelfio.app.ui.volume

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.shelfio.app.ui.theme.TextSecondary
import java.text.DateFormat
import java.util.Date
import java.util.TimeZone

/** Fecha de un selector de calendario (medianoche UTC) como texto corto, sin que la zona horaria cambie el día. */
fun formatDay(utcMillis: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(utcMillis))

/** «Prestar»: a quién (solo nombre o apodo) y, si se quiere, cuándo debería devolverlo. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LendDialog(onConfirm: (person: String, dueAt: Long?) -> Unit, onDismiss: () -> Unit) {
    var person by rememberSaveable { mutableStateOf("") }
    var dueAt by rememberSaveable { mutableStateOf<Long?>(null) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Prestar") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = person, onValueChange = { person = it }, label = { Text("¿A quién? (nombre o apodo)") }, singleLine = true)
                OutlinedButton(onClick = { pickingDate = true }) {
                    Text(dueAt?.let { "Devolver antes del ${formatDay(it)}" } ?: "Fecha de devolución (opcional)")
                }
                Text("Solo se guarda el nombre. Shelfio no accede a tus contactos.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(person.trim(), dueAt) }, enabled = person.isNotBlank()) { Text("Prestar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )

    if (pickingDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = dueAt)
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = { TextButton(onClick = { dueAt = state.selectedDateMillis; pickingDate = false }) { Text("Aceptar") } },
            dismissButton = { TextButton(onClick = { dueAt = null; pickingDate = false }) { Text("Sin fecha") } },
        ) { DatePicker(state) }
    }
}
