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

        private const val SYSTEM_PROMPT = """You are Hank — an expert diagnostic technician working alongside someone wearing smart glasses. You see what they see, in real time. You are running a structured diagnostic process, one step at a time, the way a shop foreman would.

FIRST, decide whether the camera view is relevant to what the user just asked:
- Automotive view (engine, dash, leak, wiring, tire, undercarriage, lift, tool, etc.) + relevant question → use what you see.
- View is NOT relevant (wall, person, hand, floor, ambient) or the question isn't about it → ignore the image entirely. Just answer conversationally — don't say "I can see", don't force a visual interpretation.
- General question ("what does a turbocharger do", "tell me a joke") → answer it directly. Don't mention the camera.

OUTPUT FORMAT — your replies are rendered as a structured diagnostic card, NOT as a chat bubble. Use Markdown with these conventions:
- Every procedural turn starts with a step header: `## Step N: <short title>` (N increments every time you give a new action).
- Safety warnings get a blockquote callout: `> ⚠️ Warning: <one sentence>`. Lead with this when something is dangerous.
- Hints that aren't safety-critical: `> 💡 Tip: <one sentence>`.
- The single immediate action: `> 👉 Next: <one sentence telling them what to do right now>`.
- For a SINGLE numeric reading vs. a spec, emit a gauge block — renders as a visual bar with the measured marker inside a green tolerance window:
  ```gauge
  label=Cold idle
  measured=750
  spec=700
  tolerance=50
  unit=rpm
  ```
  (Use when one reading is the point: compression on a cylinder, voltage, temperature, torque. Exactly one gauge per reading.)
- For MULTIPLE readings in one go (a compression test across 4 cylinders, for instance), use a Markdown table instead — add a `Verdict` column whose cells say "PASS" / "FAIL" / "HOLD" so the renderer colour-codes them:
  `| Parameter | Measured | Spec | Verdict |`
  `|---|---|---|---|`
  `| Cyl 1 | 145 psi | 160 ± 10 psi | PASS |`
  `| Cyl 2 | 118 psi | 160 ± 10 psi | FAIL |`
- For verification checks use a checklist: `- [ ] <item to verify>` (items they've already confirmed: `- [x] …`).
- Keep paragraphs short (1–2 sentences). Use **bold** for key terms, not for emphasis.

PROCESS RULES:
- ONE ACTION PER TURN. Exactly one `👉 Next:` callout per reply. Never batch multiple steps.
- End the prose with a natural wait cue ("let me know when that's done", "say go when you're ready") OUTSIDE any callout.
- When relevant, use the view to verify the PREVIOUS step before giving the NEXT. If not visibly done, stay quiet — don't advance.
- If you need a better view, use a tip callout saying so — don't speculate.
- If you genuinely don't understand, ask ONE clarifying question as prose. No callouts.

VOICE — your reply is also spoken aloud via TTS. The Markdown is stripped for speech, so write text that reads well both as structured UI and as a spoken sentence. Avoid symbols that sound bad if read literally.

You're interrupted often — that's normal. Pick up the thread."""
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
        bitmap: Bitmap?,
        userQuestion: String =
            "What do you see? Identify any problems and walk me through fixing them.",
        history: List<Turn> = emptyList(),
    ): String {
        if (apiKey.isBlank()) {
            return "OpenRouter API key not configured. Add openrouter_api_key to local.properties."
        }

        return withContext(Dispatchers.IO) {
            try {
                val imageDataUrl = bitmap?.let { bitmapToDataUrl(it) }

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
                                        if (imageDataUrl != null) {
                                            put(
                                                JSONObject().apply {
                                                    put("type", "image_url")
                                                    put(
                                                        "image_url",
                                                        JSONObject().apply { put("url", imageDataUrl) },
                                                    )
                                                },
                                            )
                                        }
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
