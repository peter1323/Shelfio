package com.shelfio.app.ui.add

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.shelfio.app.data.model.ItemType
import com.shelfio.app.ui.components.typeColor
import com.shelfio.app.ui.theme.TextSecondary

/** Nombre del tipo tal como se ofrece al añadir. */
fun addLabel(type: ItemType): String = when (type) {
    ItemType.BOOK -> "Libro / novela"
    ItemType.MANGA -> "Lecturas asiáticas"
    ItemType.COMIC -> "Cómic"
    ItemType.OTHER -> "Otro"
}

/** Aclaración bajo el nombre, solo donde hace falta. */
private fun addHint(type: ItemType): String? = when (type) {
    ItemType.MANGA -> "Japonés, coreano o chino (manga, manhwa, manhua y novela ligera)."
    ItemType.OTHER -> "Revistas, artbooks, guías… Luego puedes cambiarlo de tipo."
    else -> null
}

private fun icon(type: ItemType): ImageVector = when (type) {
    ItemType.BOOK -> Icons.AutoMirrored.Filled.MenuBook
    ItemType.MANGA -> Icons.Filled.AutoStories
    ItemType.COMIC -> Icons.Filled.ChatBubble
    ItemType.OTHER -> Icons.Filled.Category
}

/** Ventana emergente «¿Qué quieres añadir?» que abren el «+» y los botones «Añadir». */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTypeSheet(onChoose: (ItemType) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text("¿Qué quieres añadir?", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            ItemType.entries.forEach { type ->
                Row(
                    Modifier.fillMaxWidth().clickable { onChoose(type) }.padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(icon(type), contentDescription = null, tint = typeColor(type), modifier = Modifier.size(28.dp))
                    Column(Modifier.padding(start = 16.dp)) {
                        Text(addLabel(type), style = MaterialTheme.typography.titleMedium)
                        addHint(type)?.let { Text(it, color = TextSecondary, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
}
