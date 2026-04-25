/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.hankdaisy.session

import android.content.Context
import org.json.JSONObject

enum class WorkDomain(
    val settingsLabel: String,
    val segmentLabel: String,
) {
    CAR("Car", "Car"),
    BICYCLE("Bicycle", "Bicycle"),
    GENERAL_PURPOSE("General purpose", "General"),
    ;

    val modeDescription: String
        get() =
            when (this) {
                CAR -> "Tune Hank for automotive inspection, diagnostics, and repair walkthroughs."
                BICYCLE -> "Tune Hank for bike inspection, adjustment, and repair walkthroughs."
                GENERAL_PURPOSE ->
                    "Use Hank as a general hands-free repair guide when the task is not vehicle-specific."
            }

    val convoSubtitle: String
        get() =
            when (this) {
                CAR -> "Live car repair calls with Hank, through your glasses."
                BICYCLE -> "Live bicycle repair calls with Hank, through your glasses."
                GENERAL_PURPOSE -> "Live hands-free repair calls with Hank, through your glasses."
            }

    val orderSectionTitle: String
        get() =
            when (this) {
                CAR -> "Vehicle"
                BICYCLE -> "Bike"
                GENERAL_PURPOSE -> "Item"
            }

    val primaryDescriptorLabel: String
        get() =
            when (this) {
                CAR -> "Year / make / model"
                BICYCLE -> "Year / brand / model"
                GENERAL_PURPOSE -> "Category / brand / model"
            }

    val firstFieldLabel: String
        get() =
            when (this) {
                CAR, BICYCLE -> "Year"
                GENERAL_PURPOSE -> "Category"
            }

    val secondFieldLabel: String
        get() =
            when (this) {
                CAR -> "Make"
                BICYCLE -> "Brand"
                GENERAL_PURPOSE -> "Brand / maker"
            }

    val thirdFieldLabel: String
        get() = "Model"

    val primaryIdLabel: String
        get() =
            when (this) {
                CAR -> "VIN"
                BICYCLE -> "Serial number"
                GENERAL_PURPOSE -> "Serial / asset ID"
            }

    val secondaryIdLabel: String
        get() =
            when (this) {
                CAR -> "License plate"
                BICYCLE -> "Color / tag"
                GENERAL_PURPOSE -> "Reference tag"
            }

    val unknownSubjectLabel: String
        get() =
            when (this) {
                CAR -> "Unknown vehicle"
                BICYCLE -> "Unknown bike"
                GENERAL_PURPOSE -> "Unknown item"
            }

    val orderDocumentLabel: String
        get() =
            when (this) {
                GENERAL_PURPOSE -> "Work order"
                CAR, BICYCLE -> "Repair order"
            }

    val primaryActionLabel: String
        get() =
            when (this) {
                GENERAL_PURPOSE -> "Start walkthrough"
                CAR, BICYCLE -> "Start diagnosis"
            }

    val sessionTabLabel: String
        get() =
            when (this) {
                GENERAL_PURPOSE -> "Sessions"
                CAR, BICYCLE -> "Diagnosis"
            }

    val sessionStatsLabel: String
        get() =
            when (this) {
                GENERAL_PURPOSE -> "Guided sessions"
                CAR, BICYCLE -> "Diagnostic sessions"
            }

    val summaryAssistantDescriptor: String
        get() =
            when (this) {
                CAR -> "an automotive diagnostic assistant"
                BICYCLE -> "a bicycle repair assistant"
                GENERAL_PURPOSE -> "a general hands-free repair assistant"
            }

    fun convoBody(): String =
        "Starts the live stream from your Ray-Ban Meta glasses and opens the voice loop " +
            "with Hank. He sees what you see and guides you through ${workDescriptor()} " +
            "one step at a time."

    fun firstTipBody(): String =
        when (this) {
            CAR ->
                "No wake word needed. Once a live stream is open, Hank is always listening. " +
                    "Ask anything — if you're not looking at a car, he'll answer as a normal chat."
            BICYCLE ->
                "No wake word needed. Once a live stream is open, Hank is always listening. " +
                    "Ask anything — if you're not looking at a bike, he'll answer as a normal chat."
            GENERAL_PURPOSE ->
                "No wake word needed. Once a live stream is open, Hank is always listening. " +
                    "Ask anything — if the camera view is not relevant, he'll answer as a normal chat."
        }

    fun workDescriptor(): String =
        when (this) {
            CAR -> "car checks and repairs"
            BICYCLE -> "bike checks and repairs"
            GENERAL_PURPOSE -> "repairs, inspections, and hands-on troubleshooting"
        }

    companion object {
        private const val PREFS = "hank_sessions_v1"
        private const val KEY_SETTINGS = "settings_json"
        internal const val KEY_WORK_DOMAIN = "workDomain"

        fun current(context: Context): WorkDomain {
            val raw =
                context.applicationContext
                    .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(KEY_SETTINGS, null)
            return fromSettingsJson(raw)
        }

        fun fromSettingsJson(raw: String?): WorkDomain {
            if (raw.isNullOrBlank()) return CAR
            return try {
                fromStored(JSONObject(raw).optString(KEY_WORK_DOMAIN, CAR.name))
            } catch (_: Exception) {
                CAR
            }
        }

        fun fromStored(raw: String?): WorkDomain =
            entries.firstOrNull { it.name == raw } ?: CAR
    }
}

fun RepairOrder.displayName(workDomain: WorkDomain): String {
    val parts =
        listOfNotNull(
            vehicleYear.ifBlank { null },
            vehicleMake.ifBlank { null },
            vehicleModel.ifBlank { null },
        )
    if (parts.isNotEmpty()) return parts.joinToString(" ")
    if (vehicleVin.isNotBlank()) return "${workDomain.primaryIdLabel}: $vehicleVin"
    if (licensePlate.isNotBlank()) return "${workDomain.secondaryIdLabel}: $licensePlate"
    return workDomain.unknownSubjectLabel
}
