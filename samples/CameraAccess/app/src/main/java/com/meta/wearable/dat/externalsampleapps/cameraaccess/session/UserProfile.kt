/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.session

enum class HankPersona(val label: String, val tag: String) {
    MECHANIC("Mechanic", "diagnostic, hands-on, technical"),
    FRIEND("Friend", "casual, warm, no jargon"),
    EXPERT("Expert", "deep technical detail, specialist"),
    COACH("Coach", "encouraging, asks questions back"),
}

enum class Verbosity(val label: String) {
    CONCISE("Concise"),
    NORMAL("Normal"),
    DETAILED("Detailed"),
}

/** Six avatar colour swatches the user can pick from on the Profile page. */
val AvatarColors: List<Long> =
    listOf(
        0xFF7C3AED, // purple — default brand
        0xFF2563EB, // blue
        0xFF10B981, // emerald
        0xFFF59E0B, // amber
        0xFFEF4444, // red
        0xFF111827, // graphite
    )

data class UserProfile(
    val name: String = "",
    val role: String = "",
    val email: String = "",
    val pronouns: String = "",
    val bio: String = "",
    /** ARGB long. Falls back to brand purple. */
    val avatarColor: Long = AvatarColors.first(),
    val hankPersona: HankPersona = HankPersona.MECHANIC,
    val verbosity: Verbosity = Verbosity.NORMAL,
    /** Whether Hank should address the user by first name. */
    val useMyName: Boolean = true,
    /** First time the profile was saved (or 0 = never). */
    val createdAt: Long = 0L,
)
