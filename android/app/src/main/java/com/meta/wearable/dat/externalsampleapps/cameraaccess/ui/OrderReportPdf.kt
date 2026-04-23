/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.ui

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.meta.wearable.dat.externalsampleapps.cameraaccess.session.DiagnosisEntry
import com.meta.wearable.dat.externalsampleapps.cameraaccess.session.DiagnosisOutcome
import com.meta.wearable.dat.externalsampleapps.cameraaccess.session.OrderStatus
import com.meta.wearable.dat.externalsampleapps.cameraaccess.session.RepairOrder
import com.meta.wearable.dat.externalsampleapps.cameraaccess.session.Session
import com.meta.wearable.dat.externalsampleapps.cameraaccess.stream.ChatMessage
import com.meta.wearable.dat.externalsampleapps.cameraaccess.stream.GeminiService
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds a full-order diagnostic report PDF:
 *
 *  1. Cover page — vehicle / customer / presenting issue / order status,
 *     plus a stats block (session count, total diagnostic time, diagnoses
 *     performed).
 *  2. Per-session sections — each saved session summarised by Hank
 *     (Gemini, text-only, one paragraph per session) so the report stays
 *     skimmable instead of pasting full transcripts.
 *  3. Diagnostic findings — every [DiagnosisEntry] on the order with its
 *     pass/fail outcome.
 *  4. Closing report — final Hank-generated synthesis of what was
 *     diagnosed, what's resolved, what remains.
 *
 * All Gemini calls are best-effort — if they fail, the report still
 * renders with the structural data so the tech always gets something
 * shareable.
 */
object OrderReportPdf {
    private const val TAG = "HankDaisy:OrderReport"
    private const val PAGE_W = 612
    private const val PAGE_H = 792
    private const val MARGIN = 40f

