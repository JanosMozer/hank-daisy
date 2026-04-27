package com.meta.wearable.dat.externalsampleapps.mpi.session

import com.meta.wearable.dat.externalsampleapps.mpi.stream.ChatMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ReportField(val label: String, val value: String)

data class CaptureReport(
    val facts: List<ReportField>,
    val summary: String,
    val checklist: List<String>,
    val evidence: List<InspectionEvidence>,
)

object CaptureReportBuilder {
    private val carMakes =
        listOf(
            "Acura",
            "Audi",
            "BMW",
            "Chevrolet",
            "Ford",
            "Honda",
            "Hyundai",
            "Kia",
            "Lexus",
            "Mazda",
            "Mercedes",
            "Nissan",
            "Subaru",
            "Tesla",
            "Toyota",
            "Volkswagen",
            "Volvo",
        )

    fun build(session: Session): CaptureReport {
        val transcript = session.messages.joinToString(" ") { it.text }
        val metadata = session.metadata
        val primaryIssue = detectPrimaryIssue(session.messages)
        val deviceLabel = detectDeviceLabel(session, transcript)
        val year = detectYear(transcript)
        val pCodes = detectPCodes(transcript)
        val evidence = selectEvidence(session.evidenceAssets)

        val facts =
            buildList {
                add(ReportField("Capture date", formatTimestamp(session.createdAt)))
                add(
                    ReportField(
                        "Mode",
                        metadata?.hankMode?.label ?: HankMode.INTERACTIVE.label,
                    ),
                )
                add(
                    ReportField(
                        "Domain",
                        metadata?.domainMode?.label ?: DomainMode.CAR_ONLY.label,
                    ),
                )
                add(
                    ReportField(
                        "Video source",
                        metadata?.videoSource?.label ?: CaptureVideoSource.GLASSES.label,
                    ),
                )
                add(
                    ReportField(
                        "Audio source",
                        metadata?.audioSource?.label ?: CaptureAudioSource.GLASSES_MIC.label,
                    ),
                )
                add(
                    ReportField(
                        "Speech route",
                        metadata?.speechRecognitionRoute?.segmentLabel
                            ?: SpeechRecognitionRoute.ANDROID.segmentLabel,
                    ),
                )
                if (deviceLabel.isNotBlank()) {
                    add(ReportField("Object", deviceLabel))
                }
                if (year != null) {
                    add(ReportField("Model year", year))
                }
                add(ReportField("Main issue", primaryIssue))
                if (pCodes.isNotBlank()) {
                    add(ReportField("P codes", pCodes))
                }
            }.take(6)

        val summary =
            buildSummary(session, deviceLabel = deviceLabel, primaryIssue = primaryIssue)
        val checklist = buildChecklist(session, primaryIssue, metadata?.domainMode)

        return CaptureReport(
            facts = facts,
            summary = summary,
            checklist = checklist,
            evidence = evidence,
        )
    }

    private fun detectDeviceLabel(session: Session, transcript: String): String {
        val explicit =
            carMakes.firstOrNull { Regex("\\b$it\\b", RegexOption.IGNORE_CASE).containsMatchIn(transcript) }
        if (explicit != null) {
            val model =
                Regex("\\b$explicit\\s+([A-Za-z0-9-]{2,20})\\b", RegexOption.IGNORE_CASE)
                    .find(transcript)
                    ?.groupValues
                    ?.getOrNull(1)
                    .orEmpty()
            return listOf(explicit, model.ifBlank { null }).joinToString(" ").trim()
        }

        val domain = session.metadata?.domainMode ?: DomainMode.CAR_ONLY
        return when {
            Regex("\\bbike|bicycle|derailleur|chain|rotor\\b", RegexOption.IGNORE_CASE)
                .containsMatchIn(transcript) -> "Bicycle"
            domain == DomainMode.CAR_ONLY -> "Vehicle under inspection"
            else -> "Device under inspection"
        }
    }

    private fun detectYear(transcript: String): String? =
        Regex("\\b(19\\d{2}|20\\d{2})\\b").find(transcript)?.value

    private fun detectPCodes(transcript: String): String =
        Regex("\\bP\\d{4}\\b", RegexOption.IGNORE_CASE)
            .findAll(transcript)
            .map { it.value.uppercase(Locale.US) }
            .distinct()
            .take(4)
            .joinToString(", ")

