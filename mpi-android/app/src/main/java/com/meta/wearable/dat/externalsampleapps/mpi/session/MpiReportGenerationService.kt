/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.mpi.session

import android.util.Log
import com.meta.wearable.dat.externalsampleapps.mpi.BuildConfig
import com.meta.wearable.dat.externalsampleapps.mpi.stream.ChatMessage
import com.meta.wearable.dat.externalsampleapps.mpi.stream.GeminiService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Conservative implementation of the MPI report-generation contract.
 *
 * This intentionally does not upload media yet. It provides safe scaffolding
 * for staged VLM media indexing, transcript extraction, finding merge, and
 * synthesis while falling back to [CaptureReportBuilder] whenever model-backed
 * synthesis is unavailable or risky.
 */
class DefaultMpiReportGenerationService(
    private val geminiService: GeminiService = GeminiService(),
) : MpiReportGenerationService {
    suspend fun generateFromSession(
        session: Session,
        checklist: List<MpiChecklistSectionConfig> = MpiChecklistConfig.DEFAULT_SECTIONS,
    ): MpiReport =
        withContext(Dispatchers.IO) {
            val localReport = MpiReportBuilder.build(session)
            val indexedEvidence = indexMediaForChecklist(session, checklist)
            val transcriptFindings = extractTranscriptFindings(session, checklist)
            val draft = buildDraftReport(session, checklist, localReport, indexedEvidence, transcriptFindings)
            synthesizeReport(draft)
        }

    override suspend fun indexMediaForChecklist(
        session: Session,
        checklist: List<MpiChecklistSectionConfig>,
    ): List<MpiEvidenceItem> =
        withContext(Dispatchers.IO) {
            val knownIds = checklist.flatMap { section -> section.items.map { it.id } }.toSet()
            session.evidenceAssets.mapIndexed { index, evidence ->
                MpiEvidenceItem(
                    id = "media-${session.id}-$index",
                    type = evidence.kind.toMpiEvidenceType(),
                    uri = evidence.filePath,
                    thumbnailUri = evidence.previewImagePath,
                    startTimeMs = null,
                    endTimeMs = evidence.durationMs.takeIf { it > 0L },
                    caption = evidence.caption.ifBlank { null },
                    checklistItemIds = emptyList(),
                    confidence = null,
                    source = evidence.kind.toMpiEvidenceSource(),
                ).let { item ->
                    if (knownIds.isEmpty()) item else item
                }
            }
        }

    override suspend fun extractTranscriptFindings(
        session: Session,
        checklist: List<MpiChecklistSectionConfig>,
    ): List<MpiInspectionItem> =
        withContext(Dispatchers.IO) {
            val technicianTranscript =
                session.messages
                    .filter { it.role == ChatMessage.Role.USER }
                    .joinToString("\n") { it.text }

            checklist.flatMap { section ->
                section.items.map { item ->
                    val mentioned = item.matchesTranscript(technicianTranscript)
                    MpiInspectionItem(
                        id = item.id,
                        label = item.label,
                        sectionId = section.id,
                        status = MpiInspectionStatus.UNKNOWN,
                        valueType = item.measurement.valueType,
                        possibleUnits = item.measurement.possibleUnits,
                        selectOptions = item.measurement.selectOptions,
                        needsReview = mentioned,
                        comments =
                            MpiCommentTree(
                                autoComment =
                                    if (mentioned) {
                                        "Transcript mentions this item, but status needs confirmation."
                                    } else {
                                        null
                                    },
                            ),
                    )
                }
            }
        }

    override suspend fun synthesizeReport(draft: MpiReport): MpiReport {
        if (BuildConfig.OPENROUTER_API_KEY.isBlank()) {
            return draft.copy(reportStatus = MpiReportStatus.NEEDS_REVIEW)
        }

        val prompt = buildSynthesisPrompt(draft)
        val modelText =
            runCatching {
                geminiService.analyzeFrame(
                    bitmap = null,
                    userQuestion = prompt,
                    history = emptyList(),
                    systemPromptOverride = MpiPromptConstants.REPORT_SYNTHESIS_SYSTEM_PROMPT.trimIndent(),
                )
            }.getOrElse { error ->
                Log.w(TAG, "MPI synthesis failed; returning local draft", error)
                return draft.copy(reportStatus = MpiReportStatus.NEEDS_REVIEW)
            }.trim()

        if (modelText.isBlank() || modelText.startsWith("OpenRouter API key", ignoreCase = true)) {
            return draft.copy(reportStatus = MpiReportStatus.NEEDS_REVIEW)
        }

        val synthesized = parseSynthesisJson(modelText) ?: return draft.copy(reportStatus = MpiReportStatus.NEEDS_REVIEW)
        return draft.copy(
            conciseDiagnosis = synthesized.conciseDiagnosis ?: draft.conciseDiagnosis,
            sections = applyItemCommentUpdates(draft.sections, synthesized.itemCommentUpdates),
            inspectionStory = synthesized.inspectionStory ?: draft.inspectionStory,
            reportStatus =
                if (draft.reportStatus == MpiReportStatus.ERROR || draft.reportStatus == MpiReportStatus.EMPTY) {
                    draft.reportStatus
                } else {
                    MpiReportStatus.NEEDS_REVIEW
                },
            needsReviewSummary =
                (synthesized.needsReviewSummary.ifEmpty { draft.needsReviewSummary }).distinct(),
        )
    }

    private fun parseSynthesisJson(rawText: String): SynthesizedReport? {
        val cleaned =
            rawText
                .trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
        val jsonText =
            if (cleaned.startsWith("{")) {
                cleaned
            } else {
                cleaned.substringAfter('{', missingDelimiterValue = "")
                    .substringBeforeLast('}', missingDelimiterValue = "")
                    .takeIf { it.isNotBlank() }
                    ?.let { "{$it}" }
                    ?: return null
            }
        return runCatching {
            val root = JSONObject(jsonText)
            SynthesizedReport(
                conciseDiagnosis = root.optString("conciseDiagnosis").ifBlank { null },
                itemCommentUpdates = root.optJSONArray("itemCommentUpdates").toItemCommentUpdates(),
                inspectionStory = root.optString("inspectionStory").ifBlank { null },
                needsReviewSummary = root.optJSONArray("needsReviewSummary").toStringList(),
            )
        }.getOrElse { error ->
            Log.w(TAG, "Failed to parse MPI synthesis JSON", error)
            null
        }
    }

    private fun applyItemCommentUpdates(
        sections: List<MpiInspectionSection>,
        updates: List<ItemCommentUpdate>,
    ): List<MpiInspectionSection> {
        if (updates.isEmpty()) return sections
        val byId = updates.associateBy { it.checklistItemId }
        return sections.map { section ->
            section.copy(
                items =
                    section.items.map { item ->
                        val update = byId[item.id] ?: return@map item
                        item.copy(
                            comments =
                                item.comments.copy(
                                    autoComment = update.autoComment ?: item.comments.autoComment,
                                    advisorWording = update.advisorWording ?: item.comments.advisorWording,
                                ),
                        )
                    },
            )
        }
    }

    private fun JSONArray?.toItemCommentUpdates(): List<ItemCommentUpdate> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                val itemId = item.optString("checklistItemId")
                if (itemId.isBlank()) continue
                add(
                    ItemCommentUpdate(
                        checklistItemId = itemId,
                        autoComment = item.optString("autoComment").ifBlank { null },
                        advisorWording = item.optString("advisorWording").ifBlank { null },
                    ),
                )
            }
        }
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                optString(index).ifBlank { null }?.let { add(it) }
            }
        }
    }

    private fun buildDraftReport(
        session: Session,
        checklist: List<MpiChecklistSectionConfig>,
        localReport: MpiReport,
        indexedEvidence: List<MpiEvidenceItem>,
        transcriptFindings: List<MpiInspectionItem>,
    ): MpiReport {
        val mentionedTranscriptItems = transcriptFindings.count { it.needsReview }
        val unknownItems = localReport.sections.flatMap { it.items }.count { it.status == MpiInspectionStatus.UNKNOWN }
        val reviewNotes =
            buildList {
                addAll(localReport.needsReviewSummary)
                if (indexedEvidence.isEmpty()) add("No media evidence was attached to checklist items.")
                if (mentionedTranscriptItems > 0) add("$mentionedTranscriptItems checklist items were mentioned but still need confirmation.")
                if (unknownItems > 0) add("$unknownItems checklist items were not confirmed by available evidence.")
                if (checklist.isEmpty()) add("No checklist configuration was available.")
                if (session.messages.isEmpty() && session.evidenceAssets.isEmpty()) add("No completed capture content was available.")
            }.distinct()

        return localReport.copy(
            reportStatus =
                if (reviewNotes.isNotEmpty() && localReport.reportStatus == MpiReportStatus.READY) {
                    MpiReportStatus.NEEDS_REVIEW
                } else {
                    localReport.reportStatus
                },
            needsReviewSummary = reviewNotes,
        )
    }

    private fun buildSynthesisPrompt(draft: MpiReport): String {
        val items =
            draft.sections.joinToString("\n") { section ->
                val rows =
                    section.items.joinToString("\n") { item ->
                        "- ${item.id}: ${item.label}, status=${item.status.name.lowercase()}, " +
                            "value=${item.value.orEmpty()} ${item.unit.orEmpty()}, " +
                            "comment=${item.comments.autoComment.orEmpty()}, needsReview=${item.needsReview}"
                    }
                "${section.title}\n$rows"
            }

        return """
            Generate the report synthesis JSON for this MPI draft.

            Technical snapshot:
            Capture date: ${draft.technicalSnapshot.captureDate}
            Mode: ${draft.technicalSnapshot.mode}
            Domain: ${draft.technicalSnapshot.domain}
            Video source: ${draft.technicalSnapshot.videoSource}
            Audio source: ${draft.technicalSnapshot.audioSource}
            Speech route: ${draft.technicalSnapshot.speechRoute}

            Current concise diagnosis:
            ${draft.conciseDiagnosis}

            Checklist items:
            $items

            Current review notes:
            ${draft.needsReviewSummary.joinToString("; ")}
        """.trimIndent()
    }

    private fun MpiChecklistItemConfig.matchesTranscript(transcript: String): Boolean {
        if (transcript.isBlank()) return false
        val terms = listOf(label) + aliases
        return terms.any { term ->
            term.isNotBlank() && transcript.contains(term, ignoreCase = true)
        }
    }

    private fun EvidenceKind.toMpiEvidenceType(): MpiEvidenceType =
        when (this) {
            EvidenceKind.IMAGE -> MpiEvidenceType.IMAGE
            EvidenceKind.VIDEO -> MpiEvidenceType.VIDEO
            EvidenceKind.AUDIO -> MpiEvidenceType.TRANSCRIPT
        }

    private fun EvidenceKind.toMpiEvidenceSource(): MpiEvidenceSource =
        when (this) {
            EvidenceKind.IMAGE,
            EvidenceKind.VIDEO -> MpiEvidenceSource.GLASSES
            EvidenceKind.AUDIO -> MpiEvidenceSource.TRANSCRIPT
        }

    private fun CaptureReport.toTechnicalSnapshot(): MpiTechnicalSnapshot {
        fun fact(label: String): String =
            facts.firstOrNull { it.label == label }?.value.orEmpty()

        return MpiTechnicalSnapshot(
            captureDate = fact("Capture date"),
            mode = fact("Mode"),
            domain = fact("Domain"),
            videoSource = fact("Video source"),
            audioSource = fact("Audio source"),
            speechRoute = fact("Speech route"),
        )
    }

    private fun formatTimestamp(timestamp: Long): String =
        SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.US).format(Date(timestamp))

    companion object {
        private const val TAG = "MpiReportGeneration"
    }

    private data class SynthesizedReport(
        val conciseDiagnosis: String?,
        val itemCommentUpdates: List<ItemCommentUpdate>,
        val inspectionStory: String?,
        val needsReviewSummary: List<String>,
    )

    private data class ItemCommentUpdate(
        val checklistItemId: String,
        val autoComment: String?,
        val advisorWording: String?,
    )
}
