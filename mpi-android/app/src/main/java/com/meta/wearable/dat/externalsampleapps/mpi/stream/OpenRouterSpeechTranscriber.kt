/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.mpi.stream

import android.util.Base64
import android.util.Log
import com.meta.wearable.dat.externalsampleapps.mpi.BuildConfig
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
class OpenRouterSpeechTranscriber {

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
        // Preferred candidate: OpenAI's current diarization-capable STT model.
        // If OpenRouter/provider routing rejects it on chat/completions, we
        // fall back to GPT Audio, which OpenRouter documents clearly for audio
        // input on the chat endpoint.
        private val MODELS =
            listOf(
                "openai/gpt-4o-transcribe-diarize",
                "openai/gpt-audio",
            )

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
                modelId = MODELS.first(),
                latencyMs = 0,
                message = "OpenRouter API key not configured or audio clip empty",
            )
        }

        val startedAt = System.currentTimeMillis()
        return try {
            val audioData = Base64.encodeToString(wavBytes, Base64.NO_WRAP)
            var lastError = "Unknown OpenRouter STT failure"
            var lastModelId = MODELS.first()

            for (modelId in MODELS) {
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
                modelId = MODELS.first(),
                latencyMs = System.currentTimeMillis() - startedAt,
                message = e.message ?: "Unexpected OpenRouter STT failure",
            )
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
        var text = raw.trim()
        if (text.isEmpty()) return text

        text =
            text.removePrefix("\"")
                .removeSuffix("\"")
                .removePrefix("'")
                .removeSuffix("'")
                .trim()

        val labelPatterns =
            listOf(
                Regex("^transcript\\s*:\\s*", RegexOption.IGNORE_CASE),
                Regex("^transcription\\s*:\\s*", RegexOption.IGNORE_CASE),
                Regex("^audio\\s*:\\s*", RegexOption.IGNORE_CASE),
                Regex("^recording\\s*:\\s*", RegexOption.IGNORE_CASE),
                Regex("^speaker\\s*:\\s*", RegexOption.IGNORE_CASE),
                Regex("^primary speaker\\s*:\\s*", RegexOption.IGNORE_CASE),
                Regex("^foreground speaker\\s*:\\s*", RegexOption.IGNORE_CASE),
                Regex("^user\\s*:\\s*", RegexOption.IGNORE_CASE),
            )
        labelPatterns.forEach { pattern ->
            text = pattern.replace(text, "").trim()
        }

        val metaPrefixes =
            listOf(
                Regex("^the audio (file |clip )?(says|is)\\s*:?\\s*", RegexOption.IGNORE_CASE),
                Regex("^the recording (says|is)\\s*:?\\s*", RegexOption.IGNORE_CASE),
                Regex("^the transcription (says|is)\\s*:?\\s*", RegexOption.IGNORE_CASE),
                Regex("^the transcript (says|is)\\s*:?\\s*", RegexOption.IGNORE_CASE),
                Regex("^the (primary|foreground) speaker says\\s*:?\\s*", RegexOption.IGNORE_CASE),
                Regex("^i hear\\s*:?\\s*", RegexOption.IGNORE_CASE),
                Regex("^i heard\\s*:?\\s*", RegexOption.IGNORE_CASE),
            )
        metaPrefixes.forEach { pattern ->
            text = pattern.replace(text, "").trim()
        }

        val quoted =
            Regex("[\"“](.+?)[\"”]").find(text)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        if (quoted.isNotBlank()) {
            text = quoted
        }

        return text.trim().trim(',', '.', ';', ':', '-', ' ').trim()
    }
}
