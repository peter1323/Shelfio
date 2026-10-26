package com.shelfio.app.data.books

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

/** Datos de un libro encontrados por ISBN. Los campos que la API no trae quedan vacíos (nunca se inventan). */
data class BookResult(
    val isbn: String,
    val title: String,
    val authors: String,
    val publisher: String,
    val language: String,
    val description: String,
    val coverUrl: String?,
    /** «Google Books» u «Open Library», para que el usuario sepa de dónde viene. */
    val source: String,
)

/** Resultado de una búsqueda por ISBN. */
sealed interface LookupResult {
    data class Found(val results: List<BookResult>) : LookupResult
    data object NotFound : LookupResult
    /** Sin conexión, servidor caído o respuesta inesperada. */
    data class Failed(val message: String) : LookupResult
}

/**
 * Busca datos bibliográficos en Google Books y, si no los tiene, en Open Library (en cascada).
 * Se pregunta de una en una: si Google lo encuentra, Open Library ni se consulta.
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

    /** Busca por ISBN en cascada: primero Google Books; solo si no lo encuentra, Open Library. */
    suspend fun searchByIsbn(isbn: String): LookupResult = cascade(
        google = { key -> searchGoogleBooks("isbn:$isbn", key) },
        openLibrary = { searchOpenLibraryByIsbn(isbn) },
    )

    /** Busca por título (por ejemplo, el leído en la portada) con la misma cascada. */
    suspend fun searchByTitle(title: String): LookupResult = cascade(
        google = { key -> searchGoogleBooks("intitle:${title.trim()}", key) },
        openLibrary = { searchOpenLibraryByTitle(title.trim()) },
    )

    private suspend fun cascade(
        google: suspend (key: String) -> List<BookResult>,
        openLibrary: () -> List<BookResult>,
    ): LookupResult = withContext(Dispatchers.IO) {
        val googleResult = runCatching { google(userApiKey().trim().ifBlank { appApiKey }) }
        googleResult.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return@withContext LookupResult.Found(it) }

        val openLibraryResult = runCatching { openLibrary() }
        openLibraryResult.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return@withContext LookupResult.Found(it) }

        when {
            googleResult.isFailure && openLibraryResult.isFailure ->
                LookupResult.Failed("No se pudo conectar. Comprueba la conexión a internet o escribe los datos a mano.")
            // Sin respuesta de Google no se puede decir que «no lo tiene»: suele ser un fallo temporal suyo.
            googleResult.isFailure ->
                LookupResult.Failed("Google Books no responde ahora y Open Library no lo tiene. Prueba más tarde, escanea la portada o escribe los datos a mano.")
            else -> LookupResult.NotFound
        }
    }

    /** Descarga una imagen de portada. Devuelve null si falla. */
    suspend fun downloadImage(url: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching { get(url) }.getOrNull()
    }

    /** [query] con el formato de Google: «isbn:978…» o «intitle:…». */
    private suspend fun searchGoogleBooks(query: String, key: String): List<BookResult> {
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

    /** GET sencillo con tiempo límite. Lanza IOException si el servidor no responde bien. */
    private fun get(url: String, headers: Map<String, String> = emptyMap()): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.setRequestProperty("User-Agent", "Shelfio-Android")
        headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
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
