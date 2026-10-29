package com.shelfio.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.shelfio.app.data.model.ItemType
import com.shelfio.app.ui.AppViewModelProvider
import com.shelfio.app.ui.MainViewModel
import com.shelfio.app.ui.add.AddTypeSheet
import com.shelfio.app.ui.add.AddScreen
import com.shelfio.app.ui.charts.ChartsScreen
import com.shelfio.app.ui.collections.CollectionDetailScreen
import com.shelfio.app.ui.collections.CollectionsScreen
import com.shelfio.app.ui.components.LoadingBox
import com.shelfio.app.ui.components.LocalShowMessage
import com.shelfio.app.ui.home.HomeScreen
import com.shelfio.app.ui.library.LibraryScreen
import com.shelfio.app.ui.loans.LoansScreen
import com.shelfio.app.ui.profile.ProfileScreen
import com.shelfio.app.ui.series.EditSeriesScreen
import com.shelfio.app.ui.series.SeriesDetailScreen
import com.shelfio.app.ui.volume.VolumeEditScreen
import com.shelfio.app.ui.trophies.LocalTrophyStyle
import com.shelfio.app.ui.trophies.LocalUnlockedTrophies
import com.shelfio.app.ui.trophies.TrophiesScreen
import com.shelfio.app.ui.trophies.TrophyUnlockedDialog
import com.shelfio.app.ui.wishlist.WishlistScreen
import kotlinx.coroutines.launch

/** Rutas de navegación. HOME…CHARTS son las pestañas de la barra inferior; PROFILE se abre arriba a la derecha. */
object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val ADD = "add"
    const val COLLECTIONS = "collections"
    const val CHARTS = "charts"
    const val PROFILE = "profile"

    const val ARG_SERIES_ID = "seriesId"
    const val ARG_VOLUME_ID = "volumeId"
    const val ARG_COLLECTION_ID = "collectionId"
    const val ARG_TYPE = "type"
    const val ARG_SHELF = "shelf"

    /** Añadir siempre se abre para un tipo concreto, elegido en la ventana «¿Qué quieres añadir?». */
    /** [ARG_SHELF] (opcional): estante en el que meter lo que se guarde (desde «Añadir» dentro de un estante). */
    const val ADD_FOR_TYPE = "$ADD/{$ARG_TYPE}?$ARG_SHELF={$ARG_SHELF}"
    const val WISHLIST = "wishlist"
    const val LOANS = "loans"
    const val TROPHIES = "trophies"

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
    fun add(type: ItemType, shelfId: Long? = null) = "$ADD/${type.name}" + (shelfId?.let { "?$ARG_SHELF=$it" } ?: "")
}

/**
 * Cambia de pestaña sin apilar pantallas repetidas.
 * Inicio siempre vuelve a Inicio: no se restaura lo que hubiera encima (por ejemplo, Añadir).
 */
