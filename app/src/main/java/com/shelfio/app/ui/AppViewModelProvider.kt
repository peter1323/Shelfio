package com.shelfio.app.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.shelfio.app.ShelfioApplication
import com.shelfio.app.ui.add.AddViewModel
import com.shelfio.app.ui.series.EditSeriesViewModel

/**
 * Fábrica única de ViewModels: les pasa los repositorios de [ShelfioApplication].
 * Uso en una pantalla: `viewModel<MiViewModel>(factory = AppViewModelProvider.Factory)`.
 */
object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer { MainViewModel(app().repository, app().settings) }
        initializer { AddViewModel(app().repository) }
        initializer { EditSeriesViewModel(createSavedStateHandle(), app().repository) }
    }
}

private fun CreationExtras.app(): ShelfioApplication = this[APPLICATION_KEY] as ShelfioApplication
