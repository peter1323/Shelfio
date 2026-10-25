package com.shelfio.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shelfio.app.BuildConfig

// Segunda pantalla de la Fase 0: sirve para comprobar que Navigation Compose funciona.
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Shelfio", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Versión ${BuildConfig.VERSION_NAME} · Fase 0",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Proyecto base con Kotlin, Jetpack Compose y Material 3. " +
                "La biblioteca, el escáner y el resto de funciones llegarán en las siguientes fases.",
            style = MaterialTheme.typography.bodyLarge,
        )
        OutlinedButton(onClick = onBack) { Text("Volver") }
    }
}
