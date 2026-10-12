package com.hcdc.legalease.feature.generator

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.scale
import androidx.core.graphics.withTranslation
import com.hcdc.legalease.core.domain.di.IoDispatcher
import com.hcdc.legalease.core.domain.engine.ContractDocuments
import com.hcdc.legalease.core.domain.engine.PaperSize
import com.hcdc.legalease.core.domain.generator.ContractSection
import com.hcdc.legalease.core.domain.generator.RenderedContract
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import com.hcdc.legalease.core.designsystem.R as DesignR

internal class AndroidContractDocuments @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ContractDocuments {

    override suspend fun pageTexts(contract: RenderedContract, size: PaperSize): List<String> =
        withContext(ioDispatcher) { paginate(contract, size).map { it.text } }

    override suspend fun export(contract: RenderedContract, size: PaperSize, fileName: String): File =
        withContext(ioDispatcher) {
            val dir = File(context.cacheDir, EXPORT_DIR)
            // Exports contain personal data; keep only the latest one.
            dir.listFiles()?.forEach { it.deleteRecursively() }
            dir.mkdirs()
            File(dir, fileName).also { writePdf(contract, size, it) }
        }

    override suspend fun write(contract: RenderedContract, size: PaperSize, target: File) =
        withContext(ioDispatcher) { writePdf(contract, size, target) }

    private fun writePdf(contract: RenderedContract, size: PaperSize, file: File) {
        val pages = paginate(contract, size)
        val document = PdfDocument()
        val logo = loadLogo()
        try {
            pages.forEachIndexed { index, page ->
                val info = PdfDocument.PageInfo.Builder(size.widthPt, size.heightPt, index + 1).create()
                val pdfPage = document.startPage(info)
                val canvas = pdfPage.canvas
                logo?.let { drawLogo(canvas, it) }
                page.blocks.forEach { drawBlock(canvas, it) }
                drawFooter(canvas, size, number = index + 1, total = pages.size)
                document.finishPage(pdfPage)
            }
            file.outputStream().use { document.writeTo(it) }
        } finally {
            document.close()
            logo?.recycle()
        }
    }

    private fun paginate(contract: RenderedContract, size: PaperSize): List<LaidOutPage> {
        val styles = Styles(
            title = paint(font(DesignR.font.roboto_slab_bold, Typeface.DEFAULT_BOLD), TITLE_SIZE_PT),
            heading = paint(font(DesignR.font.roboto_slab_bold, Typeface.DEFAULT_BOLD), BODY_SIZE_PT),
            body = paint(font(DesignR.font.inter_regular, Typeface.DEFAULT), BODY_SIZE_PT),
            width = (size.widthPt - 2 * MARGIN_PT).toInt(),
        )
        val builder = PageBuilder(
            top = HEADER_TOP_PT + HEADER_LOGO_PT + HEADER_GAP_PT,
            bottom = size.heightPt - FOOTER_BOTTOM_PT - FOOTER_GAP_PT,
        )
        builder.place(styles.layout(contract.title, styles.title, Layout.Alignment.ALIGN_CENTER), keepWhole = true)
        builder.gap(SECTION_GAP_PT)
        contract.sections.forEach { section -> placeSection(builder, styles, section) }
        return builder.finish()
    }

    private fun placeSection(builder: PageBuilder, styles: Styles, section: ContractSection) {
        val acknowledgment = section.kind == ContractSection.Kind.ACKNOWLEDGMENT
        if (acknowledgment) builder.breakPage()
        val paragraphs = section.paragraphs.map { styles.layout(it, styles.body, Layout.Alignment.ALIGN_NORMAL) }
        section.heading?.let { heading ->
            val align = if (acknowledgment) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL
            // Keep a heading with at least the first two lines of its text.
            val next = paragraphs.firstOrNull()?.let { it.getLineBottom(minOf(1, it.lineCount - 1)).toFloat() } ?: 0f
            builder.place(styles.layout(heading, styles.heading, align), keepWhole = true, keepWithNext = next)
            builder.gap(HEADING_GAP_PT)
        }
        val keepWhole = acknowledgment || section.kind == ContractSection.Kind.SIGNATURES
        paragraphs.forEach {
            builder.place(it, keepWhole = keepWhole)
            builder.gap(PARAGRAPH_GAP_PT)
        }
        builder.gap(SECTION_GAP_PT - PARAGRAPH_GAP_PT)
    }

    private fun drawLogo(canvas: Canvas, logo: Bitmap) {
        val width = HEADER_LOGO_PT * logo.width / logo.height
        val target = RectF(MARGIN_PT, HEADER_TOP_PT, MARGIN_PT + width, HEADER_TOP_PT + HEADER_LOGO_PT)
        canvas.drawBitmap(logo, null, target, Paint(Paint.FILTER_BITMAP_FLAG))
    }

    private fun drawBlock(canvas: Canvas, block: PlacedBlock) {
        val layout = block.layout
        val top = layout.getLineTop(block.firstLine).toFloat()
        canvas.withTranslation(MARGIN_PT, block.y - top) {
            clipRect(0f, top, layout.width.toFloat(), layout.getLineBottom(block.lastLine).toFloat())
            layout.draw(this)
        }
    }

