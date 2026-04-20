/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.chat

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meta.wearable.dat.externalsampleapps.cameraaccess.stream.BargeInDetector
import com.meta.wearable.dat.externalsampleapps.cameraaccess.stream.ChatMessage
import com.meta.wearable.dat.externalsampleapps.cameraaccess.stream.GeminiService
import com.meta.wearable.dat.externalsampleapps.cameraaccess.stream.GlassesAudioManager
import com.meta.wearable.dat.externalsampleapps.cameraaccess.stream.VoiceCommandManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
)

class ChatOnlyViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "CameraAccess:ChatOnly"
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

    /** Manual entry from the text input — same downstream path as voice. */
    fun sendTyped(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        if (_uiState.value.isAnalyzing) return
        analyzeWithQuestion(trimmed)
    }

    private fun analyzeWithQuestion(question: String) {
        if (_uiState.value.isAnalyzing) return
        _uiState.update { it.copy(isAnalyzing = true) }
        analyzeJob?.cancel()
        analyzeJob =
            viewModelScope.launch {
                val historySnapshot = conversationHistory.toList()
                val response =
                    try {
                        gemini.analyzeFrame(
                            bitmap = null,
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
                    ChatMessage(ChatMessage.Role.USER, question),
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
