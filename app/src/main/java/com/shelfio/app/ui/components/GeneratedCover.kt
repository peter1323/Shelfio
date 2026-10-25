package com.shelfio.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelfio.app.data.model.ItemType
import com.shelfio.app.ui.theme.Background
import com.shelfio.app.ui.theme.ShelfioBlue
import com.shelfio.app.ui.theme.ShelfioCyan
import com.shelfio.app.ui.theme.ShelfioTheme
import com.shelfio.app.ui.theme.ShelfioViolet
import com.shelfio.app.ui.theme.TextPrimary

fun typeColor(type: ItemType): Color = when (type) {
    ItemType.BOOK -> ShelfioBlue
    ItemType.MANGA -> ShelfioViolet
    ItemType.COMIC -> ShelfioCyan
}

/** Texto oscuro sobre el cian; claro sobre azul y violeta. */
fun onTypeColor(type: ItemType): Color = if (type == ItemType.COMIC) Background else TextPrimary

/**
 * Portada dibujada con código para cuando no hay portada real.
 * Color según el tipo, icono arriba (libro, número de tomo o bocadillo) y el título en el centro.
 * Tiene proporción 2:3 y escala los textos según su anchura, así sirve igual en Inicio, Biblioteca y la ficha.
 */
@Composable
fun GeneratedCover(
    title: String,
    type: ItemType,
    modifier: Modifier = Modifier,
    volumeNumber: Int? = null,
) {
    val color = typeColor(type)
    val content = onTypeColor(type)
    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.verticalGradient(listOf(lerp(color, Color.White, 0.12f), lerp(color, Background, 0.35f)))),
    ) {
        val unit = maxWidth.value / 100f
        Box(Modifier.fillMaxSize().padding((8 * unit).dp)) {
            Box(Modifier.align(Alignment.TopCenter)) {
                when (type) {
                    ItemType.MANGA -> Text(
                        text = volumeNumber?.toString() ?: "",
                        color = content,
                        style = TextStyle(fontSize = (26 * unit).sp, fontWeight = FontWeight.Black),
                    )
                    ItemType.BOOK -> Icon(Icons.AutoMirrored.Filled.MenuBook, null, tint = content, modifier = Modifier.size((22 * unit).dp))
                    ItemType.COMIC -> Icon(Icons.Filled.ChatBubble, null, tint = content, modifier = Modifier.size((22 * unit).dp))
                }
            }
            Text(
                text = title,
                color = content,
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontSize = (13 * unit).sp, lineHeight = (15 * unit).sp, fontWeight = FontWeight.Bold),
                modifier = Modifier.align(Alignment.Center),
            )
            // Franja inferior, recuerda al lomo de la balda del logotipo.
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .size(width = (60 * unit).dp, height = (3 * unit).dp)
                    .clip(RoundedCornerShape(50))
                    .background(content.copy(alpha = 0.6f)),
            )
        }
    }
}

@Preview
@Composable
private fun GeneratedCoverPreview() {
    ShelfioTheme {
        androidx.compose.foundation.layout.Row {
            GeneratedCover("El faro del norte", ItemType.BOOK, Modifier.size(width = 100.dp, height = 150.dp))
            GeneratedCover("Saga del Dragón", ItemType.MANGA, Modifier.size(width = 100.dp, height = 150.dp), volumeNumber = 3)
            GeneratedCover("Ciudad Neón", ItemType.COMIC, Modifier.size(width = 100.dp, height = 150.dp))
        }
    }
}
