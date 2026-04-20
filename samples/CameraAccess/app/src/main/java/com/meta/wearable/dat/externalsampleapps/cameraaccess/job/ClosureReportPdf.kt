/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.job

import android.content.Context
import android.content.Intent
import android.graphics.Paint
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

/**
 * Port of the bay UI's `lib/closurePdf.ts` — generates a single-page closure
 * PDF for the active RO and fires Android's share sheet so it can be emailed /
 * saved to Drive / etc. Matches the same field layout so bay and glasses
 * flows produce interchangeable paperwork.
 */
object ClosureReportPdf {

    private const val TAG = "CameraAccess:ClosurePdf"

    // US Letter in points (72 dpi).
    private const val PAGE_W = 612
    private const val PAGE_H = 792
    private const val MARGIN = 40f

    fun generateAndShare(
        context: Context,
        job: WorkOrder,
        technicianName: String,
        bayNotes: String,
        repairStartedAt: Long?,
    ) {
        try {
            val dir = File(context.cacheDir, "sessions")
            dir.mkdirs()
            val now = System.currentTimeMillis()
            val file = File(dir, "closure-ro-${job.ro}-$now.pdf")

            val doc = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create()
            val page = doc.startPage(pageInfo)
            val canvas = page.canvas

            var y = MARGIN

            val title = Paint().apply { color = 0xFF000000.toInt(); textSize = 18f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
            val heading = Paint().apply { color = 0xFF111827.toInt(); textSize = 11f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
            val body = Paint().apply { color = 0xFF111827.toInt(); textSize = 10f }
            val mono = Paint(body).apply { typeface = Typeface.MONOSPACE }
            val muted = Paint(body).apply { color = 0xFF6B7280.toInt(); textSize = 9f }

            // Title
            canvas.drawText("Closure report — RO ${job.ro}", MARGIN, y + 14f, title)
            y += 28f

            canvas.drawText(
                "Generated ${fmt(now)}  ·  Bay ${job.bay}  ·  Technician: ${technicianName.ifBlank { "(not set)" }}",
                MARGIN,
                y,
                muted,
            )
            y += 18f

            // Vehicle
            y = section(canvas, heading, body, "Vehicle", job.vehicle, y)
            y = section(canvas, heading, mono, "VIN", job.vin, y)

            // Concern + writer notes
            y = section(canvas, heading, body, "Customer concern", job.concern, y)
            if (job.writerNotes.isNotBlank()) {
                y = section(canvas, heading, body, "Writer notes", job.writerNotes, y)
            }

            // DTCs
            val dtcText =
                if (job.dtcs.isEmpty()) "None listed"
                else
                    job.dtcs.joinToString("\n") {
                        "${it.code}  ${it.description}${if (it.pending) "  (pending)" else ""}"
                    }
            y = section(canvas, heading, mono, "DTCs", dtcText, y)

            // Repair timeline
            if (repairStartedAt != null) {
                val started = fmt(repairStartedAt)
                val ended = fmt(now)
                val elapsed = humanDuration(now - repairStartedAt)
                y = section(canvas, heading, body, "Repair timeline", "$started  →  $ended  (${elapsed})", y)
            }

            // Work performed / bay notes — single free-form block.
            y =
                section(
                    canvas,
                    heading,
                    body,
                    "Work performed",
                    bayNotes.ifBlank { "(not documented)" },
                    y,
                )

            // Footer
            y = (PAGE_H - MARGIN - 16f)
            canvas.drawText(
                "Verify against OEM SI for this VIN before turning wrenches.",
                MARGIN,
                y,
                Paint(muted).apply { textSize = 9f },
            )

            doc.finishPage(page)
            FileOutputStream(file).use { doc.writeTo(it) }
            doc.close()

            val uri: Uri =
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val share =
                Intent(Intent.ACTION_SEND).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Closure report RO ${job.ro}")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            val chooser =
                Intent.createChooser(share, "Export closure report").apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate closure PDF", e)
        }
    }

    /** Draw a labeled text block; returns new y cursor. Wraps long body text
     * naively at the margin-to-margin width (simple greedy wrap on spaces). */
    private fun section(
        canvas: android.graphics.Canvas,
        heading: Paint,
        body: Paint,
        label: String,
        text: String,
        startY: Float,
    ): Float {
        var y = startY + 6f
        canvas.drawText(label, MARGIN, y, heading)
        y += 13f
        for (line in wrap(text, body, (PAGE_W - 2 * MARGIN))) {
            canvas.drawText(line, MARGIN, y, body)
            y += (body.textSize + 3f)
        }
        return y + 4f
    }

    private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
        val out = mutableListOf<String>()
        for (paragraph in text.split("\n")) {
            if (paragraph.isEmpty()) {
                out.add("")
                continue
            }
            var current = StringBuilder()
            for (word in paragraph.split(" ")) {
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

    private fun fmt(ts: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(ts))

    private fun humanDuration(ms: Long): String {
        val sec = ms / 1000
        val min = sec / 60
        val hr = min / 60
        return when {
            hr > 0 -> "${hr}h ${min % 60}m"
            min > 0 -> "${min}m ${sec % 60}s"
            else -> "${sec}s"
        }
    }
}
