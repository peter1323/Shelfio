package com.shelfio.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelfio.app.data.isOverdue
import com.shelfio.app.data.model.ItemType
import com.shelfio.app.data.model.Loan
import com.shelfio.app.data.model.PublicationStatus
import com.shelfio.app.data.model.PurchasePriority
import com.shelfio.app.data.model.ReadingStatus
import com.shelfio.app.ui.theme.ShelfioBlue
import com.shelfio.app.ui.theme.ShelfioCyan
import com.shelfio.app.ui.theme.ShelfioViolet
import com.shelfio.app.ui.theme.TextSecondary

@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Buscar título, autor o ubicación",
    onSearch: (() -> Unit)? = null,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) { Icon(Icons.Filled.Close, contentDescription = "Borrar búsqueda") }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke() }),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    )
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** Estado vacío: icono sencillo, título y explicación de cómo empezar. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = ShelfioCyan, modifier = Modifier.size(36.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(message, color = TextSecondary, textAlign = TextAlign.Center)
        action?.invoke()
    }
}

@Composable
fun TypeLabel(type: ItemType, modifier: Modifier = Modifier) {
    Text(
        text = type.label,
        color = onTypeColor(type),
        style = MaterialTheme.typography.labelMedium,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(typeColor(type))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

fun statusIcon(status: ReadingStatus): ImageVector = when (status) {
    ReadingStatus.UNREAD -> Icons.Filled.RadioButtonUnchecked
    ReadingStatus.READING -> Icons.Filled.Timelapse
    ReadingStatus.READ -> Icons.Filled.CheckCircle
    ReadingStatus.PAUSED -> Icons.Filled.PauseCircle
    ReadingStatus.ABANDONED -> Icons.Filled.DoNotDisturbOn
}

fun statusColor(status: ReadingStatus): Color = when (status) {
    ReadingStatus.READ -> ShelfioCyan
    ReadingStatus.READING -> ShelfioBlue
    ReadingStatus.PAUSED -> ShelfioViolet
    ReadingStatus.UNREAD, ReadingStatus.ABANDONED -> TextSecondary
}

@Composable
fun StatusLabel(status: ReadingStatus, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(statusIcon(status), contentDescription = null, tint = statusColor(status), modifier = Modifier.size(16.dp))
        Spacer(Modifier.size(4.dp))
        Text(status.label, color = statusColor(status), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun ProgressBar(progress: Float, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { progress },
        modifier = modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
        color = ShelfioBlue,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        drawStopIndicator = {},
    )
}

/** Ubicación física compacta: icono de sitio + texto («Caja 3»). */
@Composable
fun LocationLabel(location: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Place, contentDescription = "Ubicación", tint = ShelfioCyan, modifier = Modifier.size(14.dp))
        Spacer(Modifier.size(4.dp))
        Text(location, color = ShelfioCyan, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Tarjeta pequeña con icono, nombre y número (contadores de Inicio y Perfil). */
@Composable
fun CounterCard(icon: ImageVector, label: String, count: Int, color: Color, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(6.dp))
        // Dos líneas: caben nombres largos como «Lecturas asiáticas» y todas las tarjetas miden igual.
        FitText(label, MaterialTheme.typography.labelMedium, lines = 2)
        Text(count.toString(), style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * Texto de [lines] líneas (una por defecto) que reduce su tamaño si no cabe (por ejemplo, con la letra del móvil muy grande),
 * en lugar de cortarse.
 */
@Composable
fun FitText(text: String, style: TextStyle, color: Color = MaterialTheme.colorScheme.onSurface, lines: Int = 1) {
    // Una palabra sola nunca se parte («Coleccio-nes»): se encoge en una línea, pero ocupa el alto de [lines].
    val usedLines = if (text.contains(" ")) lines else 1
    val height = with(LocalDensity.current) { (style.lineHeight * lines).toDp() }
    Box(Modifier.height(height), contentAlignment = Alignment.Center) {
        BasicText(
            text = text,
            style = style.copy(color = color, textAlign = TextAlign.Center),
            maxLines = usedLines,
            autoSize = TextAutoSize.StepBased(minFontSize = 6.sp, maxFontSize = style.fontSize),
        )
    }
}

/** Etiqueta de prioridad de compra (lista de deseos). */
@Composable
fun PriorityLabel(priority: PurchasePriority, modifier: Modifier = Modifier) {
    val color = when (priority) {
        PurchasePriority.HIGH -> ShelfioViolet
        PurchasePriority.MEDIUM -> ShelfioBlue
        PurchasePriority.LOW -> TextSecondary
    }
    Text(
        "Prioridad ${priority.label.lowercase()}",
        color = color,
        style = MaterialTheme.typography.labelMedium,
        modifier = modifier,
    )
}

/** «Prestado a <persona>» o «Me lo prestó <persona>», en rojo si está retrasado. */
@Composable
fun LoanLabel(loan: Loan, modifier: Modifier = Modifier) {
    val overdue = isOverdue(loan)
    val color = if (overdue) MaterialTheme.colorScheme.error else ShelfioCyan
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Handshake, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(Modifier.size(4.dp))
        Text(
            (if (loan.borrowed) "Me lo prestó ${loan.person}" else "Prestado a ${loan.person}") + if (overdue) " · Retrasado" else "",
            color = color,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Estado editorial compacto para las tarjetas: «🌐 Original: Finalizada  🔤 Tu idioma: En publicación».
 * Solo enseña los que no son «Desconocido»; si con letra grande no caben en una línea, el segundo baja.
 */
@Composable
fun PublicationStatusLine(origin: PublicationStatus, myLanguage: PublicationStatus, modifier: Modifier = Modifier) {
    if (origin == PublicationStatus.UNKNOWN && myLanguage == PublicationStatus.UNKNOWN) return
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (origin != PublicationStatus.UNKNOWN) PublicationStatusLabel(Icons.Filled.Public, "Original", origin)
        if (myLanguage != PublicationStatus.UNKNOWN) PublicationStatusLabel(Icons.Filled.Translate, "Tu idioma", myLanguage)
    }
}

@Composable
private fun PublicationStatusLabel(icon: ImageVector, label: String, status: PublicationStatus) {
    val color = when (status) {
        PublicationStatus.PUBLISHING -> ShelfioBlue
        PublicationStatus.FINISHED -> ShelfioCyan
        PublicationStatus.PAUSED -> ShelfioViolet
        PublicationStatus.CANCELLED -> MaterialTheme.colorScheme.error
        PublicationStatus.UNCONFIRMED, PublicationStatus.UNKNOWN -> TextSecondary
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(Modifier.size(4.dp))
        Text("$label: ${status.label}", color = color, style = MaterialTheme.typography.labelMedium)
    }
}

/** Botón de Perfil (arriba a la derecha de las pantallas principales). */
@Composable
fun ProfileButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.Filled.AccountCircle, contentDescription = "Perfil", modifier = Modifier.size(32.dp))
    }
}

/** Título de una pantalla principal con sus botones ([actions]) y Perfil a la derecha. */
@Composable
fun MainTitle(title: String, onOpenProfile: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
        actions()
        ProfileButton(onOpenProfile)
    }
}
