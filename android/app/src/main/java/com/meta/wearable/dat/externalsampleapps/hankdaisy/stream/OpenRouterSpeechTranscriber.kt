/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.hankdaisy.stream

import android.util.Base64
import android.util.Log
import com.meta.wearable.dat.externalsampleapps.hankdaisy.BuildConfig
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

    companion object {
        private const val TAG = "HankDaisy:OpenRouterSTT"
        private const val API_URL = "https://openrouter.ai/api/v1/chat/completions"
        private const val MODEL = "openai/gpt-audio"

        private const val TRANSCRIPTION_PROMPT =
            """
            Transcribe only the primary nearby speaker who is addressing the assistant.
            Ignore background music, radio, TV, shop noise, tool noise, and side conversations.
            If multiple people speak, keep only the dominant foreground speaker.
            If there is no clear foreground utterance for the assistant, return exactly <no-speech>.
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

    fun transcribe(wavBytes: ByteArray): String? {
        if (!isConfigured || wavBytes.isEmpty()) return null

        return try {
            val audioData = Base64.encodeToString(wavBytes, Base64.NO_WRAP)
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
                        put("model", MODEL)
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
                if (!response.isSuccessful) {
                    Log.e(TAG, "OpenRouter STT error ${response.code}: ${body.take(300)}")
                    return null
                }
                val text = extractText(body)?.trim().orEmpty()
                when {
                    text.isBlank() -> null
                    text.equals("<no-speech>", ignoreCase = true) -> null
                    text.equals("no speech", ignoreCase = true) -> null
                    else -> text
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "OpenRouter STT request failed", e)
            null
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
}
