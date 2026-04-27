package com.meta.wearable.dat.externalsampleapps.mpi.stream

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.meta.wearable.dat.externalsampleapps.mpi.BuildConfig
import com.meta.wearable.dat.externalsampleapps.mpi.session.AppConfigStore
import com.meta.wearable.dat.externalsampleapps.mpi.session.DomainMode
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

class GeminiService(
    private val context: Context? = null,
) {

    companion object {
        private const val TAG = "HankDaisy:GeminiService"
        private const val API_URL = "https://openrouter.ai/api/v1/chat/completions"
        private const val MODEL = "google/gemini-3.1-flash-lite-preview"
    }

    data class Turn(val role: String, val text: String)

    private val apiKey: String = BuildConfig.OPENROUTER_API_KEY

    private val client =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

    private fun currentDomainMode(): DomainMode =
        context?.let { AppConfigStore.current(it).general.domainMode } ?: DomainMode.CAR_ONLY

    private fun bitmapToDataUrl(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        val b64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
        return "data:image/jpeg;base64,$b64"
    }

    suspend fun analyzeFrame(
        bitmap: Bitmap?,
        userQuestion: String =
            "What do you see? Identify any problems and walk me through the next step.",
        history: List<Turn> = emptyList(),
        systemPromptOverride: String? = null,
    ): String {
        if (apiKey.isBlank()) {
            return "OpenRouter API key not configured. Add openrouter_api_key to local.properties."
        }

        return withContext(Dispatchers.IO) {
            try {
                val systemPrompt =
                    systemPromptOverride ?: HankPromptFactory.systemPrompt(currentDomainMode())
                val imageDataUrl = bitmap?.let { bitmapToDataUrl(it) }

                val messages =
                    JSONArray().apply {
                        put(
                            JSONObject().apply {
                                put("role", "system")
                                put("content", systemPrompt)
                            },
                        )
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
                        .addHeader("X-Title", "Capture & Report")
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
