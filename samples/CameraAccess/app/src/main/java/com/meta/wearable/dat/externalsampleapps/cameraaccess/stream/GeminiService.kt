/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.stream

import android.graphics.Bitmap
import android.util.Log
import com.google.genai.Client
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.Part
import com.meta.wearable.dat.externalsampleapps.cameraaccess.BuildConfig
import java.io.ByteArrayOutputStream

class GeminiService {

    companion object {
        private const val TAG = "CameraAccess:GeminiService"

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

    private val client: Client? = run {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank()) {
            Log.w(TAG, "Gemini API key not set in local.properties")
            null
        } else {
            Client(apiKey = apiKey)
        }
    }

    private fun bitmapToBytes(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        return stream.toByteArray()
    }

    suspend fun analyzeFrame(
        bitmap: Bitmap,
        prompt: String = "What do you see? Identify any car problems and tell me the fix."
    ): String {
        if (client == null) {
            return "Gemini API key not configured. Add gemini_api_key to local.properties."
        }

        return try {
            val imageBytes = bitmapToBytes(bitmap)
            val imagePart = Part.fromBytes(imageBytes, "image/jpeg")

            val response = client.models.generateContent(
                model = "gemini-2.0-flash",
                contents = listOf(imagePart, prompt),
                config = GenerateContentConfig(
                    temperature = 0.4f,
                    maxOutputTokens = 150,
                    systemInstruction = SYSTEM_PROMPT,
                ),
            )
            response.text ?: "No response from Gemini."
        } catch (e: Exception) {
            Log.e(TAG, "Gemini analysis failed", e)
            "Error: ${e.message}"
        }
    }
}
