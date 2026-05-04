/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.mpi.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.meta.wearable.dat.externalsampleapps.mpi.R

@Composable
fun CircleButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
  Button(
      modifier = modifier.aspectRatio(1f),
      onClick = onClick,
      enabled = enabled,
      colors = ButtonDefaults.buttonColors(containerColor = Color.White),
      shape = CircleShape,
      contentPadding = PaddingValues(0.dp),
      content = content,
  )
}

@Composable
fun CaptureButton(onClick: () -> Unit, enabled: Boolean = true) {
  CircleButton(onClick = onClick, enabled = enabled) {
    Icon(
        imageVector = Icons.Filled.PhotoCamera,
        contentDescription = stringResource(R.string.capture_photo),
        tint = Color.Black,
    )
  }
}

@Composable
fun ClipButton(onClick: () -> Unit, enabled: Boolean = true) {
  CircleButton(onClick = onClick, enabled = enabled) {
    Icon(
        imageVector = Icons.Filled.FiberManualRecord,
        contentDescription = "Save clip evidence",
        tint = Color.Red,
    )
  }
}

@Composable
fun VideoEvidenceButton(isRecording: Boolean, onClick: () -> Unit, enabled: Boolean = true) {
  CircleButton(onClick = onClick, enabled = enabled) {
    Icon(
        imageVector = if (isRecording) Icons.Filled.Stop else Icons.Filled.Videocam,
        contentDescription = if (isRecording) "Stop video recording" else "Record glasses video",
        tint = if (isRecording) Color.Red else Color.Black,
    )
  }
}

@Composable
fun AudioEvidenceButton(isRecording: Boolean, onClick: () -> Unit, enabled: Boolean = true) {
  CircleButton(onClick = onClick, enabled = enabled) {
    Icon(
        imageVector = if (isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
        contentDescription = if (isRecording) "Stop audio evidence" else "Record audio evidence",
        tint = if (isRecording) Color.Red else Color.Black,
    )
  }
}
