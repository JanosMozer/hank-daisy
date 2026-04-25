/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.hankdaisy.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meta.wearable.dat.camera.types.StreamSessionState
import com.meta.wearable.dat.externalsampleapps.hankdaisy.R
import com.meta.wearable.dat.externalsampleapps.hankdaisy.stream.PhoneCameraStreamViewModel
import com.meta.wearable.dat.externalsampleapps.hankdaisy.wearables.WearablesViewModel

@Composable
fun PhoneCameraStreamScreen(
    wearablesViewModel: WearablesViewModel,
    modifier: Modifier = Modifier,
    onSessionEnd: (List<com.meta.wearable.dat.externalsampleapps.hankdaisy.stream.ChatMessage>) -> Unit = {},
    streamViewModel: PhoneCameraStreamViewModel =
        viewModel(
            factory =
                PhoneCameraStreamViewModel.Factory(
                    application = (LocalActivity.current as ComponentActivity).application,
                ),
        ),
) {
    val streamUiState by streamViewModel.uiState.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        streamViewModel.startStream()
        onDispose {
            val snapshot = streamViewModel.uiState.value.chatMessages
            if (snapshot.isNotEmpty()) onSessionEnd(snapshot)
            streamViewModel.stopStream()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        streamUiState.videoFrame?.let { videoFrame ->
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

        StatusPill(
            isListening = streamUiState.isListening,
            isAnalyzing = streamUiState.isAnalyzing,
            isSpeaking = streamUiState.isHankSpeaking,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 140.dp),
        )

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
                    onClick = { wearablesViewModel.navigateToDeviceSelection() },
                    isDestructive = true,
                    modifier = Modifier.weight(1f),
                )

                if (streamUiState.isDemoCommentaryMode) {
                    SwitchButton(
                        label = "Comment now",
                        onClick = { streamViewModel.requestDemoCommentary() },
                        enabled = !streamUiState.isAnalyzing && !streamUiState.isHankSpeaking,
                        modifier = Modifier.weight(1f),
                    )
                } else if (streamUiState.isListening) {
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
