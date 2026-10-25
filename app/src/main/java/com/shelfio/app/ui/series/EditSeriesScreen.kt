package com.shelfio.app.ui.series

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shelfio.app.ui.AppViewModelProvider
import com.shelfio.app.ui.components.ConfirmDialog
import com.shelfio.app.ui.components.LoadingBox
import com.shelfio.app.ui.components.ScreenWithBack

/** Editar los datos comunes de una serie, o borrarla (con confirmación). */
@Composable
fun EditSeriesScreen(
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: EditSeriesViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    var askDelete by rememberSaveable { mutableStateOf(false) }
    val form = viewModel.form

    ScreenWithBack(
        title = "Editar",
        onBack = onBack,
        actions = {
            IconButton(onClick = { askDelete = true }, enabled = form != null) {
                Icon(Icons.Filled.Delete, contentDescription = "Borrar")
            }
        },
    ) {
        if (form == null) {
            LoadingBox()
            return@ScreenWithBack
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SeriesFormFields(form, { viewModel.form = it }, showFirstVolume = false)
            Button(onClick = { viewModel.save(onBack) }, enabled = form.isValid, modifier = Modifier.fillMaxWidth()) {
                Text("Guardar cambios")
            }
        }
    }

    if (askDelete) {
        ConfirmDialog(
            title = "¿Borrar «${form?.title}»?",
            message = "Se borrarán la serie y todos sus tomos. No se puede deshacer.",
            confirmLabel = "Borrar",
            onConfirm = { askDelete = false; viewModel.delete(onDeleted) },
            onDismiss = { askDelete = false },
        )
    }
}
