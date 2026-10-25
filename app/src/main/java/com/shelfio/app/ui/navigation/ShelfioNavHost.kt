package com.shelfio.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.shelfio.app.data.model.ItemType
import com.shelfio.app.data.sample.SampleData
import com.shelfio.app.ui.screens.AddScreen
import com.shelfio.app.ui.screens.CollectionSummary
import com.shelfio.app.ui.screens.CollectionsScreen
import com.shelfio.app.ui.screens.HomeScreen
import com.shelfio.app.ui.screens.LibraryScreen
import com.shelfio.app.ui.screens.ProfileScreen
import kotlinx.coroutines.launch

object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val ADD = "add"
    const val COLLECTIONS = "collections"
    const val PROFILE = "profile"
}

/** Cambia de pestaña sin apilar pantallas repetidas. */
fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun ShelfioNavHost() {
    val navController = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = { scope.launch { snackbar.showSnackbar(it) } }

    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    // Fase 1: datos de ejemplo en memoria.
    val items = SampleData.library
    val collections = SampleData.collections.map { CollectionSummary(it, 0) }
    var userName by rememberSaveable { mutableStateOf("") }
    var libraryQuery by rememberSaveable { mutableStateOf("") }
    var libraryType by rememberSaveable { mutableStateOf<ItemType?>(null) }

    val openSeries: (Long) -> Unit = { showMessage("La ficha de la serie llegará en la Fase 3.") }
    val scan: () -> Unit = { showMessage("El escáner de ISBN llegará en la Fase 4.") }

    Scaffold(
        bottomBar = { ShelfioBottomBar(currentRoute) { navController.navigateToTab(it) } },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        NavHost(navController, startDestination = Routes.HOME, modifier = Modifier.padding(padding)) {
            composable(Routes.HOME) {
                HomeScreen(
                    items = items,
                    collectionsCount = collections.size,
                    userName = userName,
                    onSearch = { libraryQuery = it; libraryType = null; navController.navigateToTab(Routes.LIBRARY) },
                    onOpenSeries = openSeries,
                    onOpenLibrary = { libraryType = it; navController.navigateToTab(Routes.LIBRARY) },
                    onOpenCollections = { navController.navigateToTab(Routes.COLLECTIONS) },
                    onAdd = { navController.navigateToTab(Routes.ADD) },
                    onScan = scan,
                )
            }
            composable(Routes.LIBRARY) {
                LibraryScreen(
                    items = items,
                    query = libraryQuery,
                    onQueryChange = { libraryQuery = it },
                    typeFilter = libraryType,
                    onTypeFilterChange = { libraryType = it },
                    onOpenSeries = openSeries,
                    onAdd = { navController.navigateToTab(Routes.ADD) },
                )
            }
            composable(Routes.ADD) {
                AddScreen(
                    onScan = scan,
                    onSave = { showMessage("Todavía no se guarda: el almacenamiento llega en la Fase 2.") },
                )
            }
            composable(Routes.COLLECTIONS) {
                CollectionsScreen(
                    collections = collections,
                    onCreate = { showMessage("Crear colecciones llegará en la Fase 3.") },
                    onOpen = { showMessage("El detalle de colección llegará en la Fase 3.") },
                )
            }
            composable(Routes.PROFILE) {
                ProfileScreen(items, collections.size, userName, onUserNameChange = { userName = it })
            }
        }
    }
}
