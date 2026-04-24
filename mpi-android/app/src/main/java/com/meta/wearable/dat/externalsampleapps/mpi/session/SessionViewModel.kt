/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.mpi.session

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meta.wearable.dat.externalsampleapps.mpi.stream.ChatMessage
import com.meta.wearable.dat.externalsampleapps.mpi.stream.GeminiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class SessionsUiState(
    val sessions: List<Session> = emptyList(),
    val userProfile: UserProfile = UserProfile(),
    val settings: AppSettings = AppSettings(),
    val splashShown: Boolean = false,
    val streamRequested: Boolean = false,
    /** True when the user wants the camera-free Chat-only screen. */
    val chatOnlyOpen: Boolean = false,
    val viewingSessionId: String? = null,
    val profileOpen: Boolean = false,
    // ---- Repair orders ----
    val orders: List<RepairOrder> = emptyList(),
    /** Order currently open on the detail screen (null = not viewing). */
    val viewingOrderId: String? = null,
    /** True while the "New order" form is open. */
    val newOrderSheetOpen: Boolean = false,
    /** Order a freshly saved session should be tagged to. Set when the user
     *  taps "Start diagnosis" inside an order; cleared after the next save. */
    val activeOrderId: String? = null,
    /** Specific finding the next capture session should attach to, if any. */
    val activeFindingId: String? = null,
    val currentTab: com.meta.wearable.dat.externalsampleapps.mpi.ui.AppTab =
        com.meta.wearable.dat.externalsampleapps.mpi.ui.AppTab.CHATS,
)

class SessionViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "HankDaisy:SessionVM"
        private const val PREFS = "hank_sessions_v1"
        private const val KEY_SESSIONS = "sessions_json"
        private const val KEY_PROFILE = "profile_json"
        private const val KEY_SETTINGS = "settings_json"
        private const val KEY_ORDERS = "orders_json"
    }

    private val prefs =
        application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val gemini = GeminiService()

    private val _uiState =
        MutableStateFlow(
            SessionsUiState(
                sessions = recoverDraftIntoSessions(loadSessions()),
                userProfile = loadProfile(),
                settings = loadSettings(),
                orders = loadOrders(),
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

    fun openChatOnly() = _uiState.update { it.copy(chatOnlyOpen = true) }
    fun closeChatOnly() = _uiState.update { it.copy(chatOnlyOpen = false) }

    fun openSession(id: String) = _uiState.update { it.copy(viewingSessionId = id) }
    fun closeSession() = _uiState.update { it.copy(viewingSessionId = null) }

    fun openProfile() = _uiState.update { it.copy(profileOpen = true) }
    fun closeProfile() = _uiState.update { it.copy(profileOpen = false) }

    // ---- Orders ----

    fun openOrder(id: String) = _uiState.update { it.copy(viewingOrderId = id) }
    fun closeOrderDetail() = _uiState.update { it.copy(viewingOrderId = null) }

    fun openNewOrderSheet() = _uiState.update { it.copy(newOrderSheetOpen = true) }
    fun closeNewOrderSheet() = _uiState.update { it.copy(newOrderSheetOpen = false) }

    /** Mark an order as the owner of the next saved session. Cleared as soon
     *  as the session is persisted (one-shot), so a subsequent free-form
     *  session doesn't accidentally land on the same order. */
    fun setActiveOrder(id: String?) = _uiState.update { it.copy(activeOrderId = id) }
    fun setActiveCaptureTarget(orderId: String?, findingId: String? = null) =
        _uiState.update { it.copy(activeOrderId = orderId, activeFindingId = findingId) }

    fun createOrder(order: RepairOrder) {
        val stamped =
            order.copy(
                updatedAt = System.currentTimeMillis(),
                findings = if (order.findings.isEmpty()) defaultInspectionFindings() else order.findings,
            )
        _uiState.update { state -> state.copy(orders = listOf(stamped) + state.orders) }
        persistOrders(_uiState.value.orders)
    }

    fun updateOrder(order: RepairOrder) {
        val stamped = order.copy(updatedAt = System.currentTimeMillis())
        _uiState.update { state ->
            state.copy(orders = state.orders.map { if (it.id == stamped.id) stamped else it })
        }
        persistOrders(_uiState.value.orders)
    }

    /** Close an order with an optional close-out summary. Status flips to
     *  CLOSED and closedAt stamps; the order stays in the list so the
     *  work history is preserved. */
    fun closeOrder(id: String, summary: String = "") {
        _uiState.update { state ->
            state.copy(
                orders =
                    state.orders.map { o ->
                        if (o.id == id)
                            o.copy(
                                status = OrderStatus.CLOSED,
                                closedAt = System.currentTimeMillis(),
                                updatedAt = System.currentTimeMillis(),
                                closeSummary = summary.ifBlank { o.closeSummary },
                            )
                        else o
                    },
            )
        }
        persistOrders(_uiState.value.orders)
    }

    fun reopenOrder(id: String) {
        _uiState.update { state ->
            state.copy(
                orders =
                    state.orders.map { o ->
                        if (o.id == id)
                            o.copy(
                                status = OrderStatus.IN_PROGRESS,
                                closedAt = null,
                                updatedAt = System.currentTimeMillis(),
                            )
                        else o
                    },
            )
        }
        persistOrders(_uiState.value.orders)
    }

    fun deleteOrder(id: String) {
        _uiState.update { state ->
            state.copy(
                orders = state.orders.filterNot { it.id == id },
                // If the user was viewing it, bounce them back.
                viewingOrderId = if (state.viewingOrderId == id) null else state.viewingOrderId,
                activeOrderId = if (state.activeOrderId == id) null else state.activeOrderId,
                activeFindingId = if (state.activeOrderId == id) null else state.activeFindingId,
            )
        }
        persistOrders(_uiState.value.orders)
    }

    /** All sessions tagged to a given order, newest first. Derived — the
     *  order doc doesn't track session ids directly. */
    fun sessionsForOrder(orderId: String): List<Session> =
        _uiState.value.sessions.filter { it.orderId == orderId }

    fun selectTab(tab: com.meta.wearable.dat.externalsampleapps.mpi.ui.AppTab) {
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

    fun saveStreamSession(
        messages: List<ChatMessage>,
        evidenceAssets: List<InspectionEvidence> = emptyList(),
    ) {
        if (messages.isEmpty()) return
        // If the user started this session from inside an open order, tag it
        // — this is how "sessions on this order" gets populated. Cleared
        // after save so the next free-form stream doesn't accidentally
        // attach to the same order.
        val activeOrder = _uiState.value.activeOrderId
        val activeFinding = _uiState.value.activeFindingId
        val session =
            Session.from(
                messages = messages,
                orderId = activeOrder,
                findingId = activeFinding,
                evidenceAssets = evidenceAssets,
            )
        _uiState.update { state ->
            state.copy(
                sessions = listOf(session) + state.sessions,
                activeOrderId = null,
                activeFindingId = null,
            )
        }
        persistSessions(_uiState.value.sessions)
        // If tagged, also bump the order into IN_PROGRESS so the list
        // sorts/filters reflect that there's live work on it.
        activeOrder?.let { orderId ->
            _uiState.update { state ->
                state.copy(
                    orders =
                        state.orders.map { o ->
                            if (o.id == orderId && o.status == OrderStatus.OPEN)
                                o.copy(
                                    status = OrderStatus.IN_PROGRESS,
                                    updatedAt = System.currentTimeMillis(),
                                )
                            else if (o.id == orderId)
                                o.copy(updatedAt = System.currentTimeMillis())
                            else o
                        },
                )
            }
            if (activeFinding != null && evidenceAssets.isNotEmpty()) {
                attachEvidenceToFinding(orderId, activeFinding, session.id, evidenceAssets)
            } else if (activeFinding != null) {
                attachEvidenceToFinding(orderId, activeFinding, session.id, emptyList())
            }
            persistOrders(_uiState.value.orders)
        }
        prefs.edit().remove("draft_chat").apply()
        generateAiTitleAsync(session.id, messages)
    }

    private fun generateAiTitleAsync(sessionId: String, messages: List<ChatMessage>) {
        viewModelScope.launch {
            val title =
                try {
                    val prompt =
                        "Generate a short, descriptive title for the conversation below. " +
                            "Rules: 3 to 6 words, no quotes, no punctuation at the end, " +
                            "no prefixes like 'Chat about'. Reply with ONLY the title."
                    val history =
                        messages.map {
                            GeminiService.Turn(
                                role = if (it.role == ChatMessage.Role.USER) "user" else "assistant",
                                text = it.text,
                            )
                        }
                    val raw =
                        gemini.analyzeFrame(
                            bitmap = null,
                            userQuestion = prompt,
                            history = history,
                        )
                    sanitiseTitle(raw)
                } catch (e: Exception) {
                    Log.w(TAG, "AI title generation failed", e)
                    ""
                }
            if (title.isBlank()) return@launch
            _uiState.update { state ->
                state.copy(
                    sessions =
                        state.sessions.map {
                            if (it.id == sessionId) it.copy(title = title) else it
                        },
                )
            }
            persistSessions(_uiState.value.sessions)
        }
    }

    private fun sanitiseTitle(raw: String): String {
        val firstLine = raw.lineSequence().firstOrNull { it.isNotBlank() } ?: return ""
        return firstLine
            .trim()
            .trim('"', '\'', '`', '*', '#', '.', ':', '—', '-', ' ')
            .take(60)
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

    private fun loadOrders(): List<RepairOrder> {
        val raw = prefs.getString(KEY_ORDERS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { orderFromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            Log.w(TAG, "loadOrders failed", e)
            emptyList()
        }
    }

    private fun persistOrders(list: List<RepairOrder>) {
        try {
            val arr = JSONArray()
            for (o in list) arr.put(orderToJson(o))
            prefs.edit().putString(KEY_ORDERS, arr.toString()).apply()
        } catch (e: Exception) {
            Log.w(TAG, "persistOrders failed", e)
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
                pronouns = o.optString("pronouns"),
                bio = o.optString("bio"),
                avatarColor = o.optLong("avatarColor", AvatarColors.first()),
                hankPersona =
                    runCatching { HankPersona.valueOf(o.optString("hankPersona", "MECHANIC")) }
                        .getOrDefault(HankPersona.MECHANIC),
                verbosity =
                    runCatching { Verbosity.valueOf(o.optString("verbosity", "NORMAL")) }
                        .getOrDefault(Verbosity.NORMAL),
                useMyName = o.optBoolean("useMyName", true),
                createdAt = o.optLong("createdAt", 0L),
            )
        } catch (_: Exception) {
            UserProfile()
        }
    }

    private fun persistProfile(p: UserProfile) {
        val withCreatedAt =
            if (p.createdAt == 0L) p.copy(createdAt = System.currentTimeMillis()) else p
        val o =
            JSONObject()
                .put("name", withCreatedAt.name)
                .put("role", withCreatedAt.role)
                .put("email", withCreatedAt.email)
                .put("pronouns", withCreatedAt.pronouns)
                .put("bio", withCreatedAt.bio)
                .put("avatarColor", withCreatedAt.avatarColor)
                .put("hankPersona", withCreatedAt.hankPersona.name)
                .put("verbosity", withCreatedAt.verbosity.name)
                .put("useMyName", withCreatedAt.useMyName)
                .put("createdAt", withCreatedAt.createdAt)
        prefs.edit().putString(KEY_PROFILE, o.toString()).apply()
        if (withCreatedAt !== p) {
            _uiState.update { it.copy(userProfile = withCreatedAt) }
        }
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
                    .put("ts", m.timestamp)
                    .apply { if (m.imagePath != null) put("imagePath", m.imagePath) },
            )
        }
        return JSONObject()
            .put("id", s.id)
            .put("createdAt", s.createdAt)
            .put("title", s.title)
            .put("description", s.description)
            .put("messages", msgs)
            .apply {
                if (s.orderId != null) put("orderId", s.orderId)
                if (s.findingId != null) put("findingId", s.findingId)
                if (s.evidenceAssets.isNotEmpty()) {
                    val evidenceArr = JSONArray()
                    for (asset in s.evidenceAssets) evidenceArr.put(evidenceToJson(asset))
                    put("evidenceAssets", evidenceArr)
                }
            }
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
                    imagePath = mo.optString("imagePath", "").ifBlank { null },
                )
            }
        val evidenceArr = o.optJSONArray("evidenceAssets") ?: JSONArray()
        val evidence =
            (0 until evidenceArr.length()).map { evidenceFromJson(evidenceArr.getJSONObject(it)) }
        return Session(
            id = o.optString("id"),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            title = o.optString("title"),
            description = o.optString("description"),
            messages = msgs,
            orderId = o.optString("orderId", "").ifBlank { null },
            findingId = o.optString("findingId", "").ifBlank { null },
            evidenceAssets = evidence,
        )
    }

    private fun orderToJson(o: RepairOrder): JSONObject {
        val findingsArr = JSONArray()
        for (f in o.findings) {
            findingsArr.put(
                JSONObject()
                    .put("id", f.id)
                    .put("system", f.system)
                    .put("component", f.component)
                    .put("location", f.location)
                    .put("measurement", f.measurement)
                    .put("recommendation", f.recommendation)
                    .put("severity", f.severity.name)
                    .put("note", f.note)
                    .put("linkedSessionIds", JSONArray(f.linkedSessionIds))
                    .put("evidenceAssets", JSONArray().apply {
                        for (asset in f.evidenceAssets) put(evidenceToJson(asset))
                    }),
            )
        }
        val diagnosesArr = JSONArray()
        for (d in o.diagnoses) {
            diagnosesArr.put(
                JSONObject()
                    .put("id", d.id)
                    .put("createdAt", d.createdAt)
                    .put("step", d.step)
                    .put("result", d.result)
                    .put("outcome", d.outcome.name)
                    .apply { if (d.sessionId != null) put("sessionId", d.sessionId) },
            )
        }
        return JSONObject()
            .put("id", o.id)
            .put("createdAt", o.createdAt)
            .put("updatedAt", o.updatedAt)
            .put("templateId", o.templateId)
            .put("repairOrderNumber", o.repairOrderNumber)
            .put("mileage", o.mileage)
            .put("advisorName", o.advisorName)
            .put("technicianName", o.technicianName)
            .put("vehicleMake", o.vehicleMake)
            .put("vehicleModel", o.vehicleModel)
            .put("vehicleYear", o.vehicleYear)
            .put("vehicleVin", o.vehicleVin)
            .put("licensePlate", o.licensePlate)
            .put("customerName", o.customerName)
            .put("customerPhone", o.customerPhone)
            .put("presentingIssue", o.presentingIssue)
            .put("notes", o.notes)
            .put("status", o.status.name)
            .put("findings", findingsArr)
            .put("diagnoses", diagnosesArr)
            .apply {
                if (o.closedAt != null) put("closedAt", o.closedAt)
                if (o.closeSummary.isNotBlank()) put("closeSummary", o.closeSummary)
            }
    }

    private fun orderFromJson(o: JSONObject): RepairOrder {
        val findingsArr = o.optJSONArray("findings") ?: JSONArray()
        val findings =
            (0 until findingsArr.length()).map {
                val f = findingsArr.getJSONObject(it)
                InspectionFinding(
                    id = f.optString("id"),
                    system = f.optString("system"),
                    component = f.optString("component"),
                    location = f.optString("location"),
                    measurement = f.optString("measurement"),
                    recommendation = f.optString("recommendation"),
                    severity =
                        runCatching {
                                FindingSeverity.valueOf(f.optString("severity", "YELLOW"))
                            }
                            .getOrDefault(FindingSeverity.YELLOW),
                    note = f.optString("note"),
                    linkedSessionIds =
                        (f.optJSONArray("linkedSessionIds") ?: JSONArray()).let { ids ->
                            (0 until ids.length()).map { ids.optString(it) }.filter { it.isNotBlank() }
                        },
                    evidenceAssets =
                        (f.optJSONArray("evidenceAssets") ?: JSONArray()).let { evidence ->
                            (0 until evidence.length()).map {
                                evidenceFromJson(evidence.getJSONObject(it))
                            }
                        },
                )
            }
        val arr = o.optJSONArray("diagnoses") ?: JSONArray()
        val diagnoses =
            (0 until arr.length()).map {
                val d = arr.getJSONObject(it)
                DiagnosisEntry(
                    id = d.optString("id"),
                    createdAt = d.optLong("createdAt", System.currentTimeMillis()),
                    step = d.optString("step"),
                    result = d.optString("result"),
                    outcome =
                        runCatching { DiagnosisOutcome.valueOf(d.optString("outcome", "PENDING")) }
                            .getOrDefault(DiagnosisOutcome.PENDING),
                    sessionId = d.optString("sessionId", "").ifBlank { null },
                )
            }
        return RepairOrder(
            id = o.optString("id"),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
            templateId = o.optString("templateId", InspectionTemplates.DEFAULT_TEMPLATE.id),
            repairOrderNumber = o.optString("repairOrderNumber"),
            mileage = o.optString("mileage"),
            advisorName = o.optString("advisorName"),
            technicianName = o.optString("technicianName"),
            vehicleMake = o.optString("vehicleMake"),
            vehicleModel = o.optString("vehicleModel"),
            vehicleYear = o.optString("vehicleYear"),
            vehicleVin = o.optString("vehicleVin"),
            licensePlate = o.optString("licensePlate"),
            customerName = o.optString("customerName"),
            customerPhone = o.optString("customerPhone"),
            presentingIssue = o.optString("presentingIssue"),
            notes = o.optString("notes"),
            status =
                runCatching { OrderStatus.valueOf(o.optString("status", "OPEN")) }
                    .getOrDefault(OrderStatus.OPEN),
            findings = if (findings.isEmpty()) InspectionTemplates.instantiate() else findings,
            diagnoses = diagnoses,
            closedAt = if (o.has("closedAt")) o.optLong("closedAt") else null,
            closeSummary = o.optString("closeSummary"),
        )
    }

    private fun defaultInspectionFindings(): List<InspectionFinding> =
        InspectionTemplates.instantiate()

    private fun evidenceToJson(asset: InspectionEvidence): JSONObject =
        JSONObject()
            .put("id", asset.id)
            .put("kind", asset.kind.name)
            .put("filePath", asset.filePath)
            .put("createdAt", asset.createdAt)
            .put("caption", asset.caption)

    private fun evidenceFromJson(o: JSONObject): InspectionEvidence =
        InspectionEvidence(
            id = o.optString("id"),
            kind =
                runCatching { EvidenceKind.valueOf(o.optString("kind", "IMAGE")) }
                    .getOrDefault(EvidenceKind.IMAGE),
            filePath = o.optString("filePath"),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            caption = o.optString("caption"),
        )

    private fun attachEvidenceToFinding(
        orderId: String,
        findingId: String,
        sessionId: String,
        evidenceAssets: List<InspectionEvidence>,
    ) {
        _uiState.update { state ->
            state.copy(
                orders =
                    state.orders.map { order ->
                        if (order.id != orderId) return@map order
                        order.copy(
                            updatedAt = System.currentTimeMillis(),
                            findings =
                                order.findings.map { finding ->
                                    if (finding.id != findingId) return@map finding
                                    finding.copy(
                                        linkedSessionIds =
                                            (finding.linkedSessionIds + sessionId).distinct(),
                                        evidenceAssets = finding.evidenceAssets + evidenceAssets,
                                    )
                                },
                        )
                    },
            )
        }
    }
}
