package com.shelfio.app.ui.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shelfio.app.data.books.BookResult
import com.shelfio.app.data.model.ItemType
import com.shelfio.app.ui.AppViewModelProvider
import com.shelfio.app.ui.components.CardRow
import com.shelfio.app.ui.components.RemoteCover
import com.shelfio.app.ui.components.SearchField
import com.shelfio.app.ui.series.SeriesFormFields
import com.shelfio.app.ui.theme.TextSecondary

/**
 * Pantalla Añadir para un tipo concreto: buscar por título (rellena la ficha) o escribir los datos a mano.
 * El tipo se eligió antes, en la ventana «¿Qué quieres añadir?»; decide dónde se busca y es el de la ficha.
 */
@Composable
fun AddScreen(
    onSaved: (seriesId: Long) -> Unit,
    viewModel: AddViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val form = viewModel.form
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Añadir: ${addLabel(viewModel.type).lowercase()}", style = MaterialTheme.typography.headlineMedium)


        // --- Buscador ---
        Text("Buscar por título", style = MaterialTheme.typography.titleMedium)
        SearchField(
            value = viewModel.query,
            onValueChange = { viewModel.query = it },
            placeholder = "Ej.: Culpa mía, Sword Art Online…",
            onSearch = viewModel::search,
        )
        Button(onClick = viewModel::search, enabled = viewModel.query.isNotBlank() && !viewModel.isSearching, modifier = Modifier.fillMaxWidth()) {
            Text("Buscar")
        }
        if (viewModel.isSearching) LinearProgressIndicator(Modifier.fillMaxWidth())
        viewModel.searchMessage?.let { Text(it, color = TextSecondary) }
        viewModel.results.forEach { result -> ResultRow(result, viewModel.type) { viewModel.choose(result) } }

        viewModel.chosen?.let { chosen -> ChosenInfo(chosen, form.title, viewModel) }

        // --- Ficha ---
        Text(if (viewModel.chosen == null) "O escríbelo a mano:" else "Revisa y corrige la ficha:", color = MaterialTheme.colorScheme.onSurfaceVariant)
        SeriesFormFields(form, { viewModel.form = it }, showFirstVolume = true, showType = false)
        Button(onClick = { viewModel.save(onSaved) }, enabled = form.isValid, modifier = Modifier.fillMaxWidth()) {
            Text("Guardar")
        }
    }
}

/** Un resultado del buscador: título, autor y de dónde viene. Al tocarlo rellena la ficha. */
@Composable
private fun ResultRow(result: BookResult, type: ItemType, onClick: () -> Unit) {
    CardRow(onClick) {
        RemoteCover(result.coverUrl, result.title, type, Modifier.width(56.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(result.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (result.authors.isNotBlank()) Text(result.authors, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOf(result.kind, "Fuente: ${result.source}").filter { it.isNotBlank() }.joinToString(" · "), color = TextSecondary, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/**
 * Desplegable de títulos. Con [closeOnClick] elige uno y se cierra (título principal);
 * sin él cada toque marca o desmarca y sigue abierto para elegir varios (otros títulos).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TitleDropdown(
    label: String,
    summary: String,
    titles: List<String>,
    isSelected: (String) -> Boolean,
    onClick: (String) -> Unit,
    closeOnClick: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = summary,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            titles.forEach { title ->
                DropdownMenuItem(
                    text = { Text(title) },
                    leadingIcon = {
                        if (closeOnClick) RadioButton(selected = isSelected(title), onClick = null)
                        else Checkbox(checked = isSelected(title), onCheckedChange = null)
                    },
                    onClick = { onClick(title); if (closeOnClick) expanded = false },
                )
            }
        }
    }
}

/** Tras elegir un resultado: otros títulos para escoger (AniList) y si se guarda su portada. */
@Composable
private fun ChosenInfo(chosen: BookResult, currentTitle: String, viewModel: AddViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Ficha rellenada con datos de ${chosen.source}.", color = TextSecondary)
        if (chosen.alternativeTitles.size > 1) {
            // Desplegables para que una lista larga de títulos no ocupe media pantalla.
            TitleDropdown(
                label = "Título principal (Obligatorio, elige uno)",
                summary = currentTitle,
                titles = chosen.alternativeTitles,
                isSelected = { it == currentTitle },
                onClick = { viewModel.chooseMainTitle(it) },
                closeOnClick = true,
            )
            // Además del principal se pueden guardar otros (inglés, original…). Tocar uno lo añade o lo quita.
            val others = viewModel.form.otherTitles.lines().filter { it.isNotBlank() }
            TitleDropdown(
                label = "Otros títulos (Opcional, puedes elegir varios)",
                summary = if (others.isEmpty()) "Ninguno" else "${others.size} elegidos: ${others.joinToString(", ")}",
                titles = chosen.alternativeTitles.filter { it != currentTitle },
                isSelected = { it in others },
                onClick = { viewModel.toggleOtherTitle(it) },
                closeOnClick = false,
            )
        }
        if (chosen.coverUrl != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = viewModel.useCover, onCheckedChange = { viewModel.useCover = it })
                Text("Guardar su portada")
            }
        }
    }
}
