/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.mpi.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Bottom-nav destinations for the MPI app scaffold.
 *  - CONVOS  : live glasses capture for technician-guided inspection work
 *  - CHATS   : evidence sessions and ad-hoc follow-up conversations
 *  - ORDERS  : inspection records tied to a repair order / service visit
 *  - SETTINGS: theme / accessibility / help / about
 *
 * Profile is intentionally NOT a tab — it's a modal opened from the
 * avatar on the Chats home (a tab loop bug previously kept reopening it).
 */
enum class AppTab(val label: String, val icon: ImageVector) {
    CONVOS("Capture", Icons.Outlined.RecordVoiceOver),
    CHATS("Sessions", Icons.Outlined.Forum),
    ORDERS("Inspections", Icons.Outlined.Build),
    SETTINGS("Settings", Icons.Outlined.Settings),
}
