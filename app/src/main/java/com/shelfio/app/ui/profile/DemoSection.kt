package com.shelfio.app.ui.profile

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shelfio.app.data.DemoData
import com.shelfio.app.ui.AppViewModelProvider
import com.shelfio.app.ui.components.LocalShowMessage
import com.shelfio.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/** Cargar y borrar la biblioteca de ejemplo. Perfil solo lo enseña en la versión de pruebas. */
class DemoViewModel(private val demo: DemoData) : ViewModel() {
    var isWorking by mutableStateOf(false)
        private set

    fun load(onMessage: (String) -> Unit) = work {
        val added = demo.load()
        onMessage(if (added == 0) "Los datos de prueba ya están cargados." else "Cargados $added ejemplos en el estante «${DemoData.DEMO_COLLECTION}».")
    }

    fun remove(onMessage: (String) -> Unit) = work {
        val removed = demo.remove()
        onMessage(if (removed == 0) "No hay datos de prueba." else "Borrados $removed ejemplos.")
    }

    private fun work(task: suspend () -> Unit) {
        if (isWorking) return
        isWorking = true
        viewModelScope.launch {
            try { task() } finally { isWorking = false }
        }
    }
}

@Composable
fun DemoSection(viewModel: DemoViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val showMessage = LocalShowMessage.current
    Text(
        "Ejemplos de libros, lecturas asiáticas, cómics y otros con préstamos, lista de deseos y estados distintos. " +
            "Van al estante «${DemoData.DEMO_COLLECTION}»; tus datos no se tocan.",
        color = TextSecondary,
        style = MaterialTheme.typography.bodySmall,
    )
    OutlinedButton(onClick = { viewModel.load(showMessage) }, enabled = !viewModel.isWorking, modifier = Modifier.fillMaxWidth()) {
        Text("Cargar datos de prueba")
    }
    OutlinedButton(onClick = { viewModel.remove(showMessage) }, enabled = !viewModel.isWorking, modifier = Modifier.fillMaxWidth()) {
        Text("Borrar datos de prueba")
    }
}
