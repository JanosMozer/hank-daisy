package com.meta.wearable.dat.externalsampleapps.mpi.session

enum class MpiInspectionStatus {
    GREEN,
    YELLOW,
    RED,
    UNKNOWN,
}

enum class MpiEvidenceType {
    IMAGE,
    VIDEO,
    TRANSCRIPT,
    NOTE,
}

enum class MpiEvidenceSource {
    GLASSES,
    PHONE,
    TRANSCRIPT,
    MANUAL,
    VLM,
}

enum class MpiReportStatus {
    EMPTY,
    PROCESSING,
    READY,
    NEEDS_REVIEW,
    ERROR,
}

enum class MpiValueType {
    NONE,
    NUMBER,
    TEXT,
    SELECT,
}

data class MpiMeasurementThresholds(
    val greenMin: Double? = null,
    val yellowMin: Double? = null,
    val yellowMax: Double? = null,
    val redMax: Double? = null,
    val redBelow: Double? = null,
)

data class MpiMeasurementMetadata(
    val valueType: MpiValueType = MpiValueType.NONE,
    val unit: String? = null,
    val possibleUnits: List<String> = emptyList(),
    val selectOptions: List<String> = emptyList(),
    val thresholds: MpiMeasurementThresholds? = null,
)

data class MpiChecklistItemConfig(
    val id: String,
    val label: String,
    val aliases: List<String> = emptyList(),
    val measurement: MpiMeasurementMetadata = MpiMeasurementMetadata(),
)

data class MpiChecklistSectionConfig(
    val id: String,
    val title: String,
    val description: String? = null,
    val collapsedByDefault: Boolean = true,
    val items: List<MpiChecklistItemConfig>,
)

data class MpiEvidenceItem(
    val id: String,
    val type: MpiEvidenceType,
    val uri: String? = null,
    val thumbnailUri: String? = null,
    val startTimeMs: Long? = null,
    val endTimeMs: Long? = null,
    val caption: String? = null,
    val checklistItemIds: List<String> = emptyList(),
    val confidence: Double? = null,
    val source: MpiEvidenceSource,
)

data class MpiCommentTree(
    val autoComment: String? = null,
    val technicianNote: String? = null,
    val advisorWording: String? = null,
)

data class MpiInspectionItem(
    val id: String,
    val label: String,
    val sectionId: String,
    val status: MpiInspectionStatus = MpiInspectionStatus.UNKNOWN,
    val value: String? = null,
    val unit: String? = null,
    val valueType: MpiValueType = MpiValueType.NONE,
    val possibleUnits: List<String> = emptyList(),
    val selectOptions: List<String> = emptyList(),
    val needsReview: Boolean = false,
    val confidence: Double? = null,
    val comments: MpiCommentTree = MpiCommentTree(),
    val evidence: List<MpiEvidenceItem> = emptyList(),
)

data class MpiInspectionSection(
    val id: String,
    val title: String,
    val description: String? = null,
    val items: List<MpiInspectionItem>,
    val collapsedByDefault: Boolean = true,
)

data class MpiTechnicalSnapshot(
    val captureDate: String,
    val mode: String,
    val domain: String,
    val videoSource: String,
    val audioSource: String,
    val speechRoute: String,
)

data class MpiReport(
    val sessionId: String,
    val technicalSnapshot: MpiTechnicalSnapshot,
    val conciseDiagnosis: String,
    val sections: List<MpiInspectionSection>,
    val inspectionStory: String,
    val generatedAt: String,
    val reportStatus: MpiReportStatus,
    val needsReviewSummary: List<String> = emptyList(),
)

object MpiPromptConstants {
    const val VLM_INDEXING_SYSTEM_PROMPT: String =
        """
You are an automotive multi-point inspection assistant.

You analyze images, video frames, short video clips, and transcript snippets from a mechanic's vehicle inspection.

Your job is to map visible or explicitly spoken evidence to a structured MPI checklist.

You must be conservative.

Rules:
- Do not hallucinate.
- Do not mark an item green just because it is not visible.
- Do not invent measurements.
- Do not diagnose hidden mechanical problems.
- Use "unknown" when evidence is unclear.
- Use transcript statements when the technician explicitly states measurements or statuses.
- Return JSON only.
- Keep comments short and useful for a mechanic.

Status definitions:
- green: item appears OK or technician explicitly says it is OK
- yellow: item needs attention soon, monitoring, or is borderline
- red: item failed, unsafe, damaged, worn beyond threshold, leaking, or explicitly marked red
- unknown: insufficient evidence

Output schema:

{
  "mediaId": "string",
  "isVehicleRelated": true,
  "vehicleArea": "exterior | interior | under_hood | under_vehicle | tires_brakes | road_test | unknown",
  "visibleComponents": ["string"],
  "observations": [
    {
      "checklistItemId": "string",
      "status": "green | yellow | red | unknown",
      "measurementValue": null,
      "measurementUnit": null,
      "condition": "short condition description",
      "comment": "short mechanic-facing comment",
      "confidence": 0.0,
      "evidenceCaption": "short caption for the evidence",
      "startTimeMs": null,
      "endTimeMs": null
    }
  ],
  "irrelevantReason": null,
  "needsTechnicianReview": false
}

Return only valid JSON.
"""

    const val TRANSCRIPT_EXTRACTION_SYSTEM_PROMPT: String =
        """
You are extracting structured multi-point inspection findings from a mechanic's spoken transcript.

Return JSON only.

Do not invent values. Extract only what is explicitly stated.

Map findings to the checklist item IDs.

Rules:
- Phrases like "mark red", "failed", "replace now", "unsafe", "leaking badly" imply red.
- Phrases like "recommend soon", "monitor", "getting low", "borderline" imply yellow.
- Phrases like "good", "okay", "passes", "no issue" imply green.
- Measurements must be copied exactly.
- If uncertain, use unknown and lower confidence.
"""

    const val REPORT_SYNTHESIS_SYSTEM_PROMPT: String =
        """
You are generating a dealership-style multi-point inspection report from structured findings.

Use only the provided checklist data. Do not invent new issues, measurements, or repair recommendations.

Generate:
1. conciseDiagnosis
2. auto comments for yellow/red/needs-review items
3. advisor/customer wording for yellow/red items
4. final inspectionStory

Tone:
- practical
- concise
- mechanic/service-advisor friendly
- not overly legalistic
- not overly verbose

Rules:
- Mention all red items.
- Mention important yellow items.
- Mention measurements when available.
- Say when evidence is limited.
- Do not claim safety-critical conclusions unless evidence strongly supports it.
- For unknown items, say "not confirmed" only if relevant.
- Return JSON only.
"""
}

interface MpiReportGenerationService {
    suspend fun indexMediaForChecklist(
        session: Session,
        checklist: List<MpiChecklistSectionConfig> = MpiChecklistConfig.DEFAULT_SECTIONS,
    ): List<MpiEvidenceItem>

    suspend fun extractTranscriptFindings(
        session: Session,
        checklist: List<MpiChecklistSectionConfig> = MpiChecklistConfig.DEFAULT_SECTIONS,
    ): List<MpiInspectionItem>

    suspend fun synthesizeReport(
        draft: MpiReport,
    ): MpiReport
}
