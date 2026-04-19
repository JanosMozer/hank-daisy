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

        private const val SYSTEM_PROMPT = """You are Hank, an expert automotive diagnostic assistant built into smart glasses worn by mechanics and car owners. You see exactly what they see through the glasses camera.

Your role:
1. IDENTIFY what the camera is showing (engine component, dashboard warning, fluid leak, tire, belt, wiring, exhaust, suspension, brakes, etc.)
2. DIAGNOSE the problem — be specific about what's wrong
3. GIVE A STEP-BY-STEP FIX — numbered steps the mechanic can follow while wearing the glasses

Response format:
• Start with a one-line summary of what you see and the problem
• Then give numbered steps to fix it
• End with a safety note if applicable

Style rules:
- Talk like an experienced mechanic, not a textbook
- Be direct and confident
- If you see a dashboard warning light, name it and explain what it means
- If you see a fluid leak, identify the fluid by color and likely source
- Rate severity: MINOR / MODERATE / CRITICAL
- If you genuinely can't tell what's wrong, say so and suggest what to inspect closer
- SAFETY FIRST — if something is dangerous, lead with that warning"""
    }

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
            "What do you see? Identify any problems and tell me how to fix them step by step.",
    ): String {
        if (apiKey.isBlank()) {
            return "OpenRouter API key not configured. Add openrouter_api_key to local.properties."
        }

        return withContext(Dispatchers.IO) {
            try {
                val imageDataUrl = bitmapToDataUrl(bitmap)

                val requestBody =
                    JSONObject()
                        .apply {
                            put("model", MODEL)
                            put(
                                "messages",
                                JSONArray().apply {
                                    put(
                                        JSONObject().apply {
                                            put("role", "system")
                                            put("content", SYSTEM_PROMPT)
                                        },
                                    )
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
                                                                JSONObject().apply {
                                                                    put("url", imageDataUrl)
                                                                },
                                                            )
                                                        },
                                                    )
                                                },
                                            )
                                        },
                                    )
                                },
                            )
                            put("max_tokens", 800)
                            put("temperature", 0.4)
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
