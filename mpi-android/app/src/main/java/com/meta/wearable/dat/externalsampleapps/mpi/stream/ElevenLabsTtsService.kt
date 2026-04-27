package com.meta.wearable.dat.externalsampleapps.mpi.stream

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.util.Log
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class ElevenLabsTtsService(
    private val context: Context,
    private val voiceId: String,
    private val apiKey: String,
    private val onSpeakingChanged: (Boolean) -> Unit,
    private val voiceSpeedProvider: () -> Float = { 1.0f },
) {
    companion object {
        private const val TAG = "HankDaisy:ElevenLabs"
        private const val MODEL_ID = "eleven_flash_v2_5"
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
    private var activeRunId = 0L
    private var cancelledRunId = 0L

    val isConfigured: Boolean
        get() = apiKey.isNotBlank() && voiceId.isNotBlank()

    fun speak(sentences: List<String>, onAllFailed: () -> Unit = {}): Boolean {
        if (!isConfigured) {
            Log.w(TAG, "Not configured — apiKey blank=${apiKey.isBlank()} voiceId blank=${voiceId.isBlank()}")
            return false
        }
        if (sentences.isEmpty()) return true
        synchronized(queue) {
            queue.clear()
            queue.addAll(sentences)
        }
        startConsumer(onAllFailed)
        return true
    }

    fun stop() {
        val cancelledRun =
            synchronized(this) {
                activeRunId.also { runId ->
                    if (runId != 0L) cancelledRunId = runId
                }
            }
        if (cancelledRun != 0L) {
            Log.d(TAG, "Stopping run $cancelledRun")
        }
        synchronized(queue) { queue.clear() }
        try {
            currentPlayer?.setVolume(0f, 0f)
        } catch (_: Exception) {}
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

    private fun startConsumer(onAllFailed: () -> Unit) {
        val existing = consumerJob
        if (existing != null && existing.isActive) return
        val runId =
            synchronized(this) {
                activeRunId += 1
                activeRunId
            }
        onSpeakingChanged(true)
        consumerJob =
            scope.launch {
                var anyPlayed = false
                try {
                    coroutineScope {
                        var nextText: String? =
                            synchronized(queue) { if (queue.isEmpty()) null else queue.removeFirst() }
                        var prefetched: Deferred<ByteArray?>? =
                            nextText?.let { async { fetchAudio(it) } }

                        while (isActive && nextText != null && prefetched != null) {
                            val currentText = nextText
                            val currentFetch = prefetched

                            nextText =
                                synchronized(queue) {
                                    if (queue.isEmpty()) null else queue.removeFirst()
                                }
                            prefetched = nextText?.let { queued -> async { fetchAudio(queued) } }

                            val bytes = currentFetch.await()
                            if (bytes == null || bytes.isEmpty()) {
                                Log.w(TAG, "No audio bytes for sentence: ${currentText.take(80)}")
                                continue
                            }

                            val file = writeTempFile(bytes)
                            try {
                                playAndAwait(file)
                                anyPlayed = true
                            } finally {
                                try {
                                    file.delete()
                                } catch (_: Exception) {}
                            }
                        }
                    }
                } catch (ce: kotlinx.coroutines.CancellationException) {
                    throw ce
                } catch (e: Exception) {
                    Log.e(TAG, "Sentence player crashed", e)
                } finally {
                    onSpeakingChanged(false)
                    consumerJob = null
                    val shouldFallback =
                        synchronized(this@ElevenLabsTtsService) {
                            val cancelled = cancelledRunId == runId
                            if (cancelled) cancelledRunId = 0L
                            if (activeRunId == runId) activeRunId = 0L
                            !cancelled
                        }
                    if (!anyPlayed && shouldFallback) {
                        Log.w(TAG, "Queue drained with nothing played — invoking fallback")
                        try {
                            onAllFailed()
                        } catch (e: Exception) {
                            Log.w(TAG, "onAllFailed callback threw", e)
                        }
                    } else if (!anyPlayed) {
                        Log.d(TAG, "Suppressing fallback for cancelled run $runId")
                    }
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
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            playbackParams = playbackParams.setSpeed(voiceSpeedProvider().coerceIn(0.8f, 1.4f))
                        }
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
