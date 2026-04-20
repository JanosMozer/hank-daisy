/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.job

/**
 * Static mock content ported verbatim from the bay UI's data/jobs.ts and
 * data/content.ts. In production these would come from a dealer back-end.
 */

val MOCK_JOBS: List<WorkOrder> =
    listOf(
        WorkOrder(
            id = "job-1",
            ro = "48291",
            bay = "4",
            vin = "1FTFW1E86PFA12345",
            vehicle = "2023 F-150 3.5L EcoBoost",
            concern = "Power loss under load; whistle under hood when accelerating uphill.",
            writerNotes =
                "Customer states worse when warm. Started ~2 weeks ago. No recent repairs. No aftermarket tune.",
            status = JobStatus.DIAG,
            dtcs =
                listOf(
                    DtcRow("P0299", "Turbocharger/Supercharger Underboost", pending = true),
                    DtcRow("P0106", "MAP/Barometric Pressure Circuit Range"),
                ),
        ),
        WorkOrder(
            id = "job-2",
            ro = "48304",
            bay = "2",
            vin = "1G1ZD5ST7PF987654",
            vehicle = "2023 Chevrolet Malibu 1.5L Turbo",
            concern = "Rough idle cold only; MIL intermittent.",
            writerNotes = "CEL not always on. Customer leaving vehicle for day — try cold soak repro.",
            status = JobStatus.QUEUED,
            dtcs = listOf(DtcRow("P0506", "Idle Air Control RPM Lower Than Expected")),
        ),
        WorkOrder(
            id = "job-3",
            ro = "48288",
            bay = "1",
            vin = "3MW5U7J09M8A112233",
            vehicle = "2021 BMW 330i",
            concern = "Lane assist warning after windshield replacement (GlassCo).",
            writerNotes = "Needs ADAS calibration per insurer. Photo calibration required.",
            status = JobStatus.REPAIR,
            dtcs = emptyList(),
        ),
    )

val REPAIR_STEPS_P0299: List<RepairStep> =
    listOf(
        RepairStep(
            "r1",
            "Visual + boost leak check (intercooler, charge pipes, BOV)",
            "Pressure test to ~15 psi; spray soapy water on couplers cold and hot.",
        ),
        RepairStep(
            "r2",
            "TSB harness inspection (if applicable to VIN)",
            "Wastegate actuator harness — follow frame rail clip routing; look for rub-through.",
        ),
        RepairStep(
            "r3",
            "Commanded vs actual boost + MAP/BARO sanity",
            "Key-on BARO plausible; compare MAP at idle WOT snapshot to spec.",
        ),
        RepairStep(
            "r4",
            "Document findings + photos for RO",
            "Attach photos of chafed harness or cracked boot before replacing parts.",
        ),
    )

val GENERIC_REPAIR_STEPS: List<RepairStep> =
    listOf(
        RepairStep(
            "g1",
            "Confirm OEM procedure for primary concern",
            "Use factory service info for this VIN — torque, sequence, special tools.",
        ),
        RepairStep(
            "g2",
            "Torque-critical fasteners + witness marks",
            "Mark staged fasteners after torque; photo if shop policy requires.",
        ),
        RepairStep(
            "g3",
            "Clear faults only after verification plan exists",
            "Avoid clearing monitors before post-repair drive plan is agreed.",
        ),
    )

val ADAS_CALIBRATION_STEPS: List<RepairStep> =
    listOf(
        RepairStep(
            "a1",
            "Pre-scan + alignment check per OEM",
            "Some procedures require thrust line / alignment in spec before calibration.",
        ),
        RepairStep(
            "a2",
            "Target setup + lighting conditions",
            "Follow OEM target placement; document bay floor level and glare issues.",
        ),
        RepairStep(
            "a3",
            "Post-calibration validation drive",
            "Confirm lane centering / ACC behavior per checklist; store confirmation codes.",
        ),
    )

val VERIFY_ITEMS_DEFAULT: List<VerifyItem> =
    listOf(
        VerifyItem("v1", "Reproduced original complaint before fix (note conditions)"),
        VerifyItem("v2", "After repair: same drive profile — load, grade, temperature"),
        VerifyItem("v3", "Pending codes cleared; monitors complete per OEM (not just MIL off)"),
        VerifyItem("v4", "Scan for new codes after drive; freeze frame saved if anything sets"),
    )

val UNKNOWN_CODE_WALKTHROUGH: List<DiagStep> =
    listOf(
        DiagStep(
            "1. Read the code in context",
            "Note pending vs stored, monitor type, and any companion codes. One code often pulls others along for the ride.",
            "Treating the first code as \"the\" problem without reading the full stack.",
        ),
        DiagStep(
            "2. Pull freeze frame / live data",
            "Capture RPM, load, temps, fuel trims, and voltages at the trip when the fault set. That narrows the operating region.",
            "Skipping data because \"the code already says catalyst\" or similar.",
        ),
        DiagStep(
            "3. OEM + TSB sweep for VIN",
            "Search by VIN and symptom family, not just the P-code string. Known calibrations and revised parts hide here.",
            "Keyword mismatch: \"hesitation\" vs \"rough idle\" can miss the same bulletin.",
        ),
        DiagStep(
            "4. Reproduce on purpose",
            "Match customer conditions: cold soak, hot restart, grade, cruise vs tip-in. Use a co-pilot for safety.",
            "Parking-lot idle checks when the fault only appears under highway load.",
        ),
        DiagStep(
            "5. Hypothesis + targeted tests",
            "Pick the smallest test that falsifies a theory: vacuum, fuel pressure, scope a sensor, compare bank-to-bank.",
            "Parts roulette — fast under flat-rate, expensive for the customer, wrong often enough to matter.",
        ),
        DiagStep(
            "6. Document, then verify",
            "Write what you tested and saw. Verify with the same drive profile that originally failed — then clear monitors.",
            "Clearing codes and releasing without a verification drive that stresses the original fault.",
        ),
    )

fun repairStepsForJob(job: WorkOrder): List<RepairStep> {
    if (job.dtcs.any { it.code == "P0299" }) return REPAIR_STEPS_P0299
    val t = "${job.concern} ${job.writerNotes}".lowercase()
    if ("adas" in t || "lane" in t || "windshield" in t) return ADAS_CALIBRATION_STEPS
    return GENERIC_REPAIR_STEPS
}
