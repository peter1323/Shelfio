package com.shelfio.app.ui.series

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.shelfio.app.data.model.ItemType
import com.shelfio.app.data.model.ReadingStatus
import com.shelfio.app.data.model.Series
import com.shelfio.app.data.model.Volume

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
    )

    /** Primer tomo de una serie nueva (el id de la serie lo pone el repositorio). */
    fun toFirstVolume(): Volume = Volume(
        seriesId = 0,
        number = volumeNumber.toIntOrNull() ?: 1,
        isbn = isbn.trim(),
        owned = owned,
        status = status,
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
        )
    }
}

/** Para que el formulario no se pierda al girar la pantalla. */
val SeriesFormSaver = listSaver<SeriesFormState, String>(
    save = {
        listOf(it.type.name, it.title, it.author, it.publisher, it.language, it.description, it.notes, it.volumeNumber, it.isbn, it.owned.toString(), it.status.name)
    },
    restore = {
        SeriesFormState(
            type = ItemType.valueOf(it[0]), title = it[1], author = it[2], publisher = it[3], language = it[4],
            description = it[5], notes = it[6], volumeNumber = it[7], isbn = it[8], owned = it[9].toBoolean(),
            status = ReadingStatus.valueOf(it[10]),
        )
    },
)

@Composable
fun SeriesFormFields(
    state: SeriesFormState,
    onChange: (SeriesFormState) -> Unit,
    showFirstVolume: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TypeSelector(state.type) { onChange(state.copy(type = it)) }
        FormField("Título *", state.title, { onChange(state.copy(title = it)) })
        FormField("Autor", state.author, { onChange(state.copy(author = it)) })
        FormField("Editorial", state.publisher, { onChange(state.copy(publisher = it)) })
        FormField("Idioma", state.language, { onChange(state.copy(language = it)) })
        FormField("Descripción", state.description, { onChange(state.copy(description = it)) }, singleLine = false)
        FormField("Notas personales", state.notes, { onChange(state.copy(notes = it)) }, singleLine = false)

        if (showFirstVolume) {
            Text(
                if (state.type == ItemType.BOOK) "Tu ejemplar" else "Primer tomo",
                style = MaterialTheme.typography.titleMedium,
            )
            if (state.type != ItemType.BOOK) {
                FormField("Número de tomo", state.volumeNumber, { onChange(state.copy(volumeNumber = it.filter(Char::isDigit))) }, keyboardType = KeyboardType.Number)
            }
            FormField("ISBN", state.isbn, { onChange(state.copy(isbn = it)) }, keyboardType = KeyboardType.Number)
            OwnedSwitch(state.owned) { onChange(state.copy(owned = it)) }
            StatusSelector(state.status) { onChange(state.copy(status = it)) }
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
