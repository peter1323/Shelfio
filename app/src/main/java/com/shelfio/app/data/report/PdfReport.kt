package com.shelfio.app.data.report

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.shelfio.app.R
import com.shelfio.app.data.model.ItemType
import com.shelfio.app.data.model.Series
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
 * Crea el informe PDF con la API de Android PdfDocument (sin librerías), con el estilo de Shelfio:
 * portada oscura con el logo y los totales, y páginas claras (para poder imprimirlas) con una ficha
 * por serie que lleva su portada. Las portadas se reducen para que el PDF no pese demasiado.
 */
class PdfReport(private val context: Context) {

    fun defaultFileName(): String = "shelfio-informe-${LocalDate.now()}.pdf"

    /** Escribe el PDF en el archivo elegido por el usuario. */
    suspend fun writeTo(uri: Uri, items: List<SeriesWithVolumes>, collections: List<ReportCollection>, options: ReportOptions, onProgress: (Float) -> Unit) {
        val output = withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri) }
            ?: throw IllegalStateException("No se pudo abrir el archivo")
        output.use { write(it, items, collections, options, onProgress) }
    }

    /** Escribe el PDF en la caché para compartirlo. */
    suspend fun writeForSharing(items: List<SeriesWithVolumes>, collections: List<ReportCollection>, options: ReportOptions, onProgress: (Float) -> Unit): File {
        val file = File(context.cacheDir, "reports/${defaultFileName()}").apply { parentFile?.mkdirs() }
        file.outputStream().use { write(it, items, collections, options, onProgress) }
        return file
    }

    private suspend fun write(
        output: OutputStream,
        items: List<SeriesWithVolumes>,
        collections: List<ReportCollection>,
        options: ReportOptions,
        onProgress: (Float) -> Unit,
    ) = withContext(Dispatchers.Default) {
        val document = PdfDocument()
        // Logo reducido: el PDF guarda la imagen tal cual y a tamaño completo pesaría mucho.
        val logo = BitmapFactory.decodeResource(context.resources, R.drawable.logo_shelfio)?.let { Bitmap.createScaledBitmap(it, 200, 200, true) }
        try {
            drawCoverPage(document, logo, reportTotals(items, collections.size))
            val writer = PageWriter(document, logo, today())
            val blocks = reportContent(items, collections, options)
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

    private fun today(): String = DateFormat.getDateInstance(DateFormat.LONG).format(Date())

    /** Primera página: fondo oscuro de la app, logo, título y tarjetas con los totales. */
    private fun drawCoverPage(document: PdfDocument, logo: Bitmap?, totals: ReportTotals) {
        val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create())
        val canvas = page.canvas
        canvas.drawColor(NAVY)

        // Tarjeta principal con el degradado azul → violeta del logotipo.
        val hero = RectF(MARGIN, 70f, PAGE_WIDTH - MARGIN, 360f)
        canvas.drawRoundRect(hero, 24f, 24f, gradientPaint(hero, BLUE, VIOLET))
        logo?.let { drawLogo(canvas, it, RectF(hero.centerX() - 60f, hero.top + 36f, hero.centerX() + 60f, hero.top + 156f)) }
        drawCentered(canvas, "Informe de mi colección", hero.centerX(), hero.top + 200f, boldPaint(26f, Color.WHITE))
        drawCentered(canvas, today(), hero.centerX(), hero.top + 232f, textPaint(13f, Color.argb(220, 255, 255, 255)))
        drawCentered(canvas, "Shelfio · Toda tu colección, en un solo lugar.", hero.centerX(), hero.top + 262f, textPaint(11f, Color.argb(190, 255, 255, 255)))

        // Totales en tarjetas, como los contadores de Inicio.
        val stats = listOf(
            Triple("Libros", totals.books, BLUE),
            Triple("Mangas", totals.mangas, VIOLET),
            Triple("Cómics", totals.comics, CYAN),
            Triple("Otros", totals.others, SLATE),
            Triple("Tomos que tengo", totals.volumes, BLUE),
            Triple("Colecciones", totals.collections, VIOLET),
        )
        val gap = 14f
        val cardWidth = (PAGE_WIDTH - 2 * MARGIN - 2 * gap) / 3
        stats.forEachIndexed { index, (label, value, color) ->
            val left = MARGIN + (index % 3) * (cardWidth + gap)
            val top = 400f + (index / 3) * (110f + gap)
            val card = RectF(left, top, left + cardWidth, top + 110f)
            canvas.drawRoundRect(card, 18f, 18f, fillPaint(SURFACE))
            canvas.drawRoundRect(RectF(card.left + 18f, card.top + 16f, card.left + 58f, card.top + 21f), 3f, 3f, fillPaint(color))
            canvas.drawText(value.toString(), card.left + 18f, card.top + 66f, boldPaint(32f, Color.WHITE))
            canvas.drawText(label, card.left + 18f, card.top + 92f, textPaint(12f, SOFT_TEXT))
        }
        drawCentered(canvas, "Creado con Shelfio", PAGE_WIDTH / 2f, PAGE_HEIGHT - 40f, textPaint(10f, SOFT_TEXT))
        document.finishPage(page)
    }

    /** Lleva la página actual y la posición vertical; pasa a otra página cuando algo no cabe. */
    private inner class PageWriter(private val document: PdfDocument, private val logo: Bitmap?, private val date: String) {
        private var pageNumber = 1 // la 1 es la portada
        private lateinit var page: PdfDocument.Page
        private lateinit var canvas: Canvas
        private var y = 0f

        init {
            startPage()
        }

        /** Página clara con la franja oscura de la app arriba y el número de página abajo. */
        private fun startPage() {
            pageNumber++
            page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            canvas = page.canvas
            canvas.drawColor(PAPER)
            canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), HEADER_HEIGHT, fillPaint(NAVY))
            canvas.drawRect(0f, HEADER_HEIGHT - 3f, PAGE_WIDTH.toFloat(), HEADER_HEIGHT, gradientPaint(RectF(0f, 0f, PAGE_WIDTH.toFloat(), 3f), BLUE, VIOLET))
            logo?.let { drawLogo(canvas, it, RectF(MARGIN, 10f, MARGIN + 26f, 36f)) }
            canvas.drawText("Shelfio · Informe de mi colección", MARGIN + 36f, 28f, boldPaint(12f, Color.WHITE))
            val datePaint = textPaint(10f, SOFT_TEXT).apply { textAlign = Paint.Align.RIGHT }
            canvas.drawText(date, PAGE_WIDTH - MARGIN, 28f, datePaint)
            drawCentered(canvas, "Página $pageNumber", PAGE_WIDTH / 2f, PAGE_HEIGHT - 20f, textPaint(9f, GREY))
            y = HEADER_HEIGHT + 24f
        }

        /** Pasa de página si no caben [height] puntos más. */
        private fun ensureSpace(height: Float) {
            if (y + height > PAGE_HEIGHT - BOTTOM_MARGIN) {
                document.finishPage(page)
                startPage()
            }
        }

        fun finish() = document.finishPage(page)

        fun draw(block: ReportBlock) = when (block) {
            is ReportBlock.Heading -> heading(block)
            is ReportBlock.SubHeading -> subHeading(block.text)
            is ReportBlock.SeriesCard -> seriesCard(block)
            is ReportBlock.CoverRow -> coverRow(block.tiles)
            is ReportBlock.Line -> line(block.text)
        }

        /** Balda de portadas: [COVERS_PER_ROW] huecos iguales, cada uno con su portada y el título debajo. */
        private fun coverRow(tiles: List<CoverTile>) {
            val gap = 12f
            val tileWidth = (PAGE_WIDTH - 2 * MARGIN - (COVERS_PER_ROW - 1) * gap) / COVERS_PER_ROW
            val coverHeight = tileWidth * 1.5f
            val titlePaint = boldPaint(8.5f, INK)
            val titles = tiles.map { tile ->
                StaticLayout.Builder.obtain(tile.series.title, 0, tile.series.title.length, titlePaint, tileWidth.toInt())
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setMaxLines(2)
                    .setEllipsize(TextUtils.TruncateAt.END)
                    .build()
            }
            val height = coverHeight + 6f + (titles.maxOfOrNull { it.height } ?: 0)
            ensureSpace(height)
            tiles.forEachIndexed { index, tile ->
                val left = MARGIN + index * (tileWidth + gap)
                drawSeriesCover(canvas, tile.series, tile.volumeNumber, RectF(left, y, left + tileWidth, y + coverHeight))
                drawLayout(titles[index], left, y + coverHeight + 6f)
            }
            y += height + 14f
        }

        /** Barra con el degradado de la app y el título de la sección en blanco. */
        private fun heading(block: ReportBlock.Heading) {
            ensureSpace(HEADING_HEIGHT + CARD_MIN_HEIGHT) // que el título no se quede solo al final de la página
            val bar = RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + HEADING_HEIGHT)
            canvas.drawRoundRect(bar, 12f, 12f, gradientPaint(bar, BLUE, VIOLET))
            canvas.drawText(block.text, bar.left + 16f, bar.centerY() + 6f, boldPaint(16f, Color.WHITE))
            if (block.subtitle.isNotBlank()) {
                val paint = textPaint(10f, Color.argb(220, 255, 255, 255)).apply { textAlign = Paint.Align.RIGHT }
                canvas.drawText(block.subtitle, bar.right - 16f, bar.centerY() + 4f, paint)
            }
            y = bar.bottom + 14f
        }

        /** Nombre de una colección con una marca violeta a la izquierda. */
        private fun subHeading(text: String) {
            ensureSpace(26f + CARD_MIN_HEIGHT)
            canvas.drawRoundRect(RectF(MARGIN, y, MARGIN + 4f, y + 18f), 2f, 2f, fillPaint(VIOLET))
            canvas.drawText(text, MARGIN + 12f, y + 14f, boldPaint(13f, INK))
            y += 28f
        }

        /** Ficha: portada a la izquierda; título, etiqueta del tipo y datos a la derecha. */
        private fun seriesCard(block: ReportBlock.SeriesCard) {
            val textLeft = MARGIN + CARD_PADDING * 2 + COVER_WIDTH
            val textWidth = (PAGE_WIDTH - MARGIN - CARD_PADDING - textLeft).toInt()
            val title = layout(block.series.title, boldPaint(12.5f, INK), textWidth)
            val details = layout(block.lines.joinToString("\n"), textPaint(9.5f, BODY), textWidth)
            val textHeight = title.height + CHIP_HEIGHT + 10f + details.height
            val height = maxOf(COVER_HEIGHT, textHeight) + CARD_PADDING * 2
            ensureSpace(height)

            val card = RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + height)
            canvas.drawRoundRect(card, 12f, 12f, fillPaint(CARD))
            val cover = RectF(card.left + CARD_PADDING, card.top + CARD_PADDING, card.left + CARD_PADDING + COVER_WIDTH, card.top + CARD_PADDING + COVER_HEIGHT)
            drawSeriesCover(canvas, block.series, block.volumeNumber, cover)

            var textTop = card.top + CARD_PADDING
            drawLayout(title, textLeft, textTop)
            textTop += title.height + 4f
            drawChip(block.series.type, textLeft, textTop)
            textTop += CHIP_HEIGHT + 6f
            drawLayout(details, textLeft, textTop)
            y = card.bottom + 10f
        }

        private fun line(text: String) {
            val layout = layout(text, textPaint(10.5f, GREY), (PAGE_WIDTH - 2 * MARGIN).toInt())
            ensureSpace(layout.height + 8f)
            drawLayout(layout, MARGIN, y)
            y += layout.height + 12f
        }

        /** Etiqueta redondeada con el color del tipo («Manga» en violeta…). */
        private fun drawChip(type: ItemType, left: Float, top: Float) {
            val paint = boldPaint(8.5f, onTypeColor(type))
            val width = paint.measureText(type.label) + 16f
            canvas.drawRoundRect(RectF(left, top, left + width, top + CHIP_HEIGHT), CHIP_HEIGHT / 2, CHIP_HEIGHT / 2, fillPaint(typeColor(type)))
            canvas.drawText(type.label, left + 8f, top + CHIP_HEIGHT - 4.5f, paint)
        }

        private fun drawLayout(layout: StaticLayout, left: Float, top: Float) {
            canvas.save()
            canvas.translate(left, top)
            layout.draw(canvas)
            canvas.restore()
        }
    }

    /** Logo con las esquinas redondeadas (la imagen trae esquinas blancas que se verían sobre el fondo oscuro). */
    private fun drawLogo(canvas: Canvas, logo: Bitmap, rect: RectF) {
        val radius = rect.width() * 0.22f
        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) })
        canvas.drawBitmap(logo, null, rect, Paint(Paint.FILTER_BITMAP_FLAG))
        canvas.restore()
    }

    /** Portada real (reducida) o, si no hay o no se puede leer, la portada generada de la app. */
    private fun drawSeriesCover(canvas: Canvas, series: Series, volumeNumber: Int?, rect: RectF) {
        val clip = Path().apply { addRoundRect(rect, 6f, 6f, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(clip)
        val bitmap = series.coverPath?.let(::decodeThumbnail)
        if (bitmap != null) {
            canvas.drawBitmap(bitmap, null, rect, Paint(Paint.FILTER_BITMAP_FLAG))
            bitmap.recycle()
        } else {
            drawGeneratedCover(canvas, series, volumeNumber, rect)
        }
        canvas.restore()
    }

    /** Igual que GeneratedCover de la app: degradado del color del tipo, número (manga) y título. */
    private fun drawGeneratedCover(canvas: Canvas, series: Series, volumeNumber: Int?, rect: RectF) {
        val color = typeColor(series.type)
        canvas.drawRect(rect, gradientPaint(rect, blend(color, Color.WHITE, 0.12f), blend(color, NAVY, 0.35f), vertical = true))
        val content = onTypeColor(series.type)
        // Los tamaños crecen con la portada, como en la app (fichas pequeñas y baldas grandes).
        val unit = rect.width() / 100f
        if (series.type == ItemType.MANGA && volumeNumber != null) {
            drawCentered(canvas, volumeNumber.toString(), rect.centerX(), rect.top + 34f * unit, boldPaint(28f * unit, content))
        }
        val title = StaticLayout.Builder.obtain(series.title, 0, series.title.length, boldPaint(maxOf(7f, 13f * unit), content), (rect.width() - 8f).toInt())
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(4)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        canvas.save()
        canvas.translate(rect.left + 4f, rect.centerY() - title.height / 2f + 6f)
        title.draw(canvas)
        canvas.restore()
    }

    /** Lee la portada reducida a [THUMB_WIDTH] píxeles de ancho. null si el archivo no existe o no es una imagen. */
    private fun decodeThumbnail(path: String): Bitmap? {
        if (!File(path).exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= THUMB_WIDTH) sample *= 2
        val decoded = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        if (decoded.width <= THUMB_WIDTH) return decoded
        val scaled = Bitmap.createScaledBitmap(decoded, THUMB_WIDTH, decoded.height * THUMB_WIDTH / decoded.width, true)
        if (scaled != decoded) decoded.recycle()
        return scaled
    }

    private companion object {
        // A4 en puntos (1/72 de pulgada)
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842
        const val MARGIN = 36f
        const val BOTTOM_MARGIN = 44f
        const val HEADER_HEIGHT = 46f
        const val HEADING_HEIGHT = 36f
        const val CARD_PADDING = 10f
        const val COVER_WIDTH = 52f
        const val COVER_HEIGHT = 78f
        const val CARD_MIN_HEIGHT = COVER_HEIGHT + 2 * CARD_PADDING
        const val CHIP_HEIGHT = 15f
        const val THUMB_WIDTH = 110

        // Colores de la app (ui/theme/Color.kt) y tonos claros para las páginas que se imprimen.
        val NAVY = Color.parseColor("#071329")
        val SURFACE = Color.parseColor("#0D1D3A")
        val BLUE = Color.parseColor("#247BFF")
        val VIOLET = Color.parseColor("#803BFF")
        val CYAN = Color.parseColor("#20D9F2")
        val SLATE = Color.parseColor("#5E7299")
        val SOFT_TEXT = Color.parseColor("#AAB8D6")
        val PAPER = Color.parseColor("#F7F9FF")
        val CARD = Color.parseColor("#E9EFFC")
        val INK = Color.parseColor("#0B1630")
        val BODY = Color.parseColor("#33405C")
        val GREY = Color.parseColor("#5A6680")

        fun typeColor(type: ItemType) = when (type) {
            ItemType.BOOK -> BLUE
            ItemType.MANGA -> VIOLET
            ItemType.COMIC -> CYAN
            ItemType.OTHER -> SLATE
        }

        /** Texto oscuro sobre el cian; blanco sobre los demás (como en la app). */
        fun onTypeColor(type: ItemType) = if (type == ItemType.COMIC) NAVY else Color.WHITE

        fun textPaint(size: Float, color: Int) = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply { textSize = size; this.color = color }
        fun boldPaint(size: Float, color: Int) = textPaint(size, color).apply { typeface = Typeface.DEFAULT_BOLD }
        fun fillPaint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

        fun gradientPaint(rect: RectF, from: Int, to: Int, vertical: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = if (vertical) LinearGradient(rect.left, rect.top, rect.left, rect.bottom, from, to, Shader.TileMode.CLAMP)
            else LinearGradient(rect.left, rect.top, rect.right, rect.bottom, from, to, Shader.TileMode.CLAMP)
        }

        /** Mezcla dos colores ([amount] = cuánto de [other]). */
        fun blend(color: Int, other: Int, amount: Float): Int = Color.rgb(
            (Color.red(color) + (Color.red(other) - Color.red(color)) * amount).toInt(),
            (Color.green(color) + (Color.green(other) - Color.green(color)) * amount).toInt(),
            (Color.blue(color) + (Color.blue(other) - Color.blue(color)) * amount).toInt(),
        )

        fun layout(text: String, paint: TextPaint, width: Int): StaticLayout =
            StaticLayout.Builder.obtain(text, 0, text.length, paint, width).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()

        fun drawCentered(canvas: Canvas, text: String, centerX: Float, baseline: Float, paint: TextPaint) {
            canvas.drawText(text, centerX - paint.measureText(text) / 2f, baseline, paint)
        }
    }
}
