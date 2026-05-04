/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.mpi.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class MpiReportPdfData(
    val title: String,
    val facts: List<MpiReportPdfFact>,
    val conciseDiagnosis: String,
    val sections: List<MpiReportPdfSection>,
    val inspectionStory: String,
    val reportStatus: String,
    val photoEvidence: List<MpiReportPdfEvidence>,
)

internal data class MpiReportPdfFact(
    val label: String,
    val value: String,
)

internal data class MpiReportPdfSection(
    val title: String,
    val items: List<MpiReportPdfItem>,
)

internal data class MpiReportPdfItem(
    val label: String,
    val status: MpiReportPdfStatus,
    val measurement: String,
    val unit: String,
    val needsReview: Boolean,
    val autoComment: String,
    val technicianNote: String,
    val advisorWording: String,
    val photoEvidence: List<MpiReportPdfEvidence>,
)

internal data class MpiReportPdfEvidence(
    val id: String,
    val caption: String,
    val filePath: String?,
    val thumbnailPath: String?,
    val source: String,
)

internal enum class MpiReportPdfStatus(val label: String) {
    GREEN("Green"),
    YELLOW("Yellow"),
    RED("Red"),
    UNKNOWN("Unchecked"),
}

internal object MpiReportPdf {
    private const val TAG = "HankDaisy:MpiReportPdf"
    private const val PAGE_W = 612
    private const val PAGE_H = 792
    private const val MARGIN = 40f
    private const val CONTENT_W = PAGE_W - 2 * MARGIN

