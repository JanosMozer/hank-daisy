/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.job

/**
 * Data model for ROs. The bay UI's checklist types (DiagStep / RepairStep /
 * VerifyItem) were removed — the mechanic works by talking to Hank and
 * writing free-form bay notes, not by ticking pre-filled dealership
 * procedure boxes.
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
