package com.shelfio.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.shelfio.app.ui.components.FitText
import com.shelfio.app.ui.theme.ShelfioBlue
import com.shelfio.app.ui.theme.ShelfioCyan
import com.shelfio.app.ui.theme.ShelfioViolet
import com.shelfio.app.ui.theme.TextPrimary

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Inicio", Icons.Filled.Home),
    Tab(Routes.LIBRARY, "Biblioteca", Icons.AutoMirrored.Filled.LibraryBooks),
    Tab(Routes.ADD, "Añadir", Icons.Filled.Add),
    Tab(Routes.COLLECTIONS, "Estantes", Icons.Filled.CollectionsBookmark),
    Tab(Routes.CHARTS, "Gráficas", Icons.Filled.BarChart),
)

/** Navegación inferior: Inicio · Biblioteca · Añadir (+) · Estantes · Gráficas. Perfil va arriba a la derecha. */
@Composable
fun ShelfioBottomBar(currentRoute: String?, onSelect: (String) -> Unit) {
    // Solo se muestra en las pantallas principales.
    if (tabs.none { currentRoute?.startsWith(it.route) == true }) return
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        tabs.forEach { tab ->
            val selected = currentRoute?.startsWith(tab.route) == true
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(tab.route) },
                label = { FitText(tab.label, MaterialTheme.typography.labelMedium, LocalContentColor.current) },
                icon = {
                    if (tab.route == Routes.ADD) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(ShelfioBlue, ShelfioViolet))),
                            contentAlignment = Alignment.Center,
                        ) { Icon(tab.icon, contentDescription = tab.label, tint = TextPrimary) }
                    } else {
                        Icon(tab.icon, contentDescription = tab.label)
                    }
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = ShelfioCyan,
                    selectedTextColor = ShelfioCyan,
                    indicatorColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            )
        }
    }
}