    /** Entry point: asks Gemini for the summaries, renders the PDF, opens
     *  the system share sheet. Caller should show a spinner while this
     *  runs — it does multiple network round-trips. */
    suspend fun generateAndShare(
        context: Context,
        order: RepairOrder,
        sessions: List<Session>,
        gemini: GeminiService,
    ) {
        // 1. Build per-session summaries (best-effort).
        val perSession: List<Pair<Session, String>> =
            sessions.map { session ->
                val summary = summariseSession(gemini, session)
                session to summary
            }

        // 2. Build an overall closing synthesis (best-effort). Runs against
        //    the concatenation of every per-session summary so Gemini has
        //    the full picture even when individual transcripts are short.
        val closing = closingSynthesis(gemini, order, perSession)

        try {
            val dir = File(context.cacheDir, "orders")
            dir.mkdirs()
            val now = System.currentTimeMillis()
            val file = File(dir, "hank-order-${order.id}-$now.pdf")

            val doc = PdfDocument()
            val paints = Paints()

            var pageNum = 1
            var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
            var canvas = page.canvas
            var y = MARGIN

            // --- COVER ---
            canvas.drawText("HANK & DAISY", MARGIN, y + 12f, paints.brandSmall)
            y += 20f
            canvas.drawText("Repair order report", MARGIN, y + 16f, paints.title)
            y += 30f

            // Vehicle + status header row.
            canvas.drawText(order.vehicleDisplay, MARGIN, y, paints.heading)
            y += 16f
            canvas.drawText(
                "Status: ${order.status.label}" +
                    (if (order.closedAt != null) "  ·  Closed ${fmt(order.closedAt)}" else "") +
                    "  ·  Created ${fmt(order.createdAt)}",
                MARGIN,
                y,
                paints.muted,
            )
            y += 18f

            // Vehicle + customer block.
            if (order.vehicleVin.isNotBlank() || order.licensePlate.isNotBlank()) {
                val plateLine =
                    listOf(
                            if (order.vehicleVin.isNotBlank()) "VIN: ${order.vehicleVin}" else null,
                            if (order.licensePlate.isNotBlank()) "Plate: ${order.licensePlate}" else null,
                        )
                        .filterNotNull()
                        .joinToString("   ·   ")
                canvas.drawText(plateLine, MARGIN, y, paints.body)
                y += 14f
            }
            if (order.customerName.isNotBlank() || order.customerPhone.isNotBlank()) {
                val custLine =
                    listOfNotNull(
                            order.customerName.ifBlank { null }?.let { "Customer: $it" },
                            order.customerPhone.ifBlank { null }?.let { "Phone: $it" },
                        )
                        .joinToString("   ·   ")
                canvas.drawText(custLine, MARGIN, y, paints.body)
                y += 14f
            }
            y += 6f

            // Presenting issue.
            if (order.presentingIssue.isNotBlank()) {
                canvas.drawText("PRESENTING ISSUE", MARGIN, y, paints.heading)
                y += 14f
                val issueLines = wrap(order.presentingIssue, paints.body, PAGE_W - 2 * MARGIN)
                for (line in issueLines) {
                    val cursor = writeLineAdvancingPage(doc, canvas, page, pageNum, line, MARGIN, y, paints.body)
                    canvas = cursor.canvas
                    page = cursor.page
                    pageNum = cursor.pageNum
                    y = cursor.y
                }
                y += 8f
            }

            // Stats block.
            val totalDurationMs = sessions.sumOf { sessionDurationMs(it) }
            val diagnoses = order.diagnoses
            val passed = diagnoses.count { it.outcome == DiagnosisOutcome.PASS }
            val failed = diagnoses.count { it.outcome == DiagnosisOutcome.FAIL }
            val pending =
                diagnoses.count {
                    it.outcome == DiagnosisOutcome.PENDING || it.outcome == DiagnosisOutcome.SKIPPED
                }

            canvas.drawText("SUMMARY", MARGIN, y, paints.heading)
            y += 14f
            val statLines =
                listOf(
                    "Diagnostic sessions: ${sessions.size}",
                    "Total time on job: ${formatDuration(totalDurationMs)}",
                    "Diagnoses recorded: ${diagnoses.size}" +
                        if (diagnoses.isEmpty()) ""
                        else "   (passed: $passed, failed: $failed, pending: $pending)",
                )
            for (line in statLines) {
                canvas.drawText(line, MARGIN, y, paints.body)
                y += 14f
            }
            y += 8f

            // --- CLOSING REPORT (right up front so readers see the verdict) ---
            y = ensureRoom(doc, canvas, page, pageNum, y, 40f).y
            val h1 = ensureRoom(doc, canvas, page, pageNum, y, 40f)
            canvas = h1.canvas
            page = h1.page
            pageNum = h1.pageNum
            y = h1.y
            canvas.drawText("CLOSING REPORT", MARGIN, y, paints.heading)
            y += 14f
            for (line in wrap(closing, paints.body, PAGE_W - 2 * MARGIN)) {
                val cursor =
                    writeLineAdvancingPage(doc, canvas, page, pageNum, line, MARGIN, y, paints.body)
                canvas = cursor.canvas
                page = cursor.page
                pageNum = cursor.pageNum
                y = cursor.y
            }
            y += 10f

            // --- DIAGNOSTIC FINDINGS ---
            if (diagnoses.isNotEmpty()) {
                val h = ensureRoom(doc, canvas, page, pageNum, y, 30f)
                canvas = h.canvas
                page = h.page
                pageNum = h.pageNum
                y = h.y
                canvas.drawText("DIAGNOSTIC FINDINGS", MARGIN, y, paints.heading)
                y += 14f
                for (d in diagnoses) {
                    val cursor = drawDiagnosis(doc, canvas, page, pageNum, d, y, paints)
                    canvas = cursor.canvas
                    page = cursor.page
                    pageNum = cursor.pageNum
                    y = cursor.y
                }
                y += 8f
            }

            // --- PER-SESSION NOTES ---
            if (perSession.isNotEmpty()) {
                val h = ensureRoom(doc, canvas, page, pageNum, y, 30f)
                canvas = h.canvas
                page = h.page
                pageNum = h.pageNum
                y = h.y
                canvas.drawText("SESSION NOTES", MARGIN, y, paints.heading)
                y += 14f
                perSession.forEachIndexed { idx, (s, summary) ->
                    val cursor = drawSessionBlock(doc, canvas, page, pageNum, idx + 1, s, summary, y, paints)
                    canvas = cursor.canvas
                    page = cursor.page
                    pageNum = cursor.pageNum
                    y = cursor.y
                }
            }

            // --- NOTES ---
            if (order.notes.isNotBlank()) {
                val h = ensureRoom(doc, canvas, page, pageNum, y, 40f)
                canvas = h.canvas
                page = h.page
                pageNum = h.pageNum
                y = h.y
                canvas.drawText("TECHNICIAN NOTES", MARGIN, y, paints.heading)
                y += 14f
                for (line in wrap(order.notes, paints.body, PAGE_W - 2 * MARGIN)) {
                    val cursor =
                        writeLineAdvancingPage(doc, canvas, page, pageNum, line, MARGIN, y, paints.body)
                    canvas = cursor.canvas
                    page = cursor.page
                    pageNum = cursor.pageNum
                    y = cursor.y
                }
            }

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
                    putExtra(
                        Intent.EXTRA_SUBJECT,
                        "Repair order — ${order.vehicleDisplay}",
                    )
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            context.startActivity(
                Intent.createChooser(share, "Share order report").apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                },
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to build order report PDF", e)
        }
    }

