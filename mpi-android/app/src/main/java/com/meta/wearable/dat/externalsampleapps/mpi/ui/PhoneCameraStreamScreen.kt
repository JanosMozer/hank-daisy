package com.meta.wearable.dat.externalsampleapps.mpi.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meta.wearable.dat.camera.types.StreamSessionState
import com.meta.wearable.dat.externalsampleapps.mpi.R
import com.meta.wearable.dat.externalsampleapps.mpi.session.HankMode
import com.meta.wearable.dat.externalsampleapps.mpi.session.InspectionEvidence
import com.meta.wearable.dat.externalsampleapps.mpi.stream.ChatMessage
import com.meta.wearable.dat.externalsampleapps.mpi.stream.PhoneCameraStreamViewModel

@Composable
fun PhoneCameraStreamScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onSessionEnd: (List<ChatMessage>, List<InspectionEvidence>) -> Unit = { _, _ -> },
    onHankModeChange: (HankMode) -> Unit = {},
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
            val snapshot = streamViewModel.uiState.value
            if (snapshot.chatMessages.isNotEmpty() || snapshot.capturedEvidence.isNotEmpty()) {
                onSessionEnd(snapshot.chatMessages, snapshot.capturedEvidence)
            }
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
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        ChatPanel(
            messages = streamUiState.chatMessages,
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = 48.dp, start = 12.dp, end = 12.dp)
                    .heightIn(max = 380.dp),
        )

        Column(
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 48.dp, start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusChip(
                title = "Phone camera",
                body =
                    if (streamUiState.hankMode == HankMode.READ_ONLY) {
                        "Using the phone camera with hands-free commentary mode."
                    } else {
                        "Using the phone camera with interactive Hank replies."
                    },
            )
            if (!streamUiState.pendingReadOnlyContext.isNullOrBlank()) {
                StatusChip(
                    title = "Queued context",
                    body = streamUiState.pendingReadOnlyContext.orEmpty(),
                    background = Color(0xFF0F766E).copy(alpha = 0.88f),
                )
            }
            val routeMessage =
                when (streamUiState.audioRouteStatus) {
                    com.meta.wearable.dat.externalsampleapps.mpi.stream.AudioRouteManager.AudioStatus.NONE ->
                        "No external audio route detected. Voice stays on the phone."
                    com.meta.wearable.dat.externalsampleapps.mpi.stream.AudioRouteManager.AudioStatus.SPEAKER_ONLY ->
                        "Bluetooth speaker route is available for Hank's replies."
                    com.meta.wearable.dat.externalsampleapps.mpi.stream.AudioRouteManager.AudioStatus.MIC_ONLY ->
                        "Bluetooth mic route is available for incoming speech."
                    com.meta.wearable.dat.externalsampleapps.mpi.stream.AudioRouteManager.AudioStatus.FULL ->
                        "Bluetooth mic and speaker routes are active."
                }
            StatusChip(title = "Audio path", body = routeMessage)
        }

        StatusPill(
            isListening = streamUiState.isListening,
            isAnalyzing = streamUiState.isAnalyzing,
            isSpeaking = streamUiState.isHankSpeaking,
            readyLabel =
                if (streamUiState.hankMode == HankMode.READ_ONLY) "Watching scene"
                else "Hank's listening",
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 148.dp),
        )

        Row(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 18.dp)
                    .height(56.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SwitchButton(
                label = stringResource(R.string.stop_stream_button_title),
                onClick = onBack,
                isDestructive = true,
                modifier = Modifier.weight(1f),
            )

            SwitchButton(
                label = if (streamUiState.hankMode == HankMode.READ_ONLY) "Interactive" else "Read-only",
                onClick = {
                    val nextMode =
                        if (streamUiState.hankMode == HankMode.READ_ONLY) HankMode.INTERACTIVE
                        else HankMode.READ_ONLY
                    streamViewModel.setHankMode(nextMode)
                    onHankModeChange(nextMode)
                },
                modifier = Modifier.weight(1f),
            )

            if (streamUiState.hankMode == HankMode.READ_ONLY) {
                SwitchButton(
                    label = "Comment now",
                    onClick = { streamViewModel.requestCommentNow() },
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

            CaptureButton(onClick = { streamViewModel.capturePhoto() })
        }
    }
}

@Composable
private fun StatusChip(
    title: String,
    body: String,
    background: Color = Color.Black.copy(alpha = 0.72f),
) {
    Column(
        modifier =
            Modifier
                .width(288.dp)
                .background(background, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text(
            body,
            color = Color.White.copy(alpha = 0.88f),
            fontSize = 11.sp,
            lineHeight = 15.sp,
        )
    }
}
