/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.session

import androidx.lifecycle.ViewModel
import com.meta.wearable.dat.externalsampleapps.cameraaccess.stream.ChatMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * State for the sessions-home flow.
 *
 * Navigation flags (splashShown, streamRequested, viewingSessionId) are held
 * here rather than in a separate nav controller because the routing surface
 * is tiny — three screens plus the existing Home/Stream flow — and a Compose
 * nav-graph would be overkill.
 */
data class SessionsUiState(
    val sessions: List<Session> = emptyList(),
    val splashShown: Boolean = false,
    /** User tapped "+" to start a new Hank session → go to StreamScreen. */
    val streamRequested: Boolean = false,
    /** Non-null when the user tapped a past session row → show it read-only. */
    val viewingSessionId: String? = null,
)

class SessionViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SessionsUiState())
    val uiState: StateFlow<SessionsUiState> = _uiState.asStateFlow()

    fun markSplashDone() = _uiState.update { it.copy(splashShown = true) }

    fun requestNewSession() = _uiState.update { it.copy(streamRequested = true) }

    fun clearStreamRequest() = _uiState.update { it.copy(streamRequested = false) }

    fun openSession(id: String) = _uiState.update { it.copy(viewingSessionId = id) }

    fun closeSession() = _uiState.update { it.copy(viewingSessionId = null) }

    /** Called when a stream session ends; persists the chat as a Session card
     * on the home screen (in-memory, lost on app restart for now). */
    fun saveStreamSession(messages: List<ChatMessage>) {
        if (messages.isEmpty()) return
        val session = Session.from(messages)
        _uiState.update { it.copy(sessions = listOf(session) + it.sessions) }
    }
}
