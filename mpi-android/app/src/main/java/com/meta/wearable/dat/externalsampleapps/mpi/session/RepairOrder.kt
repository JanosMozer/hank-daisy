/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.mpi.session

/**
 * The primary MPI work unit.
 *
 * The name stays `RepairOrder` for now to avoid a wide rename through the
 * copied scaffold, but the user-facing meaning in `mpi-android` is an
 * inspection record tied to a repair order / service visit.
 */
data class RepairOrder(
    val id: String,
    val createdAt: Long,
    val updatedAt: Long,
    // MPI identity
    val templateId: String = InspectionTemplates.DEFAULT_TEMPLATE.id,
    val repairOrderNumber: String = "",
    val mileage: String = "",
    val advisorName: String = "",
    val technicianName: String = "",
    // Vehicle
    val vehicleMake: String = "",
    val vehicleModel: String = "",
    val vehicleYear: String = "",
    val vehicleVin: String = "",
    val licensePlate: String = "",
    // Customer
    val customerName: String = "",
    val customerPhone: String = "",
    // Work
    val presentingIssue: String = "",
    val notes: String = "",
    val status: OrderStatus = OrderStatus.OPEN,
    // Structured inspection findings. This is the first MPI-specific shape
    // added on top of the copied app scaffold.
    val findings: List<InspectionFinding> = emptyList(),
    // Structured diagnostic timeline tied to evidence-capture sessions.
    val diagnoses: List<DiagnosisEntry> = emptyList(),
    // Close-out
    val closedAt: Long? = null,
    val closeSummary: String = "",
) {
    /** Top line on an order card — year/make/model when we have it, else a
     *  neutral fallback so the card never renders blank. */
    val vehicleDisplay: String
        get() {
            val parts =
                listOfNotNull(
                    vehicleYear.ifBlank { null },
                    vehicleMake.ifBlank { null },
                    vehicleModel.ifBlank { null },
                )
            return if (parts.isEmpty()) "Unknown vehicle" else parts.joinToString(" ")
        }

    /** Secondary line — customer · issue, or whichever one we have. */
    val subtitleDisplay: String
        get() =
            when {
                customerName.isNotBlank() && presentingIssue.isNotBlank() ->
                    "$customerName · $presentingIssue"
                customerName.isNotBlank() -> customerName
                presentingIssue.isNotBlank() -> presentingIssue
                else -> "No details yet"
            }

    val identityLine: String
        get() =
            listOfNotNull(
                    repairOrderNumber.ifBlank { null }?.let { "RO $it" },
                    mileage.ifBlank { null }?.let { "$it mi" },
                )
                .joinToString(" · ")

    val findingSummary: String
        get() {
            if (findings.isEmpty()) return "No findings yet"
            val red = findings.count { it.severity == FindingSeverity.RED }
            val yellow = findings.count { it.severity == FindingSeverity.YELLOW }
            val green = findings.count { it.severity == FindingSeverity.GREEN }
            return buildList {
                    if (red > 0) add("$red red")
                    if (yellow > 0) add("$yellow yellow")
                    if (green > 0) add("$green green")
                }
                .joinToString(" · ")
        }

    companion object {
        /** Create a blank draft with a stable id + timestamps. */
        fun blank(): RepairOrder {
            val now = System.currentTimeMillis()
            return RepairOrder(id = "order-$now", createdAt = now, updatedAt = now)
        }
    }
}

enum class OrderStatus(val label: String) {
    OPEN("Open"),
    IN_PROGRESS("In progress"),
    CLOSED("Closed"),
}

data class InspectionFinding(
    val id: String,
    val system: String,
    val component: String,
    val location: String = "",
    val measurement: String = "",
    val recommendation: String = "",
    val severity: FindingSeverity = FindingSeverity.YELLOW,
    val note: String = "",
    val linkedSessionIds: List<String> = emptyList(),
    val evidenceAssets: List<InspectionEvidence> = emptyList(),
)

enum class FindingSeverity(val label: String) {
    GREEN("Green"),
    YELLOW("Yellow"),
    RED("Red"),
}

data class InspectionEvidence(
    val id: String,
    val kind: EvidenceKind,
    val filePath: String,
    val createdAt: Long,
    val caption: String = "",
    val previewImagePath: String? = null,
    val clipFramePaths: List<String> = emptyList(),
    val clipFps: Int = 0,
    val durationMs: Long = 0L,
)

enum class EvidenceKind {
    IMAGE,
    VIDEO,
    AUDIO,
}

/**
 * A single diagnostic step recorded against an order.
 *
 * Populated later (Phase 3) when Hank starts emitting structured step /
 * result / outcome output. Defined now so persistence can round-trip
 * orders with an empty diagnoses list without a schema migration later.
 */
data class DiagnosisEntry(
    val id: String,
    val createdAt: Long,
    val step: String,
    val result: String,
    val outcome: DiagnosisOutcome,
    val sessionId: String? = null,
)

enum class DiagnosisOutcome(val label: String) {
    PENDING("Pending"),
    PASS("Pass"),
    FAIL("Fail"),
    SKIPPED("Skipped"),
}
