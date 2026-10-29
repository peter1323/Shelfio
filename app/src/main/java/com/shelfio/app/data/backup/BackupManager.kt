package com.shelfio.app.data.backup

import android.content.Context
import android.net.Uri
import com.shelfio.app.BuildConfig
import com.shelfio.app.data.CoverStorage
import com.shelfio.app.data.db.ShelfioDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Exporta e importa la biblioteca completa en un .zip: datos.json + carpeta portadas/.
 * El archivo se lee y escribe con el selector del sistema (Uri), así que no hace falta ningún permiso.
 */
class BackupManager(
    private val context: Context,
    private val dao: ShelfioDao,
    private val covers: CoverStorage,
) {

    /** Nombre por defecto: shelfio-copia-AAAA-MM-DD.zip */
    fun defaultFileName(): String = "shelfio-copia-${LocalDate.now()}.zip"

    /** Escribe la copia en el archivo que eligió el usuario. */
    suspend fun exportTo(uri: Uri) = withContext(Dispatchers.IO) {
        val output = context.contentResolver.openOutputStream(uri) ?: throw BackupException("No se pudo abrir el archivo para guardar.")
        output.use { writeZip(it) }
    }

    /** Crea la copia en la caché de la app para compartirla (Drive, correo…). */
    suspend fun exportForSharing(): File = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "backups/${defaultFileName()}").apply { parentFile?.mkdirs() }
        file.outputStream().use { writeZip(it) }
        file
    }

    /** Lee y valida la copia sin tocar la biblioteca. */
    suspend fun read(uri: Uri): BackupData = withContext(Dispatchers.IO) {
        val input = try {
            context.contentResolver.openInputStream(uri)
        } catch (e: Exception) {
            null
        } ?: throw BackupException("No se pudo abrir el archivo.")
        input.use { readZip(it) }
    }

    /**
     * Sustituye toda la biblioteca por la copia. Primero se guardan las portadas nuevas; si la base de datos
     * falla, se borran y la biblioteca queda como estaba. Solo al final se borran las portadas antiguas.
     */
    suspend fun restore(data: BackupData) = withContext(Dispatchers.IO) {
        val newPaths = data.covers.mapValues { (_, bytes) -> covers.saveRestored(bytes) }
        try {
            dao.replaceAll(
                series = data.series.map { it.copy(coverPath = it.coverPath?.let(newPaths::get), originalCoverPath = it.originalCoverPath?.let(newPaths::get)) },
                volumes = data.volumes.map { it.copy(coverPath = it.coverPath?.let(newPaths::get)) },
                collections = data.collections.map { it.copy(coverPath = it.coverPath?.let(newPaths::get)) },
                seriesCollections = data.seriesCollections,
                loans = data.loans,
                trophies = data.trophies,
                readCredits = data.readCredits,
            )
        } catch (e: Exception) {
            newPaths.values.forEach(covers::delete)
            throw BackupException("No se pudo importar la copia. Tu biblioteca no ha cambiado.")
        }
        covers.deleteAllExcept(newPaths.values.toSet())
    }

    // --- ZIP ---

    internal suspend fun writeZip(output: OutputStream) {
        val series = dao.allSeries()
        val volumes = dao.allVolumes()
        val collections = dao.allCollections()
        val json = BackupFormat.toJson(
            appVersion = BuildConfig.VERSION_NAME,
            exportedAt = System.currentTimeMillis(),
            series = series,
            volumes = volumes,
            collections = collections,
            seriesCollections = dao.allSeriesCollections(),
            loans = dao.allLoans(),
            trophies = dao.allTrophies(),
            readCredits = dao.allReadCredits(),
        )
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry(BackupFormat.DATA_FILE))
            zip.write(json.toByteArray())
            zip.closeEntry()
            // Solo las portadas reales; las generadas se dibujan solas.
            (series.flatMap { listOfNotNull(it.coverPath, it.originalCoverPath) } + volumes.mapNotNull { it.coverPath } + collections.mapNotNull { it.coverPath })
                .map(::File).filter { it.exists() }.distinctBy { it.name }
                .forEach { file ->
                    zip.putNextEntry(ZipEntry(BackupFormat.COVERS_FOLDER + file.name))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
        }
    }

    internal fun readZip(input: InputStream): BackupData {
        var json: String? = null
        val coverFiles = mutableMapOf<String, ByteArray>()
        var totalBytes = 0L
        try {
            ZipInputStream(input).use { zip ->
                generateSequence { zip.nextEntry }.forEach { entry ->
                    val bytes = zip.readLimited()
                    totalBytes += bytes.size
                    if (totalBytes > MAX_TOTAL_BYTES) throw BackupException("La copia es demasiado grande.")
                    val name = entry.name
                    when {
                        name == BackupFormat.DATA_FILE -> json = String(bytes)
                        // Solo nombres simples dentro de portadas/: nada de rutas con «..» ni subcarpetas.
                        name.startsWith(BackupFormat.COVERS_FOLDER) && !entry.isDirectory -> {
                            val fileName = name.removePrefix(BackupFormat.COVERS_FOLDER)
                            if (fileName.isNotEmpty() && '/' !in fileName && '\\' !in fileName && fileName != "..") coverFiles[fileName] = bytes
                        }
                    }
                }
            }
        } catch (e: BackupException) {
            throw e
        } catch (e: Exception) {
            throw BackupException("El archivo no es un .zip válido o está dañado.")
        }
        return BackupFormat.parse(json ?: throw BackupException("No es una copia de seguridad de Shelfio (falta datos.json)."), coverFiles)
    }

    /** Lee la entrada actual con un tope, para que un archivo malicioso no llene la memoria. */
    private fun ZipInputStream.readLimited(): ByteArray {
        val buffer = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(8 * 1024)
        while (true) {
            val read = read(chunk)
            if (read < 0) break
            buffer.write(chunk, 0, read)
            if (buffer.size() > MAX_ENTRY_BYTES) throw BackupException("La copia tiene un archivo demasiado grande.")
        }
        return buffer.toByteArray()
    }

    private companion object {
        const val MAX_ENTRY_BYTES = 30L * 1024 * 1024
        const val MAX_TOTAL_BYTES = 500L * 1024 * 1024
    }
}