    // ---- helpers ----

    private suspend fun summariseSession(gemini: GeminiService, session: Session): String {
        if (session.messages.isEmpty()) return "(no turns recorded)"
        val prompt =
            "Summarise the following Hank session in 2-3 sentences for a repair " +
                "order report. Focus on what was observed, what was recommended, " +
                "and what the outcome was. No markdown, plain text."
        val history =
            session.messages.map { m ->
                GeminiService.Turn(
                    role = if (m.role == ChatMessage.Role.USER) "user" else "assistant",
                    text = m.text,
                )
            }
        return try {
            gemini.analyzeFrame(bitmap = null, userQuestion = prompt, history = history)
        } catch (e: Exception) {
            Log.w(TAG, "summariseSession failed", e)
            "(Summary unavailable — " + (session.description.take(80)) + ")"
        }
    }

    private suspend fun closingSynthesis(
        gemini: GeminiService,
        order: RepairOrder,
        perSession: List<Pair<Session, String>>,
    ): String {
        if (perSession.isEmpty()) {
            return "No diagnostic sessions were run against this order. " +
                "Presenting issue: ${order.presentingIssue.ifBlank { "not specified" }}."
        }
        val bundled =
            perSession.joinToString("\n\n") { (s, summary) ->
                "Session ${fmt(s.createdAt)}: $summary"
            }
        val prompt =
            "You are Hank writing the closing report on repair order " +
                "\"${order.vehicleDisplay}\" for " +
                (order.customerName.ifBlank { "the customer" }) +
                ". Presenting issue: ${order.presentingIssue.ifBlank { "not specified" }}. " +
                "Status: ${order.status.label}. " +
                "Below are notes from the diagnostic sessions run:\n\n" +
                bundled +
                "\n\nWrite a concise (4-6 sentence) closing report that states what was diagnosed, " +
                "what was resolved, and what still needs attention. Professional tone, plain text, " +
                "no markdown, no headings. Lead with the verdict."
        return try {
            gemini.analyzeFrame(bitmap = null, userQuestion = prompt, history = emptyList())
        } catch (e: Exception) {
            Log.w(TAG, "closingSynthesis failed", e)
            "Closing report unavailable (generation failed). " +
                "Refer to the per-session notes below for the diagnostic trail."
        }
    }

    private fun sessionDurationMs(s: Session): Long {
        if (s.messages.size < 2) return 0L
        val first = s.messages.first().timestamp
        val last = s.messages.last().timestamp
        return (last - first).coerceAtLeast(0L)
    }

    private fun formatDuration(ms: Long): String {
        if (ms <= 0) return "–"
        val minutes = ms / 60_000
        val hours = minutes / 60
        val rem = minutes % 60
        return when {
            hours >= 1 -> "${hours}h ${rem}m"
            minutes >= 1 -> "${minutes}m"
            else -> "${ms / 1000}s"
        }
    }

    private fun fmt(ts: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(ts))

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

