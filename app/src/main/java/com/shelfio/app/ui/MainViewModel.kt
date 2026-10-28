package com.shelfio.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shelfio.app.data.CollectionFilter
import com.shelfio.app.data.SettingsRepository
import com.shelfio.app.data.ShelfioRepository
import com.shelfio.app.data.TrophyManager
import com.shelfio.app.data.model.CollectionWithCount
import com.shelfio.app.data.model.ItemType
import com.shelfio.app.data.model.Loan
import com.shelfio.app.data.model.SeriesWithVolumes
import com.shelfio.app.data.model.UnlockedTrophy
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Estado compartido por las pestañas principales (Inicio, Biblioteca, Colecciones, Gráficas y Perfil) y los logros.
 * Vive mientras la app está abierta, así la búsqueda y el filtro de Biblioteca no se pierden al cambiar de pestaña.
 */
class MainViewModel(
    private val repository: ShelfioRepository,
    private val settings: SettingsRepository,
    private val trophyManager: TrophyManager,
) : ViewModel() {

    /** Toda la biblioteca. null mientras carga por primera vez. */
    val library: StateFlow<List<SeriesWithVolumes>?> =
        repository.library.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val collections: StateFlow<List<CollectionWithCount>?> =
        repository.collections.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // Búsqueda y filtro de la Biblioteca (Inicio también los usa para abrir la Biblioteca ya filtrada).
    var libraryQuery by mutableStateOf("")
    var libraryType by mutableStateOf<ItemType?>(null)
    var libraryCollectionFilter by mutableStateOf<CollectionFilter?>(null)

    /** Desde Inicio se abre la Biblioteca con una búsqueda o un tipo y sin filtros viejos que oculten resultados. */
    fun openLibrary(query: String, type: ItemType?) {
        libraryQuery = query
        libraryType = type
        libraryCollectionFilter = null
    }

    /** Clave propia de Google Books para el escáner. Se lee una vez al abrir y se guarda en cada cambio. */
    var googleBooksKey by mutableStateOf("")
        private set

    /** Nombre del saludo. Se lee una vez al abrir y se guarda en cada cambio. */
    var userName by mutableStateOf("")
        private set

    // --- Logros ---

    /** Trofeos conseguidos (con su fecha). */
    val trophies: StateFlow<List<UnlockedTrophy>> =
        trophyManager.unlocked.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Tomos que ya contaron como leídos para los trofeos. */
    val credited: StateFlow<Set<Long>> =
        trophyManager.credited.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Trofeos recién conseguidos que esperan el aviso «Trofeo desbloqueado». */
    var newTrophies by mutableStateOf<List<String>>(emptyList())
        private set

    fun dismissNewTrophies() {
        newTrophies = emptyList()
    }

    /** Estilo de las imágenes de los trofeos (1 a 4), elegido en Perfil. */
    var trophyStyle by mutableStateOf(1)
        private set

    fun updateTrophyStyle(style: Int) {
        trophyStyle = style
        viewModelScope.launch { settings.setTrophyStyle(style) }
    }

    init {
        viewModelScope.launch {
            userName = settings.getUserName()
            googleBooksKey = settings.getGoogleBooksKey()
            trophyStyle = settings.getTrophyStyle()
        }
        // Cada cambio en la biblioteca o las colecciones revisa los logros y avisa de los nuevos.
        viewModelScope.launch {
            combine(repository.library, repository.collections) { items, collections -> items to collections.size }
                .collectLatest { (items, collections) ->
                    val fresh = trophyManager.check(items, collections)
                    if (fresh.isNotEmpty()) newTrophies = (newTrophies + fresh).distinct()
                }
        }
    }

    fun updateUserName(name: String) {
        userName = name
        viewModelScope.launch { settings.setUserName(name.trim()) }
    }

    fun updateGoogleBooksKey(key: String) {
        googleBooksKey = key
        viewModelScope.launch { settings.setGoogleBooksKey(key.trim()) }
    }

    fun createCollection(name: String) = viewModelScope.launch { repository.createCollection(name) }

    fun returnLoan(loan: Loan) = viewModelScope.launch { repository.returnLoan(loan) }
}
