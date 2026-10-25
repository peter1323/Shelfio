package com.shelfio.app.ui.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.shelfio.app.BuildConfig
import com.shelfio.app.R
import com.shelfio.app.data.countByType
import com.shelfio.app.data.model.ItemType
import com.shelfio.app.data.model.ReadingStatus
import com.shelfio.app.data.model.SeriesWithVolumes
import com.shelfio.app.ui.components.CounterCard
import com.shelfio.app.ui.theme.ShelfioBlue
import com.shelfio.app.ui.theme.ShelfioCyan
import com.shelfio.app.ui.theme.ShelfioViolet
import com.shelfio.app.ui.theme.TextSecondary

/** Perfil: estadísticas de la colección y ajustes. Shelfio no tiene cuentas de usuario. */
@Composable
fun ProfileScreen(
    items: List<SeriesWithVolumes>,
    collectionsCount: Int,
    userName: String,
    onUserNameChange: (String) -> Unit,
    onOpenWishlist: () -> Unit,
) {
    val volumes = items.flatMap { it.volumes }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Perfil", style = MaterialTheme.typography.headlineMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CounterCard(Icons.AutoMirrored.Filled.MenuBook, "Libros", countByType(items, ItemType.BOOK), ShelfioBlue, Modifier.weight(1f))
            CounterCard(Icons.Filled.AutoStories, "Mangas", countByType(items, ItemType.MANGA), ShelfioViolet, Modifier.weight(1f))
            CounterCard(Icons.Filled.ChatBubble, "Cómics", countByType(items, ItemType.COMIC), ShelfioCyan, Modifier.weight(1f))
            CounterCard(Icons.Filled.CollectionsBookmark, "Colecciones", collectionsCount, ShelfioViolet, Modifier.weight(1f))
        }

        ProfileCard("Mis listas") {
            OutlinedButton(onClick = onOpenWishlist, modifier = Modifier.fillMaxWidth()) { Text("Lista de deseos") }
        }

        ProfileCard("Estadísticas") {
            StatLine("Series y libros", items.size)
            StatLine("Tomos registrados", volumes.size)
            StatLine("Tomos que tengo", volumes.count { it.owned })
            StatLine("Pendientes de compra", volumes.count { !it.owned })
            StatLine("Leídos", volumes.count { it.status == ReadingStatus.READ })
            StatLine("Leyendo", volumes.count { it.status == ReadingStatus.READING })
            StatLine("Con ubicación guardada", volumes.count { it.owned && it.location.isNotBlank() })
        }

        ProfileCard("Ajustes") {
            OutlinedTextField(
                value = userName,
                onValueChange = onUserNameChange,
                label = { Text("Tu nombre (para el saludo de Inicio)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Tus datos se guardan solo en este móvil. Shelfio no tiene cuentas ni servidor.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
            BackupSection()
        }

        ProfileCard("Acerca de") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Logotipo aprobado (res/drawable/logo_shelfio.png)
                Image(painterResource(R.drawable.logo_shelfio), contentDescription = "Logotipo de Shelfio", modifier = Modifier.size(64.dp))
                Column {
                    Text("Shelfio ${BuildConfig.VERSION_NAME}")
                    Text("Toda tu colección, en un solo lugar.", color = TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun ProfileCard(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
private fun StatLine(label: String, value: Int) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = TextSecondary, modifier = Modifier.weight(1f))
        Text(value.toString(), style = MaterialTheme.typography.titleSmall)
    }
}
