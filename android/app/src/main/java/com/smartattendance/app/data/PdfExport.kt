package com.smartattendance.app.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Native equivalent of the web `exportActivity.ts`: builds a PDF of the
 * student's own class activity (last 7 days or current month) and saves it
 * to the Downloads folder. Uses only the framework PdfDocument.
 */
object PdfExport {

    enum class Period { WEEK, MONTH }

    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val timeFmt = SimpleDateFormat("hh:mm a", Locale.getDefault())

    suspend fun export(
        context: Context,
        api: ApiClient,
        period: Period,
        studentName: String,
        studentId: String?
    ): String {
        val data = api.get("/api/student/attendance")
        val all = mutableListOf<AttendanceRecord>()
        val arr = data.optJSONArray("items")
        if (arr != null) for (i in 0 until arr.length()) {
            arr.optJSONObject(i)?.let { all.add(AttendanceRecord.fromJson(it)) }
        }
        val now = Calendar.getInstance()
        val to = dateFmt.format(now.time)
        val from = if (period == Period.WEEK) {
            val c = Calendar.getInstance()
            c.add(Calendar.DAY_OF_YEAR, -6)
            dateFmt.format(c.time)
        } else {
            val c = Calendar.getInstance()
            c.set(Calendar.DAY_OF_MONTH, 1)
            dateFmt.format(c.time)
        }
        val rows = all.filter { it.date in from..to }.sortedByDescending { it.date }
        val label = if (period == Period.WEEK) "Weekly" else "Monthly"
        val attended = rows.count { it.status != "ABSENT" }
        val counts = rows.groupingBy { it.status }.eachCount()
        val sum = counts.entries.joinToString("   ") { "${it.key}: ${it.value}" }
        val summary = if (rows.isEmpty()) "No class activity in this period."
        else "Classes: ${rows.size}   ·   Attended: $attended (${(attended * 100 / rows.size)}%)" +
            if (sum.isNotEmpty()) "   ·   $sum" else ""

        val doc = PdfDocument()
        val pageW = 595
        val pageH = 842
        val margin = 40
        val titlePaint = Paint().apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 16f
            color = 0xFF12263F.toInt()
        }
        val subPaint = Paint().apply { textSize = 10f; color = 0xFF505050.toInt() }
        val headPaint = Paint().apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 8.5f
            color = 0xFFFFFFFF.toInt()
        }
        val cellPaint = Paint().apply { textSize = 8.5f; color = 0xFF000000.toInt() }
        val headBg = Paint().apply { color = 0xFF12263F.toInt() }
        val linePaint = Paint().apply { color = 0xFFBEC3CD.toInt(); strokeWidth = 0.4f }
        val footPaint = Paint().apply { textSize = 8f; color = 0xFF787878.toInt() }

        val cols = listOf("Date", "Course", "Room", "Status", "How", "First", "Last")
        val colW = listOf(62f, 150f, 70f, 62f, 62f, 52f, 52f)
        val rowH = 18f
        var pageNum = 1
        var pageInfo = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create())
        var y = 46f

        fun newPage() {
            doc.finishPage(pageInfo)
            pageNum++
            pageInfo = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create())
            y = 40f
        }

        fun drawHeader() {
            val c = pageInfo.canvas
            c.drawText("$label Class Activity", margin.toFloat(), y, titlePaint)
            y += 18f
            c.drawText("$studentName${studentId?.let { "  ·  $it" } ?: ""}", margin.toFloat(), y, subPaint)
            y += 15f
            c.drawText(
                "Period: $from to $to   ·   Generated ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(now.time)}",
                margin.toFloat(), y, subPaint
            )
            y += 15f
            c.drawText(summary, margin.toFloat(), y, subPaint)
            y += 14f
        }

        fun drawTableHead() {
            val c = pageInfo.canvas
            var x = margin.toFloat()
            c.drawRect(x, y - 12f, x + colW.sum(), y + rowH - 12f, headBg)
            for ((i, h) in cols.withIndex()) {
                c.drawText(h, x + 4f, y, headPaint)
                x += colW[i]
            }
            y += rowH
        }

        fun drawFooter() {
            val c = pageInfo.canvas
            c.drawText("Page $pageNum", pageW - margin - 40f, (pageH - 20).toFloat(), footPaint)
        }

        drawHeader()
        drawTableHead()
        for (r in rows) {
            if (y + rowH > pageH - margin) {
                drawFooter()
                newPage()
                drawTableHead()
            }
            val c = pageInfo.canvas
            val cells = listOf(
                r.date, "${r.courseCode} ${r.courseName}", r.classroom, r.status,
                if (r.method == "LECTURER_MANUAL") "Lecturer" else "Bluetooth",
                hm(r.firstDetectedAt), hm(r.lastDetectedAt)
            )
            var x = margin.toFloat()
            for ((i, cell) in cells.withIndex()) {
                c.drawText(ellipsize(cell, colW[i] - 8f, cellPaint), x + 4f, y, cellPaint)
                x += colW[i]
            }
            c.drawLine(margin.toFloat(), y + 5f, (margin + colW.sum()).toFloat(), y + 5f, linePaint)
            y += rowH
        }
        drawFooter()
        doc.finishPage(pageInfo)

        val fileName = "my-${if (period == Period.WEEK) "weekly" else "monthly"}-activity-$to.pdf"
        val bytes = doc.use { d ->
            val out = java.io.ByteArrayOutputStream()
            d.writeTo(out)
            out.toByteArray()
        }
        saveToDownloads(context, fileName, bytes)
        return fileName
    }

    private fun hm(iso: String?): String {
        if (iso == null) return "—"
        return try {
            val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            val d = f.parse(iso.substring(0, 19)) ?: return "—"
            timeFmt.format(d)
        } catch (_: Exception) {
            "—"
        }
    }

    private fun ellipsize(text: String, maxWidth: Float, paint: Paint): String {
        if (paint.measureText(text) <= maxWidth) return text
        var t = text
        while (t.isNotEmpty() && paint.measureText("$t…") > maxWidth) t = t.dropLast(1)
        return "$t…"
    }

    private fun saveToDownloads(context: Context, name: String, bytes: ByteArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Could not create file")
            try {
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ?: throw IllegalStateException("Could not write file")
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (e: Exception) {
                try { resolver.delete(uri, null, null) } catch (_: Exception) {}
                throw e
            }
        } else {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!dir.exists()) dir.mkdirs()
            FileOutputStream(File(dir, name)).use { it.write(bytes) }
        }
    }
}
