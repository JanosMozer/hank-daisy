package com.meta.wearable.dat.externalsampleapps.mpi.session

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meta.wearable.dat.externalsampleapps.mpi.stream.ChatMessage
import com.meta.wearable.dat.externalsampleapps.mpi.ui.AppTab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class SessionsUiState(
    val config: AppConfig = AppConfig(),
    val sessions: List<Session> = emptyList(),
    val currentTab: AppTab = AppTab.CAPTURE,
    val lastCompletedSessionId: String? = null,
    val isPhoneCaptureActive: Boolean = false,
)

class SessionViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val PREFS = AppConfigStore.PREFS
        private const val KEY_SESSIONS = "sessions_json"
    }

    private val prefs =
        application.getSharedPreferences(PREFS, Application.MODE_PRIVATE)

    private val _uiState =
        MutableStateFlow(
            SessionsUiState(
                config = AppConfigStore.current(application),
                sessions = loadSessions(),
            ),
        )
    val uiState: StateFlow<SessionsUiState> = _uiState.asStateFlow()

    init {
        val latestId = _uiState.value.sessions.firstOrNull()?.id
        _uiState.update { it.copy(lastCompletedSessionId = latestId) }
    }

    fun selectTab(tab: AppTab) {
        _uiState.update { it.copy(currentTab = tab) }
    }

    fun updateConfig(config: AppConfig) {
        val normalized = config.normalized()
        _uiState.update { it.copy(config = normalized) }
        AppConfigStore.save(getApplication(), normalized)
    }

    fun updateCaptureConfig(transform: (CaptureConfig) -> CaptureConfig) {
        updateConfig(_uiState.value.config.copy(capture = transform(_uiState.value.config.capture)))
    }

    fun updateSpeechConfig(transform: (SpeechConfig) -> SpeechConfig) {
        updateConfig(_uiState.value.config.copy(speech = transform(_uiState.value.config.speech)))
    }

    fun updateGeneralSettings(transform: (GeneralSettings) -> GeneralSettings) {
        updateConfig(_uiState.value.config.copy(general = transform(_uiState.value.config.general)))
    }

    fun beginPhoneCapture() {
        _uiState.update { it.copy(isPhoneCaptureActive = true) }
    }

    fun endPhoneCapture() {
        _uiState.update { it.copy(isPhoneCaptureActive = false) }
    }

    fun saveStreamSession(
        messages: List<ChatMessage>,
        evidenceAssets: List<InspectionEvidence> = emptyList(),
    ) {
        if (messages.isEmpty() && evidenceAssets.isEmpty()) {
            _uiState.update { it.copy(isPhoneCaptureActive = false) }
            return
        }

        val session =
            Session.from(
                messages = messages,
                evidenceAssets = evidenceAssets,
                metadata = _uiState.value.config.captureMetadata(),
            )

        _uiState.update { state ->
            val merged =
                listOf(session) +
                    state.sessions.filterNot { it.id == session.id }
                        .sortedByDescending { it.createdAt }
            state.copy(
                sessions = merged,
                lastCompletedSessionId = session.id,
                currentTab = AppTab.REPORT,
                isPhoneCaptureActive = false,
            )
        }
        persistSessions(_uiState.value.sessions)
    }

    fun latestSession(): Session? {
        val state = _uiState.value
        return state.sessions.firstOrNull { it.id == state.lastCompletedSessionId }
            ?: state.sessions.firstOrNull()
    }

    fun clearCompletedCapture() {
        _uiState.update { it.copy(lastCompletedSessionId = null) }
    }

    private fun loadSessions(): List<Session> {
        val raw = prefs.getString(KEY_SESSIONS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    add(sessionFromJson(item))
                }
            }.sortedByDescending { it.createdAt }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun persistSessions(list: List<Session>) {
        val arr = JSONArray()
        list.forEach { arr.put(sessionToJson(it)) }
        prefs.edit().putString(KEY_SESSIONS, arr.toString()).apply()
    }

    private fun sessionToJson(session: Session): JSONObject {
        val messages =
            JSONArray().apply {
                session.messages.forEach { message ->
                    put(
                        JSONObject()
                            .put("role", message.role.name)
                            .put("text", message.text)
                            .put("timestamp", message.timestamp)
                            .put("imagePath", message.imagePath),
                    )
                }
            }

        val evidence =
            JSONArray().apply {
                session.evidenceAssets.forEach { asset ->
                    put(
                        JSONObject()
                            .put("id", asset.id)
                            .put("kind", asset.kind.name)
                            .put("filePath", asset.filePath)
                            .put("createdAt", asset.createdAt)
                            .put("caption", asset.caption)
                            .put("previewImagePath", asset.previewImagePath)
                            .put("clipFps", asset.clipFps)
                            .put("durationMs", asset.durationMs)
                            .put(
                                "clipFramePaths",
                                JSONArray().apply {
                                    asset.clipFramePaths.forEach { put(it) }
                                },
                            ),
                    )
                }
            }

        return JSONObject()
            .put("id", session.id)
            .put("createdAt", session.createdAt)
            .put("endedAt", session.endedAt)
            .put("title", session.title)
            .put("description", session.description)
            .put("orderId", session.orderId)
            .put("findingId", session.findingId)
            .put("messages", messages)
            .put("evidenceAssets", evidence)
            .put(
                "metadata",
                session.metadata?.let {
                    JSONObject()
                        .put("videoSource", it.videoSource.name)
                        .put("audioSource", it.audioSource.name)
                        .put("preferredPhoneMic", it.preferredPhoneMic.name)
                        .put("hankMode", it.hankMode.name)
                        .put("domainMode", it.domainMode.name)
                        .put("speechRecognitionRoute", it.speechRecognitionRoute.name)
                },
            )
    }

    private fun sessionFromJson(obj: JSONObject): Session {
        val messages =
            buildList {
                val arr = obj.optJSONArray("messages") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val message = arr.optJSONObject(i) ?: continue
                    add(
                        ChatMessage(
                            role =
                                ChatMessage.Role.entries.firstOrNull {
                                    it.name == message.optString("role")
                                } ?: ChatMessage.Role.USER,
                            text = message.optString("text"),
                            timestamp = message.optLong("timestamp", System.currentTimeMillis()),
                            imagePath = message.optString("imagePath").ifBlank { null },
                        ),
                    )
                }
            }
        val evidenceAssets =
            buildList {
                val arr = obj.optJSONArray("evidenceAssets") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    add(
                        InspectionEvidence(
                            id = item.optString("id"),
                            kind =
                                EvidenceKind.entries.firstOrNull {
                                    it.name == item.optString("kind")
                                } ?: EvidenceKind.IMAGE,
                            filePath = item.optString("filePath"),
                            createdAt = item.optLong("createdAt"),
                            caption = item.optString("caption"),
                            previewImagePath = item.optString("previewImagePath").ifBlank { null },
                            clipFramePaths =
                                buildList {
                                    val frames = item.optJSONArray("clipFramePaths") ?: JSONArray()
                                    for (frameIndex in 0 until frames.length()) {
                                        add(frames.optString(frameIndex))
                                    }
                                },
                            clipFps = item.optInt("clipFps", 0),
                            durationMs = item.optLong("durationMs", 0L),
                        ),
                    )
                }
            }

        val metadataObj = obj.optJSONObject("metadata")
        val metadata =
            metadataObj?.let {
                CaptureSessionMetadata(
                    videoSource =
                        CaptureVideoSource.entries.firstOrNull {
                            it.name == metadataObj.optString("videoSource")
                        } ?: CaptureVideoSource.GLASSES,
                    audioSource =
                        CaptureAudioSource.entries.firstOrNull {
                            it.name == metadataObj.optString("audioSource")
                        } ?: CaptureAudioSource.GLASSES_MIC,
                    preferredPhoneMic =
                        PreferredMicDevice.entries.firstOrNull {
                            it.name == metadataObj.optString("preferredPhoneMic")
                        } ?: PreferredMicDevice.SYSTEM_DEFAULT,
                    hankMode =
                        HankMode.entries.firstOrNull {
                            it.name == metadataObj.optString("hankMode")
                        } ?: HankMode.INTERACTIVE,
                    domainMode =
                        DomainMode.entries.firstOrNull {
                            it.name == metadataObj.optString("domainMode")
                        } ?: DomainMode.CAR_ONLY,
                    speechRecognitionRoute =
                        SpeechRecognitionRoute.fromStored(
                            metadataObj.optString(
                                "speechRecognitionRoute",
                                SpeechRecognitionRoute.ANDROID.name,
                            ),
                        ),
                )
            }

        return Session(
            id = obj.optString("id"),
            createdAt = obj.optLong("createdAt"),
            endedAt = obj.optLong("endedAt", obj.optLong("createdAt")),
            title = obj.optString("title"),
            description = obj.optString("description"),
            messages = messages,
            orderId = obj.optString("orderId").ifBlank { null },
            findingId = obj.optString("findingId").ifBlank { null },
            evidenceAssets = evidenceAssets,
            metadata = metadata,
        )
    }
}
