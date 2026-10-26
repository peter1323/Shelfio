package com.shelfio.app.ui.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.window.Dialog
import com.shelfio.app.ui.components.LocalShowMessage
import com.shelfio.app.ui.components.rememberCameraPermissionRequest
import com.shelfio.app.ui.theme.TextSecondary

/**
 * Campo ISBN con un botón de cámara: abre el lector de código de barras y escribe el ISBN leído en el campo.
 * El permiso de cámara se pide solo al pulsar el botón.
 */
@Composable
fun IsbnField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val showMessage = LocalShowMessage.current
    var scanning by rememberSaveable { mutableStateOf(false) }
    val openCamera = rememberCameraPermissionRequest { granted ->
        if (granted) scanning = true else showMessage("Sin permiso de cámara no se puede leer el código. Escribe el ISBN a mano.")
    }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("ISBN (Opcional)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        trailingIcon = {
            IconButton(onClick = openCamera) { Icon(Icons.Filled.PhotoCamera, contentDescription = "Leer el ISBN con la cámara") }
        },
        modifier = modifier.fillMaxWidth(),
    )

    if (scanning) {
        Dialog(onDismissRequest = { scanning = false }) {
            Column(
                Modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Leer ISBN", style = MaterialTheme.typography.titleMedium)
                Text("Apunta al código de barras de la contraportada.", color = TextSecondary)
                BarcodeScanner(
                    // El primer ISBN válido se escribe en el campo y se cierra la cámara.
                    onIsbn = { isbn -> if (scanning) { onValueChange(isbn); scanning = false } },
                    modifier = Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(16.dp)),
                )
                OutlinedButton(onClick = { scanning = false }, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
            }
        }
    }
}