fun NavHostController.navigateToTab(route: String) {
    if (route == Routes.HOME) {
        popBackStack(Routes.HOME, inclusive = false)
        return
    }
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
    val memberships by main.seriesCollections.collectAsStateWithLifecycle()
    val trophies by main.trophies.collectAsStateWithLifecycle()
    val credited by main.credited.collectAsStateWithLifecycle()

    val openSeries: (Long) -> Unit = { navController.navigate(Routes.series(it)) }
    // El «+» y los botones «Añadir» abren primero la ventana para elegir qué se añade.
    var choosingType by rememberSaveable { mutableStateOf(false) }
    // Estante desde el que se pulsó «Añadir › Nueva» (null si se añade desde fuera).
    var addToShelf by rememberSaveable { mutableStateOf<Long?>(null) }
    val openAdd = { choosingType = true }
    val back: () -> Unit = { navController.popBackStack() }
    val openWishlist: () -> Unit = { navController.navigate(Routes.WISHLIST) }
    val openLoans: () -> Unit = { navController.navigate(Routes.LOANS) }
    val openProfile: () -> Unit = { navController.navigate(Routes.PROFILE) { launchSingleTop = true } }
    val openTrophies: () -> Unit = { navController.navigate(Routes.TROPHIES) { launchSingleTop = true } }

    CompositionLocalProvider(
        LocalShowMessage provides showMessage,
        LocalTrophyStyle provides main.trophyStyle,
        LocalUnlockedTrophies provides trophies.map { it.id }.toSet(),
    ) {
        Scaffold(
            bottomBar = { ShelfioBottomBar(currentRoute) { route -> if (route == Routes.ADD) openAdd() else navController.navigateToTab(route) } },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            NavHost(navController, startDestination = Routes.HOME, modifier = Modifier.padding(padding)) {

                // --- Pestañas principales ---

                composable(Routes.HOME) {
                    HomeScreen(
                        items = library ?: return@composable LoadingBox(),
                        userName = main.userName,
                        onSearch = { main.openLibrary(query = it, type = null); navController.navigateToTab(Routes.LIBRARY) },
                        onOpenSeries = openSeries,
                        onOpenLibrary = { main.openLibrary(query = "", type = it); navController.navigateToTab(Routes.LIBRARY) },
                        onOpenWishlist = openWishlist,
                        onOpenLoans = openLoans,
                        onAdd = openAdd,
                        onOpenProfile = openProfile,
                    )
                }
                composable(Routes.LIBRARY) {
                    LibraryScreen(
                        items = library ?: return@composable LoadingBox(),
                        query = main.libraryQuery,
                        onQueryChange = { main.libraryQuery = it },
                        typeFilter = main.libraryType,
                        onTypeFilterChange = { main.libraryType = it },
                        collectionFilter = main.libraryCollectionFilter,
                        onCollectionFilterChange = { main.libraryCollectionFilter = it },
                        onOpenSeries = openSeries,
                        onAdd = openAdd,
                        onOpenProfile = openProfile,
                    )
                }
                composable(
                    Routes.ADD_FOR_TYPE,
                    arguments = listOf(
                        navArgument(Routes.ARG_TYPE) { type = NavType.StringType },
                        navArgument(Routes.ARG_SHELF) { type = NavType.LongType; defaultValue = -1L },
                    ),
                ) { entry ->
                    AddScreen(
                        onSaved = { seriesId ->
                            // Desde un estante se vuelve a él; si no, se abre la ficha de lo guardado.
                            if ((entry.arguments?.getLong(Routes.ARG_SHELF) ?: -1L) > 0) {
                                showMessage("Guardado en tu biblioteca y en el estante.")
                                back()
                            } else {
                                showMessage("Guardado en tu biblioteca.")
                                openSeries(seriesId)
                            }
                        },
                    )
                }
                composable(Routes.COLLECTIONS) {
                    CollectionsScreen(
                        collections = collections ?: return@composable LoadingBox(),
                        library = library ?: return@composable LoadingBox(),
                        memberships = memberships,
                        onCreate = main::createCollection,
                        onOpen = { navController.navigate(Routes.collection(it)) },
                        onOpenProfile = openProfile,
                    )
                }
                composable(Routes.CHARTS) {
                    ChartsScreen(items = library ?: return@composable LoadingBox(), onOpenProfile = openProfile, onOpenTrophies = openTrophies)
                }
                composable(Routes.PROFILE) {
                    ProfileScreen(
                        items = library ?: return@composable LoadingBox(),
                        collectionsCount = collections?.size ?: 0,
                        userName = main.userName,
                        onUserNameChange = main::updateUserName,
                        googleBooksKey = main.googleBooksKey,
                        onGoogleBooksKeyChange = main::updateGoogleBooksKey,
                        onOpenWishlist = openWishlist,
                        onOpenLoans = openLoans,
                        onOpenTrophies = openTrophies,
                        trophyStyle = main.trophyStyle,
                        onTrophyStyleChange = main::updateTrophyStyle,
                        onBack = back,
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
                composable(Routes.LOANS) {
                    LoansScreen(
                        items = library ?: return@composable LoadingBox(),
                        onBack = back,
                        onOpenVolume = { seriesId, volumeId -> navController.navigate(Routes.volume(seriesId, volumeId)) },
                        onReturn = main::returnLoan,
                    )
                }
                composable(Routes.WISHLIST) {
                    WishlistScreen(
                        onBack = back,
                        onOpenVolume = { seriesId, volumeId -> navController.navigate(Routes.volume(seriesId, volumeId)) },
                    )
                }
                composable(Routes.TROPHIES) {
                    TrophiesScreen(
                        library = library ?: return@composable LoadingBox(),
                        collections = collections?.size ?: 0,
                        unlocked = trophies,
                        credited = credited,
                        onBack = back,
                    )
                }
                composable(Routes.COLLECTION, arguments = listOf(longArg(Routes.ARG_COLLECTION_ID))) {
                    CollectionDetailScreen(
                        onBack = back,
                        onOpenSeries = openSeries,
                        onAddNew = { shelf -> addToShelf = shelf; choosingType = true },
                    )
                }
            }
        }

        // Aviso «Trofeo desbloqueado» al conseguir uno o varios.
        val loaded = library
        if (main.newTrophies.isNotEmpty() && loaded != null) {
            TrophyUnlockedDialog(main.newTrophies, loaded, onDismiss = main::dismissNewTrophies)
        }

        if (choosingType) {
            AddTypeSheet(
                onChoose = { type ->
                    choosingType = false
                    val shelf = addToShelf
                    addToShelf = null
                    if (shelf != null) navController.navigate(Routes.add(type, shelf))
                    else navController.navigate(Routes.add(type)) { popUpTo(Routes.HOME) }
                },
                onDismiss = { choosingType = false; addToShelf = null },
            )
        }
    }
}