    /** Footer: the review reminder and the page number. */
    private fun drawFooter(canvas: Canvas, size: PaperSize, number: Int, total: Int) {
        val footer = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = FOOTER_SIZE_PT
            typeface = font(DesignR.font.inter_regular, Typeface.DEFAULT)
        }
        val y = size.heightPt - FOOTER_BOTTOM_PT
        canvas.drawText(context.getString(R.string.generator_pdf_footer), MARGIN_PT, y, footer)
        val pageLabel = context.getString(R.string.generator_pdf_page, number, total)
        canvas.drawText(pageLabel, size.widthPt - MARGIN_PT - footer.measureText(pageLabel), y, footer)
    }

    private fun loadLogo(): Bitmap? {
        val original = BitmapFactory.decodeResource(context.resources, DesignR.drawable.lawyer) ?: return null
        // Downscale once so the PDF stays small; it is drawn at about 0.4 inch.
        val scaled = original.scale(LOGO_PIXELS * original.width / original.height, LOGO_PIXELS)
        if (scaled != original) original.recycle()
        return scaled
    }

    private fun font(id: Int, fallback: Typeface): Typeface =
        runCatching { ResourcesCompat.getFont(context, id) }.getOrNull() ?: fallback

    private fun paint(typeface: Typeface, sizePt: Float) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        this.typeface = typeface
        textSize = sizePt
        color = Color.BLACK
    }

    private class Styles(val title: TextPaint, val heading: TextPaint, val body: TextPaint, val width: Int) {
        fun layout(text: String, paint: TextPaint, align: Layout.Alignment): StaticLayout =
            StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setAlignment(align)
                .setLineSpacing(0f, LINE_SPACING)
                .build()
    }

    /**
     * Fills pages top to bottom. Paragraphs may break across pages; [place] with keepWhole moves a
     * block to a new page instead when it would fit there whole.
     */
    private class PageBuilder(private val top: Float, private val bottom: Float) {
        private val pages = mutableListOf<LaidOutPage>()
        private var blocks = mutableListOf<PlacedBlock>()
        private var y = top

        fun gap(points: Float) {
            y += points
        }

        fun breakPage() {
            if (blocks.isEmpty()) return
            pages += LaidOutPage(blocks)
            blocks = mutableListOf()
            y = top
        }

        fun place(layout: StaticLayout, keepWhole: Boolean, keepWithNext: Float = 0f) {
            var line = 0
            while (line < layout.lineCount) {
                val remaining = bottom - y
                val rest = (layout.getLineBottom(layout.lineCount - 1) - layout.getLineTop(line)).toFloat()
                val last = lastLineFitting(layout, line, remaining)
                if (mustMove(last < line, keepWhole && rest > remaining, rest, keepWithNext, remaining)) {
                    breakPage()
                    continue
                }
                // A single line taller than a page is placed anyway rather than looping forever.
                val end = maxOf(last, line)
                blocks += PlacedBlock(layout, line, end, y)
                y += layout.getLineBottom(end) - layout.getLineTop(line)
                line = end + 1
                if (line < layout.lineCount) breakPage()
            }
        }

        /** Never moves from an empty page, so every block is eventually placed. */
        private fun mustMove(
            nothingFits: Boolean,
            splitsWhole: Boolean,
            rest: Float,
            keepWithNext: Float,
            remaining: Float,
        ) =
            blocks.isNotEmpty() && when {
                nothingFits -> true
                splitsWhole -> rest <= bottom - top
                else -> keepWithNext > 0 && rest + keepWithNext > remaining
            }

        private fun lastLineFitting(layout: StaticLayout, from: Int, height: Float): Int {
            val start = layout.getLineTop(from)
            var last = from - 1
            while (last + 1 < layout.lineCount && layout.getLineBottom(last + 1) - start <= height) last++
            return last
        }

        fun finish(): List<LaidOutPage> {
            breakPage()
            return pages
        }
    }

    private class PlacedBlock(val layout: StaticLayout, val firstLine: Int, val lastLine: Int, val y: Float) {
        val text: String
            get() = layout.text.subSequence(layout.getLineStart(firstLine), layout.getLineEnd(lastLine)).toString()
    }

    private class LaidOutPage(val blocks: List<PlacedBlock>) {
        val text: String get() = blocks.joinToString("\n") { it.text.trimEnd() }
    }

    private companion object {
        const val EXPORT_DIR = "exports"
        const val MARGIN_PT = 72f
        const val HEADER_TOP_PT = 36f
        const val HEADER_LOGO_PT = 28f
        const val HEADER_GAP_PT = 20f
        const val FOOTER_BOTTOM_PT = 36f
        const val FOOTER_GAP_PT = 24f
        const val FOOTER_SIZE_PT = 8f
        const val TITLE_SIZE_PT = 14f
        const val BODY_SIZE_PT = 10.5f
        const val LINE_SPACING = 1.25f
        const val PARAGRAPH_GAP_PT = 8f
        const val HEADING_GAP_PT = 4f
        const val SECTION_GAP_PT = 12f
        const val LOGO_PIXELS = 120
    }
}
