package com.shelfio.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Place
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelfio.app.data.isOverdue
import com.shelfio.app.data.model.ItemType
import com.shelfio.app.data.model.Loan
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
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(6.dp))
        FitText(label, MaterialTheme.typography.labelMedium)
        Text(count.toString(), style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * Texto de una línea que reduce su tamaño si no cabe (por ejemplo, con la letra del móvil muy grande),
 * en lugar de cortarse.
 */
@Composable
fun FitText(text: String, style: TextStyle, color: Color = MaterialTheme.colorScheme.onSurface) {
    BasicText(
        text = text,
        style = style.copy(color = color),
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = style.fontSize),
    )
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

/** «Prestado a <persona>», en rojo si está retrasado. */
@Composable
fun LoanLabel(loan: Loan, modifier: Modifier = Modifier) {
    val overdue = isOverdue(loan)
    val color = if (overdue) MaterialTheme.colorScheme.error else ShelfioCyan
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Handshake, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(Modifier.size(4.dp))
        Text(
            if (overdue) "Prestado a ${loan.person} · Retrasado" else "Prestado a ${loan.person}",
            color = color,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
