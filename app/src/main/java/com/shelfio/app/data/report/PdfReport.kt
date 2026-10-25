package com.shelfio.app.data.report

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.shelfio.app.R
import com.shelfio.app.data.model.SeriesWithVolumes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.text.DateFormat
import java.time.LocalDate
import java.util.Date

/**
 * Crea el informe PDF con la API de Android PdfDocument (sin librerías). Fondo blanco para imprimir,
 * colores de Shelfio solo en títulos y sin portadas para que pese poco. Se genera en segundo plano.
 */
class PdfReport(private val context: Context) {

    fun defaultFileName(): String = "shelfio-informe-${LocalDate.now()}.pdf"

    /** Escribe el PDF en el archivo elegido por el usuario. */
    suspend fun writeTo(uri: Uri, items: List<SeriesWithVolumes>, collections: Int, options: ReportOptions, onProgress: (Float) -> Unit) {
        val output = withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri) }
            ?: throw IllegalStateException("No se pudo abrir el archivo")
        output.use { write(it, items, collections, options, onProgress) }
    }

    /** Escribe el PDF en la caché para compartirlo. */
    suspend fun writeForSharing(items: List<SeriesWithVolumes>, collections: Int, options: ReportOptions, onProgress: (Float) -> Unit): File {
        val file = File(context.cacheDir, "reports/${defaultFileName()}").apply { parentFile?.mkdirs() }
        file.outputStream().use { write(it, items, collections, options, onProgress) }
        return file
    }

    private suspend fun write(output: OutputStream, items: List<SeriesWithVolumes>, collections: Int, options: ReportOptions, onProgress: (Float) -> Unit) =
        withContext(Dispatchers.Default) {
            val document = PdfDocument()
            try {
                val writer = PageWriter(document)
                drawCover(writer, reportTotals(items, collections))
                val blocks = reportContent(items, options)
                blocks.forEachIndexed { index, block ->
                    ensureActive() // permite cancelar a mitad
                    writer.draw(block)
                    onProgress((index + 1).toFloat() / blocks.size)
                }
                writer.finish()
                withContext(Dispatchers.IO) { document.writeTo(output) }
            } finally {
                document.close()
            }
        }

    private fun drawCover(writer: PageWriter, totals: ReportTotals) {
        // Logo reducido a 160 px: el PDF guarda la imagen tal cual y a tamaño completo pesaría mucho.
        BitmapFactory.decodeResource(context.resources, R.drawable.logo_shelfio)?.let { Bitmap.createScaledBitmap(it, 160, 160, true) }?.let { logo ->
            writer.canvas.drawBitmap(logo, null, RectF(MARGIN, writer.y, MARGIN + 110f, writer.y + 110f), null)
            writer.y += 130f
        }
        writer.text("Informe de mi colección", titlePaint(26f, BLUE))
        writer.text(DateFormat.getDateInstance(DateFormat.LONG).format(Date()), textPaint(12f, GREY))
        writer.y += 16f
        writer.text(
            "Libros: ${totals.books}   ·   Mangas: ${totals.mangas}   ·   Cómics: ${totals.comics}\n" +
                "Tomos que tengo: ${totals.volumes}   ·   Colecciones: ${totals.collections}",
            textPaint(13f, Color.BLACK),
        )
        writer.newPage()
    }

    /** Lleva la página actual y la posición vertical; pasa a otra página cuando el texto no cabe. */
    private class PageWriter(private val document: PdfDocument) {
        private var pageNumber = 0
        private lateinit var page: PdfDocument.Page
        lateinit var canvas: Canvas
        var y = MARGIN

        init {
            startPage()
        }

        private fun startPage() {
            pageNumber++
            page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            canvas = page.canvas
            y = MARGIN
        }

        fun newPage() {
            document.finishPage(page)
            startPage()
        }

        fun finish() = document.finishPage(page)

        fun draw(block: ReportBlock) = when (block) {
            is ReportBlock.Heading -> { y += 10f; text(block.text, titlePaint(18f, VIOLET)) }
            is ReportBlock.SeriesTitle -> text(block.text, titlePaint(13f, BLUE))
            is ReportBlock.Line -> text(block.text, textPaint(10.5f, Color.BLACK))
            ReportBlock.Gap -> { y += 8f }
        }

        /** Dibuja texto partido en líneas al ancho de la página; nunca se sale por los lados ni por abajo. */
        fun text(text: String, paint: TextPaint) {
            val width = (PAGE_WIDTH - 2 * MARGIN).toInt()
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .build()
            if (y + layout.height > PAGE_HEIGHT - MARGIN) newPage()
            canvas.save()
            canvas.translate(MARGIN, y)
            layout.draw(canvas)
            canvas.restore()
            y += layout.height + 4f
        }
    }

    private companion object {
        // A4 en puntos (1/72 de pulgada)
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842
        const val MARGIN = 40f
        val BLUE = Color.parseColor("#247BFF")
        val VIOLET = Color.parseColor("#803BFF")
        val GREY = Color.parseColor("#5A6680")

        fun textPaint(size: Float, color: Int) = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply { textSize = size; this.color = color }
        fun titlePaint(size: Float, color: Int) = textPaint(size, color).apply { typeface = Typeface.DEFAULT_BOLD }
    }
}
