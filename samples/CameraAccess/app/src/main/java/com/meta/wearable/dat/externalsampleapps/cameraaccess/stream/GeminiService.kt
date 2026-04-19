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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class GeminiService {

    companion object {
        private const val TAG = "CameraAccess:GeminiService"
        private const val API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"

        private const val SYSTEM_PROMPT = """You are Hank, an expert automotive diagnostic assistant integrated into smart glasses worn by mechanics and car owners. You analyze images of car components, dashboards, engine bays, and vehicle issues in real-time.

Your job:
1. IDENTIFY what the camera is looking at (engine part, dashboard warning light, fluid leak, tire condition, wiring, etc.)
2. DIAGNOSE the problem if one is visible
3. PROVIDE a clear, actionable fix in simple language

Rules:
- Keep responses SHORT (2-3 sentences max) — they will be spoken aloud through the glasses
- Use plain mechanic language, not textbook jargon
- If you see a dashboard warning light, identify it and explain what to do
- If you see a fluid leak, identify the fluid color/location and the likely source
- If you see wear/damage, estimate severity (minor/moderate/critical)
- If you can't identify a problem, say so honestly and suggest what to check next
- Always prioritize SAFETY — if something looks dangerous, say so immediately"""
    }

    private val apiKey: String = BuildConfig.GEMINI_API_KEY

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    suspend fun analyzeFrame(
        bitmap: Bitmap,
        prompt: String = "What do you see? Identify any car problems and tell me the fix."
    ): String {
        if (apiKey.isBlank()) {
            return "Gemini API key not configured. Add gemini_api_key to local.properties."
        }

        return withContext(Dispatchers.IO) {
            try {
                val base64Image = bitmapToBase64(bitmap)

                val requestBody = JSONObject().apply {
                    put("system_instruction", JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", SYSTEM_PROMPT) })
                        })
                    })
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("inline_data", JSONObject().apply {
                                        put("mime_type", "image/jpeg")
                                        put("data", base64Image)
                                    })
                                })
                                put(JSONObject().apply { put("text", prompt) })
                            })
                        })
                    })
                    put("generationConfig", JSONObject().apply {
                        put("temperature", 0.4)
                        put("maxOutputTokens", 150)
                    })
                }.toString()

                val request = Request.Builder()
                    .url("$API_URL?key=$apiKey")
                    .post(requestBody.toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext "No response from Gemini."

                if (!response.isSuccessful) {
                    Log.e(TAG, "Gemini API error ${response.code}: $body")
                    return@withContext "Error: Gemini API returned ${response.code}"
                }

                val json = JSONObject(body)
                val candidates = json.optJSONArray("candidates")
                val text = candidates
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text")

                text ?: "No response from Gemini."
            } catch (e: Exception) {
                Log.e(TAG, "Gemini analysis failed", e)
                "Error: ${e.message}"
            }
        }
    }
}
