package com.shelfio.app.data.books

import kotlinx.coroutines.Dispatchers
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
 * Busca datos bibliográficos por ISBN en Google Books y Open Library.
 * Si una de las dos falla pero la otra responde, se usan los resultados que haya.
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

    suspend fun searchByIsbn(isbn: String): LookupResult = withContext(Dispatchers.IO) {
        val key = userApiKey().trim().ifBlank { appApiKey }
        val google = runCatching { searchGoogleBooks(isbn, key) }
        val openLibrary = runCatching { searchOpenLibrary(isbn) }
        val results = google.getOrDefault(emptyList()) + openLibrary.getOrDefault(emptyList())
        when {
            results.isNotEmpty() -> LookupResult.Found(results)
            google.isFailure && openLibrary.isFailure ->
                LookupResult.Failed("No se pudo conectar. Comprueba la conexión a internet o escribe los datos a mano.")
            // Sin respuesta de Google no se puede decir que «no lo tiene»: suele ser su límite diario de consultas.
            google.isFailure ->
                LookupResult.Failed("Google Books no responde ahora (límite diario de consultas o clave no válida) y Open Library no tiene este libro. Prueba más tarde o escribe los datos a mano.")
            else -> LookupResult.NotFound
        }
    }

    /** Descarga una imagen de portada. Devuelve null si falla. */
    suspend fun downloadImage(url: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching { get(url) }.getOrNull()
    }

    private fun searchGoogleBooks(isbn: String, key: String): List<BookResult> {
        val keyParam = if (key.isBlank()) "" else "&key=" + URLEncoder.encode(key, "UTF-8")
        val androidHeaders = if (key.isBlank()) emptyMap() else mapOf("X-Android-Package" to androidPackage, "X-Android-Cert" to androidCertSha1)
        val json = JSONObject(String(get("https://www.googleapis.com/books/v1/volumes?q=isbn:$isbn$keyParam", androidHeaders)))
        val items = json.optJSONArray("items") ?: return emptyList()
        return (0 until items.length()).map { i ->
            val info = items.getJSONObject(i).getJSONObject("volumeInfo")
            BookResult(
                isbn = isbn,
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

    private fun searchOpenLibrary(isbn: String): List<BookResult> {
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
            if (connection.responseCode !in 200..299) throw IOException("HTTP ${connection.responseCode}")
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

    private companion object {
        const val TIMEOUT_MS = 10_000
    }
}
