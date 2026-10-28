package com.shelfio.app.data

import com.shelfio.app.data.model.ItemType
import com.shelfio.app.data.model.PublicationStatus
import com.shelfio.app.data.model.SeriesWithVolumes

/** Categoría del trofeo, de menos a más. */
enum class TrophyTier(val label: String) {
    BRONZE("Bronce"),
    SILVER("Plata"),
    GOLD("Oro"),
    PLATINUM("Platino"),
}

/** Lo que miran los trofeos de la app. «Leídos» son tomos que ya contaron (ver [ReadCreditRules]). */
data class TrophyStats(
    val series: Int = 0,
    val readCredits: Int = 0,
    val owned: Int = 0,
    val collections: Int = 0,
    val returnedLoans: Int = 0,
    val borrowedLoans: Int = 0,
    val located: Int = 0,
    val wanted: Int = 0,
    val maxOtherTitles: Int = 0,
    val types: Int = 0,
    val completeSeries: Int = 0,
    val seriesPlatinums: Int = 0,
)

/** Un trofeo de la app: se consigue cuando [progress] llega a [goal]. Los ocultos no dicen qué piden hasta conseguirlos. */
class AppTrophy(
    val key: String,
    val name: String,
    val description: String,
    val tier: TrophyTier,
    val goal: Int = 1,
    val hidden: Boolean = false,
    val progress: (TrophyStats) -> Int,
) {
    val id: String get() = "app.$key"
}

/** Trofeos de la app. El platino de Shelfio se consigue al tener todos los demás. */
val APP_TROPHIES: List<AppTrophy> = listOf(
    AppTrophy("first_series", "Primer estante", "Añade tu primera serie o libro", TrophyTier.BRONZE) { it.series },
    AppTrophy("first_read", "Primera página", "Lee tu primer tomo", TrophyTier.BRONZE) { it.readCredits },
    AppTrophy("read_10", "Lector", "Lee 10 tomos", TrophyTier.BRONZE, goal = 10) { it.readCredits },
    AppTrophy("first_collection", "Organizador", "Crea una colección", TrophyTier.BRONZE) { it.collections },
    AppTrophy("loan_returned", "Buen amigo", "Presta algo y márcalo como devuelto", TrophyTier.BRONZE) { it.returnedLoans },
    AppTrophy("borrowed", "Me lo dejan", "Apunta algo que te han prestado", TrophyTier.BRONZE, hidden = true) { it.borrowedLoans },
    AppTrophy("located_10", "Todo en su sitio", "Guarda la ubicación de 10 tomos", TrophyTier.BRONZE, goal = 10) { it.located },
    AppTrophy("wanted_5", "Lista de la compra", "Ten 5 tomos pendientes de compra", TrophyTier.BRONZE, goal = 5) { it.wanted },
    AppTrophy("polyglot", "Políglota", "Guarda 3 títulos más de una misma obra", TrophyTier.BRONZE, goal = 3, hidden = true) { it.maxOtherTitles },
    AppTrophy("read_50", "Devorador", "Lee 50 tomos", TrophyTier.SILVER, goal = 50) { it.readCredits },
    AppTrophy("complete_1", "Coleccionista", "Completa una serie", TrophyTier.SILVER) { it.completeSeries },
    AppTrophy("all_types", "Todoterreno", "Ten algo de los cuatro tipos", TrophyTier.SILVER, goal = 4) { it.types },
    AppTrophy("collections_5", "Bibliotecario", "Crea 5 colecciones", TrophyTier.SILVER, goal = 5) { it.collections },
    AppTrophy("owned_100", "Estantería llena", "Ten 100 tomos", TrophyTier.SILVER, goal = 100) { it.owned },
    AppTrophy("series_platinum_1", "Primer platino", "Consigue el platino de una serie", TrophyTier.SILVER) { it.seriesPlatinums },
    AppTrophy("read_200", "Leyenda lectora", "Lee 200 tomos", TrophyTier.GOLD, goal = 200) { it.readCredits },
    AppTrophy("complete_5", "Completista", "Completa 5 series", TrophyTier.GOLD, goal = 5) { it.completeSeries },
    AppTrophy("owned_500", "Gran biblioteca", "Ten 500 tomos", TrophyTier.GOLD, goal = 500) { it.owned },
    AppTrophy("series_platinum_5", "Cazador de platinos", "Consigue el platino de 5 series", TrophyTier.GOLD, goal = 5) { it.seriesPlatinums },
)

const val APP_PLATINUM_ID = "app.platinum"

