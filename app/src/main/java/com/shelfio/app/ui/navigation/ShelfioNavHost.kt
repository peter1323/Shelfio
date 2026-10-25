package com.shelfio.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.shelfio.app.ui.AppViewModelProvider
import com.shelfio.app.ui.MainViewModel
import com.shelfio.app.ui.add.AddScreen
import com.shelfio.app.ui.collections.CollectionDetailScreen
import com.shelfio.app.ui.collections.CollectionsScreen
import com.shelfio.app.ui.components.LoadingBox
import com.shelfio.app.ui.components.LocalShowMessage
import com.shelfio.app.ui.home.HomeScreen
import com.shelfio.app.ui.library.LibraryScreen
import com.shelfio.app.ui.profile.ProfileScreen
import com.shelfio.app.ui.series.EditSeriesScreen
import com.shelfio.app.ui.series.SeriesDetailScreen
import com.shelfio.app.ui.volume.VolumeEditScreen
import kotlinx.coroutines.launch

/** Rutas de navegación. Las cinco primeras son las pestañas de la barra inferior. */
object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val ADD = "add"
    const val COLLECTIONS = "collections"
    const val PROFILE = "profile"

    const val ARG_SERIES_ID = "seriesId"
    const val ARG_VOLUME_ID = "volumeId"
    const val ARG_COLLECTION_ID = "collectionId"

    /** volumeId especial para crear un tomo nuevo («Añadir siguiente tomo»). */
    const val NEW_VOLUME = 0L

    const val SERIES = "series/{$ARG_SERIES_ID}"
    const val EDIT_SERIES = "series/{$ARG_SERIES_ID}/edit"
    const val VOLUME = "series/{$ARG_SERIES_ID}/volume/{$ARG_VOLUME_ID}"
    const val COLLECTION = "collection/{$ARG_COLLECTION_ID}"

    fun series(seriesId: Long) = "series/$seriesId"
    fun editSeries(seriesId: Long) = "series/$seriesId/edit"
    fun volume(seriesId: Long, volumeId: Long) = "series/$seriesId/volume/$volumeId"
    fun collection(collectionId: Long) = "collection/$collectionId"
}

/** Cambia de pestaña sin apilar pantallas repetidas. */
fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun longArg(name: String) = navArgument(name) { type = NavType.LongType }

/** Estructura de la app: barra inferior, avisos (snackbar) y todas las pantallas. */
@Composable
fun ShelfioNavHost() {
    val navController = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = { scope.launch { snackbar.showSnackbar(it) } }

    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    // Un solo MainViewModel para las pestañas principales.
    val main: MainViewModel = viewModel(factory = AppViewModelProvider.Factory)
    val library by main.library.collectAsStateWithLifecycle()
    val collections by main.collections.collectAsStateWithLifecycle()

    val openSeries: (Long) -> Unit = { navController.navigate(Routes.series(it)) }
    val openAdd = { navController.navigateToTab(Routes.ADD) }
    val scan: () -> Unit = { showMessage("El escáner de ISBN llegará en la Fase 4.") }
    val back: () -> Unit = { navController.popBackStack() }

    CompositionLocalProvider(LocalShowMessage provides showMessage) {
        Scaffold(
            bottomBar = { ShelfioBottomBar(currentRoute) { navController.navigateToTab(it) } },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            NavHost(navController, startDestination = Routes.HOME, modifier = Modifier.padding(padding)) {

                // --- Pestañas principales ---

                composable(Routes.HOME) {
                    HomeScreen(
                        items = library ?: return@composable LoadingBox(),
                        collectionsCount = collections?.size ?: 0,
                        userName = main.userName,
                        onSearch = { main.libraryQuery = it; main.libraryType = null; navController.navigateToTab(Routes.LIBRARY) },
                        onOpenSeries = openSeries,
                        onOpenLibrary = { main.libraryType = it; navController.navigateToTab(Routes.LIBRARY) },
                        onOpenCollections = { navController.navigateToTab(Routes.COLLECTIONS) },
                        onAdd = openAdd,
                        onScan = scan,
                    )
                }
                composable(Routes.LIBRARY) {
                    LibraryScreen(
                        items = library ?: return@composable LoadingBox(),
                        query = main.libraryQuery,
                        onQueryChange = { main.libraryQuery = it },
                        typeFilter = main.libraryType,
                        onTypeFilterChange = { main.libraryType = it },
                        onOpenSeries = openSeries,
                        onAdd = openAdd,
                    )
                }
                composable(Routes.ADD) {
                    AddScreen(
                        onScan = scan,
                        onSaved = { seriesId -> showMessage("Guardado en tu biblioteca."); openSeries(seriesId) },
                    )
                }
                composable(Routes.COLLECTIONS) {
                    CollectionsScreen(
                        collections = collections ?: return@composable LoadingBox(),
                        onCreate = main::createCollection,
                        onOpen = { navController.navigate(Routes.collection(it)) },
                    )
                }
                composable(Routes.PROFILE) {
                    ProfileScreen(
                        items = library ?: return@composable LoadingBox(),
                        collectionsCount = collections?.size ?: 0,
                        userName = main.userName,
                        onUserNameChange = main::updateUserName,
                    )
                }

                // --- Pantallas de detalle ---

                composable(Routes.SERIES, arguments = listOf(longArg(Routes.ARG_SERIES_ID))) {
                    SeriesDetailScreen(
                        onBack = back,
                        onEditSeries = { navController.navigate(Routes.editSeries(it)) },
                        onOpenVolume = { seriesId, volumeId -> navController.navigate(Routes.volume(seriesId, volumeId)) },
                        onAddNextVolume = { navController.navigate(Routes.volume(it, Routes.NEW_VOLUME)) },
                    )
                }
                composable(Routes.EDIT_SERIES, arguments = listOf(longArg(Routes.ARG_SERIES_ID))) {
                    EditSeriesScreen(
                        onBack = back,
                        onDeleted = { showMessage("Serie borrada."); back() },
                    )
                }
                composable(Routes.VOLUME, arguments = listOf(longArg(Routes.ARG_SERIES_ID), longArg(Routes.ARG_VOLUME_ID))) {
                    VolumeEditScreen(onBack = back)
                }
                composable(Routes.COLLECTION, arguments = listOf(longArg(Routes.ARG_COLLECTION_ID))) {
                    CollectionDetailScreen(onBack = back, onOpenSeries = openSeries)
                }
            }
        }
    }
}
