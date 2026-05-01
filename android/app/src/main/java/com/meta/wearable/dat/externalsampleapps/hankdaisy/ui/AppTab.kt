/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.hankdaisy.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.ui.graphics.vector.ImageVector

enum class TopLevelTab(val label: String, val icon: ImageVector) {
    DEMO("Demo", Icons.Outlined.RecordVoiceOver),
    PIPELINE("Pipeline", Icons.Outlined.Tune),
    SETTINGS("Settings", Icons.Outlined.Settings),
}