fun seriesTrophyId(seriesId: Long, tier: TrophyTier) = "series.$seriesId.${tier.name}"

/** Serie y categoría de un id de trofeo de serie, o null si es de la app. */
fun parseSeriesTrophyId(id: String): Pair<Long, TrophyTier>? {
    val parts = id.split(".")
    if (parts.size != 3 || parts[0] != "series") return null
    val seriesId = parts[1].toLongOrNull() ?: return null
    return seriesId to (TrophyTier.entries.firstOrNull { it.name == parts[2] } ?: return null)
}

/** Frase del platino de una serie, según su tipo (sale bajo el título, como en las consolas). */
fun platinumPhrase(type: ItemType): String = when (type) {
    ItemType.COMIC -> "¡Excelsior!"
    ItemType.MANGA -> "Kanpeki — perfecto"
    ItemType.BOOK -> "Y colorín colorado…"
    ItemType.OTHER -> "Misión cumplida"
}

/** Tengo todo lo publicado: en un libro suelto, tenerlo; en una serie, todos los tomos publicados (dato obligatorio). */
fun ownsEverything(item: SeriesWithVolumes): Boolean =
    if (item.series.type.isSingleItem && item.isSingleBook) item.volumes.any { it.owned }
    else item.series.publishedInMyLanguage.let { it != null && it > 0 && item.ownedOfPublished == it }

/**
 * Trofeos de una serie según sus tomos ya contados como leídos ([credited]):
 * bronce el primero leído, plata la mitad, oro tenerla completa, platino completa, toda leída y finalizada.
 */
fun seriesTrophyTiers(item: SeriesWithVolumes, credited: Set<Long>): Set<TrophyTier> {
    val volumes = item.volumes
    if (volumes.isEmpty()) return emptySet()
    val read = volumes.count { it.id in credited }
    val complete = ownsEverything(item)
    val finished = item.isSingleBook || item.series.statusInMyLanguage == PublicationStatus.FINISHED
    return buildSet {
        if (read >= 1) add(TrophyTier.BRONZE)
        if (read * 2 >= volumes.size && read > 0) add(TrophyTier.SILVER)
        if (complete) add(TrophyTier.GOLD)
        if (complete && finished && volumes.filter { it.owned }.all { it.id in credited }) add(TrophyTier.PLATINUM)
    }
}

/** Calcula los datos que miran los trofeos de la app. */
fun trophyStats(library: List<SeriesWithVolumes>, collections: Int, credited: Set<Long>, seriesPlatinums: Int): TrophyStats {
    val volumes = library.flatMap { it.volumes }
    val loans = library.flatMap { it.loans }
    return TrophyStats(
        series = library.size,
        readCredits = credited.size,
        owned = volumes.count { it.owned },
        collections = collections,
        returnedLoans = loans.count { !it.borrowed && it.returnedAt != null },
        borrowedLoans = loans.count { it.borrowed },
        located = volumes.count { it.owned && it.location.isNotBlank() },
        wanted = volumes.count { !it.owned },
        maxOtherTitles = library.maxOfOrNull { item -> item.series.otherTitles.lines().count { it.isNotBlank() } } ?: 0,
        types = library.map { it.series.type }.distinct().size,
        completeSeries = library.count(::ownsEverything),
        seriesPlatinums = seriesPlatinums,
    )
}

/**
 * Todos los trofeos conseguidos ahora mismo: los ya guardados ([stored], nunca se pierden)
 * más los que se cumplen con la biblioteca actual. Función pura, se prueba en TrophiesTest.
 */
fun achievedTrophies(library: List<SeriesWithVolumes>, collections: Int, credited: Set<Long>, stored: Set<String>): Set<String> {
    val series = library.flatMap { item -> seriesTrophyTiers(item, credited).map { seriesTrophyId(item.series.id, it) } }
    val all = stored + series
    val platinums = all.count { parseSeriesTrophyId(it)?.second == TrophyTier.PLATINUM }
    val stats = trophyStats(library, collections, credited, platinums)
    val app = APP_TROPHIES.filter { it.progress(stats) >= it.goal }.map { it.id }
    val result = all + app
    // Platino de Shelfio: todos los demás trofeos de la app.
    return if (APP_TROPHIES.all { it.id in result }) result + APP_PLATINUM_ID else result
}

/** Antitrampas de lectura: como mucho [DAILY_LIMIT] tomos al día cuentan para los trofeos. */
object ReadCreditRules {
    const val DAILY_LIMIT = 20
}
