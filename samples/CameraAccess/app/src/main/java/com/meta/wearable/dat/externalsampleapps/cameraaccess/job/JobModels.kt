/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.job

/**
 * Data model ported from the `ui/` bay UI's `jobs.ts`. Keeps field names
 * identical so a JSON export between the two is interchangeable.
 */

enum class JobStatus(val label: String, val color: Long) {
    QUEUED("Queued", 0xFF6B7280),
    DIAG("Diagnosing", 0xFFF59E0B),
    REPAIR("Repairing", 0xFF3B82F6),
    VERIFY("Verifying", 0xFF10B981),
}

data class DtcRow(
    val code: String,
    val description: String,
    val pending: Boolean = false,
)

data class WorkOrder(
    val id: String,
    val ro: String,
    val bay: String,
    val vin: String,
    val vehicle: String,
    val concern: String,
    val writerNotes: String,
    val status: JobStatus,
    val dtcs: List<DtcRow>,
)

data class RepairStep(val id: String, val title: String, val detail: String)

data class VerifyItem(val id: String, val label: String)

data class DiagStep(val title: String, val body: String, val pitfall: String)
