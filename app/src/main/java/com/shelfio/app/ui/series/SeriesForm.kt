package com.shelfio.app.ui.series

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.shelfio.app.data.model.ItemType
import com.shelfio.app.data.model.PublicationStatus
import com.shelfio.app.data.model.PurchasePriority
import com.shelfio.app.data.model.ReadingStatus
import com.shelfio.app.data.model.Series
import com.shelfio.app.data.model.Volume
import com.shelfio.app.ui.scan.IsbnField

/** Datos que se escriben en el formulario de serie (y de su primer tomo al crearla). */
data class SeriesFormState(
    val type: ItemType = ItemType.BOOK,
    val title: String = "",
    val author: String = "",
    val publisher: String = "",
    val language: String = "",
    val description: String = "",
    val notes: String = "",
    // Primer tomo (solo al crear)
    val volumeNumber: String = "1",
    val isbn: String = "",
    val owned: Boolean = true,
    val status: ReadingStatus = ReadingStatus.UNREAD,
    val location: String = "",
    /** Para meter en la lista de deseos una serie que aún no tengo: crea también los tomos hasta este número. */
    val wishlistUpTo: String = "",
    // Estado editorial (solo al editar)
    val statusInMyLanguage: PublicationStatus = PublicationStatus.UNKNOWN,
    val statusInOrigin: PublicationStatus = PublicationStatus.UNKNOWN,
    val publishedInMyLanguage: String = "",
) {
    val isValid: Boolean get() = title.isNotBlank() && (volumeNumber.toIntOrNull() ?: 0) > 0

    /** Pasa el formulario a una serie. Con [base] conserva id, portada y fechas de la serie que se edita. */
    fun toSeries(base: Series? = null): Series = (base ?: Series(title = "", type = type)).copy(
        type = type,
        title = title.trim(),
        author = author.trim(),
        publisher = publisher.trim(),
        language = language.trim(),
        description = description.trim(),
        notes = notes.trim(),
        statusInMyLanguage = statusInMyLanguage,
        statusInOrigin = statusInOrigin,
        publishedInMyLanguage = publishedInMyLanguage.toIntOrNull()?.takeIf { it > 0 },
    )

    /** Primer tomo de una serie nueva (el id de la serie lo pone el repositorio). */
    fun toFirstVolume(): Volume = Volume(
        seriesId = 0,
        number = volumeNumber.toIntOrNull() ?: 1,
        isbn = isbn.trim(),
        owned = owned,
        status = status,
        location = location.trim(),
    )

    companion object {
        fun fromSeries(series: Series) = SeriesFormState(
            type = series.type,
            title = series.title,
            author = series.author,
            publisher = series.publisher,
            language = series.language,
            description = series.description,
            notes = series.notes,
            statusInMyLanguage = series.statusInMyLanguage,
            statusInOrigin = series.statusInOrigin,
            publishedInMyLanguage = series.publishedInMyLanguage?.toString().orEmpty(),
        )
    }
}


