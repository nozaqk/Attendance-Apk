package com.smartiq.app

import android.content.ContentValues
import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Draws the student activity report and saves it into Downloads.
 *
 * Every piece of content — period, summary line, rows and their formatting —
 * arrives from Dart. This object only renders and stores it, because MediaStore
 * and the framework PDF canvas are Android APIs.
 */
object PdfWriter {

    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 40f
    private const val ROW_H = 18f

    private val COLUMNS = listOf("Date", "Course", "Room", "Status", "How", "First", "Last")
    private val WIDTHS = listOf(62f, 150f, 70f, 62f, 62f, 52f, 52f)

    fun write(context: Context, payload: JSONObject): String {
        val rows = payload.optJSONArray("rows") ?: JSONArray()
        val label = payload.optString("label", "Weekly")
        val studentName = payload.optString("studentName", "")
        val studentId = payload.optString("studentId", "")
        val from = payload.optString("from", "")
        val to = payload.optString("to", "")
        val generated = payload.optString("generated", "")
        val summary = payload.optString("summary", "")
        val fileName = payload.optString("fileName", "activity.pdf")

        val titlePaint = Paint().apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 16f
            color = 0xFF12263F.toInt()
        }
        val subPaint = Paint().apply {
            textSize = 10f
            color = 0xFF505050.toInt()
        }
        val headPaint = Paint().apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 8.5f
            color = 0xFFFFFFFF.toInt()
        }
        val cellPaint = Paint().apply {
            textSize = 8.5f
            color = 0xFF000000.toInt()
        }
        val headBg = Paint().apply { color = 0xFF12263F.toInt() }
        val linePaint = Paint().apply {
            color = 0xFFBEC3CD.toInt()
            strokeWidth = 0.4f
        }
        val footPaint = Paint().apply {
            textSize = 8f
            color = 0xFF787878.toInt()
        }

        val doc = PdfDocument()
        var pageNumber = 1
        var page = doc.startPage(
            PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNumber).create()
        )
        var y = 46f

        fun newPage() {
            doc.finishPage(page)
            pageNumber += 1
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNumber).create())
            y = 40f
        }

        fun drawHeader() {
            val canvas = page.canvas
            canvas.drawText("$label Class Activity", MARGIN, y, titlePaint)
            y += 18f
            canvas.drawText(
                if (studentId.isEmpty()) studentName else "$studentName  ·  $studentId",
                MARGIN, y, subPaint
            )
            y += 15f
            canvas.drawText(
                "Period: $from to $to   ·   Generated $generated",
                MARGIN, y, subPaint
            )
            y += 15f
            canvas.drawText(summary, MARGIN, y, subPaint)
            y += 14f
        }

        fun drawTableHead() {
            val canvas = page.canvas
            var x = MARGIN
            canvas.drawRect(x, y - 12f, x + WIDTHS.sum(), y + ROW_H - 12f, headBg)
            for (i in COLUMNS.indices) {
                canvas.drawText(COLUMNS[i], x + 4f, y, headPaint)
                x += WIDTHS[i]
            }
            y += ROW_H
        }

        fun drawFooter() {
            page.canvas.drawText("Page $pageNumber", PAGE_W - MARGIN - 40f, (PAGE_H - 20).toFloat(), footPaint)
        }

        drawHeader()
        drawTableHead()
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            if (y + ROW_H > PAGE_H - MARGIN) {
                drawFooter()
                newPage()
                drawTableHead()
            }
            val cells = listOf(
                row.optString("date"),
                row.optString("course"),
                row.optString("room"),
                row.optString("status"),
                row.optString("how"),
                row.optString("first"),
                row.optString("last")
            )
            val canvas = page.canvas
            var x = MARGIN
            for (c in cells.indices) {
                val text = ellipsize(cells[c], WIDTHS[c] - 8f, cellPaint)
                canvas.drawText(text, x + 4f, y, cellPaint)
                x += WIDTHS[c]
            }
            canvas.drawLine(MARGIN, y + 5f, MARGIN + WIDTHS.sum(), y + 5f, linePaint)
            y += ROW_H
        }
        drawFooter()
        doc.finishPage(page)

        val out = java.io.ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()
        save(context, fileName, out.toByteArray())
        return fileName
    }

    private fun ellipsize(text: String, maxWidth: Float, paint: Paint): String {
        if (paint.measureText(text) <= maxWidth) return text
        var trimmed = text
        while (trimmed.isNotEmpty() && paint.measureText("$trimmed…") > maxWidth) {
            trimmed = trimmed.dropLast(1)
        }
        return "$trimmed…"
    }

    private fun save(context: Context, name: String, bytes: ByteArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Could not create the file")
            try {
                val stream = resolver.openOutputStream(uri)
                    ?: throw IllegalStateException("Could not write the file")
                stream.use { it.write(bytes) }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (e: Exception) {
                try {
                    resolver.delete(uri, null, null)
                } catch (_: Exception) {
                    // nothing else to clean up
                }
                throw e
            }
        } else {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!dir.exists()) dir.mkdirs()
            FileOutputStream(File(dir, name)).use { it.write(bytes) }
        }
    }
}
