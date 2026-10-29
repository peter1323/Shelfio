package com.shelfio.app.data.backup

import com.shelfio.app.data.withOwnershipRules
import com.shelfio.app.data.model.ItemType
import com.shelfio.app.data.model.Loan
import com.shelfio.app.data.model.ReadCredit
import com.shelfio.app.data.model.PublicationStatus
import com.shelfio.app.data.model.PurchasePriority
import com.shelfio.app.data.model.ReadingStatus
import com.shelfio.app.data.model.Series
import com.shelfio.app.data.model.SeriesCollectionCrossRef
import com.shelfio.app.data.model.ShelfCollection
import com.shelfio.app.data.model.UnlockedTrophy
import com.shelfio.app.data.model.Volume
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File

/** Error de una copia de seguridad, con un mensaje que se puede enseñar al usuario. */
class BackupException(message: String) : Exception(message)

/**
 * Contenido de una copia de seguridad ya validado.
 * Las rutas de portada (coverPath) contienen solo el nombre del archivo dentro de la copia (portadas/<nombre>).
 */
data class BackupData(
    val formatVersion: Int,
    val exportedAt: Long,
    val series: List<Series>,
    val volumes: List<Volume>,
    val collections: List<ShelfCollection>,
    val seriesCollections: List<SeriesCollectionCrossRef>,
    val loans: List<Loan>,
    /** Logros (formato 7). Las copias anteriores no los traen. */
    val trophies: List<UnlockedTrophy> = emptyList(),
    val readCredits: List<ReadCredit> = emptyList(),
    /** Nombre de archivo → bytes de la imagen. */
    val covers: Map<String, ByteArray>,
)

/**
 * Formato del archivo datos.json. Cada vez que se añadan datos hay que subir [CURRENT_VERSION]
 * y seguir leyendo las versiones anteriores (los campos nuevos tienen valor por defecto).
 *   1: series, tomos, colecciones y ubicaciones (Fase 7).
 *   2: + lista de deseos en los tomos: prioridad, nota de compra y fecha de alta (Fase 8).
 *   3: + préstamos (Fase 9).
 *   4: + estado editorial de las series (Fase 10).
 *   5: + otros títulos de la serie y préstamos «me lo han prestado».
 *   6: + portada original del buscador de cada serie (originalCoverFile).
 *   7: + logros (trofeos conseguidos y tomos que ya contaron como leídos).
 *   8: + foto de cada estante (coverFile) y fecha en que se añadió cada serie (addedAt).
 */
object BackupFormat {
    const val CURRENT_VERSION = 8
    const val DATA_FILE = "datos.json"
    const val COVERS_FOLDER = "portadas/"

    fun toJson(
        appVersion: String,
        exportedAt: Long,
        series: List<Series>,
        volumes: List<Volume>,
        collections: List<ShelfCollection>,
        seriesCollections: List<SeriesCollectionCrossRef>,
        loans: List<Loan>,
        trophies: List<UnlockedTrophy>,
        readCredits: List<ReadCredit>,
    ): String = JSONObject()
        .put("formatVersion", CURRENT_VERSION)
        .put("appVersion", appVersion)
        .put("exportedAt", exportedAt)
        .put("series", JSONArray(series.map(::seriesToJson)))
        .put("volumes", JSONArray(volumes.map(::volumeToJson)))
        .put("collections", JSONArray(collections.map { JSONObject().put("id", it.id).put("name", it.name).put("coverFile", it.coverPath?.let { path -> File(path).name } ?: JSONObject.NULL) }))
        .put("seriesCollections", JSONArray(seriesCollections.map { JSONObject().put("seriesId", it.seriesId).put("collectionId", it.collectionId).put("addedAt", it.addedAt) }))
        .put("loans", JSONArray(loans.map(::loanToJson)))
        .put("trophies", JSONArray(trophies.map { JSONObject().put("id", it.id).put("unlockedAt", it.unlockedAt) }))
        .put("readCredits", JSONArray(readCredits.map { JSONObject().put("volumeId", it.volumeId).put("creditedAt", it.creditedAt) }))
        .toString(2)