    /** Per-Paint bundle — cached on [Paints] instead of rebuilt per draw. */
    private class Paints {
        val title =
            Paint().apply {
                color = 0xFF000000.toInt()
                textSize = 20f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
        val brandSmall =
            Paint().apply {
                color = 0xFF0D9488.toInt()
                textSize = 10f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
        val heading =
            Paint().apply {
                color = 0xFF111827.toInt()
                textSize = 11f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
        val body = Paint().apply { color = 0xFF111827.toInt(); textSize = 10f }
        val muted = Paint(body).apply { color = 0xFF6B7280.toInt(); textSize = 9f }
        val pass =
            Paint(body).apply {
                color = 0xFF0D9488.toInt()
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
        val fail =
            Paint(body).apply {
                color = 0xFFB91C1C.toInt()
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
    }

    private data class Cursor(
        val canvas: android.graphics.Canvas,
        val page: PdfDocument.Page,
        val pageNum: Int,
        val y: Float,
    )

    /** Start a new page when `y` is too close to the bottom margin for
     *  `minRoom` more content. Returns an updated cursor. */
    private fun ensureRoom(
        doc: PdfDocument,
        canvas: android.graphics.Canvas,
        page: PdfDocument.Page,
        pageNum: Int,
        y: Float,
        minRoom: Float,
    ): Cursor {
        if (y <= PAGE_H - MARGIN - minRoom) return Cursor(canvas, page, pageNum, y)
        doc.finishPage(page)
        val nextNum = pageNum + 1
        val nextPage =
            doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, nextNum).create())
        return Cursor(nextPage.canvas, nextPage, nextNum, MARGIN)
    }

    private fun writeLineAdvancingPage(
        doc: PdfDocument,
        canvas: android.graphics.Canvas,
        page: PdfDocument.Page,
        pageNum: Int,
        line: String,
        x: Float,
        y: Float,
        paint: Paint,
    ): Cursor {
        val c = ensureRoom(doc, canvas, page, pageNum, y, paint.textSize + 4f)
        c.canvas.drawText(line, x, c.y, paint)
        return c.copy(y = c.y + (paint.textSize + 3f))
    }

    private fun drawDiagnosis(
        doc: PdfDocument,
        canvas: android.graphics.Canvas,
        page: PdfDocument.Page,
        pageNum: Int,
        d: DiagnosisEntry,
        y: Float,
        paints: Paints,
    ): Cursor {
        var cursor = ensureRoom(doc, canvas, page, pageNum, y, 40f)
        val verdictPaint =
            when (d.outcome) {
                DiagnosisOutcome.PASS -> paints.pass
                DiagnosisOutcome.FAIL -> paints.fail
                else -> paints.muted
            }
        cursor.canvas.drawText(
            "[${d.outcome.label.uppercase()}]  ${d.step}",
            MARGIN,
            cursor.y,
            verdictPaint,
        )
        cursor = cursor.copy(y = cursor.y + 13f)
        for (line in wrap(d.result, paints.body, PAGE_W - 2 * MARGIN)) {
            cursor = writeLineAdvancingPage(doc, cursor.canvas, cursor.page, cursor.pageNum, line, MARGIN + 14f, cursor.y, paints.body)
        }
        cursor = cursor.copy(y = cursor.y + 6f)
        return cursor
    }

    private fun drawSessionBlock(
        doc: PdfDocument,
        canvas: android.graphics.Canvas,
        page: PdfDocument.Page,
        pageNum: Int,
        idx: Int,
        session: Session,
        summary: String,
        y: Float,
        paints: Paints,
    ): Cursor {
        var cursor = ensureRoom(doc, canvas, page, pageNum, y, 50f)
        val header =
            "Session $idx  ·  ${fmt(session.createdAt)}  ·  " +
                "${session.messages.size} turns  ·  " +
                "${formatDuration(sessionDurationMs(session))}"
        cursor.canvas.drawText(header, MARGIN, cursor.y, paints.muted)
        cursor = cursor.copy(y = cursor.y + 13f)
        for (line in wrap(summary, paints.body, PAGE_W - 2 * MARGIN)) {
            cursor = writeLineAdvancingPage(doc, cursor.canvas, cursor.page, cursor.pageNum, line, MARGIN, cursor.y, paints.body)
        }
        cursor = cursor.copy(y = cursor.y + 10f)
        // Handle status change when this call moves onto a new page.
        if (cursor.pageNum != pageNum) return cursor
        return cursor
    }
}
