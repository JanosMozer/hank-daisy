/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.stream

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.meta.wearable.dat.externalsampleapps.cameraaccess.BuildConfig
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Vision LLM client. Talks to OpenRouter (OpenAI-compatible chat/completions
 * endpoint) so we can pick any vision-capable model without re-coding the
 * transport. Class name kept as GeminiService for blast-radius reasons.
 */
class GeminiService {

    companion object {
        private const val TAG = "CameraAccess:GeminiService"
        private const val API_URL = "https://openrouter.ai/api/v1/chat/completions"

        private const val MODEL = "google/gemini-3.1-flash-lite-preview"

        private const val SYSTEM_PROMPT = """You are Hank — a friendly, sharp mechanic talking to someone wearing smart glasses. You see exactly what they see, in real time. You're having a CONVERSATION, not delivering a manual.

Voice rules (this is critical — your replies are spoken aloud through their glasses):
- Keep replies digestible. Usually 2–5 sentences. Hard ceiling: 7 sentences.
- One step at a time. After each instruction, STOP and wait for them to do it. Do NOT batch multiple steps.
- After giving a step, end with something like "let me know when you're there", "tell me when it's off", or "say go when you're ready".
- If you can't see clearly, say so plainly and ask them to move closer / change angle / shine a light. Don't speculate.
- If they tell you they've done something, look at the new view and react to what's actually different.
- Talk like a buddy in the shop — warm, direct, a little casual. No bullet points, no markdown, no numbered lists. Just talk.
- If something is dangerous, lead with the warning in one short sentence.
- If the camera moves and you see something new mid-conversation, acknowledge it ("oh wait, I can see now...").
- If you genuinely don't understand the question, ask one clarifying question. Don't guess.

You're being interrupted often — that's normal. Pick up the thread."""
    }

    data class Turn(val role: String, val text: String)

    private val apiKey: String = BuildConfig.OPENROUTER_API_KEY

    private val client =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

    private fun bitmapToDataUrl(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        val b64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
        return "data:image/jpeg;base64,$b64"
    }

    suspend fun analyzeFrame(
        bitmap: Bitmap,
        userQuestion: String =
            "What do you see? Identify any problems and walk me through fixing them.",
        history: List<Turn> = emptyList(),
    ): String {
        if (apiKey.isBlank()) {
            return "OpenRouter API key not configured. Add openrouter_api_key to local.properties."
        }

        return withContext(Dispatchers.IO) {
            try {
                val imageDataUrl = bitmapToDataUrl(bitmap)

                val messages =
                    JSONArray().apply {
                        put(
                            JSONObject().apply {
                                put("role", "system")
                                put("content", SYSTEM_PROMPT)
                            },
                        )
                        // Prior turns — text only. Old frames are stale; always attach
                        // the live frame to the *current* user turn below.
                        history.forEach { turn ->
                            put(
                                JSONObject().apply {
                                    put("role", turn.role)
                                    put("content", turn.text)
                                },
                            )
                        }
                        put(
                            JSONObject().apply {
                                put("role", "user")
                                put(
                                    "content",
                                    JSONArray().apply {
                                        put(
                                            JSONObject().apply {
                                                put("type", "text")
                                                put("text", userQuestion)
                                            },
                                        )
                                        put(
                                            JSONObject().apply {
                                                put("type", "image_url")
                                                put(
                                                    "image_url",
                                                    JSONObject().apply { put("url", imageDataUrl) },
                                                )
                                            },
                                        )
                                    },
                                )
                            },
                        )
                    }

                val requestBody =
                    JSONObject()
                        .apply {
                            put("model", MODEL)
                            put("messages", messages)
                            put("max_tokens", 700)
                            put("temperature", 0.6)
                        }
                        .toString()

                val request =
                    Request.Builder()
                        .url(API_URL)
                        .addHeader("Authorization", "Bearer $apiKey")
                        .addHeader("Content-Type", "application/json")
                        .addHeader("HTTP-Referer", "https://github.com/JanosMozer/hank-daisy")
                        .addHeader("X-Title", "Hank")
                        .post(requestBody.toRequestBody("application/json".toMediaType()))
                        .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext "No response from Hank."

                if (!response.isSuccessful) {
                    Log.e(TAG, "OpenRouter API error ${response.code}: $body")
                    return@withContext when (response.code) {
                        401, 403 ->
                            "Hank's API key is invalid or unauthorized. Check openrouter_api_key in local.properties."
                        429 -> "I'm getting too many requests right now. Try again in a moment."
                        in 500..599 -> "Hank's brain is offline. Try again in a moment."
                        else -> "Error talking to Hank (HTTP ${response.code})."
                    }
                }

                val json = JSONObject(body)
                val text =
                    json.optJSONArray("choices")
                        ?.optJSONObject(0)
                        ?.optJSONObject("message")
                        ?.optString("content")

                if (text.isNullOrBlank()) "No response from Hank." else text
            } catch (e: Exception) {
                Log.e(TAG, "OpenRouter call failed", e)
                "Couldn't reach Hank: ${e.message}"
            }
        }
    }
}
