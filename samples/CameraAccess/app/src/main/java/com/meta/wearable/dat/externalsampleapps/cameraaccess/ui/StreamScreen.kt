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
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.DisposableEffect
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
import com.meta.wearable.dat.externalsampleapps.cameraaccess.stream.GlassesAudioManager
import com.meta.wearable.dat.externalsampleapps.cameraaccess.stream.StreamViewModel
import com.meta.wearable.dat.externalsampleapps.cameraaccess.wearables.WearablesViewModel

@Composable
fun StreamScreen(
    wearablesViewModel: WearablesViewModel,
    modifier: Modifier = Modifier,
    onSessionEnd: (List<com.meta.wearable.dat.externalsampleapps.cameraaccess.stream.ChatMessage>) -> Unit = {},
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

  DisposableEffect(Unit) {
    streamViewModel.startStream()
    onDispose {
      // Capture the conversation BEFORE stopStream() wipes it, so we can
      // save it as a Session on the home screen.
      val snapshot = streamViewModel.uiState.value.chatMessages
      if (snapshot.isNotEmpty()) onSessionEnd(snapshot)
      streamViewModel.stopStream()
    }
  }

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

    // ChatGPT-style scrollable conversation panel — replaces the old single
    // Gemini-response overlay so the user sees the full back-and-forth.
    ChatPanel(
        messages = streamUiState.chatMessages,
        onExport = { streamViewModel.exportSession() },
        modifier =
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 48.dp, start = 12.dp, end = 12.dp)
                .heightIn(max = 380.dp),
    )

    // Unified status pill — single cross-fading indicator so the
    // Listening → Thinking → Speaking → Ready transitions don't flicker.
    StatusPill(
        isListening = streamUiState.isListening,
        isAnalyzing = streamUiState.isAnalyzing,
        isSpeaking = streamUiState.isHankSpeaking,
        modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 140.dp),
    )

    // Glasses audio routing banner — warns the mechanic if the glasses
    // aren't paired as a Bluetooth headset (so I/O falls back to the phone).
    if (streamUiState.glassesAudioStatus != GlassesAudioManager.AudioStatus.FULL &&
        streamUiState.streamSessionState == StreamSessionState.STREAMING) {
      val (msg, bg) = when (streamUiState.glassesAudioStatus) {
        GlassesAudioManager.AudioStatus.NONE ->
          "Pair glasses as Bluetooth audio in Settings — using phone mic+speaker." to Color(0xFFB91C1C)
        GlassesAudioManager.AudioStatus.SPEAKER_ONLY ->
          "Glasses speaker OK; mic falls back to phone (HFP not connected)." to Color(0xFFB45309)
        GlassesAudioManager.AudioStatus.MIC_ONLY ->
          "Glasses mic OK; reply plays on phone (A2DP not connected)." to Color(0xFFB45309)
        GlassesAudioManager.AudioStatus.FULL -> "" to Color.Transparent
      }
      Box(
          modifier = Modifier
              .align(Alignment.BottomCenter)
              .padding(bottom = 96.dp, start = 16.dp, end = 16.dp)
              .background(bg.copy(alpha = 0.92f), shape = RoundedCornerShape(12.dp))
              .padding(horizontal = 12.dp, vertical = 8.dp),
      ) {
        Text(text = msg, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
      }
    }

    // (The old top-right wake-word indicator has been absorbed into the
    // unified StatusPill above, since always-on listening and "Ready" state
    // are now communicated there.)

    // (The old manual camera-context toggle was replaced by a system-prompt
    // rule: Hank decides whether the view is relevant to the current
    // question and ignores it otherwise. No UI control needed.)

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
              // Do NOT stopStream() here. It wipes chatMessages before the
              // DisposableEffect snapshot runs → session lost. Just navigate
              // away; DisposableEffect captures the chat then stops the
              // stream in the right order.
              wearablesViewModel.navigateToDeviceSelection()
            },
            isDestructive = true,
            modifier = Modifier.weight(1f),
        )

        // Manual Ask Hank (skips wake word, goes straight to listening)
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
