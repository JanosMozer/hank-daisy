package com.meta.wearable.dat.externalsampleapps.mpi.session

import java.util.Locale

data class ReportField(val label: String, val value: String)

data class CaptureReport(
    val facts: List<ReportField>,
    val summary: String,
    val checklist: List<String>,
    val evidence: List<InspectionEvidence>,
)

object CaptureReportBuilder {
    fun build(session: Session): CaptureReport {
        val mpiReport = MpiReportBuilder.build(session)
        val evidence = selectEvidence(session.evidenceAssets)
        val facts =
            listOf(
                ReportField("Capture date", mpiReport.technicalSnapshot.captureDate),
                ReportField("Mode", mpiReport.technicalSnapshot.mode),
                ReportField("Domain", mpiReport.technicalSnapshot.domain),
                ReportField("Video source", mpiReport.technicalSnapshot.videoSource),
                ReportField("Audio source", mpiReport.technicalSnapshot.audioSource),
                ReportField("Speech route", mpiReport.technicalSnapshot.speechRoute),
            )

        val checklist =
            mpiReport.sections.flatMap { section ->
                section.items
                    .filter { it.status != MpiInspectionStatus.UNKNOWN || it.value != null || it.needsReview }
                    .map { item ->
                        val value = item.value?.let { " - $it${item.unit?.let { unit -> " $unit" }.orEmpty()}" }.orEmpty()
                        val review = if (item.needsReview) " (review)" else ""
                        "${section.title}: ${item.label}$value - ${item.status.name.lowercase(Locale.US)}$review"
                    }
            }.ifEmpty {
                listOf("Complete the MPI checklist manually. No explicit statuses or measurements were extracted from this capture.")
            }

        return CaptureReport(
            facts = facts,
            summary = mpiReport.conciseDiagnosis,
            checklist = checklist,
            evidence = evidence,
        )
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
}
