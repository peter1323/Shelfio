package com.shelfio.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Guarda las portadas reales (fotos o descargadas por ISBN) en el almacenamiento interno de la app.
 * Cada portada nueva tiene un nombre distinto, así las pantallas notan el cambio al momento.
 * Las imágenes se reducen a [MAX_SIDE] píxeles para no llenar el móvil.
 */
class CoverStorage(context: Context) {
    private val coversDir = File(context.filesDir, "covers").apply { mkdirs() }

    /** Archivo temporal donde la cámara del sistema deja la foto antes de procesarla. */
    val cameraTempFile: File = File(context.cacheDir, "camera/photo.jpg").apply { parentFile?.mkdirs() }

    /** Procesa la foto de la cámara (gira según EXIF y reduce). Devuelve la ruta guardada o null si falla. */
    suspend fun saveCameraPhoto(): String? = withContext(Dispatchers.IO) {
        val bitmap = decodeScaled(cameraTempFile.readBytesOrNull() ?: return@withContext null) ?: return@withContext null
        val rotated = rotateFromExif(bitmap, cameraTempFile)
        cameraTempFile.delete()
        save(rotated)
    }

    /** Guarda una imagen descargada (bytes JPEG/PNG). Devuelve la ruta o null si no es una imagen válida. */
    suspend fun saveDownloaded(bytes: ByteArray): String? = withContext(Dispatchers.IO) { decodeScaled(bytes)?.let(::save) }

    /** Borra el archivo de una portada. No hace nada si la ruta es null. */
    fun delete(path: String?) {
        if (path != null) File(path).delete()
    }

    private fun save(bitmap: Bitmap): String {
        val file = File(coversDir, "cover_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        return file.absolutePath
    }

    /** Decodifica reduciendo de golpe (inSampleSize) y luego ajusta al tamaño máximo. */
    private fun decodeScaled(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null

        val scale = MAX_SIDE.toFloat() / maxOf(bitmap.width, bitmap.height)
        return if (scale >= 1f) bitmap
        else Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
    }

    private fun rotateFromExif(bitmap: Bitmap, file: File): Bitmap {
        val degrees = when (ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees) }, true)
    }

    private fun File.readBytesOrNull(): ByteArray? = if (exists() && length() > 0) readBytes() else null

    private companion object {
        const val MAX_SIDE = 1000
        const val JPEG_QUALITY = 85
    }
}
