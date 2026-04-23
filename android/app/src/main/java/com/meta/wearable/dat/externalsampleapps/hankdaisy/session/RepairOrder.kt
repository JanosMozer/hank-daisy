/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.hankdaisy.session

/**
 * A repair order — the primary unit of work for the shop pivot.
 *
 * Owns vehicle + customer info, the presenting issue, the diagnostic
 * timeline (filled in as Hank runs sessions), and the final close-out
 * notes. Sessions point BACK to an order via [Session.orderId]; that
 * one-way link keeps persistence simple (session writes don't need to
 * amend the order doc) and lets us derive "sessions on this order" with
 * a single filter pass.
 */
data class RepairOrder(
    val id: String,
    val createdAt: Long,
    val updatedAt: Long,
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
    // Structured diagnostic timeline — populated in Phase 3 when Hank
    // starts emitting structured output. Defined now so the schema is
    // stable in persistence.
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
