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
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Bottom-nav destinations. Profile is intentionally NOT a tab — it's a
 * modal opened from the avatar on the Chats home. Making it a tab created
 * a loop where the LaunchedEffect re-opened the profile every time the
 * back button closed it.
 */
enum class AppTab(val label: String, val icon: ImageVector) {
    CHATS("Chats", Icons.Outlined.Forum),
    TIPS("Tips", Icons.Outlined.Lightbulb),
    SETTINGS("Settings", Icons.Outlined.Settings),
}
