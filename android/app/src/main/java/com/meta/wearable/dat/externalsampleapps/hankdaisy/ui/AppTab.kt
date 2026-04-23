/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.hankdaisy.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Bottom-nav destinations.
 *  - CONVOS  : entry point for a fresh live, glasses-mediated call.
 *  - CHATS   : library of past free-form sessions + "+" for a text/voice
 *              chat (no glasses). Order-linked sessions live inside each
 *              order rather than here.
 *  - ORDERS  : repair orders — the primary workspace for the shop pivot.
 *              Replaces the former TIPS tab; tip content moves to Settings.
 *  - SETTINGS: theme / accessibility / help / about.
 *
 * Profile is intentionally NOT a tab — it's a modal opened from the
 * avatar on the Chats home (a tab loop bug previously kept reopening it).
 */
enum class AppTab(val label: String, val icon: ImageVector) {
    CONVOS("Convos", Icons.Outlined.RecordVoiceOver),
    CHATS("Chats", Icons.Outlined.Forum),
    ORDERS("Orders", Icons.Outlined.Build),
    SETTINGS("Settings", Icons.Outlined.Settings),
}
