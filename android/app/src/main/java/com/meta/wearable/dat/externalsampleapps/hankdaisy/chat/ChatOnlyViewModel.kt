/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.hankdaisy.chat

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meta.wearable.dat.externalsampleapps.hankdaisy.stream.BargeInDetector
import com.meta.wearable.dat.externalsampleapps.hankdaisy.stream.ChatMessage
import com.meta.wearable.dat.externalsampleapps.hankdaisy.stream.GeminiService
import com.meta.wearable.dat.externalsampleapps.hankdaisy.stream.GlassesAudioManager
import com.meta.wearable.dat.externalsampleapps.hankdaisy.stream.VoiceCommandManager
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Voice + text chat with Hank, with NO glasses stream and NO DAT SDK
 * involvement. Owns its own VoiceCommandManager / GlassesAudioManager /
 * BargeInDetector instances; phone mic in, phone speaker (or any active
 * Bluetooth audio) out.
 *
 * Mirrors the conversational behaviour of StreamViewModel — always-on
 * listening, barge-in, follow-up listen after Hank speaks, conversation
 * history sent on each turn — minus everything camera-related.
 */
data class ChatOnlyUiState(
    val chatMessages: List<ChatMessage> = emptyList(),
    val isListening: Boolean = false,
    val isAnalyzing: Boolean = false,
    val isHankSpeaking: Boolean = false,
    val spokenQuestion: String? = null,
    /** Bitmap the user just attached via the 📎 button — waits in the
     *  input bar as a preview until the next send, then clears. */
    val attachedImage: Bitmap? = null,
    /** Absolute cache path of [attachedImage], so we can thread it into
     *  the eventual ChatMessage.imagePath without re-encoding. */
    val attachedImagePath: String? = null,
)

class ChatOnlyViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "HankDaisy:ChatOnly"
    }

    private val voiceCommand = VoiceCommandManager(application)
    private val glassesAudio = GlassesAudioManager(application)
    private val gemini = GeminiService()

    private val _uiState = MutableStateFlow(ChatOnlyUiState())
    val uiState: StateFlow<ChatOnlyUiState> = _uiState.asStateFlow()

    private val conversationHistory = mutableListOf<GeminiService.Turn>()

    private var voiceJob: Job? = null
    private var speakingJob: Job? = null
    private var analyzeJob: Job? = null
    private var started = false

    private val bargeInDetector =
        BargeInDetector(
            onUserSpeaking = {
                viewModelScope.launch {
                    glassesAudio.stopSpeaking()
                    voiceCommand.startConversationFollowUp()
                }
            },
        )

    fun start() {
        if (started) return
        started = true
        voiceCommand.startContinuousListening()
        voiceJob =
            viewModelScope.launch {
                voiceCommand.state.collect { v ->
                    when (v) {
                        is VoiceCommandManager.VoiceState.Passive ->
                            _uiState.update { it.copy(isListening = false) }
                        is VoiceCommandManager.VoiceState.Listening ->
                            _uiState.update { it.copy(isListening = true) }
                        is VoiceCommandManager.VoiceState.QuestionReady -> {
                            _uiState.update {
                                it.copy(isListening = false, spokenQuestion = v.text)
                            }
                            analyzeWithQuestion(v.text)
                        }
                        is VoiceCommandManager.VoiceState.Error ->
                            Log.w(TAG, "Voice error: ${v.message}")
                        is VoiceCommandManager.VoiceState.Off ->
                            _uiState.update { it.copy(isListening = false) }
                    }
                }
            }
        speakingJob =
            viewModelScope.launch {
                glassesAudio.isSpeaking.collect { speaking ->
                    _uiState.update { it.copy(isHankSpeaking = speaking) }
                    voiceCommand.setMuted(speaking)
                    if (speaking) {
                        delay(80)
                        if (glassesAudio.isSpeaking.value) bargeInDetector.start()
                    } else {
                        bargeInDetector.stop()
                        voiceCommand.startConversationFollowUp()
                    }
                }
            }
    }

    fun stop() {
        if (!started) return
        started = false
        try { voiceJob?.cancel() } catch (_: Exception) {}
        try { speakingJob?.cancel() } catch (_: Exception) {}
        try { analyzeJob?.cancel() } catch (_: Exception) {}
        try { bargeInDetector.stop() } catch (_: Exception) {}
        try { glassesAudio.stopSpeaking() } catch (_: Exception) {}
        try { voiceCommand.stopContinuousListening() } catch (_: Exception) {}
    }

    /** Manual entry from the text input — same downstream path as voice,
     *  with any image staged via [attachImage] attached to the turn. */
    fun sendTyped(text: String) {
        val trimmed = text.trim()
        val bitmap = _uiState.value.attachedImage
        val path = _uiState.value.attachedImagePath
        // Allow image-only messages ("what am I looking at?" typed — or not).
        if (trimmed.isEmpty() && bitmap == null) return
        if (_uiState.value.isAnalyzing) return
        analyzeWithQuestion(
            question = trimmed.ifBlank { "What do you see in this image?" },
            bitmap = bitmap,
            imagePath = path,
        )
        // Clear attachment immediately so the preview disappears on send.
        _uiState.update { it.copy(attachedImage = null, attachedImagePath = null) }
    }

    /** Kick the speech recognizer into focused-listen mode right now —
     *  bypasses the passive background loop so the mic button feels
     *  responsive. The existing state collector picks up the transition
     *  and flips isListening=true for the UI. */
    fun triggerVoiceCapture() {
        if (_uiState.value.isAnalyzing || _uiState.value.isHankSpeaking) return
        voiceCommand.startManualListen()
    }

    /** Decode the picked image on IO, cache it as a JPEG, and stage it
     *  as the next send's attachment. Bitmap is kept in memory for the
     *  inline preview; the path is what the ChatMessage gets tagged with
     *  so the session history can re-display it later. */
    fun attachImage(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val bitmap =
                try {
                    app.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to decode attached image", e)
                    null
                } ?: return@launch
            // Downscale huge images so we don't blow the Gemini request
            // size + keep the cache tidy. Long edge capped at 1600px.
            val scaled = downscale(bitmap, maxDim = 1600)
            val file =
                File(
                    app.cacheDir,
                    "chat_attach_${System.currentTimeMillis()}.jpg",
                )
            try {
                FileOutputStream(file).use { out ->
                    scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to save attached image", e)
                return@launch
            }
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(attachedImage = scaled, attachedImagePath = file.absolutePath)
                }
            }
        }
    }

    fun clearAttachedImage() {
        _uiState.update { it.copy(attachedImage = null, attachedImagePath = null) }
    }

    private fun downscale(src: Bitmap, maxDim: Int): Bitmap {
        val w = src.width
        val h = src.height
        val longEdge = maxOf(w, h)
        if (longEdge <= maxDim) return src
        val ratio = maxDim.toFloat() / longEdge
        val nw = (w * ratio).toInt().coerceAtLeast(1)
        val nh = (h * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, nw, nh, true)
    }

    private fun analyzeWithQuestion(
        question: String,
        bitmap: Bitmap? = null,
        imagePath: String? = null,
    ) {
        if (_uiState.value.isAnalyzing) return
        _uiState.update { it.copy(isAnalyzing = true) }
        analyzeJob?.cancel()
        analyzeJob =
            viewModelScope.launch {
                val historySnapshot = conversationHistory.toList()
                val response =
                    try {
                        gemini.analyzeFrame(
                            bitmap = bitmap,
                            userQuestion = question,
                            history = historySnapshot,
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Gemini call failed", e)
                        "Couldn't reach Hank: ${e.message}"
                    }
                conversationHistory.add(GeminiService.Turn("user", question))
                conversationHistory.add(GeminiService.Turn("assistant", response))
                while (conversationHistory.size > 24) conversationHistory.removeAt(0)
                appendChatMessages(
                    ChatMessage(
                        role = ChatMessage.Role.USER,
                        text = question,
                        imagePath = imagePath,
                    ),
                    ChatMessage(ChatMessage.Role.ASSISTANT, response),
                )
                _uiState.update { it.copy(isAnalyzing = false) }
                glassesAudio.speak(response)
            }
    }

    private fun appendChatMessages(vararg msgs: ChatMessage) {
        _uiState.update { state ->
            val combined = (state.chatMessages + msgs).takeLast(100)
            state.copy(chatMessages = combined)
        }
    }

    override fun onCleared() {
        super.onCleared()
        stop()
        try { glassesAudio.shutdown() } catch (_: Exception) {}
        try { voiceCommand.shutdown() } catch (_: Exception) {}
    }
}