    /** Lee y valida datos.json. Lanza [BackupException] con un mensaje claro si algo no cuadra. */
    fun parse(json: String, covers: Map<String, ByteArray>): BackupData {
        val root = try {
            JSONObject(json)
        } catch (e: JSONException) {
            throw BackupException("El archivo de datos está dañado.")
        }
        val version = root.optInt("formatVersion", -1)
        if (version < 1) throw BackupException("No es una copia de seguridad de Shelfio.")
        if (version > CURRENT_VERSION) throw BackupException("La copia es de una versión más nueva de Shelfio. Actualiza la app para importarla.")

        try {
            val series = root.getJSONArray("series").objects().map { seriesFromJson(it, covers) }
            val volumes = root.getJSONArray("volumes").objects().map { volumeFromJson(it, covers) }
            val collections = root.getJSONArray("collections").objects().map {
                ShelfCollection(id = it.getLong("id"), name = it.getString("name").also { name -> require(name.isNotBlank()) }, coverPath = coverName(it, covers))
            }
            val seriesCollections = root.getJSONArray("seriesCollections").objects().map {
                SeriesCollectionCrossRef(seriesId = it.getLong("seriesId"), collectionId = it.getLong("collectionId"), addedAt = it.optLong("addedAt", 0))
            }
            // Las copias de versiones anteriores a la 3 no tienen préstamos.
            val loans = root.optJSONArray("loans")?.objects()?.map(::loanFromJson).orEmpty()
            // Las copias anteriores a la 7 no tienen logros.
            val trophies = root.optJSONArray("trophies")?.objects()?.map { UnlockedTrophy(it.getString("id"), it.getLong("unlockedAt")) }.orEmpty()
            val readCredits = root.optJSONArray("readCredits")?.objects()?.map { ReadCredit(it.getLong("volumeId"), it.getLong("creditedAt")) }.orEmpty()
            val data = BackupData(version, root.optLong("exportedAt"), series, volumes, collections, seriesCollections, loans, trophies, readCredits, covers)
            checkReferences(data)
            return data
        } catch (e: BackupException) {
            throw e
        } catch (e: Exception) {
            // JSONException (falta un campo), IllegalArgumentException (valor no válido)...
            throw BackupException("La copia tiene datos incompletos o no válidos.")
        }
    }

    /** Ids sin repetir y que cada tomo y relación apunte a algo que existe. */
    private fun checkReferences(data: BackupData) {
        val seriesIds = data.series.map { it.id }
        val volumeIds = data.volumes.map { it.id }
        val collectionIds = data.collections.map { it.id }
        val loanIds = data.loans.map { it.id }
        if (listOf(seriesIds, volumeIds, collectionIds, loanIds).any { it.size != it.toSet().size }) {
            throw BackupException("La copia tiene elementos repetidos.")
        }
        if (data.volumes.any { it.seriesId !in seriesIds }) throw BackupException("La copia tiene tomos sin serie.")
        if (data.seriesCollections.any { it.seriesId !in seriesIds || it.collectionId !in collectionIds }) {
            throw BackupException("La copia tiene estantes que apuntan a series que no existen.")
        }
        if (data.loans.any { it.volumeId !in volumeIds }) throw BackupException("La copia tiene préstamos de tomos que no existen.")
        if (data.loans.filter { it.isActive }.groupBy { it.volumeId }.any { it.value.size > 1 }) {
            throw BackupException("La copia tiene un tomo prestado dos veces a la vez.")
        }
    }

    private fun seriesToJson(s: Series) = JSONObject()
        .put("id", s.id)
        .put("title", s.title)
        .put("author", s.author)
        .put("type", s.type.name)
        .put("publisher", s.publisher)
        .put("language", s.language)
        .put("coverFile", s.coverPath?.let { File(it).name } ?: JSONObject.NULL)
        .put("description", s.description)
        .put("notes", s.notes)
        .put("createdAt", s.createdAt)
        .put("updatedAt", s.updatedAt)
        .put("statusInMyLanguage", s.statusInMyLanguage.name)
        .put("statusInOrigin", s.statusInOrigin.name)
        .put("publishedInMyLanguage", s.publishedInMyLanguage ?: JSONObject.NULL)
        .put("otherTitles", s.otherTitles)
        .put("originalCoverFile", s.originalCoverPath?.let { File(it).name } ?: JSONObject.NULL)

