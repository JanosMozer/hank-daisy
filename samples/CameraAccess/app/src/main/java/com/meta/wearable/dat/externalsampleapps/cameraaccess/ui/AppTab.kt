/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Bottom-nav destinations.
 *  - CONVOS  : entry point for a fresh live, glasses-mediated call.
 *  - CHATS   : library of past sessions + entry point for a fresh
 *              text/voice chat (no glasses).
 *  - TIPS    : onboarding cards.
 *  - SETTINGS: theme / accessibility / about.
 *
 * Profile is intentionally NOT a tab — it's a modal opened from the
 * avatar on the Chats home (a tab loop bug previously kept reopening it).
 */
enum class AppTab(val label: String, val icon: ImageVector) {
    CONVOS("Convos", Icons.Outlined.RecordVoiceOver),
    CHATS("Chats", Icons.Outlined.Forum),
    TIPS("Tips", Icons.Outlined.Lightbulb),
    SETTINGS("Settings", Icons.Outlined.Settings),
}
