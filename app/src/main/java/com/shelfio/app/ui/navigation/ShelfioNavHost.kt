package com.shelfio.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
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
import com.shelfio.app.ui.collections.CollectionsScreen
import com.shelfio.app.ui.components.LoadingBox
import com.shelfio.app.ui.home.HomeScreen
import com.shelfio.app.ui.library.LibraryScreen
import com.shelfio.app.ui.profile.ProfileScreen
import com.shelfio.app.ui.series.EditSeriesScreen
import kotlinx.coroutines.launch

/** Rutas de navegación. Las cinco primeras son las pestañas de la barra inferior. */
object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val ADD = "add"
    const val COLLECTIONS = "collections"
    const val PROFILE = "profile"

    const val ARG_SERIES_ID = "seriesId"
    const val EDIT_SERIES = "series/{$ARG_SERIES_ID}/edit"
    fun editSeries(seriesId: Long) = "series/$seriesId/edit"
}

/** Cambia de pestaña sin apilar pantallas repetidas. */
fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

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

    val openSeries: (Long) -> Unit = { navController.navigate(Routes.editSeries(it)) }
    val openAdd = { navController.navigateToTab(Routes.ADD) }
    val scan: () -> Unit = { showMessage("El escáner de ISBN llegará en la Fase 4.") }

    Scaffold(
        bottomBar = { ShelfioBottomBar(currentRoute) { navController.navigateToTab(it) } },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        NavHost(navController, startDestination = Routes.HOME, modifier = Modifier.padding(padding)) {
            composable(Routes.HOME) {
                val items = library ?: return@composable LoadingBox()
                HomeScreen(
                    items = items,
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
                val items = library ?: return@composable LoadingBox()
                LibraryScreen(
                    items = items,
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
                    onSaved = { showMessage("Guardado en tu biblioteca."); navController.navigateToTab(Routes.LIBRARY) },
                )
            }
            composable(Routes.COLLECTIONS) {
                CollectionsScreen(
                    collections = collections ?: return@composable LoadingBox(),
                    onCreate = { showMessage("Crear colecciones llegará en la Fase 3.") },
                    onOpen = { showMessage("El detalle de colección llegará en la Fase 3.") },
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
            composable(
                Routes.EDIT_SERIES,
                arguments = listOf(navArgument(Routes.ARG_SERIES_ID) { type = NavType.LongType }),
            ) {
                EditSeriesScreen(
                    onBack = { navController.popBackStack() },
                    onDeleted = { showMessage("Serie borrada."); navController.popBackStack() },
                )
            }
        }
    }
}
