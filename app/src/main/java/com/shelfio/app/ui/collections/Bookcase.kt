package com.shelfio.app.ui.collections

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelfio.app.data.ShelfSpine
import com.shelfio.app.ui.components.typeColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.absoluteValue

/** Colores de la balda: la tabla de madera y su canto, más oscuro. */
private val Wood = Color(0xFF9A6A3F)
private val WoodEdge = Color(0xFF6E4A2A)

/** Una balda: sus lomos apoyados abajo y la madera debajo. */
@Composable
fun BookcaseShelf(spines: List<ShelfSpine>, onOpenSeries: (Long) -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().height(150.dp).padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            spines.forEach { BookSpine(it, onClick = { onOpenSeries(it.item.series.id) }) }
        }
        Box(Modifier.fillMaxWidth().height(12.dp).background(Wood, RoundedCornerShape(2.dp)))
        Box(Modifier.fillMaxWidth().padding(horizontal = 2.dp).height(5.dp).background(WoodEdge))
    }
}

/**
 * Lomo de un tomo (mío o que me han prestado): color de la portada de la serie (o del tipo si no tiene), título en vertical y número abajo.
 * Grosor y altura salen del id del tomo, así varían entre libros pero cada uno sale siempre igual.
 * Al pulsarlo sube un poco, como si se sacara de la balda.
 */
@Composable
private fun BookSpine(spine: ShelfSpine, onClick: () -> Unit) {
    val series = spine.item.series
    val seed = (spine.volume.id * 31 + series.id).hashCode().absoluteValue
    val color by produceState(typeColor(series.type), series.coverPath) {
        series.coverPath?.let { path -> coverColor(path)?.let { value = it } }
    }
    val ink = if (color.luminance() > 0.5f) Color(0xFF1A1A1A) else Color.White
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val lift by animateDpAsState(if (pressed) (-10).dp else 0.dp, label = "lomo")
    val number = if (spine.item.isSingleBook) "" else "${spine.volume.number}"

    Column(
        Modifier
            .offset(y = lift)
            .width((22 + seed % 9).dp)
            .height((112 + seed % 31).dp)
            .background(color, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp, bottomStart = 1.dp, bottomEnd = 1.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .semantics { contentDescription = series.title + if (number.isEmpty()) "" else ", ${series.type.unit} $number" }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            // Me lo han prestado: manos en rojo sobre un círculo blanco, para que se vea en cualquier color de lomo.
            spine.borrowed -> Box(Modifier.size(16.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Handshake, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(12.dp))
            }
            spine.platinum -> Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = ink, modifier = Modifier.size(12.dp))
            spine.lent -> Icon(Icons.Filled.Handshake, contentDescription = null, tint = ink, modifier = Modifier.size(12.dp))
            else -> Spacer(Modifier.height(12.dp))
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Text(
                series.title,
                color = ink,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.vertical().rotate(-90f),
            )
        }
        Text(number, color = ink, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** Mide el texto en horizontal con el alto disponible como ancho y lo deja girado en vertical. */
private fun Modifier.vertical() = layout { measurable, constraints ->
    val placeable = measurable.measure(Constraints(maxWidth = constraints.maxHeight, maxHeight = constraints.maxWidth))
    layout(placeable.height, placeable.width) {
        placeable.place(x = (placeable.height - placeable.width) / 2, y = (placeable.width - placeable.height) / 2)
    }
}

/** Color medio de cada portada ya calculado, para no volver a leer la imagen al hacer scroll. */
private val coverColors = ConcurrentHashMap<String, Color>()

/** Color medio de la portada: se lee en pequeño y se reduce a un píxel. null si no se puede leer. */
private suspend fun coverColor(path: String): Color? = coverColors[path] ?: withContext(Dispatchers.IO) {
    val file = File(path)
    if (!file.exists()) return@withContext null
    val bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = 8 }) ?: return@withContext null
    val pixel = Bitmap.createScaledBitmap(bitmap, 1, 1, true).getPixel(0, 0)
    Color(pixel).also { coverColors[path] = it }
}
