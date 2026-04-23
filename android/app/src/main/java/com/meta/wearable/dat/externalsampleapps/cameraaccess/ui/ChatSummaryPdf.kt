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
import com.meta.wearable.dat.externalsampleapps.cameraaccess.session.Session
import com.meta.wearable.dat.externalsampleapps.cameraaccess.stream.ChatMessage
import com.meta.wearable.dat.externalsampleapps.cameraaccess.stream.GeminiService
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Asks Hank (via the existing GeminiService, no frame — text-only) to
 * summarise a saved session, then renders the summary + full transcript
 * as a single-page-per-section PDF and opens the system share sheet.
 */
object ChatSummaryPdf {
    private const val TAG = "HankDaisy:ChatSummaryPdf"
    private const val PAGE_W = 612
    private const val PAGE_H = 792
    private const val MARGIN = 40f

    suspend fun summariseAndShare(
        context: Context,
        session: Session,
        gemini: GeminiService,
    ) {
        val prompt =
            "Summarise the following conversation between a user and Hank " +
                "(an automotive diagnostic assistant) as a professional " +
                "technician's report. Sections:\n" +
                "1) Problem / topic (1-2 sentences)\n" +
                "2) Observations and diagnosis\n" +
                "3) Actions taken or recommended, in order\n" +
                "4) Open follow-ups or next steps\n" +
                "Keep it tight, factual, no filler. Plain text, no markdown."

        val history =
            session.messages.map { m ->
                GeminiService.Turn(
                    role = if (m.role == ChatMessage.Role.USER) "user" else "assistant",
                    text = m.text,
                )
            }
        val summary =
            try {
                gemini.analyzeFrame(bitmap = null, userQuestion = prompt, history = history)
            } catch (e: Exception) {
                Log.e(TAG, "summarise failed", e)
                "(Could not generate summary: ${e.message})"
            }

        try {
            val dir = File(context.cacheDir, "sessions")
            dir.mkdirs()
            val now = System.currentTimeMillis()
            val file = File(dir, "hank-summary-${session.id}-$now.pdf")

            val doc = PdfDocument()
            val title = Paint().apply { color = 0xFF000000.toInt(); textSize = 18f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
            val heading = Paint().apply { color = 0xFF111827.toInt(); textSize = 11f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
            val body = Paint().apply { color = 0xFF111827.toInt(); textSize = 10f }
            val muted = Paint(body).apply { color = 0xFF6B7280.toInt(); textSize = 9f }
            val userLbl = Paint(body).apply { color = 0xFF1D4ED8.toInt(); textSize = 9f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
            val asstLbl = Paint(body).apply { color = 0xFF0D9488.toInt(); textSize = 9f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }

            var pageNum = 1
            var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
            var canvas = page.canvas
            var y = MARGIN

            canvas.drawText("Hank & Daisy — session summary", MARGIN, y + 14f, title)
            y += 26f
            canvas.drawText(
                "${fmt(session.createdAt)}  ·  ${session.messages.size} turns",
                MARGIN,
                y,
                muted,
            )
            y += 16f
            canvas.drawText(session.title, MARGIN, y, heading)
            y += 18f

            // Summary block
            canvas.drawText("SUMMARY", MARGIN, y, heading)
            y += 13f
            val summaryLines = wrap(summary, body, PAGE_W - 2 * MARGIN)
            for (line in summaryLines) {
                if (y > PAGE_H - MARGIN - 12f) {
                    doc.finishPage(page)
                    pageNum++
                    page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
                    canvas = page.canvas
                    y = MARGIN
                }
                canvas.drawText(line, MARGIN, y, body)
                y += (body.textSize + 3f)
            }

            y += 14f
            if (y > PAGE_H - MARGIN - 20f) {
                doc.finishPage(page)
                pageNum++
                page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
                canvas = page.canvas
                y = MARGIN
            }
            canvas.drawText("FULL TRANSCRIPT", MARGIN, y, heading)
            y += 14f

            for (m in session.messages) {
                val lbl = if (m.role == ChatMessage.Role.USER) "You" else "Hank"
                val lblPaint = if (m.role == ChatMessage.Role.USER) userLbl else asstLbl
                val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(m.timestamp))
                if (y > PAGE_H - MARGIN - 30f) {
                    doc.finishPage(page)
                    pageNum++
                    page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
                    canvas = page.canvas
                    y = MARGIN
                }
                canvas.drawText("$lbl  $ts", MARGIN, y, lblPaint)
                y += 12f
                val lines = wrap(m.text, body, PAGE_W - 2 * MARGIN)
                for (line in lines) {
                    if (y > PAGE_H - MARGIN - 12f) {
                        doc.finishPage(page)
                        pageNum++
                        page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
                        canvas = page.canvas
                        y = MARGIN
                    }
                    canvas.drawText(line, MARGIN, y, body)
                    y += (body.textSize + 3f)
                }
                y += 6f
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
                    putExtra(Intent.EXTRA_SUBJECT, "Hank session summary — ${session.title}")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            context.startActivity(
                Intent.createChooser(share, "Export summary").apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                },
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to build summary PDF", e)
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
}
