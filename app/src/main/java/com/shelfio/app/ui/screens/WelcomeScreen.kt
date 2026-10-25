package com.shelfio.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shelfio.app.R
import com.shelfio.app.ui.theme.ShelfioTheme

// Pantalla mínima de la Fase 0: comprueba que la app arranca con Compose y el tema de Shelfio.
@Composable
fun WelcomeScreen(onAboutClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.logo_shelfio),
            contentDescription = "Logotipo de Shelfio",
            modifier = Modifier.size(180.dp),
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Toda tu colección, en un solo lugar.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(40.dp))
        Button(onClick = onAboutClick, modifier = Modifier.fillMaxWidth()) {
            Text("Acerca de esta versión")
        }
    }
}

@Preview
@Composable
private fun WelcomeScreenPreview() {
    ShelfioTheme { WelcomeScreen(onAboutClick = {}) }
}