    private fun detectPrimaryIssue(messages: List<ChatMessage>): String {
        val userText =
            messages
                .filter { it.role == ChatMessage.Role.USER }
                .joinToString(". ") { it.text.trim() }
        if (userText.isBlank()) return "No explicit issue captured"

        val patterns =
            listOf(
                Regex("(leak(?:ing)? [^.]{0,40})", RegexOption.IGNORE_CASE),
                Regex("(crack(?:ed)? [^.]{0,40})", RegexOption.IGNORE_CASE),
                Regex("(noise [^.]{0,40})", RegexOption.IGNORE_CASE),
                Regex("(won't start[^.]{0,40})", RegexOption.IGNORE_CASE),
                Regex("(misfire[^.]{0,40})", RegexOption.IGNORE_CASE),
                Regex("(overheat(?:ing)?[^.]{0,40})", RegexOption.IGNORE_CASE),
                Regex("(brake[^.]{0,40})", RegexOption.IGNORE_CASE),
                Regex("(battery[^.]{0,40})", RegexOption.IGNORE_CASE),
            )
        val matched = patterns.firstNotNullOfOrNull { it.find(userText)?.value?.trim() }
        if (!matched.isNullOrBlank()) return matched.replaceFirstChar { it.uppercase() }

        val firstSentence = userText.substringBefore('.').trim()
        return firstSentence.take(80).ifBlank { "No explicit issue captured" }
    }

    private fun buildSummary(
        session: Session,
        deviceLabel: String,
        primaryIssue: String,
    ): String {
        val lastAssistant =
            session.messages.lastOrNull { it.role == ChatMessage.Role.ASSISTANT }?.text?.trim()
        if (!lastAssistant.isNullOrBlank()) {
            return lastAssistant.split(Regex("(?<=[.!?])\\s+")).take(3).joinToString(" ").trim()
        }

        val domain = session.metadata?.domainMode ?: DomainMode.CAR_ONLY
        val scope =
            when (domain) {
                DomainMode.CAR_ONLY -> "The capture focused on a vehicle scene"
                DomainMode.GENERAL_DEVICE -> "The capture focused on a repair scene"
            }
        return "$scope around $deviceLabel. The primary observed concern was $primaryIssue."
    }

    private fun buildChecklist(
        session: Session,
        primaryIssue: String,
        domainMode: DomainMode?,
    ): List<String> {
        val assistantSentences =
            session.messages
                .filter { it.role == ChatMessage.Role.ASSISTANT }
                .flatMap { it.text.split(Regex("(?<=[.!?])\\s+")) }
                .map { it.trim() }
                .filter { it.length > 18 }

        val extracted =
            assistantSentences
                .mapNotNull { sentence ->
                    val clean = sentence.removeSuffix(".").trim()
                    if (
                        clean.startsWith("check ", ignoreCase = true) ||
                            clean.startsWith("inspect ", ignoreCase = true) ||
                            clean.startsWith("look ", ignoreCase = true) ||
                            clean.startsWith("verify ", ignoreCase = true) ||
                            clean.startsWith("move ", ignoreCase = true)
                    ) clean else null
                }
                .distinct()
                .take(4)

        if (extracted.isNotEmpty()) return extracted

        return when (domainMode ?: DomainMode.CAR_ONLY) {
            DomainMode.CAR_ONLY ->
                listOf(
                    "Confirm the symptom around $primaryIssue with a closer visual pass.",
                    "Inspect adjacent hoses, connectors, fasteners, and wear points in the same area.",
                    "Check for supporting fault codes, leaks, or heat damage before recommending parts.",
                )
            DomainMode.GENERAL_DEVICE ->
                listOf(
                    "Confirm the symptom around $primaryIssue with a closer visual pass.",
                    "Inspect surrounding connectors, fasteners, and wear points in the same area.",
                    "Collect one more angle or label shot before locking the diagnosis.",
                )
        }
    }

    private fun selectEvidence(allEvidence: List<InspectionEvidence>): List<InspectionEvidence> =
        allEvidence
            .sortedByDescending { it.createdAt }
            .filter {
                it.kind == EvidenceKind.IMAGE ||
                    it.previewImagePath != null ||
                    it.clipFramePaths.isNotEmpty()
            }
            .take(4)

    private fun formatTimestamp(timestamp: Long): String =
        SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.US).format(Date(timestamp))
}
