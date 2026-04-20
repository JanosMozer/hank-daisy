/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.job

/**
 * Static mock ROs. Kept deliberately small — the dealership-style
 * diag/repair/verify checklists from the bay UI were removed because they
 * didn't fit the actual user flow (mechanic talks to Hank, does the work,
 * writes a free-form note). RO content itself (vehicle, VIN, concern) stays
 * because that's the customer handoff and belongs on the closure PDF.
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
