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
import android.net.Uri
import androidx.core.content.FileProvider
import com.meta.wearable.dat.externalsampleapps.mpi.session.EvidenceKind
import com.meta.wearable.dat.externalsampleapps.mpi.session.FindingSeverity
import com.meta.wearable.dat.externalsampleapps.mpi.session.InspectionEvidence
import com.meta.wearable.dat.externalsampleapps.mpi.session.InspectionFinding
import com.meta.wearable.dat.externalsampleapps.mpi.session.RepairOrder
import com.meta.wearable.dat.externalsampleapps.mpi.session.Session
import com.meta.wearable.dat.externalsampleapps.mpi.stream.GeminiService
import java.io.File
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale

object InspectionReportHtml {

    suspend fun generateAndShare(
        context: Context,
        order: RepairOrder,
        sessions: List<Session>,
        gemini: GeminiService,
    ) {
        val perSession = sessions.map { it to summariseSession(gemini, it) }
        val closing = closingSynthesis(gemini, order, perSession)

        val dir = File(context.cacheDir, "inspection-reports")
        dir.mkdirs()
        val file = File(dir, "inspection-${order.id}-${System.currentTimeMillis()}.html")
        file.writeText(buildHtml(order, perSession, closing))

        val uri: Uri =
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val share =
            Intent(Intent.ACTION_SEND).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                type = "text/html"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Inspection report ${order.repairOrderNumber.ifBlank { order.vehicleDisplay }}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        val chooser = Intent.createChooser(share, "Share inspection report").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(chooser)
    }

    private suspend fun summariseSession(gemini: GeminiService, session: Session): String {
        val transcript =
            session.messages.joinToString("\n") { msg ->
                "${if (msg.role.name == "USER") "Technician" else "Hank"}: ${msg.text}"
            }
        return runCatching {
            gemini.analyzeFrame(
                bitmap = null,
                userQuestion =
                    "Summarise this inspection evidence session in 3 concise sentences. Focus on observed condition, measurements, and next action.\n\n$transcript",
                history = emptyList(),
            ).trim()
        }.getOrElse {
            session.description
        }
    }

    private suspend fun closingSynthesis(
        gemini: GeminiService,
        order: RepairOrder,
        perSession: List<Pair<Session, String>>,
    ): String {
        val findingLines =
            order.findings.joinToString("\n") { finding ->
                "- ${finding.system}/${finding.component}: ${finding.measurement.ifBlank { "no measurement" }}, ${finding.recommendation.ifBlank { "no recommendation" }}"
            }
        val sessionLines =
            perSession.joinToString("\n") { (session, summary) ->
                "- ${session.title}: $summary"
            }
        return runCatching {
            gemini.analyzeFrame(
                bitmap = null,
                userQuestion =
                    "Write a concise closing summary for a multi-point inspection report. Mention the vehicle, the most important findings, and the recommended next actions in 4 short sentences.\n\nVehicle: ${order.vehicleDisplay}\nRO: ${order.repairOrderNumber}\nFindings:\n$findingLines\nSessions:\n$sessionLines",
                history = emptyList(),
            ).trim()
        }.getOrElse {
            buildString {
                append("Inspection completed for ${order.vehicleDisplay}. ")
                append(
                    if (order.findings.isEmpty()) "No structured findings were recorded. "
                    else "${order.findings.size} findings were documented. ",
                )
                if (order.presentingIssue.isNotBlank()) {
                    append("Customer concern: ${order.presentingIssue}. ")
                }
                append("Review the finding list and embedded evidence for recommended next steps.")
            }
        }
    }

