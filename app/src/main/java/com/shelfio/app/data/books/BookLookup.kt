package com.shelfio.app.data.books

import com.shelfio.app.data.model.ItemType
import com.shelfio.app.data.model.PublicationStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/** Datos de un libro encontrados en una fuente. Los campos que la fuente no trae quedan vacíos (nunca se inventan). */
data class BookResult(
    val isbn: String,
    val title: String,
    val authors: String,
    val publisher: String,
    val language: String,
    val description: String,
    val coverUrl: String?,
    /** «Google Books», «Open Library» o «AniList», para que el usuario sepa de dónde viene. */
    val source: String,
    /** Otros títulos de la misma obra (AniList: romaji, inglés, japonés y sinónimos en varios idiomas). */
    val alternativeTitles: List<String> = emptyList(),
    /** Estado de publicación en el país de origen, si la fuente lo da (AniList). */
    val originStatus: PublicationStatus? = null,
)

/** Resultado de una búsqueda. */
sealed interface LookupResult {
    data class Found(val results: List<BookResult>) : LookupResult
    data object NotFound : LookupResult
    /** Sin conexión, servidor caído o respuesta inesperada. */
    data class Failed(val message: String) : LookupResult
}

/**
 * Busca datos bibliográficos. Por ISBN va en cascada: en cuanto una fuente lo encuentra, no se pregunta a las demás.
 *   Por ISBN (todos los tipos): Google Books → Open Library.
 *   Por título se pregunta a todas las fuentes del tipo y se juntan los resultados (ver [searchByTitle]).
 *
 * Google Books se consulta con la clave propia del usuario (Ajustes) o, si no tiene, con la de la app.
 * La clave de la app está restringida a esta app Android: Google exige las cabeceras con el paquete
 * y la huella SHA-1 del certificado con el que está firmada.
 */
