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
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.meta.wearable.dat.externalsampleapps.cameraaccess.BuildConfig

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

    private val model: GenerativeModel? = run {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank()) {
            Log.w(TAG, "Gemini API key not set in local.properties")
            null
        } else {
            GenerativeModel(
                modelName = "gemini-2.0-flash",
                apiKey = apiKey,
                generationConfig = generationConfig {
                    temperature = 0.4f
                    maxOutputTokens = 150
                },
                systemInstruction = content { text(SYSTEM_PROMPT) },
            )
        }
    }

    suspend fun analyzeFrame(
        bitmap: Bitmap,
        prompt: String = "What do you see? Identify any car problems and tell me the fix."
    ): String {
        if (model == null) {
            return "Gemini API key not configured. Add gemini_api_key to local.properties."
        }

        return try {
            val response = model.generateContent(
                content {
                    image(bitmap)
                    text(prompt)
                }
            )
            response.text ?: "No response from Gemini."
        } catch (e: Exception) {
            Log.e(TAG, "Gemini analysis failed", e)
            "Error: ${e.message}"
        }
    }
}