    private fun buildHtml(
        order: RepairOrder,
        perSession: List<Pair<Session, String>>,
        closing: String,
    ): String {
        val findingsHtml =
            order.findings.joinToString("\n") { finding ->
                """
                <section class="card finding">
                  <div class="row between">
                    <div>
                      <h3>${escapeHtml(finding.system)} · ${escapeHtml(finding.component)}</h3>
                      <p class="muted">${escapeHtml(finding.location)}</p>
                    </div>
                    <span class="pill ${finding.severity.name.lowercase(Locale.US)}">${escapeHtml(finding.severity.label)}</span>
                  </div>
                  ${kv("Measurement", finding.measurement)}
                  ${kv("Recommendation", finding.recommendation)}
                  ${if (finding.note.isNotBlank()) "<p>${escapeHtml(finding.note)}</p>" else ""}
                  <p class="muted">${finding.linkedSessionIds.size} linked sessions · ${finding.evidenceAssets.size} media files</p>
                  ${renderEvidenceGallery(finding.evidenceAssets)}
                </section>
                """.trimIndent()
            }

        val sessionsHtml =
            perSession.joinToString("\n") { (session, summary) ->
                val inlineImages =
                    session.messages.mapNotNull { msg ->
                        msg.imagePath?.let {
                            InspectionEvidence(
                                id = "msg-${msg.timestamp}",
                                kind = EvidenceKind.IMAGE,
                                filePath = it,
                                createdAt = msg.timestamp,
                                caption = "Attached image",
                            )
                        }
                    }
                """
                <section class="card">
                  <div class="row between">
                    <h3>${escapeHtml(session.title)}</h3>
                    <span class="muted">${escapeHtml(fmt(session.createdAt))}</span>
                  </div>
                  <p>${escapeHtml(summary)}</p>
                  ${if (session.findingId != null) "<p class=\"muted\">Linked finding: ${escapeHtml(session.findingId)}</p>" else ""}
                  ${renderEvidenceGallery(session.evidenceAssets + inlineImages)}
                  <details>
                    <summary>Transcript</summary>
                    <div class="transcript">
                      ${
                          session.messages.joinToString("\n") { msg ->
                              "<p><strong>${if (msg.role.name == "USER") "Technician" else "Hank"}:</strong> ${escapeHtml(msg.text)}</p>"
                          }
                      }
                    </div>
                  </details>
                </section>
                """.trimIndent()
            }

        return """
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1" />
              <title>Inspection Report ${escapeHtml(order.repairOrderNumber.ifBlank { order.vehicleDisplay })}</title>
              <style>
                body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; margin: 0; background: #0b1020; color: #e8edf7; }
                main { max-width: 980px; margin: 0 auto; padding: 32px 20px 60px; }
                h1, h2, h3, p { margin: 0; }
                h1 { font-size: 32px; margin-bottom: 8px; }
                h2 { font-size: 20px; margin: 28px 0 14px; }
                h3 { font-size: 16px; margin-bottom: 6px; }
                p { line-height: 1.55; }
                .muted { color: #a9b3c9; }
                .grid { display: grid; gap: 14px; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); }
                .card { background: #121a2f; border: 1px solid #23304f; border-radius: 12px; padding: 16px; margin-bottom: 14px; }
                .row { display: flex; gap: 12px; align-items: center; }
                .between { justify-content: space-between; align-items: start; }
                .pill { border-radius: 999px; padding: 4px 10px; font-size: 12px; font-weight: 600; }
                .green { background: #173d28; color: #a8f0bf; }
                .yellow { background: #4f4210; color: #ffe08a; }
                .red { background: #4a171c; color: #ffb0bb; }
                .kv { margin-top: 8px; }
                .kv strong { display: block; font-size: 12px; color: #9fb0d2; margin-bottom: 3px; }
                .media-grid { display: grid; gap: 12px; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); margin-top: 12px; }
                .media-card { background: #0d1426; border-radius: 10px; padding: 10px; border: 1px solid #23304f; }
                img, video, audio { width: 100%; border-radius: 8px; background: #000; }
                audio { min-height: 42px; }
                .clip-toggle { margin-top: 8px; background: #24385f; color: #fff; border: 0; border-radius: 8px; padding: 8px 12px; cursor: pointer; }
                details { margin-top: 12px; }
                summary { cursor: pointer; color: #9fd0ff; }
                .transcript p { margin-top: 8px; }
              </style>
            </head>
            <body>
              <main>
                <header>
                  <h1>Multipoint Inspection Report</h1>
                  <p class="muted">${escapeHtml(order.vehicleDisplay)}${if (order.repairOrderNumber.isNotBlank()) " · RO ${escapeHtml(order.repairOrderNumber)}" else ""}</p>
                </header>

                <section class="grid" style="margin-top:20px;">
                  <div class="card">
                    <h3>Vehicle</h3>
                    <p>${escapeHtml(order.vehicleDisplay)}</p>
                    ${if (order.vehicleVin.isNotBlank()) "<p class=\"muted\">VIN ${escapeHtml(order.vehicleVin)}</p>" else ""}
                    ${if (order.licensePlate.isNotBlank()) "<p class=\"muted\">Plate ${escapeHtml(order.licensePlate)}</p>" else ""}
                  </div>
                  <div class="card">
                    <h3>Visit</h3>
                    ${if (order.mileage.isNotBlank()) "<p>Mileage ${escapeHtml(order.mileage)}</p>" else ""}
                    ${if (order.advisorName.isNotBlank()) "<p class=\"muted\">Advisor ${escapeHtml(order.advisorName)}</p>" else ""}
                    ${if (order.technicianName.isNotBlank()) "<p class=\"muted\">Technician ${escapeHtml(order.technicianName)}</p>" else ""}
                  </div>
                  <div class="card">
                    <h3>Customer</h3>
                    <p>${escapeHtml(order.customerName.ifBlank { "Not recorded" })}</p>
                    ${if (order.customerPhone.isNotBlank()) "<p class=\"muted\">${escapeHtml(order.customerPhone)}</p>" else ""}
                  </div>
                </section>

                <section class="card">
                  <h2>Closing Summary</h2>
                  <p>${escapeHtml(closing)}</p>
                </section>

                <section class="card">
                  <h2>Inspection Scope</h2>
                  <p>${escapeHtml(order.presentingIssue.ifBlank { "No customer concern recorded." })}</p>
                </section>

                <h2>Findings</h2>
                $findingsHtml

                <h2>Evidence Sessions</h2>
                $sessionsHtml

                ${
                    if (order.notes.isNotBlank()) {
                        """<section class="card"><h2>Technician Notes</h2><p>${escapeHtml(order.notes)}</p></section>"""
                    } else {
                        ""
                    }
                }
              </main>
              <script>
                document.querySelectorAll('.clip-player').forEach((player) => {
                  const img = player.querySelector('.clip-frame');
                  const button = player.querySelector('.clip-toggle');
                  const frames = JSON.parse(player.querySelector('.clip-data').textContent);
                  const fps = Number(player.dataset.fps || '4');
                  let index = 0;
                  let timer = null;
                  button.addEventListener('click', () => {
                    if (timer) {
                      clearInterval(timer);
                      timer = null;
                      button.textContent = 'Play clip';
                      return;
                    }
                    button.textContent = 'Pause clip';
                    timer = setInterval(() => {
                      index = (index + 1) % frames.length;
                      img.src = frames[index];
                    }, Math.max(80, Math.round(1000 / fps)));
                  });
                });
              </script>
            </body>
            </html>
        """.trimIndent()
    }

