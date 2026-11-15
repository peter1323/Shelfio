package com.shelfio.app.data

import com.shelfio.app.data.model.Series
import com.shelfio.app.data.model.SeriesWithVolumes
import com.shelfio.app.data.model.Volume

// Calculadora de coste: cuánto vale lo que tengo y cuánto cuesta lo que me falta.
// Los importes van siempre en céntimos (Int) para no perder decimales al sumar. Funciones puras, probadas en CostTest.

/** Precio de un tomo: el suyo si lo tiene; si no, el de la serie. null = sin precio. */
fun volumePrice(series: Series, volume: Volume): Int? = volume.priceCents ?: series.volumePriceCents

/**
 * Suma de un grupo de tomos: cuántos son, cuánto cuestan los que tienen precio
 * y en cuántas series hay alguno sin precio (para avisar «3 series sin precio»).
 */
data class Cost(val volumes: Int = 0, val cents: Int = 0, val seriesWithoutPrice: Int = 0) {
    operator fun plus(other: Cost) = Cost(volumes + other.volumes, cents + other.cents, seriesWithoutPrice + other.seriesWithoutPrice)
}

/** Coste de unos tomos de una serie. Los que no tienen precio no suman, pero marcan la serie como «sin precio». */
fun seriesCost(series: Series, volumes: List<Volume>): Cost {
    val prices = volumes.map { volumePrice(series, it) }
    return Cost(volumes.size, prices.filterNotNull().sum(), if (prices.any { it == null }) 1 else 0)
}

/**
 * Coste pendiente ([owned] = false: los tomos que no tengo) o valor de mi colección ([owned] = true).
 * Solo cuentan los tomos que existen en la app: de una serie en curso no se inventan los que aún no han salido.
 */
fun libraryCost(items: List<SeriesWithVolumes>, owned: Boolean): Cost =
    items.fold(Cost()) { total, item -> total + seriesCost(item.series, item.volumes.filter { it.owned == owned }) }

/** Total de la lista de deseos tal como se ve (con sus filtros). */
fun wishlistCost(groups: List<WishlistGroup>): Cost =
    groups.fold(Cost()) { total, group -> total + seriesCost(group.series, group.volumes) }

/** Meses para pagar [cents] con [budgetCents] al mes, redondeando hacia arriba. null si no hay presupuesto o nada que pagar. */
fun monthsToComplete(cents: Int, budgetCents: Int): Int? =
    if (cents <= 0 || budgetCents <= 0) null else (cents + budgetCents - 1) / budgetCents

/** Importe con formato español: 124050 → «1.240,50 €». */
fun formatEuros(cents: Int): String {
    val euros = (cents / 100).toString().reversed().chunked(3).joinToString(".").reversed()
    return "$euros,${(cents % 100).toString().padStart(2, '0')} €"
}

/** Importe para un campo de texto, sin símbolo ni puntos: 124050 → «1240,50». Vacío si no hay precio. */
fun eurosText(cents: Int?): String = cents?.let { "${it / 100},${(it % 100).toString().padStart(2, '0')}" }.orEmpty()

/**
 * Deja escribir solo un importe: dígitos y una coma o un punto con dos decimales como mucho.
 * Así no se puede meter un negativo ni letras.
 */
fun cleanEuros(text: String): String {
    val kept = text.filter { it.isDigit() || it == ',' || it == '.' }
    val separator = kept.indexOfFirst { it == ',' || it == '.' }
    if (separator < 0) return kept.take(7)
    return kept.take(separator).take(7) + kept[separator] + kept.drop(separator + 1).filter(Char::isDigit).take(2)
}

/** Texto escrito por el usuario («8,5», «8.50», «12») a céntimos. null si está vacío o no es un importe. */
fun parseEuros(text: String): Int? {
    val parts = text.trim().split(',', '.')
    if (parts.size > 2 || parts.any { part -> part.any { !it.isDigit() } }) return null
    val euros = parts[0].toIntOrNull() ?: return null
    val cents = parts.getOrNull(1).orEmpty().padEnd(2, '0').take(2).toIntOrNull() ?: 0
    return euros * 100 + cents
}

/** «Faltan 12 tomos · 102,00 €» (o «· sin precio» si ninguno lo tiene). [unit] y [units] son «tomo»/«tomos» o «volumen»/«volúmenes». */
fun missingLine(cost: Cost, unit: String = "tomo", units: String = "tomos"): String {
    val count = if (cost.volumes == 1) "Falta 1 $unit" else "Faltan ${cost.volumes} $units"
    return "$count · " + if (cost.cents == 0 && cost.seriesWithoutPrice > 0) "sin precio" else formatEuros(cost.cents)
}

/** Aviso de series que no suman porque les falta el precio: «3 series sin precio». null si no hay ninguna. */
fun withoutPriceLine(cost: Cost): String? = when (cost.seriesWithoutPrice) {
    0 -> null
    1 -> "1 serie sin precio"
    else -> "${cost.seriesWithoutPrice} series sin precio"
}

/** «Con 40,00 €/mes la completas en 11 meses». null si no hay presupuesto o no falta nada con precio. */
fun budgetLine(cents: Int, budgetCents: Int): String? = monthsToComplete(cents, budgetCents)?.let { months ->
    "Con ${formatEuros(budgetCents)}/mes la completas en " + if (months == 1) "1 mes" else "$months meses"
}
