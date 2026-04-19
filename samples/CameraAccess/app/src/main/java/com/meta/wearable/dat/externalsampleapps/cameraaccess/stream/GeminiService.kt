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
import com.meta.wearable.dat.externalsampleapps.cameraaccess.BuildConfig

class GeminiService {

    companion object {
        private const val TAG = "CameraAccess:GeminiService"
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
            )
        }
    }

    suspend fun analyzeFrame(
        bitmap: Bitmap,
        prompt: String = "Describe what you see in this image concisely in 1-2 sentences."
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
