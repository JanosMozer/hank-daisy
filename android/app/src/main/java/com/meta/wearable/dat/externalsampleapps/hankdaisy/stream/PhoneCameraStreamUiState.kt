/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.hankdaisy.stream

import android.graphics.Bitmap
import com.meta.wearable.dat.camera.types.StreamSessionState

data class PhoneCameraStreamUiState(
    val streamSessionState: StreamSessionState = StreamSessionState.STOPPED,
    val videoFrame: Bitmap? = null,
    val videoFrameCount: Int = 0,
    val capturedPhoto: Bitmap? = null,
    val isShareDialogVisible: Boolean = false,
    val isCapturing: Boolean = false,
    val isAnalyzing: Boolean = false,
    val isListening: Boolean = false,
    val isWakeWordActive: Boolean = false,
    val isHankSpeaking: Boolean = false,
    val isDemoCommentaryMode: Boolean = false,
    val spokenQuestion: String? = null,
    val lastGeminiResponse: String? = null,
    val voiceStatusMessage: String? = null,
    val chatMessages: List<ChatMessage> = emptyList(),
)
