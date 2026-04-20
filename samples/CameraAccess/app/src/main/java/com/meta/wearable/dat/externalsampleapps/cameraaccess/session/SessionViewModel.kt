/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.session

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.meta.wearable.dat.externalsampleapps.cameraaccess.stream.ChatMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject

data class SessionsUiState(
    val sessions: List<Session> = emptyList(),
    val userProfile: UserProfile = UserProfile(),
    val settings: AppSettings = AppSettings(),
    val splashShown: Boolean = false,
    val streamRequested: Boolean = false,
    val viewingSessionId: String? = null,
    val profileOpen: Boolean = false,
    val currentTab: com.meta.wearable.dat.externalsampleapps.cameraaccess.ui.AppTab =
        com.meta.wearable.dat.externalsampleapps.cameraaccess.ui.AppTab.CHATS,
)

class SessionViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "CameraAccess:SessionVM"
        private const val PREFS = "hank_sessions_v1"
        private const val KEY_SESSIONS = "sessions_json"
        private const val KEY_PROFILE = "profile_json"
        private const val KEY_SETTINGS = "settings_json"
    }

    private val prefs =
        application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _uiState =
        MutableStateFlow(
            SessionsUiState(
                sessions = recoverDraftIntoSessions(loadSessions()),
                userProfile = loadProfile(),
                settings = loadSettings(),
            ),
        )
    val uiState: StateFlow<SessionsUiState> = _uiState.asStateFlow()

    /** If the app was killed mid-stream, a JSON draft of the running chat
     * is sitting in prefs. Recover it as a saved session so the user doesn't
     * lose the conversation, then clear the draft so we don't duplicate on
     * the next cold start. */
    private fun recoverDraftIntoSessions(existing: List<Session>): List<Session> {
        val raw = prefs.getString("draft_chat", null) ?: return existing
        return try {
            val arr = JSONArray(raw)
            if (arr.length() == 0) {
                prefs.edit().remove("draft_chat").apply()
                return existing
            }
            val msgs =
                (0 until arr.length()).map {
                    val mo = arr.getJSONObject(it)
                    ChatMessage(
                        role =
                            if (mo.optString("role") == "user") ChatMessage.Role.USER
                            else ChatMessage.Role.ASSISTANT,
                        text = mo.optString("text"),
                        timestamp = mo.optLong("ts", System.currentTimeMillis()),
                    )
                }
            val base = Session.from(msgs)
            val recovered = base.copy(title = "(recovered) ${base.title}")
            prefs.edit().remove("draft_chat").apply()
            val merged = listOf(recovered) + existing
            persistSessions(merged)
            merged
        } catch (e: Exception) {
            Log.w(TAG, "recoverDraftIntoSessions failed", e)
            prefs.edit().remove("draft_chat").apply()
            existing
        }
    }

    fun markSplashDone() = _uiState.update { it.copy(splashShown = true) }

    fun requestNewSession() = _uiState.update { it.copy(streamRequested = true) }
    fun clearStreamRequest() = _uiState.update { it.copy(streamRequested = false) }

    fun openSession(id: String) = _uiState.update { it.copy(viewingSessionId = id) }
    fun closeSession() = _uiState.update { it.copy(viewingSessionId = null) }

    fun openProfile() = _uiState.update { it.copy(profileOpen = true) }
    fun closeProfile() = _uiState.update { it.copy(profileOpen = false) }

    fun selectTab(tab: com.meta.wearable.dat.externalsampleapps.cameraaccess.ui.AppTab) {
        _uiState.update { it.copy(currentTab = tab) }
    }

    fun updateSettings(settings: AppSettings) {
        _uiState.update { it.copy(settings = settings) }
        persistSettings(settings)
    }

    private fun loadSettings(): AppSettings {
        val raw = prefs.getString(KEY_SETTINGS, null) ?: return AppSettings()
        return try {
            val o = JSONObject(raw)
            AppSettings(
                themeMode = ThemeMode.valueOf(o.optString("theme", "LIGHT")),
                textScale = TextScale.valueOf(o.optString("textScale", "NORMAL")),
                highContrast = o.optBoolean("highContrast", false),
                hapticFeedback = o.optBoolean("hapticFeedback", true),
            )
        } catch (_: Exception) {
            AppSettings()
        }
    }

    private fun persistSettings(s: AppSettings) {
        val o =
            JSONObject()
                .put("theme", s.themeMode.name)
                .put("textScale", s.textScale.name)
                .put("highContrast", s.highContrast)
                .put("hapticFeedback", s.hapticFeedback)
        prefs.edit().putString(KEY_SETTINGS, o.toString()).apply()
    }

    fun updateProfile(profile: UserProfile) {
        _uiState.update { it.copy(userProfile = profile) }
        persistProfile(profile)
    }

    fun saveStreamSession(messages: List<ChatMessage>) {
        if (messages.isEmpty()) return
        val session = Session.from(messages)
        _uiState.update { state -> state.copy(sessions = listOf(session) + state.sessions) }
        persistSessions(_uiState.value.sessions)
        // Clear the in-flight draft now that the session is saved cleanly.
        prefs.edit().remove("draft_chat").apply()
    }

    fun deleteSession(id: String) {
        _uiState.update { state ->
            state.copy(sessions = state.sessions.filterNot { it.id == id })
        }
        persistSessions(_uiState.value.sessions)
    }

    // ---- persistence ----

    private fun loadSessions(): List<Session> {
        val raw = prefs.getString(KEY_SESSIONS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { sessionFromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            Log.w(TAG, "loadSessions failed", e)
            emptyList()
        }
    }

    private fun persistSessions(list: List<Session>) {
        try {
            val arr = JSONArray()
            for (s in list) arr.put(sessionToJson(s))
            prefs.edit().putString(KEY_SESSIONS, arr.toString()).apply()
        } catch (e: Exception) {
            Log.w(TAG, "persistSessions failed", e)
        }
    }

    private fun loadProfile(): UserProfile {
        val raw = prefs.getString(KEY_PROFILE, null) ?: return UserProfile()
        return try {
            val o = JSONObject(raw)
            UserProfile(
                name = o.optString("name"),
                role = o.optString("role"),
                email = o.optString("email"),
            )
        } catch (_: Exception) {
            UserProfile()
        }
    }

    private fun persistProfile(p: UserProfile) {
        val o =
            JSONObject()
                .put("name", p.name)
                .put("role", p.role)
                .put("email", p.email)
        prefs.edit().putString(KEY_PROFILE, o.toString()).apply()
    }

    private fun sessionToJson(s: Session): JSONObject {
        val msgs = JSONArray()
        for (m in s.messages) {
            msgs.put(
                JSONObject()
                    .put(
                        "role",
                        if (m.role == ChatMessage.Role.USER) "user" else "assistant",
                    )
                    .put("text", m.text)
                    .put("ts", m.timestamp),
            )
        }
        return JSONObject()
            .put("id", s.id)
            .put("createdAt", s.createdAt)
            .put("title", s.title)
            .put("description", s.description)
            .put("messages", msgs)
    }

    private fun sessionFromJson(o: JSONObject): Session {
        val arr = o.optJSONArray("messages") ?: JSONArray()
        val msgs =
            (0 until arr.length()).map {
                val mo = arr.getJSONObject(it)
                ChatMessage(
                    role =
                        if (mo.optString("role") == "user") ChatMessage.Role.USER
                        else ChatMessage.Role.ASSISTANT,
                    text = mo.optString("text"),
                    timestamp = mo.optLong("ts", System.currentTimeMillis()),
                )
            }
        return Session(
            id = o.optString("id"),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            title = o.optString("title"),
            description = o.optString("description"),
            messages = msgs,
        )
    }
}
