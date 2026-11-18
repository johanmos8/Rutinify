package com.mirkwood.rutinify.data.system

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import android.util.Log
import androidx.core.content.FileProvider
import com.mirkwood.rutinify.data.LOG_TAG
import com.mirkwood.rutinify.di.IoDispatcher
import com.mirkwood.rutinify.domain.system.DocumentExporter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject

/**
 * PDF with the framework's `PdfDocument` (D-78): A4 pages, the title in bold, then the lines wrapped to the page
 * width. Written to `cache/exports/` (the previous export is replaced) and shared through `FileProvider`.
 */
class AndroidDocumentExporter @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : DocumentExporter {

    override suspend fun writePdf(title: String, lines: List<String>): String? = withContext(io) {
        val document = PdfDocument()
        try {
            render(document, title, lines)
            val dir = File(context.cacheDir, EXPORTS).apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, FILE_NAME)
            file.outputStream().use { document.writeTo(it) }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file).toString()
        } catch (e: IOException) {
            Log.e(LOG_TAG, "Could not write the history PDF", e)
            null
        } catch (e: IllegalArgumentException) {
            Log.e(LOG_TAG, "Could not share the history PDF", e)
            null
        } finally {
            document.close()
        }
    }

    private fun render(document: PdfDocument, title: String, lines: List<String>) {
        val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = BODY_SIZE }
        val heading = TextPaint(body).apply {
            textSize = TITLE_SIZE
            typeface = Typeface.DEFAULT_BOLD
        }
        val width = PAGE_WIDTH - 2 * MARGIN
        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var y = MARGIN + TITLE_SIZE
        page.canvas.drawText(title, MARGIN.toFloat(), y, heading)
        y += TITLE_SIZE
        for (line in lines.flatMap { wrap(it, body, width) }) {
            if (y + LINE_HEIGHT > PAGE_HEIGHT - MARGIN) {
                document.finishPage(page)
                pageNumber++
                page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
                y = MARGIN.toFloat()
            }
            y += LINE_HEIGHT
            page.canvas.drawText(line, MARGIN.toFloat(), y, body)
        }
        document.finishPage(page)
    }

    /** Splits [text] at spaces so each piece fits [width]; an empty line stays as a blank line. */
    private fun wrap(text: String, paint: TextPaint, width: Int): List<String> {
        if (text.isEmpty()) return listOf("")
        val out = mutableListOf<String>()
        var current = ""
        for (word in text.split(' ')) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= width || current.isEmpty()) {
                current = candidate
            } else {
                out += current
                current = word
            }
        }
        out += current
        return out
    }

    private companion object {
        const val EXPORTS = "exports"
        const val FILE_NAME = "historial.pdf"
        const val PAGE_WIDTH = 595 // A4 in points
        const val PAGE_HEIGHT = 842
        const val MARGIN = 48
        const val TITLE_SIZE = 18f
        const val BODY_SIZE = 11f
        const val LINE_HEIGHT = 16f
    }
}
