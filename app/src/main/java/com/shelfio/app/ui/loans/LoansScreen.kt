package com.shelfio.app.ui.loans

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.shelfio.app.data.LoanItem
import com.shelfio.app.data.filterLoans
import com.shelfio.app.data.loanItems
import com.shelfio.app.data.model.Loan
import com.shelfio.app.data.model.SeriesWithVolumes
import com.shelfio.app.ui.components.EmptyState
import com.shelfio.app.ui.components.LoanLabel
import com.shelfio.app.ui.components.LocationLabel
import com.shelfio.app.ui.components.ScreenWithBack
import com.shelfio.app.ui.components.SearchField
import com.shelfio.app.ui.components.VolumeCover
import com.shelfio.app.ui.theme.TextSecondary
import com.shelfio.app.ui.volume.formatDay

/** Préstamos: pestañas «Activos» e «Historial», con búsqueda por persona. */
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
    val shown = if (tab == 0) all.filter { it.loan.isActive } else all.filter { !it.loan.isActive }

    ScreenWithBack(title = "Préstamos", onBack = onBack) {
        Column {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Activos (${all.count { it.loan.isActive }})") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Historial") })
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { SearchField(query, { query = it }, placeholder = "Buscar por persona o título") }
                if (shown.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Filled.Handshake,
                            title = if (tab == 0) "No tienes nada prestado" else "Sin préstamos anteriores",
                            message = "Para prestar un tomo, ábrelo desde su ficha y pulsa «Prestar».",
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

@Composable
private fun LoanRow(item: LoanItem, onClick: () -> Unit, onReturn: () -> Unit) {
    val loan = item.loan
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VolumeCover(item.series, item.volume, Modifier.width(48.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                if (item.singleBook) item.series.title else "${item.series.title} · Tomo ${item.volume.number}",
                style = MaterialTheme.typography.titleSmall,
            )
            if (loan.isActive) {
                LoanLabel(loan)
                val due = loan.dueAt?.let { " · devolver antes del ${formatDay(it)}" }.orEmpty()
                Text("Desde el ${formatDay(loan.lentAt)}$due", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                if (item.volume.location.isNotBlank()) LocationLabel(item.volume.location)
            } else {
                Text("Prestado a ${loan.person}", style = MaterialTheme.typography.bodyMedium)
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
