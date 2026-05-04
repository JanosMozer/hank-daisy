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
import com.meta.wearable.dat.externalsampleapps.mpi.stream.StreamViewModel
import com.meta.wearable.dat.externalsampleapps.mpi.wearables.WearablesViewModel
import java.util.Locale

@Composable
fun StreamScreen(
    wearablesViewModel: WearablesViewModel,
    modifier: Modifier = Modifier,
    onSessionEnd: (List<ChatMessage>, List<InspectionEvidence>) -> Unit = { _, _ -> },
    onHankModeChange: (HankMode) -> Unit = {},
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
            streamViewModel.finalizePendingEvidenceCapture()
            val (messages, evidence) = streamViewModel.currentSessionSnapshot()
            if (messages.isNotEmpty() || evidence.isNotEmpty()) {
                onSessionEnd(messages, evidence)
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
            onExport = { streamViewModel.exportSession() },
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
                title = if (streamUiState.hankMode == HankMode.READ_ONLY) "Read-only mode" else "Interactive mode",
                body =
                    if (streamUiState.hankMode == HankMode.READ_ONLY) {
                        "Hank keeps watching the scene and folds in only relevant captured speech."
                    } else {
                        "Hank answers direct spoken questions and keeps the turn loop live."
                    },
            )
            if (!streamUiState.pendingReadOnlyContext.isNullOrBlank()) {
                StatusChip(
                    title = "Queued context",
                    body = streamUiState.pendingReadOnlyContext.orEmpty(),
                    background = Color(0xFF0F766E).copy(alpha = 0.88f),
                )
            }
            val audioMessage =
                when (streamUiState.glassesAudioStatus) {
                    com.meta.wearable.dat.externalsampleapps.mpi.stream.GlassesAudioManager.AudioStatus.NONE ->
                        "Phone audio fallback. Pair the glasses as Bluetooth audio for voice in/out."
                    com.meta.wearable.dat.externalsampleapps.mpi.stream.GlassesAudioManager.AudioStatus.SPEAKER_ONLY ->
                        "Replies are on the glasses; speech input still falls back to the phone."
                    com.meta.wearable.dat.externalsampleapps.mpi.stream.GlassesAudioManager.AudioStatus.MIC_ONLY ->
                        "Glasses mic is available; replies still play on the phone."
                    com.meta.wearable.dat.externalsampleapps.mpi.stream.GlassesAudioManager.AudioStatus.FULL ->
                        "Glasses audio path is active."
                }
            StatusChip(title = "Audio path", body = audioMessage)
            if (streamUiState.isVideoRecording) {
                StatusChip(
                    title = "Video evidence",
                    body = "Recording glasses video • ${formatDuration(streamUiState.videoRecordingDurationMs)}",
                    background = Color(0xFF7F1D1D).copy(alpha = 0.88f),
                )
            }
            if (streamUiState.isAudioRecording) {
                StatusChip(
                    title = "Audio evidence",
                    body = "Recording narrated note • ${formatDuration(streamUiState.audioRecordingDurationMs)}",
                    background = Color(0xFF1D4ED8).copy(alpha = 0.88f),
                )
            }
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
                onClick = { wearablesViewModel.navigateToDeviceSelection() },
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

            VideoEvidenceButton(
                isRecording = streamUiState.isVideoRecording,
                onClick = { streamViewModel.toggleVideoEvidenceRecording() },
                enabled =
                    streamUiState.streamSessionState == StreamSessionState.STREAMING ||
                        streamUiState.isVideoRecording,
            )

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

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
