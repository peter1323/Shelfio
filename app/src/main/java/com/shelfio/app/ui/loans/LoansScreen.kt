package com.shelfio.app.ui.loans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shelfio.app.data.LoanItem
import com.shelfio.app.data.filterLoans
import com.shelfio.app.data.loanItems
import com.shelfio.app.data.model.Loan
import com.shelfio.app.data.model.SeriesWithVolumes
import com.shelfio.app.ui.components.CardRow
import com.shelfio.app.ui.components.EmptyState
import com.shelfio.app.ui.components.LoanLabel
import com.shelfio.app.ui.components.LocationLabel
import com.shelfio.app.ui.components.ScreenWithBack
import com.shelfio.app.ui.components.SearchField
import com.shelfio.app.ui.components.VolumeCover
import com.shelfio.app.ui.theme.TextSecondary
import com.shelfio.app.ui.volume.formatDay

/** Préstamos: «He prestado», «Me han prestado» e «Historial», con búsqueda por persona. */
@Composable
fun LoansScreen(
    items: List<SeriesWithVolumes>,
    onBack: () -> Unit,
    onOpenVolume: (seriesId: Long, volumeId: Long) -> Unit,
    onReturn: (Loan) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }

    val all = filterLoans(loanItems(items), query)
    // Pestañas: 0 = lo que he prestado, 1 = lo que me han prestado (activos), 2 = historial de los dos.
    val shown = when (tab) {
        0 -> all.filter { it.loan.isActive && !it.loan.borrowed }
        1 -> all.filter { it.loan.isActive && it.loan.borrowed }
        else -> all.filter { !it.loan.isActive }
    }

    ScreenWithBack(title = "Préstamos", onBack = onBack) {
        Column {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("He prestado (${all.count { it.loan.isActive && !it.loan.borrowed }})") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Me han prestado (${all.count { it.loan.isActive && it.loan.borrowed }})") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Historial") })
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { SearchField(query, { query = it }, placeholder = "Buscar por persona o título") }
                if (shown.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Filled.Handshake,
                            title = when (tab) { 0 -> "No tienes nada prestado"; 1 -> "Nadie te ha prestado nada"; else -> "Sin préstamos anteriores" },
                            message = "Abre un tomo desde su ficha y pulsa «Prestar» (si es tuyo) o «Me lo han prestado» (si no lo es).",
                        )
                    }
                }
                items(shown, key = { it.loan.id }) { loanItem ->
                    LoanRow(
                        item = loanItem,
                        onClick = { onOpenVolume(loanItem.series.id, loanItem.volume.id) },
                        onReturn = { onReturn(loanItem.loan) },
                    )
                }
            }
        }
    }
}

/** Fila de un préstamo: el tomo, con quién y desde cuándo; si sigue activo, botón «Devuelto». */
@Composable
private fun LoanRow(item: LoanItem, onClick: () -> Unit, onReturn: () -> Unit) {
    val loan = item.loan
    CardRow(onClick) {
        VolumeCover(item.series, item.volume, Modifier.width(48.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                if (item.singleBook) item.series.title else "${item.series.title} · ${item.series.type.unitTitle} ${item.volume.number}",
                style = MaterialTheme.typography.titleSmall,
            )
            if (loan.isActive) {
                LoanLabel(loan)
                val due = loan.dueAt?.let { " · devolver antes del ${formatDay(it)}" }.orEmpty()
                Text("Desde el ${formatDay(loan.lentAt)}$due", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                if (item.volume.location.isNotBlank()) LocationLabel(item.volume.location)
            } else {
                Text(if (loan.borrowed) "Me lo prestó ${loan.person}" else "Prestado a ${loan.person}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Del ${formatDay(loan.lentAt)} al ${formatDay(loan.returnedAt ?: loan.lentAt)}",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (loan.isActive) OutlinedButton(onClick = onReturn) { Text("Devuelto") }
    }
}
