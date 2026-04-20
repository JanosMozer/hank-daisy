/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.stream

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * High-quality TTS via ElevenLabs. Plays each sentence as soon as its audio is
 * fetched, so barge-in only has to interrupt the *current* sentence (~300ms-2s)
 * rather than the full reply.
 *
 * Returns "did we manage to handle this?" from [speak] so the caller can fall
 * back to Android TTS on first-sentence failure (bad key, no network, etc.).
 */
class ElevenLabsTtsService(
    private val context: Context,
    private val voiceId: String,
    private val apiKey: String,
    private val onSpeakingChanged: (Boolean) -> Unit,
) {
    companion object {
        private const val TAG = "CameraAccess:ElevenLabs"
        private const val MODEL_ID = "eleven_turbo_v2_5"
        private const val ENDPOINT = "https://api.elevenlabs.io/v1/text-to-speech"
    }

    private val client =
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val queue = ArrayDeque<String>()
    private var consumerJob: Job? = null
    private var currentPlayer: MediaPlayer? = null

    val isConfigured: Boolean
        get() = apiKey.isNotBlank() && voiceId.isNotBlank()

    /**
     * Returns true if speak was accepted (config valid). Caller should fall
     * back to Android TTS if false. Actual fetch failures inside the queue
     * are logged and skipped per-sentence.
     */
    fun speak(sentences: List<String>): Boolean {
        if (!isConfigured) return false
        if (sentences.isEmpty()) return true
        synchronized(queue) {
            queue.clear()
            queue.addAll(sentences)
        }
        startConsumer()
        return true
    }

    fun stop() {
        synchronized(queue) { queue.clear() }
        consumerJob?.cancel()
        consumerJob = null
        try {
            currentPlayer?.stop()
        } catch (_: Exception) {}
        try {
            currentPlayer?.release()
        } catch (_: Exception) {}
        currentPlayer = null
        onSpeakingChanged(false)
    }

    fun shutdown() {
        stop()
        scope.cancel()
    }

    private fun startConsumer() {
        val existing = consumerJob
        if (existing != null && existing.isActive) return
        onSpeakingChanged(true)
        consumerJob =
            scope.launch {
                try {
                    while (isActive) {
                        val sentence =
                            synchronized(queue) {
                                if (queue.isEmpty()) null else queue.removeFirst()
                            } ?: break
                        val mp3 = fetchAudio(sentence)
                        if (mp3 == null) {
                            Log.w(TAG, "Skipping sentence — fetch failed: ${sentence.take(40)}")
                            continue
                        }
                        val file = writeTempFile(mp3)
                        try {
                            playAndAwait(file)
                        } finally {
                            try {
                                file.delete()
                            } catch (_: Exception) {}
                        }
                    }
                } finally {
                    onSpeakingChanged(false)
                }
            }
    }

    private suspend fun fetchAudio(text: String): ByteArray? =
        withContext(Dispatchers.IO) {
            val body =
                JSONObject()
                    .apply {
                        put("text", text)
                        put("model_id", MODEL_ID)
                        put(
                            "voice_settings",
                            JSONObject().apply {
                                put("stability", 0.5)
                                put("similarity_boost", 0.75)
                                put("style", 0.0)
                                put("use_speaker_boost", true)
                            },
                        )
                    }
                    .toString()
            val request =
                Request.Builder()
                    .url("$ENDPOINT/$voiceId")
                    .addHeader("xi-api-key", apiKey)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "audio/mpeg")
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e(
                            TAG,
                            "ElevenLabs error ${response.code}: ${response.body?.string()?.take(200)}",
                        )
                        return@use null
                    }
                    response.body?.bytes()
                }
            } catch (e: Exception) {
                Log.e(TAG, "ElevenLabs fetch failed", e)
                null
            }
        }

    private fun writeTempFile(bytes: ByteArray): File {
        val file = File.createTempFile("hank_tts_", ".mp3", context.cacheDir)
        file.writeBytes(bytes)
        return file
    }

    private suspend fun playAndAwait(file: File): Unit =
        suspendCancellableCoroutine { cont ->
            var player: MediaPlayer? = null
            try {
                player =
                    MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build(),
                        )
                        setDataSource(file.absolutePath)
                        setOnCompletionListener {
                            if (cont.isActive) cont.resumeWith(Result.success(Unit))
                            try {
                                release()
                            } catch (_: Exception) {}
                            if (currentPlayer === this) currentPlayer = null
                        }
                        setOnErrorListener { _, what, extra ->
                            Log.w(TAG, "MediaPlayer error: what=$what extra=$extra")
                            if (cont.isActive) cont.resumeWith(Result.success(Unit))
                            try {
                                release()
                            } catch (_: Exception) {}
                            if (currentPlayer === this) currentPlayer = null
                            true
                        }
                        prepare()
                        start()
                    }
                currentPlayer = player
            } catch (e: Exception) {
                Log.e(TAG, "MediaPlayer setup failed", e)
                try {
                    player?.release()
                } catch (_: Exception) {}
                if (cont.isActive) cont.resumeWith(Result.success(Unit))
                return@suspendCancellableCoroutine
            }
            cont.invokeOnCancellation {
                try {
                    player.stop()
                } catch (_: Exception) {}
                try {
                    player.release()
                } catch (_: Exception) {}
                if (currentPlayer === player) currentPlayer = null
            }
        }
}
