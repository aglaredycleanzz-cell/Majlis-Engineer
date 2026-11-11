package com.majlisengineer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint

object ProjectPdfExporter {
    fun write(context: Context, uri: Uri, lines: List<String>) {
        val document = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 42f
        val contentWidth = (pageWidth - margin * 2).toInt()
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(35, 43, 42)
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 78, 67)
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas: Canvas = page.canvas
        var y = margin

        fun newPage() {
            document.finishPage(page)
            pageNumber += 1
            page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            canvas = page.canvas
            y = margin
        }

        canvas.drawColor(Color.WHITE)
        lines.forEachIndexed { index, raw ->
            val value = raw.ifBlank { " " }
            val paint = if (index == 0) titlePaint else bodyPaint
            val layout = StaticLayout.Builder.obtain(value, 0, value.length, paint, contentWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setTextDirection(TextDirectionHeuristics.RTL)
                .setIncludePad(false)
                .setLineSpacing(4f, 1f)
                .build()
            if (y + layout.height + 12f > pageHeight - margin) newPage()
            canvas.save()
            canvas.translate(margin, y)
            layout.draw(canvas)
            canvas.restore()
            y += layout.height + if (value == " ") 7f else 10f
        }
        document.finishPage(page)
        context.contentResolver.openOutputStream(uri)?.use { output -> document.writeTo(output) }
            ?: throw IllegalStateException("Unable to open destination")
        document.close()
    }
}
