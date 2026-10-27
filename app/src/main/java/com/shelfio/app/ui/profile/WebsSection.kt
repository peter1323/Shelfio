package com.shelfio.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.shelfio.app.ui.theme.TextSecondary

/** Una web recomendada: nombre, para qué sirve, enlace y dominio (para su logo). */
private data class Web(val name: String, val description: String, val url: String, val domain: String)

/** Webs para mirar más detalles de una obra. Las marcadas «la usa el buscador» son las fuentes de Añadir. */
private val webs = listOf(
    Web("ListadoManga", "Ediciones de manga publicadas en España", "https://www.listadomanga.es/buscador.php", "listadomanga.es"),
    Web("AbeBooks", "Libros nuevos, usados y de colección", "https://www.abebooks.com/", "abebooks.com"),
    Web("MangaDex", "Manga, manhwa y manhua", "https://mangadex.org/", "mangadex.org"),
    Web("AniList", "Manga, manhwa y manhua · la usa el buscador", "https://anilist.co/search/manga", "anilist.co"),
    Web("MangaUpdates", "Series, autores y estado · la usa el buscador", "https://www.mangaupdates.com/", "mangaupdates.com"),
    Web("Google Books", "Libros · la usa el buscador", "https://books.google.com/", "books.google.com"),
    Web("Open Library", "Libros y ediciones · la usa el buscador", "https://openlibrary.org/", "openlibrary.org"),
    Web("Comic Vine", "Cómics · la usa el buscador", "https://comicvine.gamespot.com/", "comicvine.gamespot.com"),
)

/**
 * «Webs recomendadas» de Perfil: tocar una la abre en el navegador.
 * El logo es el icono de la propia web (servicio de iconos de Google); si no carga, queda un hueco discreto.
 */
@Composable
fun WebsSection() {
    val uriHandler = LocalUriHandler.current
    Text("Para mirar más detalles de una obra.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
    webs.forEach { web ->
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { uriHandler.openUri(web.url) }
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = "https://www.google.com/s2/favicons?domain=${web.domain}&sz=64",
                contentDescription = "Logo de ${web.name}",
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(Color.White).padding(4.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(web.name, style = MaterialTheme.typography.titleSmall)
                Text(web.description, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Abrir", tint = TextSecondary)
        }
    }
}
