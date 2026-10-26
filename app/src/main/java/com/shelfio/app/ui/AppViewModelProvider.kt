package com.shelfio.app.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.shelfio.app.ShelfioApplication
import com.shelfio.app.ui.add.AddViewModel
import com.shelfio.app.ui.collections.CollectionDetailViewModel
import com.shelfio.app.ui.profile.BackupViewModel
import com.shelfio.app.ui.profile.ReportViewModel
import com.shelfio.app.ui.series.EditSeriesViewModel
import com.shelfio.app.ui.series.SeriesDetailViewModel
import com.shelfio.app.ui.volume.VolumeEditViewModel
import com.shelfio.app.ui.wishlist.WishlistViewModel

/**
 * Fábrica única de ViewModels: les pasa los repositorios de [ShelfioApplication]
 * y, a los que lo necesitan, los argumentos de la ruta (SavedStateHandle).
 * Uso en una pantalla: `viewModel<MiViewModel>(factory = AppViewModelProvider.Factory)`.
 */
object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer { MainViewModel(app().repository, app().settings) }
        initializer { AddViewModel(createSavedStateHandle(), app().repository, app().bookLookup, app().covers) }
        initializer { EditSeriesViewModel(createSavedStateHandle(), app().repository) }
        initializer { SeriesDetailViewModel(createSavedStateHandle(), app().repository, app().covers) }
        initializer { VolumeEditViewModel(createSavedStateHandle(), app().repository, app().covers) }
        initializer { CollectionDetailViewModel(createSavedStateHandle(), app().repository) }
        initializer { BackupViewModel(app().backup, app().settings) }
        initializer { WishlistViewModel(app().repository) }
        initializer { ReportViewModel(app().repository, app().pdfReport) }
    }
}

private fun CreationExtras.app(): ShelfioApplication = this[APPLICATION_KEY] as ShelfioApplication
