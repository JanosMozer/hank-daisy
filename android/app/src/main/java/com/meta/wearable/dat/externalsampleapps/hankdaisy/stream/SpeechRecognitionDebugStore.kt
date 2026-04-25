/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.hankdaisy.stream

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

data class SpeechRecognitionDebugInfo(
    val backendLabel: String = "No samples yet",
    val modelId: String = "",
    val status: String = "Waiting for a transcription",
    val transcript: String = "",
    val latencyMs: Long? = null,
    val updatedAt: Long = 0L,
)

object SpeechRecognitionDebugStore {
    private const val PREFS = "hank_sessions_v1"
    private const val KEY_DEBUG = "speech_debug_json"

    private val state = MutableStateFlow(SpeechRecognitionDebugInfo())
    @Volatile private var loaded = false

    fun observe(context: Context): StateFlow<SpeechRecognitionDebugInfo> {
        ensureLoaded(context.applicationContext)
        return state.asStateFlow()
    }

    fun record(
        context: Context,
        backendLabel: String,
        modelId: String,
        status: String,
        transcript: String,
        latencyMs: Long?,
    ) {
        ensureLoaded(context.applicationContext)
        val updated =
            SpeechRecognitionDebugInfo(
                backendLabel = backendLabel,
                modelId = modelId,
                status = status,
                transcript = transcript,
                latencyMs = latencyMs,
                updatedAt = System.currentTimeMillis(),
            )
        state.value = updated
        persist(context.applicationContext, updated)
    }

    private fun ensureLoaded(context: Context) {
        if (loaded) return
        val raw =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_DEBUG, null)
        state.value =
            try {
                if (raw.isNullOrBlank()) {
                    SpeechRecognitionDebugInfo()
                } else {
                    val json = JSONObject(raw)
                    SpeechRecognitionDebugInfo(
                        backendLabel = json.optString("backendLabel", "No samples yet"),
                        modelId = json.optString("modelId", ""),
                        status = json.optString("status", "Waiting for a transcription"),
                        transcript = json.optString("transcript", ""),
                        latencyMs =
                            if (json.has("latencyMs")) json.optLong("latencyMs") else null,
                        updatedAt = json.optLong("updatedAt", 0L),
                    )
                }
            } catch (_: Exception) {
                SpeechRecognitionDebugInfo()
            }
        loaded = true
    }

    private fun persist(context: Context, info: SpeechRecognitionDebugInfo) {
        val json =
            JSONObject()
                .put("backendLabel", info.backendLabel)
                .put("modelId", info.modelId)
                .put("status", info.status)
                .put("transcript", info.transcript)
                .put("latencyMs", info.latencyMs ?: JSONObject.NULL)
                .put("updatedAt", info.updatedAt)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DEBUG, json.toString())
            .apply()
    }
}