    private fun kv(label: String, value: String): String =
        if (value.isBlank()) ""
        else """<div class="kv"><strong>${escapeHtml(label)}</strong><span>${escapeHtml(value)}</span></div>"""

    private fun renderEvidenceGallery(assets: List<InspectionEvidence>): String {
        val available = assets.filter { it.filePath.isNotBlank() && File(it.filePath).exists() }
        if (available.isEmpty()) return ""
        return """
            <div class="media-grid">
              ${
                  available.joinToString("\n") { asset ->
                      val dataUri = inlineDataUri(asset.filePath)
                      val caption = escapeHtml(asset.caption.ifBlank { fmt(asset.createdAt) })
                      when (asset.kind) {
                          EvidenceKind.IMAGE ->
                              """<figure class="media-card"><img alt="$caption" src="$dataUri" /><figcaption class="muted">$caption</figcaption></figure>"""
                          EvidenceKind.VIDEO ->
                              if (asset.clipFramePaths.isNotEmpty()) {
                                  renderClipPlayer(asset, caption)
                              } else {
                                  """<figure class="media-card"><video controls preload="metadata" src="$dataUri"></video><figcaption class="muted">$caption</figcaption></figure>"""
                              }
                          EvidenceKind.AUDIO ->
                              """<figure class="media-card"><audio controls preload="metadata" src="$dataUri"></audio><figcaption class="muted">$caption</figcaption></figure>"""
                      }
                  }
              }
            </div>
        """.trimIndent()
    }

    private fun renderClipPlayer(asset: InspectionEvidence, caption: String): String {
        val frames =
            asset.clipFramePaths.filter { File(it).exists() }.map { inlineDataUri(it) }
        if (frames.isEmpty()) return ""
        val frameJson = frames.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
        val first = frames.first()
        val seconds = if (asset.durationMs > 0) String.format(Locale.US, "%.1fs", asset.durationMs / 1000.0) else ""
        return """
            <figure class="media-card clip-player" data-fps="${asset.clipFps.coerceAtLeast(1)}">
              <img class="clip-frame" alt="$caption" src="$first" />
              <button class="clip-toggle" type="button">Play clip</button>
              <figcaption class="muted">$caption${if (seconds.isNotBlank()) " · $seconds" else ""}</figcaption>
              <script type="application/json" class="clip-data">$frameJson</script>
            </figure>
        """.trimIndent()
    }

    private fun inlineDataUri(path: String): String {
        val file = File(path)
        val mime =
            when (file.extension.lowercase(Locale.US)) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "webp" -> "image/webp"
                "mp4" -> "video/mp4"
                "webm" -> "video/webm"
                "m4a" -> "audio/mp4"
                "mp3" -> "audio/mpeg"
                "wav" -> "audio/wav"
                else -> "application/octet-stream"
            }
        val encoded = Base64.getEncoder().encodeToString(file.readBytes())
        return "data:$mime;base64,$encoded"
    }

    private fun fmt(ts: Long): String =
        SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.US).format(Date(ts))

    private fun escapeHtml(raw: String): String =
        raw
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
}
