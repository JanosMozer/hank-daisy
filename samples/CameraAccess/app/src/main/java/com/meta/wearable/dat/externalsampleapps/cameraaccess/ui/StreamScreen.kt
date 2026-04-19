/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

// StreamScreen - DAT Camera Streaming UI
//
// This composable demonstrates the main streaming UI for DAT camera functionality. It shows how to
// display live video from wearable devices and handle photo capture.

package com.meta.wearable.dat.externalsampleapps.cameraaccess.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meta.wearable.dat.camera.types.StreamSessionState
import com.meta.wearable.dat.externalsampleapps.cameraaccess.R
import com.meta.wearable.dat.externalsampleapps.cameraaccess.stream.StreamViewModel
import com.meta.wearable.dat.externalsampleapps.cameraaccess.wearables.WearablesViewModel

@Composable
fun StreamScreen(
    wearablesViewModel: WearablesViewModel,
    modifier: Modifier = Modifier,
    streamViewModel: StreamViewModel =
        viewModel(
            factory =
                StreamViewModel.Factory(
                    application = (LocalActivity.current as ComponentActivity).application,
                    wearablesViewModel = wearablesViewModel,
                ),
        ),
) {
  val streamUiState by streamViewModel.uiState.collectAsStateWithLifecycle()

  LaunchedEffect(Unit) { streamViewModel.startStream() }

  Box(modifier = modifier.fillMaxSize()) {
    streamUiState.videoFrame?.let { videoFrame ->
      // Use key() to force recomposition when frame counter changes,
      // even if the bitmap reference is the same (due to caching optimization)
      key(streamUiState.videoFrameCount) {
        Image(
            bitmap = videoFrame.asImageBitmap(),
            contentDescription = stringResource(R.string.live_stream),
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
      }
    }
    if (streamUiState.streamSessionState == StreamSessionState.STARTING) {
      CircularProgressIndicator(
          modifier = Modifier.align(Alignment.Center),
      )
    }

    // Gemini response overlay (scrollable for longer step-by-step responses)
    streamUiState.lastGeminiResponse?.let { response ->
      Box(
          modifier = Modifier
              .align(Alignment.TopCenter)
              .padding(top = 48.dp, start = 16.dp, end = 16.dp)
              .background(
                  Color.Black.copy(alpha = 0.8f),
                  shape = RoundedCornerShape(16.dp),
              )
              .padding(16.dp)
              .heightIn(max = 300.dp),
      ) {
        Column {
          streamUiState.spokenQuestion?.let { question ->
            Text(
                text = "You asked: \"$question\"",
                color = Color(0xFF38BDF8),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp),
            )
          }
          Text(
              text = response,
              color = Color.White,
              fontSize = 13.sp,
              lineHeight = 18.sp,
              modifier = Modifier
                  .fillMaxWidth()
                  .verticalScroll(rememberScrollState()),
          )
        }
      }
    }

    // Listening indicator
    if (streamUiState.isListening) {
      Box(
          modifier = Modifier
              .align(Alignment.TopCenter)
              .padding(top = 48.dp, start = 16.dp, end = 16.dp)
              .background(
                  Color(0xFF1E40AF).copy(alpha = 0.9f),
                  shape = RoundedCornerShape(16.dp),
              )
              .padding(16.dp),
      ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
          CircularProgressIndicator(
              color = Color(0xFF38BDF8),
              modifier = Modifier.height(20.dp),
          )
          Text(
              text = "\uD83C\uDF99\uFE0F Listening... ask your question",
              color = Color.White,
              fontSize = 14.sp,
              fontWeight = FontWeight.SemiBold,
          )
        }
      }
    }

    // Analyzing indicator
    if (streamUiState.isAnalyzing) {
      Box(
          modifier = Modifier
              .align(Alignment.TopCenter)
              .padding(top = 48.dp, start = 16.dp, end = 16.dp)
              .background(
                  Color.Black.copy(alpha = 0.75f),
                  shape = RoundedCornerShape(16.dp),
              )
              .padding(16.dp),
      ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
          CircularProgressIndicator(
              color = Color.White,
              modifier = Modifier.height(20.dp),
          )
          Text(
              text = "Hank is analyzing...",
              color = Color.White,
              fontSize = 14.sp,
              fontWeight = FontWeight.SemiBold,
          )
        }
      }
    }

    Box(modifier = Modifier.fillMaxSize().padding(all = 24.dp)) {
      Row(
          modifier =
              Modifier.align(Alignment.BottomCenter)
                  .navigationBarsPadding()
                  .fillMaxWidth()
                  .height(56.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        SwitchButton(
            label = stringResource(R.string.stop_stream_button_title),
            onClick = {
              streamViewModel.stopStream()
              wearablesViewModel.navigateToDeviceSelection()
            },
            isDestructive = true,
            modifier = Modifier.weight(1f),
        )

        // Ask Hank button — tapping starts voice listening
        if (streamUiState.isListening) {
          SwitchButton(
              label = "Cancel",
              onClick = { streamViewModel.cancelListening() },
              isDestructive = true,
              modifier = Modifier.weight(1f),
          )
        } else {
          SwitchButton(
              label = "\uD83C\uDF99\uFE0F Ask Hank",
              onClick = { streamViewModel.askHank() },
              enabled = !streamUiState.isAnalyzing,
              modifier = Modifier.weight(1f),
          )
        }

        // Photo capture button
        CaptureButton(
            onClick = { streamViewModel.capturePhoto() },
        )
      }
    }
  }

  streamUiState.capturedPhoto?.let { photo ->
    if (streamUiState.isShareDialogVisible) {
      SharePhotoDialog(
          photo = photo,
          onDismiss = { streamViewModel.hideShareDialog() },
          onShare = { bitmap ->
            streamViewModel.sharePhoto(bitmap)
            streamViewModel.hideShareDialog()
          },
      )
    }
  }
}
