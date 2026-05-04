package com.meta.wearable.dat.externalsampleapps.mpi.session

import com.meta.wearable.dat.externalsampleapps.mpi.stream.ChatMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MpiReportBuilder {
    private const val AUTO_FILL_CONFIDENCE = 0.75
    private const val REVIEW_CONFIDENCE = 0.45

    private val sentenceSplitRegex = Regex("(?<=[.!?])\\s+|\\n+")
    private val numberRegex = Regex("(\\d+(?:\\.\\d+)?)")

    fun build(session: Session): MpiReport {
        val transcriptMessages = session.messages.filter { it.role == ChatMessage.Role.USER }
        val transcript = transcriptMessages.joinToString(" ") { it.text.trim() }
        val mediaEvidence = mapSessionEvidence(session)
        val findings = mutableMapOf<String, DraftFinding>()

        extractTranscriptFindings(transcriptMessages).forEach { finding ->
            findings.mergeFinding(finding)
        }

        attachMediaEvidence(mediaEvidence).forEach { (itemId, evidence) ->
            findings.mergeFinding(
                DraftFinding(
                    itemId = itemId,
                    evidence = evidence,
                    confidence = evidence.maxOfOrNull { it.confidence ?: 0.0 } ?: 0.0,
                    needsReview = evidence.any { (it.confidence ?: 0.0) >= REVIEW_CONFIDENCE },
                ),
            )
        }

        if (transcript.isNotBlank() && findings.values.none { it.status != MpiInspectionStatus.UNKNOWN }) {
            findings.mergeFinding(
                DraftFinding(
                    itemId = "manual_confirmation_required",
                    needsReview = true,
                    confidence = REVIEW_CONFIDENCE,
                    autoComment = "Transcript captured, but no explicit MPI measurements or statuses were found.",
                    evidence = transcriptMessages.take(1).map { transcriptEvidence("transcript-review", it, listOf("manual_confirmation_required")) },
                ),
            )
        }

        val sections =
            MpiChecklistConfig.DEFAULT_SECTIONS.map { sectionConfig ->
                val items =
                    sectionConfig.items.map { itemConfig ->
                        val draft = findings[itemConfig.id]
                        val measurement = itemConfig.measurement
                        val status =
                            if ((draft?.confidence ?: 0.0) >= AUTO_FILL_CONFIDENCE) {
                                draft?.status ?: MpiInspectionStatus.UNKNOWN
                            } else {
                                MpiInspectionStatus.UNKNOWN
                            }
                        val draftConfidence = draft?.confidence ?: 1.0
                        val needsReview =
                            draft?.needsReview == true ||
                                (draftConfidence >= REVIEW_CONFIDENCE && draftConfidence < AUTO_FILL_CONFIDENCE)
                        MpiInspectionItem(
                            id = itemConfig.id,
                            label = itemConfig.label,
                            sectionId = sectionConfig.id,
                            status = status,
                            value = draft?.value,
                            unit = draft?.unit ?: measurement.unit,
                            valueType = measurement.valueType,
                            possibleUnits = measurement.possibleUnits,
                            selectOptions = measurement.selectOptions,
                            needsReview = needsReview,
                            confidence = draft?.confidence,
                            comments =
                                MpiCommentTree(
                                    autoComment = draft?.autoComment,
                                    advisorWording = draft?.advisorWording,
                                ),
                            evidence = draft?.evidence.orEmpty().distinctBy { it.id },
                        )
                    }
                sectionConfig.toReportSection(items)
            }

        val allItems = sections.flatMap { it.items }
        val reviewItems = allItems.filter { it.needsReview }
        val populatedItems = allItems.filter { it.status != MpiInspectionStatus.UNKNOWN || it.value != null }
        val reportStatus =
            when {
                session.messages.isEmpty() && session.evidenceAssets.isEmpty() -> MpiReportStatus.EMPTY
                reviewItems.isNotEmpty() || populatedItems.isEmpty() -> MpiReportStatus.NEEDS_REVIEW
                else -> MpiReportStatus.READY
            }

        return MpiReport(
            sessionId = session.id,
            technicalSnapshot = buildTechnicalSnapshot(session),
            conciseDiagnosis = buildConciseDiagnosis(populatedItems, reviewItems, session),
            sections = sections,
            inspectionStory = buildInspectionStory(populatedItems, reviewItems, session),
            generatedAt = formatTimestamp(System.currentTimeMillis()),
            reportStatus = reportStatus,
            needsReviewSummary = reviewItems.take(6).map { "${it.label}: technician review recommended." },
        )
    }

    private fun extractTranscriptFindings(messages: List<ChatMessage>): List<DraftFinding> {
        if (messages.isEmpty()) return emptyList()

        val findings = mutableListOf<DraftFinding>()
        val transcript = messages.joinToString(" ") { it.text }
        findings += extractMeasurementFindings(transcript, messages)

        messages.forEachIndexed { messageIndex, message ->
            message.text
                .split(sentenceSplitRegex)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .forEachIndexed { sentenceIndex, sentence ->
                    val matchedItem = bestChecklistMatch(sentence) ?: return@forEachIndexed
                    val status = explicitStatus(sentence)
                    val review = reviewHint(sentence)
                    if (status != MpiInspectionStatus.UNKNOWN || review) {
                        val confidence = if (status != MpiInspectionStatus.UNKNOWN) 0.9 else 0.6
                        findings +=
                            DraftFinding(
                                itemId = matchedItem.id,
                                status = status,
                                needsReview = review,
                                confidence = confidence,
                                autoComment = mechanicComment(matchedItem.label, status, null, null, sentence),
                                advisorWording = advisorWording(matchedItem.label, status, null, null),
                                evidence =
                                    listOf(
                                        transcriptEvidence(
                                            idSuffix = "transcript-$messageIndex-$sentenceIndex",
                                            message = message,
                                            checklistItemIds = listOf(matchedItem.id),
                                            caption = sentence.take(120),
                                        ),
                                    ),
                            )
                    }
                }
        }

        return findings
    }

    private fun extractMeasurementFindings(
        transcript: String,
        messages: List<ChatMessage>,
    ): List<DraftFinding> {
        val findings = mutableListOf<DraftFinding>()
        val lowerTranscript = transcript.lowercase(Locale.US)
        val transcriptEvidence = messages.take(1)

        fun addMeasuredFinding(
            itemId: String,
            value: String,
            unit: String,
            rawText: String,
        ) {
            val itemConfig = MpiChecklistConfig.ITEMS_BY_ID[itemId] ?: return
            val numericValue = value.toDoubleOrNull()
            val inferredStatus = numericValue?.let { inferStatus(itemConfig.measurement.thresholds, it) }
                ?: MpiInspectionStatus.UNKNOWN
            val status =
                if (inferredStatus == MpiInspectionStatus.UNKNOWN) {
                    explicitStatus(rawText)
                } else {
                    inferredStatus
                }
            findings +=
                DraftFinding(
                    itemId = itemId,
                    status = status,
                    value = value,
                    unit = unit,
                    confidence = 0.95,
                    autoComment = mechanicComment(itemConfig.label, status, value, unit, rawText),
                    advisorWording = advisorWording(itemConfig.label, status, value, unit),
                    evidence =
                        transcriptEvidence.map {
                            transcriptEvidence(
                                idSuffix = "transcript-measurement-$itemId",
                                message = it,
                                checklistItemIds = listOf(itemId),
                                caption = rawText.take(120),
                            )
                        },
                )
        }

        sideItems(
            transcript = lowerTranscript,
            suffix = "tire_tread",
            unit = "/32",
            patterns =
                listOf(
                    "(%s).{0,36}?(?:tread|tire).{0,24}?(\\d+(?:\\.\\d+)?)\\s*(?:/32|32nds?|thirty seconds?)",
                    "(%s).{0,36}?(\\d+(?:\\.\\d+)?)\\s*(?:/32|32nds?|thirty seconds?)",
                ),
        ).forEach { (itemId, match) -> addMeasuredFinding(itemId, match.value, match.unit, match.rawText) }

        sideItems(
            transcript = lowerTranscript,
            suffix = "tire_pressure",
            unit = "PSI",
            patterns =
                listOf(
                    "(%s).{0,36}?(?:pressure|psi).{0,24}?(\\d+(?:\\.\\d+)?)\\s*(?:psi)?",
                    "(%s).{0,36}?(\\d+(?:\\.\\d+)?)\\s*psi",
                ),
        ).forEach { (itemId, match) -> addMeasuredFinding(itemId, match.value, match.unit, match.rawText) }

        sideItems(
            transcript = lowerTranscript,
            suffix = "brake_pad",
            unit = "mm",
            patterns =
                listOf(
                    "(%s).{0,36}?(?:brake pad|pad).{0,24}?(\\d+(?:\\.\\d+)?)\\s*(?:mm|millimeters?)",
                    "(%s).{0,36}?(\\d+(?:\\.\\d+)?)\\s*(?:mm|millimeters?).{0,24}?(?:brake pad|pad)",
                ),
        ).forEach { (itemId, match) -> addMeasuredFinding(itemId, match.value, match.unit, match.rawText) }

        Regex("battery.{0,36}?(\\d+(?:\\.\\d+)?)\\s*(?:v|volts?)", RegexOption.IGNORE_CASE)
            .find(lowerTranscript)
            ?.let { match ->
                addMeasuredFinding(
                    itemId = "battery_condition",
                    value = match.groupValues[1],
                    unit = "V",
                    rawText = match.value,
                )
            }

        extractFluidSelectFindings(lowerTranscript, messages).forEach { findings += it }

        return findings
    }

    private fun sideItems(
        transcript: String,
        suffix: String,
        unit: String,
        patterns: List<String>,
    ): List<Pair<String, MeasurementMatch>> {
        val sides =
            listOf(
                Side("lf", "left front", "lf"),
                Side("rf", "right front", "rf"),
                Side("lr", "left rear", "lr"),
                Side("rr", "right rear", "rr"),
            )

        return sides.mapNotNull { side ->
            val sidePattern = "(?:${side.longName}|${side.shortName})"
            val match =
                patterns.firstNotNullOfOrNull { pattern ->
                    Regex(pattern.format(sidePattern), RegexOption.IGNORE_CASE).find(transcript)
                }
            match?.let {
                "${side.id}_$suffix" to
                    MeasurementMatch(
                        value = it.groupValues.last(),
                        unit = unit,
                        rawText = it.value,
                    )
            }
        }
    }

    private fun extractFluidSelectFindings(
        transcript: String,
        messages: List<ChatMessage>,
    ): List<DraftFinding> {
        val fluidItems =
            listOf(
                "engine_oil_level",
                "engine_oil_condition",
                "coolant_antifreeze",
                "brake_fluid",
                "power_steering_fluid",
                "transmission_fluid",
                "windshield_washer_fluid",
            )
        val findings = mutableListOf<DraftFinding>()
        val firstMessage = messages.firstOrNull()
        fluidItems.forEach { itemId ->
            val itemConfig = MpiChecklistConfig.ITEMS_BY_ID[itemId] ?: return@forEach
            val sentence =
                transcript.split(sentenceSplitRegex).firstOrNull { sentence ->
                    itemTokens(itemConfig).any { token -> sentence.contains(token, ignoreCase = true) }
                } ?: return@forEach
            val value =
                when {
                    Regex("\\blow\\b", RegexOption.IGNORE_CASE).containsMatchIn(sentence) -> "low"
                    Regex("\\bhigh\\b|\\boverfilled\\b", RegexOption.IGNORE_CASE).containsMatchIn(sentence) -> "high"
                    Regex("\\bdirty\\b|\\bdark\\b", RegexOption.IGNORE_CASE).containsMatchIn(sentence) -> "dirty"
                    Regex("\\bcontaminated\\b|\\bmilky\\b|\\bburnt\\b", RegexOption.IGNORE_CASE).containsMatchIn(sentence) -> "contaminated"
                    Regex("\\bok\\b|\\bgood\\b|\\bfull\\b", RegexOption.IGNORE_CASE).containsMatchIn(sentence) -> "ok"
                    else -> null
                } ?: return@forEach
            val status =
                when (value) {
                    "ok" -> MpiInspectionStatus.GREEN
                    "low", "high", "dirty" -> MpiInspectionStatus.YELLOW
                    "contaminated" -> MpiInspectionStatus.RED
                    else -> MpiInspectionStatus.UNKNOWN
                }
            findings +=
                DraftFinding(
                    itemId = itemId,
                    status = status,
                    value = value,
                    confidence = 0.9,
                    autoComment = mechanicComment(itemConfig.label, status, value, null, sentence),
                    advisorWording = advisorWording(itemConfig.label, status, value, null),
                    evidence =
                        firstMessage?.let {
                            listOf(
                                transcriptEvidence(
                                    idSuffix = "transcript-fluid-$itemId",
                                    message = it,
                                    checklistItemIds = listOf(itemId),
                                    caption = sentence.take(120),
                                ),
                            )
                        }.orEmpty(),
                )
        }
        return findings
    }

    private fun mapSessionEvidence(session: Session): List<MpiEvidenceItem> =
        session.evidenceAssets.map { evidence ->
            val type =
                when (evidence.kind) {
                    EvidenceKind.IMAGE -> MpiEvidenceType.IMAGE
                    EvidenceKind.VIDEO -> MpiEvidenceType.VIDEO
                    EvidenceKind.AUDIO -> MpiEvidenceType.TRANSCRIPT
                }
            val source =
                when (evidence.kind) {
                    EvidenceKind.AUDIO -> MpiEvidenceSource.TRANSCRIPT
                    EvidenceKind.IMAGE, EvidenceKind.VIDEO ->
                        when (session.metadata?.videoSource) {
                            CaptureVideoSource.PHONE_CAMERA -> MpiEvidenceSource.PHONE
                            else -> MpiEvidenceSource.GLASSES
                        }
                }
            MpiEvidenceItem(
                id = evidence.id,
                type = type,
                uri = evidence.filePath,
                thumbnailUri = evidence.previewImagePath ?: evidence.clipFramePaths.firstOrNull(),
                startTimeMs = null,
                endTimeMs = evidence.durationMs.takeIf { it > 0 },
                caption = evidence.caption.ifBlank { null },
                confidence = null,
                source = source,
            )
        }

    private fun attachMediaEvidence(evidenceItems: List<MpiEvidenceItem>): Map<String, List<MpiEvidenceItem>> {
        if (evidenceItems.isEmpty()) return emptyMap()
        val attached = mutableMapOf<String, MutableList<MpiEvidenceItem>>()
        evidenceItems.forEach { evidence ->
            val haystack = listOfNotNull(evidence.caption, evidence.uri, evidence.thumbnailUri).joinToString(" ")
            if (haystack.isBlank()) return@forEach
            MpiChecklistConfig.ITEMS_BY_ID.values
                .filter { item -> itemTokens(item).any { token -> haystack.contains(token, ignoreCase = true) } }
                .take(3)
                .forEach { item ->
                    attached.getOrPut(item.id) { mutableListOf() } +=
                        evidence.copy(
                            checklistItemIds = (evidence.checklistItemIds + item.id).distinct(),
                            confidence = evidence.confidence ?: 0.55,
                        )
                }
        }
        return attached
    }

    private fun bestChecklistMatch(sentence: String): MpiChecklistItemConfig? =
        MpiChecklistConfig.ITEMS_BY_ID.values
            .mapNotNull { item ->
                val score = itemTokens(item).count { token -> sentence.contains(token, ignoreCase = true) }
                if (score > 0) item to score else null
            }
            .maxByOrNull { it.second }
            ?.first

    private fun itemTokens(item: MpiChecklistItemConfig): List<String> =
        (listOf(item.label) + item.aliases)
            .flatMap { value ->
                listOf(
                    value.lowercase(Locale.US),
                    value.lowercase(Locale.US).replace("/", " "),
                )
            }
            .map { it.trim() }
            .filter { it.length >= 3 }
            .distinct()

    private fun explicitStatus(text: String): MpiInspectionStatus =
        when {
            Regex("\\b(red|failed|fail|unsafe|replace now|recommend now|bad|torn|broken|leaking badly)\\b", RegexOption.IGNORE_CASE)
                .containsMatchIn(text) -> MpiInspectionStatus.RED
            Regex("\\b(yellow|attention soon|recommend soon|monitor|borderline|getting low|low|worn|dirty)\\b", RegexOption.IGNORE_CASE)
                .containsMatchIn(text) -> MpiInspectionStatus.YELLOW
            Regex("\\b(green|ok|okay|good|passes|pass|no issue|normal)\\b", RegexOption.IGNORE_CASE)
                .containsMatchIn(text) -> MpiInspectionStatus.GREEN
            else -> MpiInspectionStatus.UNKNOWN
        }

    private fun reviewHint(text: String): Boolean =
        Regex("\\b(unclear|ambiguous|not sure|can't confirm|cannot confirm|needs review|review|manual confirmation)\\b", RegexOption.IGNORE_CASE)
            .containsMatchIn(text)

    private fun inferStatus(
        thresholds: MpiMeasurementThresholds?,
        value: Double,
    ): MpiInspectionStatus {
        if (thresholds == null) return MpiInspectionStatus.UNKNOWN
        if (thresholds.redBelow != null && value < thresholds.redBelow) return MpiInspectionStatus.RED
        if (thresholds.redMax != null && value <= thresholds.redMax) return MpiInspectionStatus.RED
        if (thresholds.yellowMin != null && thresholds.yellowMax != null && value >= thresholds.yellowMin && value <= thresholds.yellowMax) {
            return MpiInspectionStatus.YELLOW
        }
        if (thresholds.greenMin != null && value >= thresholds.greenMin) return MpiInspectionStatus.GREEN
        return MpiInspectionStatus.UNKNOWN
    }

    private fun mechanicComment(
        label: String,
        status: MpiInspectionStatus,
        value: String?,
        unit: String?,
        rawText: String?,
    ): String? {
        val measurement = value?.let { " recorded at $it${unit?.let { unitValue -> " $unitValue" }.orEmpty()}" }.orEmpty()
        return when (status) {
            MpiInspectionStatus.GREEN -> "$label$measurement marked OK from explicit capture evidence."
            MpiInspectionStatus.YELLOW -> "$label$measurement needs attention or monitoring based on explicit capture evidence."
            MpiInspectionStatus.RED -> "$label$measurement was flagged red from explicit capture evidence."
            MpiInspectionStatus.UNKNOWN -> rawText?.take(140)
        }
    }

    private fun advisorWording(
        label: String,
        status: MpiInspectionStatus,
        value: String?,
        unit: String?,
    ): String? {
        val measurement = value?.let { " ($it${unit?.let { unitValue -> " $unitValue" }.orEmpty()})" }.orEmpty()
        return when (status) {
            MpiInspectionStatus.RED -> "Recommend technician confirmation and correction for $label$measurement before delivery."
            MpiInspectionStatus.YELLOW -> "Review $label$measurement with the customer and monitor or quote according to shop policy."
            else -> null
        }
    }

    private fun buildConciseDiagnosis(
        populatedItems: List<MpiInspectionItem>,
        reviewItems: List<MpiInspectionItem>,
        session: Session,
    ): String {
        if (session.messages.isEmpty() && session.evidenceAssets.isEmpty()) {
            return "No completed capture content was available to generate an MPI report."
        }
        val reds = populatedItems.filter { it.status == MpiInspectionStatus.RED }
        val yellows = populatedItems.filter { it.status == MpiInspectionStatus.YELLOW }
        return when {
            reds.isNotEmpty() || yellows.isNotEmpty() -> {
                val redText = reds.take(3).joinToString(", ") { it.labelWithValue() }
                val yellowText = yellows.take(3).joinToString(", ") { it.labelWithValue() }
                buildString {
                    append("MPI draft generated from explicit capture evidence.")
                    if (redText.isNotBlank()) append(" Red items: $redText.")
                    if (yellowText.isNotBlank()) append(" Attention items: $yellowText.")
                    if (reviewItems.isNotEmpty()) append(" Some items still need technician review.")
                }
            }
            populatedItems.isNotEmpty() -> {
                val okText = populatedItems.take(4).joinToString(", ") { it.labelWithValue() }
                "MPI draft generated. Explicitly confirmed items: $okText. Unobserved checklist items remain unknown."
            }
            else ->
                "Capture quality or transcript detail was limited. The checklist remains mostly unknown and should be completed manually before sending."
        }
    }

    private fun buildInspectionStory(
        populatedItems: List<MpiInspectionItem>,
        reviewItems: List<MpiInspectionItem>,
        session: Session,
    ): String {
        if (session.messages.isEmpty() && session.evidenceAssets.isEmpty()) {
            return "No inspection story is available because no capture content was recorded."
        }

        val reds = populatedItems.filter { it.status == MpiInspectionStatus.RED }
        val yellows = populatedItems.filter { it.status == MpiInspectionStatus.YELLOW }
        val greens = populatedItems.filter { it.status == MpiInspectionStatus.GREEN }

        return buildString {
            append("Courtesy inspection draft completed from available capture evidence.")
            if (greens.isNotEmpty()) {
                append(" Confirmed OK items include ${greens.take(4).joinToString(", ") { it.labelWithValue() }}.")
            }
            if (yellows.isNotEmpty()) {
                append(" Items needing attention or monitoring include ${yellows.take(6).joinToString(", ") { it.labelWithValue() }}.")
            }
            if (reds.isNotEmpty()) {
                append(" Red items flagged for immediate review include ${reds.take(6).joinToString(", ") { it.labelWithValue() }}.")
            }
            if (reviewItems.isNotEmpty()) {
                append(" Evidence was limited or ambiguous for ${reviewItems.take(4).joinToString(", ") { it.label }}, so technician confirmation is recommended.")
            }
            if (populatedItems.isEmpty()) {
                append(" The capture did not contain enough explicit vehicle evidence to auto-fill checklist items.")
            }
        }
    }

    private fun buildTechnicalSnapshot(session: Session): MpiTechnicalSnapshot {
        val metadata = session.metadata
        return MpiTechnicalSnapshot(
            captureDate = formatTimestamp(session.createdAt),
            mode = metadata?.hankMode?.label ?: HankMode.INTERACTIVE.label,
            domain = metadata?.domainMode?.label ?: DomainMode.CAR_ONLY.label,
            videoSource = metadata?.videoSource?.label ?: CaptureVideoSource.GLASSES.label,
            audioSource = metadata?.audioSource?.label ?: CaptureAudioSource.GLASSES_MIC.label,
            speechRoute = metadata?.speechRecognitionRoute?.segmentLabel ?: SpeechRecognitionRoute.ANDROID.segmentLabel,
        )
    }

    private fun MpiChecklistSectionConfig.toReportSection(items: List<MpiInspectionItem>): MpiInspectionSection {
        val shouldExpand = items.any { it.status == MpiInspectionStatus.RED || it.status == MpiInspectionStatus.YELLOW || it.needsReview }
        return MpiInspectionSection(
            id = id,
            title = title,
            description = description,
            items = items,
            collapsedByDefault = collapsedByDefault && !shouldExpand,
        )
    }

    private fun MutableMap<String, DraftFinding>.mergeFinding(finding: DraftFinding) {
        val existing = this[finding.itemId]
        if (existing == null) {
            this[finding.itemId] = finding
            return
        }
        this[finding.itemId] =
            existing.copy(
                status = strongestStatus(existing.status, finding.status),
                value = finding.value ?: existing.value,
                unit = finding.unit ?: existing.unit,
                needsReview = existing.needsReview || finding.needsReview,
                confidence = maxOf(existing.confidence, finding.confidence),
                autoComment = finding.autoComment ?: existing.autoComment,
                advisorWording = finding.advisorWording ?: existing.advisorWording,
                evidence = existing.evidence + finding.evidence,
            )
    }

    private fun strongestStatus(
        first: MpiInspectionStatus,
        second: MpiInspectionStatus,
    ): MpiInspectionStatus {
        val rank =
            mapOf(
                MpiInspectionStatus.UNKNOWN to 0,
                MpiInspectionStatus.GREEN to 1,
                MpiInspectionStatus.YELLOW to 2,
                MpiInspectionStatus.RED to 3,
            )
        return if ((rank[second] ?: 0) > (rank[first] ?: 0)) second else first
    }

    private fun transcriptEvidence(
        idSuffix: String,
        message: ChatMessage,
        checklistItemIds: List<String>,
        caption: String = message.text.take(120),
    ): MpiEvidenceItem =
        MpiEvidenceItem(
            id = "$idSuffix-${message.timestamp}",
            type = MpiEvidenceType.TRANSCRIPT,
            startTimeMs = message.timestamp,
            endTimeMs = message.timestamp,
            caption = caption,
            checklistItemIds = checklistItemIds,
            confidence = 0.95,
            source = MpiEvidenceSource.TRANSCRIPT,
        )

    private fun MpiInspectionItem.labelWithValue(): String =
        if (value.isNullOrBlank()) label else "$label $value${unit?.let { " $it" }.orEmpty()}"

    private fun formatTimestamp(timestamp: Long): String =
        SimpleDateFormat("MMM d, yyyy HH:mm", Locale.US).format(Date(timestamp))

    private data class DraftFinding(
        val itemId: String,
        val status: MpiInspectionStatus = MpiInspectionStatus.UNKNOWN,
        val value: String? = null,
        val unit: String? = null,
        val needsReview: Boolean = false,
        val confidence: Double = 0.0,
        val autoComment: String? = null,
        val advisorWording: String? = null,
        val evidence: List<MpiEvidenceItem> = emptyList(),
    )

    private data class Side(
        val id: String,
        val longName: String,
        val shortName: String,
    )

    private data class MeasurementMatch(
        val value: String,
        val unit: String,
        val rawText: String,
    )
}
