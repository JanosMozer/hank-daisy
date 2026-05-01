/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.hankdaisy.stream

import android.content.Context
import android.util.Base64
import android.util.Log
import com.meta.wearable.dat.externalsampleapps.hankdaisy.BuildConfig
import com.meta.wearable.dat.externalsampleapps.hankdaisy.session.AppConfigStore
import com.meta.wearable.dat.externalsampleapps.hankdaisy.session.RemoteSpeechModel
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Sends short audio clips to OpenRouter and asks OpenAI's audio model to return
 * only the primary speaker's utterance. This is a transcription-style route,
 * but implemented over OpenRouter's audio-input chat API so it fits the same
 * API key and transport the rest of the app already uses.
 */
class OpenRouterSpeechTranscriber(
    private val context: Context,
) {

    data class TranscriptionResult(
        val status: Status,
        val text: String?,
        val modelId: String,
        val latencyMs: Long,
        val message: String? = null,
    ) {
        enum class Status {
            OK,
            IGNORED,
            ERROR,
        }
    }

    companion object {
        private const val TAG = "HankDaisy:OpenRouterSTT"
        private const val API_URL = "https://openrouter.ai/api/v1/chat/completions"
        private const val DEFAULT_MODEL = "openai/gpt-4o-transcribe-diarize"
        private const val FALLBACK_MODEL = "openai/gpt-audio"

        private const val TRANSCRIPTION_PROMPT =
            """
            Transcribe only the primary nearby speaker who is addressing the assistant.
            Ignore background music, radio, TV, shop noise, tool noise, and side conversations.
            If multiple people speak, keep only the dominant foreground speaker.
            If there is no clear foreground utterance for the assistant, return exactly <no-speech>.
            Return only the spoken words. Never mention audio, transcript, transcription, speaker, recording, file, or clip.
            Return plain text only. No labels, no commentary, no quotes.
            """
    }

    private val apiKey: String = BuildConfig.OPENROUTER_API_KEY

    private val client =
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()

    val isConfigured: Boolean
        get() = apiKey.isNotBlank()

    fun transcribe(wavBytes: ByteArray): TranscriptionResult {
        if (!isConfigured || wavBytes.isEmpty()) {
            return TranscriptionResult(
                status = TranscriptionResult.Status.ERROR,
                text = null,
                modelId = configuredModels().first(),
                latencyMs = 0,
                message = "OpenRouter API key not configured or audio clip empty",
            )
        }

        val startedAt = System.currentTimeMillis()
        return try {
            val audioData = Base64.encodeToString(wavBytes, Base64.NO_WRAP)
            var lastError = "Unknown OpenRouter STT failure"
            var lastModelId = configuredModels().first()

            for (modelId in configuredModels()) {
                lastModelId = modelId
                val result = transcribeWithModel(audioData, modelId, startedAt)
                when (result.status) {
                    TranscriptionResult.Status.OK,
                    TranscriptionResult.Status.IGNORED -> return result
                    TranscriptionResult.Status.ERROR -> {
                        lastError = result.message ?: lastError
                        Log.w(TAG, "STT candidate failed for $modelId: $lastError")
                    }
                }
            }

            TranscriptionResult(
                status = TranscriptionResult.Status.ERROR,
                text = null,
                modelId = lastModelId,
                latencyMs = System.currentTimeMillis() - startedAt,
                message = lastError,
            )
        } catch (e: Exception) {
            Log.e(TAG, "OpenRouter STT request failed", e)
            TranscriptionResult(
                status = TranscriptionResult.Status.ERROR,
                text = null,
                modelId = configuredModels().first(),
                latencyMs = System.currentTimeMillis() - startedAt,
                message = e.message ?: "Unexpected OpenRouter STT failure",
            )
        }
    }

    private fun configuredModels(): List<String> {
        return when (AppConfigStore.current(context).audio.transcription.remoteModel) {
            RemoteSpeechModel.AUTO -> listOf(DEFAULT_MODEL, FALLBACK_MODEL)
            RemoteSpeechModel.GPT_4O_TRANSCRIBE_DIARIZE -> listOf(DEFAULT_MODEL, FALLBACK_MODEL)
            RemoteSpeechModel.GPT_AUDIO -> listOf(FALLBACK_MODEL)
        }
    }

    private fun transcribeWithModel(
        audioData: String,
        modelId: String,
        startedAt: Long,
    ): TranscriptionResult {
        val messages =
            JSONArray().apply {
                put(
                    JSONObject().apply {
                        put("role", "user")
                        put(
                            "content",
                            JSONArray().apply {
                                put(
                                    JSONObject().apply {
                                        put("type", "text")
                                        put("text", TRANSCRIPTION_PROMPT.trimIndent())
                                    },
                                )
                                put(
                                    JSONObject().apply {
                                        put("type", "input_audio")
                                        put(
                                            "input_audio",
                                            JSONObject().apply {
                                                put("data", audioData)
                                                put("format", "wav")
                                            },
                                        )
                                    },
                                )
                            },
                        )
                    },
                )
            }

        val payload =
            JSONObject()
                .apply {
                    put("model", modelId)
                    put("messages", messages)
                    put("temperature", 0)
                    put("max_tokens", 120)
                }
                .toString()

        val request =
            Request.Builder()
                .url(API_URL)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .addHeader("HTTP-Referer", "https://github.com/JanosMozer/hank-daisy")
                .addHeader("X-Title", "Hank Speech Recognition")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            val latencyMs = System.currentTimeMillis() - startedAt
            if (!response.isSuccessful) {
                Log.e(TAG, "OpenRouter STT error ${response.code} on $modelId: ${body.take(300)}")
                return TranscriptionResult(
                    status = TranscriptionResult.Status.ERROR,
                    text = null,
                    modelId = modelId,
                    latencyMs = latencyMs,
                    message = "HTTP ${response.code}",
                )
            }
            val text = normalizeTranscript(extractText(body).orEmpty())
            return when {
                text.isBlank() ||
                    text.equals("<no-speech>", ignoreCase = true) ||
                    text.equals("no speech", ignoreCase = true) ->
                    TranscriptionResult(
                        status = TranscriptionResult.Status.IGNORED,
                        text = null,
                        modelId = modelId,
                        latencyMs = latencyMs,
                        message = "No clear foreground speech",
                    )
                else ->
                    TranscriptionResult(
                        status = TranscriptionResult.Status.OK,
                        text = text,
                        modelId = modelId,
                        latencyMs = latencyMs,
                    )
            }
        }
    }

    private fun extractText(body: String): String? {
        val json = JSONObject(body)
        val message =
            json.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?: return null
        return when (val content = message.opt("content")) {
            is String -> content
            is JSONArray -> {
                buildString {
                    for (i in 0 until content.length()) {
                        val part = content.optJSONObject(i) ?: continue
                        val text =
                            when {
                                part.has("text") -> part.optString("text")
                                part.has("content") -> part.optString("content")
                                else -> ""
                            }
                        if (text.isNotBlank()) {
                            if (isNotEmpty()) append(' ')
                            append(text.trim())
                        }
                    }
                }.ifBlank { null }
            }
            else -> null
        }
    }

    private fun normalizeTranscript(raw: String): String {
        return SpeechTurnSanitizer.sanitizeRecognizedSpeech(raw)
    }
}
