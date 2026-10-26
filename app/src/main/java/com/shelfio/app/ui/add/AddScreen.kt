package com.shelfio.app.ui.add

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shelfio.app.data.books.BookResult
import com.shelfio.app.ui.AppViewModelProvider
import com.shelfio.app.ui.components.SearchField
import com.shelfio.app.ui.series.SeriesFormFields
import com.shelfio.app.ui.series.TypeSelector
import com.shelfio.app.ui.theme.TextSecondary

/**
 * Pantalla Añadir: escanear el ISBN, buscar por título (rellena la ficha) o escribir los datos a mano.
 * El tipo elegido arriba decide dónde se busca (AniList para manga y novela ligera) y es el de la ficha.
 */
@Composable
fun AddScreen(
    onScan: () -> Unit,
    onSaved: (seriesId: Long) -> Unit,
    viewModel: AddViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val form = viewModel.form
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Añadir a tu colección", style = MaterialTheme.typography.headlineMedium)
        FilledTonalButton(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
            Text("Escanear ISBN", Modifier.padding(start = 8.dp))
        }

        TypeSelector(form.type) { viewModel.form = form.copy(type = it) }

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
        viewModel.results.forEach { result -> ResultRow(result) { viewModel.choose(result) } }

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
private fun ResultRow(result: BookResult, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(result.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (result.authors.isNotBlank()) Text(result.authors, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("Fuente: ${result.source}", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
    }
}

/** Tras elegir un resultado: otros títulos para escoger (AniList) y si se guarda su portada. */
@Composable
private fun ChosenInfo(chosen: BookResult, currentTitle: String, viewModel: AddViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Ficha rellenada con datos de ${chosen.source}.", color = TextSecondary)
        if (chosen.alternativeTitles.size > 1) {
            Text("Elige el título que quieres guardar", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                chosen.alternativeTitles.forEach { title ->
                    FilterChip(selected = currentTitle == title, onClick = { viewModel.form = viewModel.form.copy(title = title) }, label = { Text(title) })
                }
            }
        }
        if (chosen.coverUrl != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = viewModel.useCover, onCheckedChange = { viewModel.useCover = it })
                Text("Guardar su portada")
            }
        }
    }
}