    private fun seriesFromJson(o: JSONObject, covers: Map<String, ByteArray>) = Series(
        id = o.getLong("id"),
        title = o.getString("title").also { require(it.isNotBlank()) },
        author = o.optString("author"),
        type = ItemType.valueOf(o.getString("type")),
        publisher = o.optString("publisher"),
        language = o.optString("language"),
        coverPath = coverName(o, covers),
        description = o.optString("description"),
        notes = o.optString("notes"),
        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
        updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
        statusInMyLanguage = o.nullableString("statusInMyLanguage")?.let(PublicationStatus::valueOf) ?: PublicationStatus.UNKNOWN,
        statusInOrigin = o.nullableString("statusInOrigin")?.let(PublicationStatus::valueOf) ?: PublicationStatus.UNKNOWN,
        publishedInMyLanguage = o.nullableLong("publishedInMyLanguage")?.toInt(),
        otherTitles = o.optString("otherTitles"),
        originalCoverPath = coverName(o, covers, "originalCoverFile"),
    )

    private fun volumeToJson(v: Volume) = JSONObject()
        .put("id", v.id)
        .put("seriesId", v.seriesId)
        .put("number", v.number)
        .put("title", v.title)
        .put("isbn", v.isbn)
        .put("coverFile", v.coverPath?.let { File(it).name } ?: JSONObject.NULL)
        .put("owned", v.owned)
        .put("status", v.status.name)
        .put("location", v.location)
        .put("notes", v.notes)
        .put("purchasePriority", v.purchasePriority?.name ?: JSONObject.NULL)
        .put("purchaseNote", v.purchaseNote)
        .put("wishlistAddedAt", v.wishlistAddedAt ?: JSONObject.NULL)

    private fun volumeFromJson(o: JSONObject, covers: Map<String, ByteArray>) = Volume(
        id = o.getLong("id"),
        seriesId = o.getLong("seriesId"),
        number = o.getInt("number").also { require(it > 0) },
        title = o.optString("title"),
        isbn = o.optString("isbn"),
        coverPath = coverName(o, covers),
        owned = o.getBoolean("owned"),
        status = ReadingStatus.valueOf(o.getString("status")),
        location = o.optString("location"),
        notes = o.optString("notes"),
        purchasePriority = o.nullableString("purchasePriority")?.let(PurchasePriority::valueOf),
        purchaseNote = o.optString("purchaseNote"),
        wishlistAddedAt = o.nullableLong("wishlistAddedAt"),
    ).withOwnershipRules() // copias v1: los tomos que no tengo entran en la lista con la fecha de hoy

    private fun loanToJson(l: Loan) = JSONObject()
        .put("id", l.id)
        .put("volumeId", l.volumeId)
        .put("person", l.person)
        .put("lentAt", l.lentAt)
        .put("dueAt", l.dueAt ?: JSONObject.NULL)
        .put("returnedAt", l.returnedAt ?: JSONObject.NULL)
        .put("notes", l.notes)
        .put("borrowed", l.borrowed)

    private fun loanFromJson(o: JSONObject) = Loan(
        id = o.getLong("id"),
        volumeId = o.getLong("volumeId"),
        person = o.getString("person").also { require(it.isNotBlank()) },
        lentAt = o.getLong("lentAt"),
        dueAt = o.nullableLong("dueAt"),
        returnedAt = o.nullableLong("returnedAt"),
        notes = o.optString("notes"),
        borrowed = o.optBoolean("borrowed", false),
    )

    /** Nombre de la portada si su imagen viene en la copia; si falta, sin portada (se usará la generada). */
    private fun coverName(o: JSONObject, covers: Map<String, ByteArray>, field: String = "coverFile"): String? =
        o.nullableString(field)?.takeIf { it in covers }

    /** Texto del campo, o null si no está o es null (campos opcionales y de versiones antiguas). */
    private fun JSONObject.nullableString(name: String): String? = if (has(name) && !isNull(name)) getString(name) else null

    private fun JSONObject.nullableLong(name: String): Long? = if (has(name) && !isNull(name)) getLong(name) else null

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
}
