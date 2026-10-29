package com.shelfio.app.ui.series

import com.shelfio.app.ui.theme.TextSecondary
import com.shelfio.app.data.statusForStep
import com.shelfio.app.data.readStep
import kotlin.math.roundToInt
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Slider
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
    val readLevel: Int = 0,
    val location: String = "",
    /** Para meter en la lista de deseos una serie que aún no tengo: crea también los tomos hasta este número. */
    val wishlistUpTo: String = "",
    // Estado editorial (solo al editar)
    val statusInMyLanguage: PublicationStatus = PublicationStatus.UNKNOWN,
    val statusInOrigin: PublicationStatus = PublicationStatus.UNKNOWN,
    val publishedInMyLanguage: String = "",
    /** Otros títulos de la obra (otros idiomas, original…), uno por línea. */
    val otherTitles: String = "",
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
        otherTitles = otherTitles.lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString("\n"),
    )

    /** Primer tomo de una serie nueva (el id de la serie lo pone el repositorio). */
    fun toFirstVolume(): Volume = Volume(
        seriesId = 0,
        number = volumeNumber.toIntOrNull() ?: 1,
        isbn = isbn.trim(),
        owned = owned,
        status = status,
        readLevel = readLevel,
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
            otherTitles = series.otherTitles,
        )
    }
}


/** Campos de la ficha de una serie, compartidos por Añadir y Editar. */
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
        FormField("Otros títulos (Opcional, uno por línea)", state.otherTitles, { onChange(state.copy(otherTitles = it)) }, singleLine = false)
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
                if (state.type.isSingleItem) "Tu ejemplar" else "Primer ${state.type.unit}",
                style = MaterialTheme.typography.titleMedium,
            )
            if (!state.type.isSingleItem) {
                FormField("Número de ${state.type.unit} (Obligatorio)", state.volumeNumber, { onChange(state.copy(volumeNumber = it.filter(Char::isDigit))) }, keyboardType = KeyboardType.Number)
            }
            IsbnField(state.isbn, { onChange(state.copy(isbn = it)) })
            OwnedSwitch(state.owned) { onChange(state.copy(owned = it)) }
            ReadingBar(state.status, state.readLevel) { status, level -> onChange(state.copy(status = status, readLevel = level)) }
            if (state.owned) LocationField(state.location) { onChange(state.copy(location = it)) }
            if (!state.owned && !state.type.isSingleItem) {
                FormField("Me faltan hasta el ${state.type.unit} (Opcional)", state.wishlistUpTo, { onChange(state.copy(wishlistUpTo = it.filter(Char::isDigit))) }, keyboardType = KeyboardType.Number)
            }
        }
    }
}

/** Campo de texto con su etiqueta. */
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

/** Selector del tipo de obra. */
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

/** Interruptor «Lo tengo». */
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

/**
 * Barra de lectura de un tomo: Nada · Un cuarto · Mitad · Entero. Al moverla cambia el estado
 * (Sin leer, Leyendo o Leído) y el libro del deslizador se abre y se cierra. Pausado y Abandonado van aparte.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingBar(status: ReadingStatus, readLevel: Int, onChange: (ReadingStatus, Int) -> Unit) {
    val step = readStep(status, readLevel)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("¿Cuánto has leído?", color = TextSecondary, style = MaterialTheme.typography.labelLarge)
        Slider(
            value = step.toFloat(),
            onValueChange = { value ->
                val new = value.roundToInt()
                // Pausado o abandonado se mantiene mientras no se llegue a «nada» o a «entero».
                val newStatus = if (status in listOf(ReadingStatus.PAUSED, ReadingStatus.ABANDONED) && new in 1..2) status else statusForStep(new)
                if (new != step || newStatus != status) onChange(newStatus, new.coerceIn(1, 2))
            },
            valueRange = 0f..3f,
            steps = 2,
            thumb = {
                Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                    Icon(
                        // Cerrado sin empezar, medio abierto, abierto y cerrado otra vez al terminarlo.
                        when (step) {
                            1 -> Icons.Filled.AutoStories
                            2 -> Icons.AutoMirrored.Filled.MenuBook
                            else -> Icons.Filled.Book
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            },
            modifier = Modifier.semantics { stateDescription = READ_STEPS[step] },
        )
        Row(Modifier.fillMaxWidth()) {
            READ_STEPS.forEachIndexed { index, label ->
                Text(
                    label,
                    color = if (index == step) MaterialTheme.colorScheme.primary else TextSecondary,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = when (index) { 0 -> TextAlign.Start; 3 -> TextAlign.End; else -> TextAlign.Center },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(ReadingStatus.PAUSED, ReadingStatus.ABANDONED).forEach { other ->
                FilterChip(
                    selected = status == other,
                    // Tocarlo otra vez lo quita y vuelve a «Leyendo».
                    onClick = { onChange(if (status == other) ReadingStatus.READING else other, step.coerceIn(1, 2)) },
                    label = { Text(other.label) },
                )
            }
        }
    }
}

private val READ_STEPS = listOf("Nada", "Un cuarto", "Mitad", "Entero")

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