    suspend fun exportAndShare(
        context: Context,
        report: MpiReportPdfData,
    ): Boolean {
        return try {
            val appContext = context.applicationContext
            val file = withContext(Dispatchers.IO) { renderToFile(appContext, report) }
            val uri: Uri =
                FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)
            val share =
                Intent(Intent.ACTION_SEND).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, report.title)
                    putExtra(Intent.EXTRA_TITLE, file.name)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            appContext.startActivity(
                Intent.createChooser(share, "Export MPI report").apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                },
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export MPI report PDF", e)
            false
        }
    }

    private fun renderToFile(
        context: Context,
        report: MpiReportPdfData,
    ): File {
        val dir = File(context.cacheDir, "mpi-reports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(dir, "mpi-report-$stamp.pdf")
        val doc = PdfDocument()
        val paints = Paints()
        try {
            var cursor = startPage(doc, paints, 1)

            cursor.canvas.drawText("HANK & DAISY", MARGIN, cursor.y, paints.brand)
            cursor = cursor.copy(y = cursor.y + 22f)
            cursor.canvas.drawText(report.title, MARGIN, cursor.y, paints.title)
            cursor = cursor.copy(y = cursor.y + 17f)
            cursor.canvas.drawText(
                "Status: ${report.reportStatus.ifBlank { "draft" }}  |  Exported ${fmt(System.currentTimeMillis())}",
                MARGIN,
                cursor.y,
                paints.muted,
            )
            cursor = cursor.copy(y = cursor.y + 24f)

            cursor = drawSectionTitle(doc, cursor, paints, "Technical snapshot")
            report.facts.forEach { fact ->
                cursor = writeParagraph(doc, cursor, paints, "${fact.label}: ${fact.value}", paints.body)
            }
            cursor = cursor.copy(y = cursor.y + 8f)

            cursor = drawSectionTitle(doc, cursor, paints, "Concise diagnosis")
            cursor = writeParagraph(doc, cursor, paints, report.conciseDiagnosis.ifBlank { "No diagnosis available." }, paints.body)
            cursor = cursor.copy(y = cursor.y + 8f)

            cursor = drawSectionTitle(doc, cursor, paints, "Inspection story")
            cursor = writeParagraph(doc, cursor, paints, report.inspectionStory.ifBlank { "No inspection story entered." }, paints.body)
            cursor = cursor.copy(y = cursor.y + 8f)

            cursor = drawSectionTitle(doc, cursor, paints, "Multipoint checklist")
            report.sections.forEach { section ->
                cursor = drawChecklistSection(doc, cursor, paints, section)
            }

            cursor = drawPhotoGallery(doc, cursor, paints, report.photoEvidence)

            finishPage(doc, cursor, paints)
            FileOutputStream(file).use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
        return file
    }

    private fun drawChecklistSection(
        doc: PdfDocument,
        cursor: Cursor,
        paints: Paints,
        section: MpiReportPdfSection,
    ): Cursor {
        var c = ensureRoom(doc, cursor, paints, 34f)
        c.canvas.drawText(section.title, MARGIN, c.y, paints.heading)
        c = c.copy(y = c.y + 14f)
        section.items.forEach { item ->
            c = drawChecklistItem(doc, c, paints, item)
        }
        return c.copy(y = c.y + 8f)
    }

    private fun drawChecklistItem(
        doc: PdfDocument,
        cursor: Cursor,
        paints: Paints,
        item: MpiReportPdfItem,
    ): Cursor {
        var c = ensureRoom(doc, cursor, paints, 62f)
        drawStatusTicks(c.canvas, MARGIN, c.y, item.status, paints)
        val label =
            buildString {
                append(item.label)
                val measurement = item.measurementDisplay()
                if (measurement.isNotBlank()) append("  |  ").append(measurement)
                if (item.needsReview) append("  |  needs review")
            }
        c.canvas.drawText(label.take(82), MARGIN + 150f, c.y, paints.itemTitle)
        c = c.copy(y = c.y + 13f)

        val comments =
            listOfNotNull(
                item.autoComment.takeIf { it.isNotBlank() }?.let { "Auto: $it" },
                item.technicianNote.takeIf { it.isNotBlank() }?.let { "Tech note: $it" },
                item.advisorWording.takeIf { it.isNotBlank() }?.let { "Advisor: $it" },
                item.photoEvidence
                    .takeIf { it.isNotEmpty() }
                    ?.let { "Attached photos: ${it.size}" },
            )
        if (comments.isNotEmpty()) {
            comments.forEach { comment ->
                c = writeParagraph(doc, c, paints, comment, paints.body, x = MARGIN + 18f)
            }
        } else {
            c = c.copy(y = c.y + 2f)
        }
        c.canvas.drawLine(MARGIN, c.y + 2f, PAGE_W - MARGIN, c.y + 2f, paints.rule)
        return c.copy(y = c.y + 10f)
    }

    private fun drawPhotoGallery(
        doc: PdfDocument,
        cursor: Cursor,
        paints: Paints,
        photos: List<MpiReportPdfEvidence>,
    ): Cursor {
        var c = drawSectionTitle(doc, cursor.copy(y = cursor.y + 4f), paints, "Photographic evidence")
        if (photos.isEmpty()) {
            return writeParagraph(
                doc,
                c,
                paints,
                "No still photo evidence was attached to this MPI report. Video evidence is intentionally excluded from this PDF export.",
                paints.muted,
            )
        }

        val gap = 12f
        val cellW = (CONTENT_W - gap) / 2f
        val cellH = 178f
        photos.forEachIndexed { index, photo ->
            val col = index % 2
            if (col == 0) {
                c = ensureRoom(doc, c, paints, cellH + 18f)
            }
            val x = MARGIN + col * (cellW + gap)
            drawPhotoCell(c.canvas, paints, photo, x, c.y, cellW, cellH)
            if (col == 1 || index == photos.lastIndex) {
                c = c.copy(y = c.y + cellH + 14f)
            }
        }
        return c
    }

    private fun drawPhotoCell(
        canvas: Canvas,
        paints: Paints,
        photo: MpiReportPdfEvidence,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
    ) {
        val card = RectF(x, y - 10f, x + width, y + height - 10f)
        canvas.drawRoundRect(card, 8f, 8f, paints.cardFill)
        canvas.drawRoundRect(card, 8f, 8f, paints.cardStroke)

        val imageBox = RectF(x + 8f, y, x + width - 8f, y + 116f)
        canvas.drawRect(imageBox, paints.imageFill)
        val bitmap = decodeScaledBitmap(photo.thumbnailPath ?: photo.filePath)
        if (bitmap != null) {
            val dest = fitInside(bitmap, imageBox)
            canvas.drawBitmap(bitmap, null, dest, paints.bitmap)
            bitmap.recycle()
        } else {
            canvas.drawText("Image unavailable", imageBox.left + 12f, imageBox.centerY(), paints.muted)
        }

        val caption = photo.caption.ifBlank { photo.source.ifBlank { "Captured photo" } }
        val lines = wrap(caption, paints.smallBody, width - 18f).take(3)
        var textY = y + 133f
        lines.forEach { line ->
            canvas.drawText(line, x + 9f, textY, paints.smallBody)
            textY += 11f
        }
    }

    private fun drawSectionTitle(
        doc: PdfDocument,
        cursor: Cursor,
        paints: Paints,
        title: String,
    ): Cursor {
        var c = ensureRoom(doc, cursor, paints, 32f)
        c.canvas.drawText(title.uppercase(Locale.US), MARGIN, c.y, paints.section)
        return c.copy(y = c.y + 15f)
    }

    private fun writeParagraph(
        doc: PdfDocument,
        cursor: Cursor,
        paints: Paints,
        text: String,
        paint: Paint,
        x: Float = MARGIN,
        maxWidth: Float = PAGE_W - MARGIN - x,
    ): Cursor {
        var c = cursor
        wrap(text, paint, maxWidth).ifEmpty { listOf("") }.forEach { line ->
            c = ensureRoom(doc, c, paints, paint.textSize + 5f)
            if (line.isBlank()) {
                c = c.copy(y = c.y + paint.textSize + 3f)
            } else {
                c.canvas.drawText(line, x, c.y, paint)
                c = c.copy(y = c.y + paint.textSize + 3f)
            }
        }
        return c.copy(y = c.y + 3f)
    }

    private fun drawStatusTicks(
        canvas: Canvas,
        x: Float,
        baseline: Float,
        status: MpiReportPdfStatus,
        paints: Paints,
    ) {
        var cursorX = x
        listOf(
            MpiReportPdfStatus.GREEN to "G",
            MpiReportPdfStatus.YELLOW to "Y",
            MpiReportPdfStatus.RED to "R",
        ).forEach { (candidate, label) ->
            val rect = RectF(cursorX, baseline - 10f, cursorX + 11f, baseline + 1f)
            val selected = status == candidate
            canvas.drawRoundRect(
                rect,
                2f,
                2f,
                if (selected) paints.statusFill(candidate) else paints.emptyBox,
            )
            canvas.drawRoundRect(rect, 2f, 2f, paints.boxStroke)
            if (selected) drawCheck(canvas, rect, paints.check)
            canvas.drawText(label, cursorX + 15f, baseline, paints.statusLabel(candidate))
            cursorX += 42f
        }
        if (status == MpiReportPdfStatus.UNKNOWN) {
            canvas.drawText("Unchecked", cursorX, baseline, paints.muted)
        }
    }

    private fun drawCheck(
        canvas: Canvas,
        rect: RectF,
        paint: Paint,
    ) {
        canvas.drawLine(rect.left + 2.3f, rect.centerY(), rect.left + 4.7f, rect.bottom - 2f, paint)
        canvas.drawLine(rect.left + 4.7f, rect.bottom - 2f, rect.right - 2f, rect.top + 2f, paint)
    }

    private fun startPage(
        doc: PdfDocument,
        paints: Paints,
        pageNum: Int,
    ): Cursor {
        val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
        page.canvas.drawColor(Color.WHITE)
        page.canvas.drawText("Multipoint Inspection", MARGIN, PAGE_H - 24f, paints.footer)
        return Cursor(page.canvas, page, pageNum, MARGIN)
    }

    private fun ensureRoom(
        doc: PdfDocument,
        cursor: Cursor,
        paints: Paints,
        minRoom: Float,
    ): Cursor {
        if (cursor.y <= PAGE_H - MARGIN - minRoom) return cursor
        finishPage(doc, cursor, paints)
        return startPage(doc, paints, cursor.pageNum + 1)
    }

    private fun finishPage(
        doc: PdfDocument,
        cursor: Cursor,
        paints: Paints,
    ) {
        cursor.canvas.drawText("Page ${cursor.pageNum}", PAGE_W - MARGIN - 36f, PAGE_H - 24f, paints.footer)
        doc.finishPage(cursor.page)
    }

    private fun MpiReportPdfItem.measurementDisplay(): String =
        when {
            measurement.isBlank() && unit.isBlank() -> ""
            measurement.isBlank() -> unit
            unit == "/32" -> "$measurement/32"
            unit.isBlank() -> measurement
            else -> "$measurement $unit"
        }

    private fun decodeScaledBitmap(path: String?): Bitmap? {
        if (path.isNullOrBlank()) return null
        val bounds =
            BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > 900 || bounds.outHeight / sampleSize > 900) {
            sampleSize *= 2
        }
        return BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            },
        )
    }

    private fun fitInside(
        bitmap: Bitmap,
        box: RectF,
    ): RectF {
        val scale = min(box.width() / bitmap.width.toFloat(), box.height() / bitmap.height.toFloat())
        val width = bitmap.width * scale
        val height = bitmap.height * scale
        val left = box.left + (box.width() - width) / 2f
        val top = box.top + (box.height() - height) / 2f
        return RectF(left, top, left + width, top + height)
    }

    private fun fmt(ts: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(ts))

    private fun wrap(
        text: String,
        paint: Paint,
        maxWidth: Float,
    ): List<String> {
        val out = mutableListOf<String>()
        text.split("\n").forEach { paragraph ->
            if (paragraph.isBlank()) {
                out.add("")
                return@forEach
            }
            var current = StringBuilder()
            paragraph.split(Regex("\\s+")).forEach { word ->
                val attempt = if (current.isEmpty()) word else "$current $word"
                if (paint.measureText(attempt) <= maxWidth) {
                    current = StringBuilder(attempt)
                } else {
                    if (current.isNotEmpty()) out.add(current.toString())
                    current = StringBuilder(word)
                }
            }
            if (current.isNotEmpty()) out.add(current.toString())
        }
        return out
    }

    private data class Cursor(
        val canvas: Canvas,
        val page: PdfDocument.Page,
        val pageNum: Int,
        val y: Float,
    )

    private class Paints {
        val brand =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF0D9488.toInt()
                textSize = 10f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
        val title =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 21f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
        val section =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF0F766E.toInt()
                textSize = 10f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
        val heading =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF111827.toInt()
                textSize = 11f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
        val itemTitle =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF111827.toInt()
                textSize = 9.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
        val body =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF111827.toInt()
                textSize = 9.5f
            }
        val smallBody =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF111827.toInt()
                textSize = 8.5f
            }
        val muted =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF6B7280.toInt()
                textSize = 8.8f
            }
        val footer =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF9CA3AF.toInt()
                textSize = 8f
            }
        val rule =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFE5E7EB.toInt()
                strokeWidth = 0.8f
            }
        val emptyBox =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }
        val boxStroke =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF9CA3AF.toInt()
                style = Paint.Style.STROKE
                strokeWidth = 0.9f
            }
        val check =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = 1.8f
                strokeCap = Paint.Cap.ROUND
            }
        val cardFill =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFF9FAFB.toInt()
                style = Paint.Style.FILL
            }
        val cardStroke =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFE5E7EB.toInt()
                style = Paint.Style.STROKE
                strokeWidth = 0.8f
            }
        val imageFill =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFE5E7EB.toInt()
                style = Paint.Style.FILL
            }
        val bitmap = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        fun statusFill(status: MpiReportPdfStatus): Paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color =
                    when (status) {
                        MpiReportPdfStatus.GREEN -> 0xFF16A34A.toInt()
                        MpiReportPdfStatus.YELLOW -> 0xFFF59E0B.toInt()
                        MpiReportPdfStatus.RED -> 0xFFDC2626.toInt()
                        MpiReportPdfStatus.UNKNOWN -> 0xFF9CA3AF.toInt()
                    }
                style = Paint.Style.FILL
            }

        fun statusLabel(status: MpiReportPdfStatus): Paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color =
                    when (status) {
                        MpiReportPdfStatus.GREEN -> 0xFF15803D.toInt()
                        MpiReportPdfStatus.YELLOW -> 0xFFB45309.toInt()
                        MpiReportPdfStatus.RED -> 0xFFB91C1C.toInt()
                        MpiReportPdfStatus.UNKNOWN -> 0xFF6B7280.toInt()
                    }
                textSize = 8.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
    }
}