@Composable
fun SeriesFormFields(
    state: SeriesFormState,
    onChange: (SeriesFormState) -> Unit,
    showFirstVolume: Boolean,
    modifier: Modifier = Modifier,
    /** false cuando la pantalla ya pone el selector de tipo arriba (Añadir). */
    showType: Boolean = true,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showType) TypeSelector(state.type) { onChange(state.copy(type = it)) }
        FormField("Título (Obligatorio)", state.title, { onChange(state.copy(title = it)) })
        FormField("Autor (Opcional)", state.author, { onChange(state.copy(author = it)) })
        FormField("Editorial (Opcional)", state.publisher, { onChange(state.copy(publisher = it)) })
        FormField("Idioma (Opcional)", state.language, { onChange(state.copy(language = it)) })
        FormField("Descripción (Opcional)", state.description, { onChange(state.copy(description = it)) }, singleLine = false)
        FormField("Notas personales (Opcional)", state.notes, { onChange(state.copy(notes = it)) }, singleLine = false)

        if (!showFirstVolume) {
            // Estado editorial: solo al editar una serie y siempre a mano.
            Text("Estado editorial", style = MaterialTheme.typography.titleMedium)
            PublicationStatusSelector("En mi idioma", state.statusInMyLanguage) { onChange(state.copy(statusInMyLanguage = it)) }
            PublicationStatusSelector("En su país de origen", state.statusInOrigin) { onChange(state.copy(statusInOrigin = it)) }
            FormField("Tomos publicados en mi idioma (Opcional)", state.publishedInMyLanguage, { onChange(state.copy(publishedInMyLanguage = it.filter(Char::isDigit))) }, keyboardType = KeyboardType.Number)
        }

        if (showFirstVolume) {
            Text(
                if (state.type.isSingleItem) "Tu ejemplar" else "Primer tomo",
                style = MaterialTheme.typography.titleMedium,
            )
            if (!state.type.isSingleItem) {
                FormField("Número de tomo (Obligatorio)", state.volumeNumber, { onChange(state.copy(volumeNumber = it.filter(Char::isDigit))) }, keyboardType = KeyboardType.Number)
            }
            IsbnField(state.isbn, { onChange(state.copy(isbn = it)) })
            OwnedSwitch(state.owned) { onChange(state.copy(owned = it)) }
            StatusSelector(state.status) { onChange(state.copy(status = it)) }
            if (state.owned) LocationField(state.location) { onChange(state.copy(location = it)) }
            if (!state.owned && !state.type.isSingleItem) {
                FormField("Me faltan hasta el tomo (Opcional)", state.wishlistUpTo, { onChange(state.copy(wishlistUpTo = it.filter(Char::isDigit))) }, keyboardType = KeyboardType.Number)
            }
        }
    }
}

@Composable
fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun TypeSelector(selected: ItemType, onSelect: (ItemType) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        ItemType.entries.forEachIndexed { index, type ->
            SegmentedButton(
                selected = selected == type,
                onClick = { onSelect(type) },
                shape = SegmentedButtonDefaults.itemShape(index, ItemType.entries.size),
            ) { Text(type.label) }
        }
    }
}

@Composable
fun OwnedSwitch(owned: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Lo tengo")
            Text(
                if (owned) "Está en tu estantería" else "Pendiente de compra",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = owned, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusSelector(selected: ReadingStatus, onSelect: (ReadingStatus) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Estado de lectura") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ReadingStatus.entries.forEach { status ->
                DropdownMenuItem(text = { Text(status.label) }, onClick = { onSelect(status); expanded = false })
            }
        }
    }
}

/**
 * «Dónde lo tengo»: sitio físico del tomo, en texto libre. Opcional y solo para tomos que tengo.
 * No es una colección: la colección agrupa series; la ubicación dice dónde está guardado el libro.
 */
@Composable
fun LocationField(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("Dónde lo tengo (Opcional)") },
        placeholder = { Text("Ej.: Caja 3, Estante de arriba…") },
        leadingIcon = { Icon(Icons.Filled.Place, contentDescription = null) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Prioridad de compra para la lista de deseos: sin prioridad, alta, media o baja. */
@Composable
fun PrioritySelector(selected: PurchasePriority?, onSelect: (PurchasePriority?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Prioridad de compra", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("Sin prioridad") })
            PurchasePriority.entries.forEach { priority ->
                FilterChip(selected = selected == priority, onClick = { onSelect(priority) }, label = { Text(priority.label) })
            }
        }
    }
}

/** Desplegable de estado de publicación (en publicación, finalizada…, desconocido). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicationStatusSelector(label: String, selected: PublicationStatus, onSelect: (PublicationStatus) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            PublicationStatus.entries.forEach { status ->
                DropdownMenuItem(text = { Text(status.label) }, onClick = { onSelect(status); expanded = false })
            }
        }
    }
}