class BookLookup(
    /** Clave propia de Google Books del usuario (vacía si no tiene). */
    private val userApiKey: suspend () -> String,
    private val appApiKey: String,
    private val androidPackage: String,
    private val androidCertSha1: String,
) {

    suspend fun searchByIsbn(isbn: String): LookupResult = cascade(
        { searchGoogleBooks("isbn:$isbn") },
        { searchOpenLibraryByIsbn(isbn) },
    )

    /**
     * Busca por título (escrito o leído en la portada) en todas las fuentes que encajan con el tipo y junta
     * los resultados, sin repetidos, para que el usuario elija: las ediciones (Google, Open Library) primero
     * y después la obra en AniList (manga o novela ligera).
     */
    suspend fun searchByTitle(title: String, type: ItemType): LookupResult = withContext(Dispatchers.IO) {
        val query = title.trim()
        val sources: List<suspend () -> List<BookResult>> = listOfNotNull(
            { searchGoogleBooks("intitle:$query") },
            { searchOpenLibraryByTitle(query) },
            when (type) {
                ItemType.BOOK -> ({ searchAniList(query, novel = true) })
                ItemType.MANGA -> ({ searchAniList(query, novel = false) })
                ItemType.COMIC -> null
            },
        )
        val answers = sources.map { runCatching { it() } }
        val results = withoutDuplicates(answers.flatMap { it.getOrDefault(emptyList()) })
        when {
            results.isNotEmpty() -> LookupResult.Found(results)
            answers.all { it.isFailure } -> LookupResult.Failed("No se pudo conectar. Comprueba la conexión a internet o escribe los datos a mano.")
            answers.any { it.isFailure } -> LookupResult.Failed("Alguna fuente no responde ahora y las demás no lo tienen. Prueba más tarde o escribe los datos a mano.")
            else -> LookupResult.NotFound
        }
    }

    /** Pregunta a cada fuente por orden y se queda con la primera que devuelve algo. */
    private suspend fun cascade(vararg sources: suspend () -> List<BookResult>): LookupResult = withContext(Dispatchers.IO) {
        var failures = 0
        for (source in sources) {
            val result = runCatching { source() }
            result.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return@withContext LookupResult.Found(it) }
            if (result.isFailure) failures++
        }
        when (failures) {
            0 -> LookupResult.NotFound
            sources.size -> LookupResult.Failed("No se pudo conectar. Comprueba la conexión a internet o escribe los datos a mano.")
            // Si alguna no respondió no se puede decir que «no existe»: suele ser un fallo temporal (a menudo de Google Books).
            else -> LookupResult.Failed("Alguna fuente no responde ahora y las demás no lo tienen. Prueba más tarde, busca por título o escribe los datos a mano.")
        }
    }

    /** Descarga una imagen de portada. Devuelve null si falla. */
    suspend fun downloadImage(url: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching { get(url) }.getOrNull()
    }

    /** [query] con el formato de Google: «isbn:978…» o «intitle:…». */
    private suspend fun searchGoogleBooks(query: String): List<BookResult> {
        val key = userApiKey().trim().ifBlank { appApiKey }
        val keyParam = if (key.isBlank()) "" else "&key=" + URLEncoder.encode(key, "UTF-8")
        val androidHeaders = if (key.isBlank()) emptyMap() else mapOf("X-Android-Package" to androidPackage, "X-Android-Cert" to androidCertSha1)
        val url = "https://www.googleapis.com/books/v1/volumes?q=${URLEncoder.encode(query, "UTF-8")}&maxResults=10$keyParam"
        // Google Books falla a ratos (503 «backendFailed», 429): se reintenta antes de rendirse.
        val json = JSONObject(String(retryOnServerError { get(url, androidHeaders) }))
        val items = json.optJSONArray("items") ?: return emptyList()
        return (0 until items.length()).map { i ->
            val info = items.getJSONObject(i).getJSONObject("volumeInfo")
            BookResult(
                isbn = isbn13(info.optJSONArray("industryIdentifiers")),
                title = info.optString("title"),
                authors = info.optJSONArray("authors").joinStrings(),
                publisher = info.optString("publisher"),
                language = languageName(info.optString("language")),
                description = info.optString("description"),
                // Google devuelve http; se fuerza https. zoom=1 es la miniatura más fiable.
                coverUrl = info.optJSONObject("imageLinks")?.optString("thumbnail")?.takeIf { it.isNotBlank() }
                    ?.replace("http://", "https://")?.replace("&edge=curl", ""),
                source = "Google Books",
            )
        }.filter { it.title.isNotBlank() }
    }

    /** ISBN-13 que Google da en «industryIdentifiers», o vacío si no lo trae. */
    private fun isbn13(identifiers: JSONArray?): String =
        (0 until (identifiers?.length() ?: 0)).map { identifiers!!.getJSONObject(it) }
            .firstOrNull { it.optString("type") == "ISBN_13" }?.optString("identifier").orEmpty()

    /** Búsqueda por título en Open Library (hasta 10 resultados). */
    private fun searchOpenLibraryByTitle(title: String): List<BookResult> {
        val url = "https://openlibrary.org/search.json?title=${URLEncoder.encode(title, "UTF-8")}&limit=10&fields=title,author_name,publisher,isbn,cover_i,language"
        val docs = JSONObject(String(get(url))).optJSONArray("docs") ?: return emptyList()
        return (0 until docs.length()).map { docs.getJSONObject(it) }.map { doc ->
            BookResult(
                isbn = (0 until (doc.optJSONArray("isbn")?.length() ?: 0)).map { doc.getJSONArray("isbn").getString(it) }
                    .firstOrNull { it.length == 13 }.orEmpty(),
                title = doc.optString("title"),
                authors = doc.optJSONArray("author_name").joinStrings(),
                publisher = doc.optJSONArray("publisher")?.optString(0).orEmpty(),
                language = "",
                description = "",
                coverUrl = doc.optLong("cover_i", 0).takeIf { it > 0 }?.let { "https://covers.openlibrary.org/b/id/$it-L.jpg" },
                source = "Open Library",
            )
        }.filter { it.title.isNotBlank() }
    }

    /**
     * AniList (API GraphQL gratuita, sin clave): manga ([novel] = false) o novela ligera ([novel] = true).
     * Da la obra original (no la edición española): título principal, otros títulos, autor, estado en Japón y portada.
     * El título que se propone es el que más se parece a lo buscado (a menudo un sinónimo en español).
     */
    private suspend fun searchAniList(query: String, novel: Boolean): List<BookResult> {
        val graphQl = """
            query (${'$'}search: String, ${'$'}formats: [MediaFormat]) {
              Page(perPage: 8) {
                media(search: ${'$'}search, type: MANGA, format_in: ${'$'}formats) {
                  title { romaji english native }
                  synonyms
                  status
                  description(asHtml: false)
                  coverImage { large }
                  staff(perPage: 4, sort: RELEVANCE) { edges { role node { name { full } } } }
                }
              }
            }
        """.trimIndent()
        val variables = JSONObject()
            .put("search", query)
            .put("formats", JSONArray(if (novel) listOf("NOVEL") else listOf("MANGA", "ONE_SHOT")))
        val body = JSONObject().put("query", graphQl).put("variables", variables).toString()
        val media = JSONObject(String(retryOnServerError { get("https://graphql.anilist.co", jsonBody = body) }))
            .getJSONObject("data").getJSONObject("Page").getJSONArray("media")

        return (0 until media.length()).map { media.getJSONObject(it) }.map { item ->
            val titles = item.getJSONObject("title").let { t -> listOf("romaji", "english", "native").map { t.optString(it) } }
                .plus((0 until (item.optJSONArray("synonyms")?.length() ?: 0)).map { item.getJSONArray("synonyms").getString(it) })
                .filter { it.isNotBlank() && it != "null" }
                .distinct()
            val staff = item.optJSONObject("staff")?.optJSONArray("edges")
            val writers = (0 until (staff?.length() ?: 0)).map { staff!!.getJSONObject(it) }
                .filter { it.optString("role").contains("Story", ignoreCase = true) || it.optString("role").contains("Original", ignoreCase = true) }
                .map { it.getJSONObject("node").getJSONObject("name").optString("full") }
                .ifEmpty { listOfNotNull(staff?.optJSONObject(0)?.getJSONObject("node")?.getJSONObject("name")?.optString("full")) }
            BookResult(
                isbn = "",
                title = bestTitle(query, titles),
                authors = writers.distinct().joinToString(", "),
                publisher = "",
                language = "",
                description = item.optString("description").replace(Regex("<[^>]*>"), "").trim().takeIf { it != "null" }.orEmpty(),
                coverUrl = item.optJSONObject("coverImage")?.optString("large")?.takeIf { it.isNotBlank() },
                source = "AniList",
                alternativeTitles = titles,
                originStatus = aniListStatus(item.optString("status")),
            )
        }.filter { it.title.isNotBlank() }
    }

    private fun aniListStatus(status: String): PublicationStatus? = when (status) {
        "RELEASING" -> PublicationStatus.PUBLISHING
        "FINISHED" -> PublicationStatus.FINISHED
        "HIATUS" -> PublicationStatus.PAUSED
        "CANCELLED" -> PublicationStatus.CANCELLED
        "NOT_YET_RELEASED" -> PublicationStatus.UNCONFIRMED
        else -> null
    }

    private fun searchOpenLibraryByIsbn(isbn: String): List<BookResult> {
        val json = JSONObject(String(get("https://openlibrary.org/api/books?bibkeys=ISBN:$isbn&format=json&jscmd=data")))
        val book = json.optJSONObject("ISBN:$isbn") ?: return emptyList()
        val title = book.optString("title")
        if (title.isBlank()) return emptyList()
        return listOf(
            BookResult(
                isbn = isbn,
                title = title,
                authors = book.optJSONArray("authors").joinNames(),
                publisher = book.optJSONArray("publishers").joinNames(),
                language = "", // Open Library no lo da de forma directa en esta consulta
                description = "",
                coverUrl = book.optJSONObject("cover")?.let { it.optString("large").ifBlank { it.optString("medium") } }
                    ?.takeIf { it.isNotBlank() },
                source = "Open Library",
            ),
        )
    }

    /** Petición sencilla con tiempo límite (GET, o POST si lleva [jsonBody]). Lanza IOException si el servidor no responde bien. */
    private fun get(url: String, headers: Map<String, String> = emptyMap(), jsonBody: String? = null): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.setRequestProperty("User-Agent", "Shelfio-Android")
        headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
        if (jsonBody != null) {
            // Con cuerpo se envía como POST (AniList usa GraphQL por POST).
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(jsonBody.toByteArray()) }
        }
        try {
            if (connection.responseCode !in 200..299) throw HttpError(connection.responseCode)
            return connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private fun JSONArray?.joinStrings(): String =
        if (this == null) "" else (0 until length()).joinToString(", ") { optString(it) }

    private fun JSONArray?.joinNames(): String =
        if (this == null) "" else (0 until length()).joinToString(", ") { optJSONObject(it)?.optString("name").orEmpty() }

    /** «es» → «español». Si no hay código, vacío. */
    private fun languageName(code: String): String {
        if (code.isBlank()) return ""
        val spanish = Locale.forLanguageTag("es")
        return Locale.forLanguageTag(code).getDisplayLanguage(spanish).ifBlank { code }
    }

    /** Error HTTP con su código, para saber si merece la pena reintentar. */
    private class HttpError(val code: Int) : IOException("HTTP $code")

    /** Repite [request] hasta 3 veces si el servidor responde 429 o 5xx, esperando un poco más cada vez. */
    private suspend fun <T> retryOnServerError(request: () -> T): T {
        repeat(2) { attempt ->
            try {
                return request()
            } catch (e: HttpError) {
                if (e.code != 429 && e.code < 500) throw e
                delay(1_000L * (attempt + 1))
            }
        }
        return request()
    }

    private companion object {
        const val TIMEOUT_MS = 10_000
    }
}
