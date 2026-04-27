package com.meta.wearable.dat.externalsampleapps.mpi.stream

import android.graphics.Bitmap
import com.meta.wearable.dat.camera.types.StreamSessionState
import com.meta.wearable.dat.externalsampleapps.mpi.session.HankMode
import com.meta.wearable.dat.externalsampleapps.mpi.session.InspectionEvidence

data class PhoneCameraStreamUiState(
    val streamSessionState: StreamSessionState = StreamSessionState.STOPPED,
    val videoFrame: Bitmap? = null,
    val videoFrameCount: Int = 0,
    val isAnalyzing: Boolean = false,
    val isListening: Boolean = false,
    val isWakeWordActive: Boolean = false,
    val isHankSpeaking: Boolean = false,
    val hankMode: HankMode = HankMode.INTERACTIVE,
    val spokenQuestion: String? = null,
    val lastGeminiResponse: String? = null,
    val pendingReadOnlyContext: String? = null,
    val audioRouteStatus: AudioRouteManager.AudioStatus = AudioRouteManager.AudioStatus.NONE,
    val chatMessages: List<ChatMessage> = emptyList(),
    val capturedEvidence: List<InspectionEvidence> = emptyList(),
)
