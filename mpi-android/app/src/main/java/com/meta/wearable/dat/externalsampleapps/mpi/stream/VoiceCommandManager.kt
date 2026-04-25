/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.mpi.stream

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.meta.wearable.dat.externalsampleapps.mpi.session.SpeechRecognitionRoute
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Always-on voice command manager with switchable speech-recognition routes.
 *
 * The default route uses Android's SpeechRecognizer. The alternative route
 * records short utterances locally and sends them to OpenRouter/OpenAI audio
 * for transcription with instructions to focus on the foreground speaker.
 */
class VoiceCommandManager(private val context: Context) {

    companion object {
        private const val TAG = "HankDaisy:VoiceCmd"
        /** Gap between recognizer cycles — kept very short so the user almost
         * never talks into a dead window. */
        private const val RESTART_DELAY_MS = 150L
        private const val WATCHDOG_INTERVAL_MS = 10_000L
        /** If no recognizer callback for this long while we think we're
         * listening, the recognizer is presumed dead and force-recreated. */
        private const val MAX_SILENCE_MS = 25_000L
        /** Min recognised text length before we treat it as a real question.
         * Low enough that short replies like "yes" / "no" / "now" count;
         * filters out one-character noise fragments from the recognizer. */
        private const val MIN_QUERY_LENGTH = 2
        /** Silence before the recognizer decides the user finished speaking.
         * Tuned down from 4000/2200 to 1500/900: natural conversational
         * pauses are under 1 second, so 4s was adding a flat ~2.5s of dead
         * air to every turn before we even kicked off the LLM call. At 1.5s
         * mid-sentence thinking pauses ("uh... let me think...") still hold
         * the turn, but normal turn-ends dispatch in ~1s instead of ~4s.
         * MAYBE value is the faster of the two — it's what fires when the
         * recognizer is already confident you're done. */
        private const val END_SILENCE_MS = 1_500L
        private const val MAYBE_END_SILENCE_MS = 900L
        private const val AUDIO_SAMPLE_RATE = 16_000
        private const val AUDIO_MAX_WAIT_MS = 15_000L
        private const val AUDIO_MIN_SPEECH_MS = 220L
        private const val AUDIO_START_HOLD_MS = 120L
        private const val AUDIO_END_HOLD_MS = 900L
        private const val AUDIO_MAX_UTTERANCE_MS = 12_000L
        private const val AUDIO_PREROLL_MS = 250L
        private const val AUDIO_MIN_START_THRESHOLD = 900.0
        private const val AUDIO_MIN_END_THRESHOLD = 450.0

        // Wake word variations kept for prefix-stripping only — they're no
        // longer required to trigger Hank, just filtered out if the user
        // happens to say them out of habit.
        private val WAKE_WORDS = listOf("hey hank", "hank", "hey hunk", "a hank", "hey frank")
    }

    sealed interface VoiceState {
        /** Listening passively for wake word */
        data object Passive : VoiceState
        /** Wake word detected, listening for the follow-up question */
        data object Listening : VoiceState
        /** Question captured, ready for analysis */
        data class QuestionReady(val text: String) : VoiceState
        /** Error occurred */
        data class Error(val message: String) : VoiceState
        /** Not listening at all */
        data object Off : VoiceState
    }

    private val _state = MutableStateFlow<VoiceState>(VoiceState.Off)
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    private var recognizer: SpeechRecognizer? = null
    private var audioRecord: AudioRecord? = null
    private var captureThread: Thread? = null
    private val handler = Handler(Looper.getMainLooper())
    private val openRouterTranscriber = OpenRouterSpeechTranscriber()
    private var isRunning = false
    private var isFollowUp = false  // true when we detected wake word, now waiting for question
    private var isMuted = false     // pause listening while Hank himself is talking
    private var recognitionCycleStartedAt: Long = 0L
    @Volatile private var audioCaptureActive = false
    @Volatile private var lastCallbackAt: Long = 0L

    /** Periodically verify the recognizer is actually alive. Google's
     * SpeechRecognizer is known to silently die after long runs or certain
     * error sequences; without this the app would "just stop listening" with
     * no sign why. */
    private val watchdog =
        object : Runnable {
            override fun run() {
                if (!isRunning) return
                if (!isMuted) {
                    val silent = System.currentTimeMillis() - lastCallbackAt
                    if (silent > MAX_SILENCE_MS) {
                        Log.w(TAG, "Watchdog: no callbacks for ${silent}ms — recreating recognizer")
                        lastCallbackAt = System.currentTimeMillis()
                        destroyRecognizer()
                        handler.postDelayed({ startRecognizer() }, 200)
                    }
                }
                handler.postDelayed(this, WATCHDOG_INTERVAL_MS)
            }
        }

    /**
     * Start always-on passive listening for "Hey Hank".
     * Automatically restarts after timeouts/results.
     */
    fun startContinuousListening() {
        if (isRunning) return
        when (currentRoute()) {
            SpeechRecognitionRoute.ANDROID ->
                if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                    _state.value = VoiceState.Error("Speech recognition not available")
                    return
                }
            SpeechRecognitionRoute.OPENROUTER ->
                if (!openRouterTranscriber.isConfigured) {
                    _state.value =
                        VoiceState.Error("OpenRouter speech route requires openrouter_api_key")
                    return
                }
        }

        isRunning = true
        isFollowUp = false
        isMuted = false
        lastCallbackAt = System.currentTimeMillis()
        handler.postDelayed(watchdog, WATCHDOG_INTERVAL_MS)
        startRecognizer()
    }

    /** Stop all listening. */
    fun stopContinuousListening() {
        isRunning = false
        isFollowUp = false
        handler.removeCallbacksAndMessages(null)
        destroyRecognizer()
        _state.value = VoiceState.Off
    }

    /** Manually trigger question listening (same as tapping "Ask Hank"). */
    fun startManualListen() {
        if (_state.value is VoiceState.Listening) return
        // Safety: if the recognizer loop had somehow stopped, resurrect it.
        if (!isRunning) {
            isRunning = true
            lastCallbackAt = System.currentTimeMillis()
            handler.removeCallbacks(watchdog)
            handler.postDelayed(watchdog, WATCHDOG_INTERVAL_MS)
        }
        isMuted = false
        isFollowUp = true
        _state.value = VoiceState.Listening
        restartRecognizer()
    }

    /**
     * Hank just finished speaking — auto-listen for a follow-up turn without
     * requiring "Hey Hank" again. Lets the conversation flow naturally.
     */
    fun startConversationFollowUp() {
        // Safety: if the recognizer loop had somehow stopped, resurrect it.
        if (!isRunning) {
            isRunning = true
            lastCallbackAt = System.currentTimeMillis()
            handler.removeCallbacks(watchdog)
            handler.postDelayed(watchdog, WATCHDOG_INTERVAL_MS)
        }
        isMuted = false
        isFollowUp = true
        _state.value = VoiceState.Listening
        restartRecognizer()
    }

    /** Reset state after question has been processed. */
    fun onQuestionHandled() {
        _state.value = VoiceState.Passive
        isFollowUp = false
        if (isRunning) {
            scheduleRestart()
        }
    }

    /**
     * Pause/resume the recognizer while TTS is speaking through the glasses, so
     * Hank's own reply doesn't re-trigger the wake word (audio feedback loop).
     */
    fun setMuted(muted: Boolean) {
        if (muted == isMuted) return
        isMuted = muted
        if (muted) {
            handler.removeCallbacksAndMessages(null)
            destroyRecognizer()
        } else if (isRunning || isFollowUp) {
            scheduleRestart()
        }
    }

    fun shutdown() {
        stopContinuousListening()
    }

    // ---- Internal ----

    private fun startRecognizer() {
        if (!isRunning && !isFollowUp) return
        if (isMuted) return
        when (currentRoute()) {
            SpeechRecognitionRoute.ANDROID -> startAndroidRecognizer()
            SpeechRecognitionRoute.OPENROUTER -> startOpenRouterRecognizer()
        }
    }

    private fun startAndroidRecognizer() {
        handler.post {
            destroyRecognizer()

            if (!isFollowUp) {
                _state.value = VoiceState.Passive
            }
            recognitionCycleStartedAt = System.currentTimeMillis()

            recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(createListener())
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                // Same forgiving timeouts whether this cycle was kicked off
                // fresh, after TTS, or as a restart — we want a uniformly
                // steady conversational feel with no cold starts.
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, END_SILENCE_MS)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, MAYBE_END_SILENCE_MS)
            }

            try {
                recognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start recognizer", e)
                scheduleRestart()
            }
        }
    }

    @SuppressLint("MissingPermission") // RECORD_AUDIO is enforced at request time in MainActivity
    private fun startOpenRouterRecognizer() {
        handler.post {
            destroyRecognizer()

            if (!isFollowUp) {
                _state.value = VoiceState.Passive
            }
            if (!openRouterTranscriber.isConfigured) {
                _state.value = VoiceState.Error("OpenRouter speech route requires openrouter_api_key")
                return@post
            }

            audioCaptureActive = true
            recognitionCycleStartedAt = System.currentTimeMillis()
            lastCallbackAt = recognitionCycleStartedAt
            captureThread =
                thread(name = "hank-openrouter-stt") {
                    val wavBytes = captureUtteranceWav()
                    if (!audioCaptureActive) return@thread

                    if (wavBytes == null) {
                        handler.post {
                            lastCallbackAt = System.currentTimeMillis()
                            if (isFollowUp) {
                                SpeechRecognitionDebugStore.record(
                                    context = context,
                                    backendLabel = "OpenRouter OpenAI",
                                    modelId = "capture",
                                    status = "No speech detected",
                                    transcript = "",
                                    latencyMs = currentLatencyMs(),
                                )
                            }
                            if (isFollowUp) {
                                Log.d(TAG, "OpenRouter follow-up timed out, returning to passive")
                                isFollowUp = false
                            }
                            scheduleRestart()
                        }
                        return@thread
                    }

                    val text = openRouterTranscriber.transcribe(wavBytes)
                    if (!audioCaptureActive) return@thread

                    handler.post {
                        lastCallbackAt = System.currentTimeMillis()
                        when (text.status) {
                            OpenRouterSpeechTranscriber.TranscriptionResult.Status.OK -> {
                                SpeechRecognitionDebugStore.record(
                                    context = context,
                                    backendLabel = "OpenRouter OpenAI",
                                    modelId = text.modelId,
                                    status = "OK",
                                    transcript = text.text.orEmpty(),
                                    latencyMs = text.latencyMs,
                                )
                                handleSpeechResults(listOf(text.text.orEmpty()))
                            }
                            OpenRouterSpeechTranscriber.TranscriptionResult.Status.IGNORED -> {
                                if (isFollowUp) {
                                    SpeechRecognitionDebugStore.record(
                                        context = context,
                                        backendLabel = "OpenRouter OpenAI",
                                        modelId = text.modelId,
                                        status = text.message ?: "No clear foreground speech",
                                        transcript = "",
                                        latencyMs = text.latencyMs,
                                    )
                                }
                                if (isFollowUp) {
                                    isFollowUp = false
                                }
                                scheduleRestart()
                            }
                            OpenRouterSpeechTranscriber.TranscriptionResult.Status.ERROR -> {
                                if (isFollowUp) {
                                    SpeechRecognitionDebugStore.record(
                                        context = context,
                                        backendLabel = "OpenRouter OpenAI",
                                        modelId = text.modelId,
                                        status = text.message ?: "Remote transcription failed",
                                        transcript = "",
                                        latencyMs = text.latencyMs,
                                    )
                                }
                                if (isFollowUp) {
                                    isFollowUp = false
                                }
                                scheduleRestart()
                            }
                        }
                    }
                }
        }
    }

    private fun createListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            lastCallbackAt = System.currentTimeMillis()
            Log.d(TAG, "Ready (mode=${if (isFollowUp) "follow-up" else "passive"})")
        }

        override fun onBeginningOfSpeech() { lastCallbackAt = System.currentTimeMillis() }
        override fun onRmsChanged(rmsdB: Float) { lastCallbackAt = System.currentTimeMillis() }
        override fun onBufferReceived(buffer: ByteArray?) { lastCallbackAt = System.currentTimeMillis() }
        override fun onEndOfSpeech() { lastCallbackAt = System.currentTimeMillis() }

        override fun onError(error: Int) {
            lastCallbackAt = System.currentTimeMillis()
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    if (isFollowUp) {
                        SpeechRecognitionDebugStore.record(
                            context = context,
                            backendLabel = "Android local",
                            modelId = "android.speech.SpeechRecognizer",
                            status = "No speech detected",
                            transcript = "",
                            latencyMs = currentLatencyMs(),
                        )
                    }
                    // Normal timeout — just restart
                    if (isFollowUp) {
                        Log.d(TAG, "Follow-up timed out, returning to passive")
                        isFollowUp = false
                    }
                    scheduleRestart()
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                    // Already running, wait and retry
                    scheduleRestart()
                }
                else -> {
                    Log.e(TAG, "Recognition error: $error")
                    scheduleRestart()
                }
            }
        }

        override fun onResults(results: Bundle?) {
            lastCallbackAt = System.currentTimeMillis()
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val transcript = matches?.firstOrNull()?.trim().orEmpty()
            if (transcript.isNotBlank() || isFollowUp) {
                SpeechRecognitionDebugStore.record(
                    context = context,
                    backendLabel = "Android local",
                    modelId = "android.speech.SpeechRecognizer",
                    status = if (transcript.isBlank()) "Empty recognition result" else "OK",
                    transcript = transcript,
                    latencyMs = currentLatencyMs(),
                )
            }
            handleSpeechResults(matches)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            lastCallbackAt = System.currentTimeMillis()
            // Check partials for wake word to respond faster
            if (!isFollowUp) {
                val partials = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = partials?.firstOrNull()?.lowercase() ?: return
                if (containsWakeWord(text)) {
                    Log.d(TAG, "Wake word detected in partial: $text")
                    // Don't act yet — wait for full results to get the complete utterance
                }
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {
            lastCallbackAt = System.currentTimeMillis()
        }
    }

    private fun handleSpeechResults(matches: List<String>?) {
        val text = matches?.firstOrNull()?.trim() ?: ""
        Log.d(TAG, "Result: \"$text\" (followUp=$isFollowUp)")

        if (text.length < MIN_QUERY_LENGTH) {
            // Too short to be a real question (likely ambient noise, a cough,
            // an "uh", etc.). Don't bother Hank.
            if (isFollowUp) {
                isFollowUp = false
            }
            scheduleRestart()
            return
        }

        // Always-on mode: anything substantive the user says is a question.
        // Strip a leading "Hey Hank" if present (user may still say it out of
        // habit) but don't require it.
        val lowerText = text.lowercase()
        val wakeWord = WAKE_WORDS.find { lowerText.startsWith(it) || lowerText.contains(it) }
        val question =
            if (wakeWord != null) {
                val idx = lowerText.indexOf(wakeWord) + wakeWord.length
                text.substring(idx).trim().trimStart(',', '.', '!', '?', ':').trim()
            } else {
                text
            }
        if (question.length < MIN_QUERY_LENGTH) {
            // They said only the wake word with no follow-on question.
            // Treat as a "yes?" prompt — switch to focused follow-up listen.
            isFollowUp = true
            _state.value = VoiceState.Listening
            restartRecognizer()
            return
        }
        Log.d(TAG, "Question captured: $question")
        _state.value = VoiceState.QuestionReady(question)
    }

    private fun containsWakeWord(text: String): Boolean {
        val lower = text.lowercase()
        return WAKE_WORDS.any { lower.contains(it) }
    }

    private fun scheduleRestart() {
        if (!isRunning && !isFollowUp) return
        if (isMuted) return
        handler.postDelayed({ startRecognizer() }, RESTART_DELAY_MS)
    }

    private fun restartRecognizer() {
        destroyRecognizer()
        handler.postDelayed({ startRecognizer() }, 100)
    }

    private fun destroyRecognizer() {
        destroyOpenRouterRecognizer()
        try {
            recognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error destroying recognizer", e)
        }
        recognizer = null
    }

    private fun currentRoute(): SpeechRecognitionRoute = SpeechRecognitionRoute.current(context)

    private fun destroyOpenRouterRecognizer() {
        audioCaptureActive = false
        try {
            audioRecord?.stop()
        } catch (_: Exception) {}
        try {
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
        captureThread?.interrupt()
        captureThread = null
    }

    @SuppressLint("MissingPermission") // RECORD_AUDIO is enforced at request time in MainActivity
    private fun captureUtteranceWav(): ByteArray? {
        val minBuffer =
            AudioRecord.getMinBufferSize(
                AUDIO_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
        if (minBuffer <= 0) {
            Log.w(TAG, "AudioRecord.getMinBufferSize() returned $minBuffer")
            return null
        }

        val recorder =
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                AUDIO_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                max(minBuffer * 2, 4096),
            )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            Log.w(TAG, "OpenRouter recorder failed to initialize")
            recorder.release()
            return null
        }

        audioRecord = recorder
        val buffer = ShortArray(1024)
        val maxPrerollBytes = (AUDIO_SAMPLE_RATE * 2 * AUDIO_PREROLL_MS) / 1000
        val preroll = ArrayDeque<ByteArray>()
        var prerollBytes = 0
        val speech = ByteArrayOutputStream()

        var speechStarted = false
        var waitMs = 0L
        var speechMs = 0L
        var aboveStartMs = 0L
        var belowEndMs = 0L
        var noiseFloor = 250.0
        var noiseSamples = 0

        try {
            recorder.startRecording()
            while (audioCaptureActive && !Thread.currentThread().isInterrupted) {
                val read = recorder.read(buffer, 0, buffer.size)
                lastCallbackAt = System.currentTimeMillis()
                if (read <= 0) {
                    Thread.sleep(20)
                    continue
                }

                val chunkMs = (1000L * read) / AUDIO_SAMPLE_RATE
                val pcmBytes = shortsToBytes(buffer, read)
                val rms = computeRms(buffer, read)

                if (!speechStarted && noiseSamples < 20) {
                    noiseFloor = ((noiseFloor * noiseSamples) + rms) / (noiseSamples + 1)
                    noiseSamples += 1
                }

                val startThreshold = max(AUDIO_MIN_START_THRESHOLD, noiseFloor * 2.8)
                val endThreshold = max(AUDIO_MIN_END_THRESHOLD, noiseFloor * 1.5)

                if (!speechStarted) {
                    preroll.addLast(pcmBytes)
                    prerollBytes += pcmBytes.size
                    while (prerollBytes > maxPrerollBytes && preroll.isNotEmpty()) {
                        prerollBytes -= preroll.removeFirst().size
                    }

                    waitMs += chunkMs
                    if (rms >= startThreshold) {
                        aboveStartMs += chunkMs
                    } else {
                        aboveStartMs = 0L
                    }

                    if (aboveStartMs >= AUDIO_START_HOLD_MS) {
                        speechStarted = true
                        for (bytes in preroll) {
                            speech.write(bytes)
                        }
                        speechMs = ((speech.size() / 2L) * 1000L) / AUDIO_SAMPLE_RATE
                        preroll.clear()
                        prerollBytes = 0
                        belowEndMs = 0L
                        Log.d(
                            TAG,
                            "OpenRouter capture start (noise=${noiseFloor.toInt()}, start=${startThreshold.toInt()})",
                        )
                    } else if (waitMs >= AUDIO_MAX_WAIT_MS) {
                        return null
                    }
                    continue
                }

                speech.write(pcmBytes)
                speechMs += chunkMs
                if (rms <= endThreshold) {
                    belowEndMs += chunkMs
                } else {
                    belowEndMs = 0L
                }

                if (speechMs >= AUDIO_MAX_UTTERANCE_MS ||
                    (speechMs >= AUDIO_MIN_SPEECH_MS && belowEndMs >= AUDIO_END_HOLD_MS)
                ) {
                    break
                }
            }
        } catch (e: InterruptedException) {
            return null
        } catch (e: Exception) {
            Log.w(TAG, "OpenRouter audio capture failed", e)
            return null
        } finally {
            try {
                recorder.stop()
            } catch (_: Exception) {}
            try {
                recorder.release()
            } catch (_: Exception) {}
            if (audioRecord === recorder) {
                audioRecord = null
            }
        }

        val pcm = speech.toByteArray()
        if (!speechStarted || pcm.isEmpty()) return null
        return pcm16ToWav(pcm)
    }

    private fun shortsToBytes(buffer: ShortArray, count: Int): ByteArray {
        val bytes = ByteArray(count * 2)
        ByteBuffer.wrap(bytes)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asShortBuffer()
            .put(buffer, 0, count)
        return bytes
    }

    private fun pcm16ToWav(pcm: ByteArray): ByteArray {
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        val byteRate = AUDIO_SAMPLE_RATE * 2
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(36 + pcm.size)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)
        header.putShort(1)
        header.putShort(1)
        header.putInt(AUDIO_SAMPLE_RATE)
        header.putInt(byteRate)
        header.putShort(2)
        header.putShort(16)
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(pcm.size)
        return header.array() + pcm
    }

    private fun computeRms(buffer: ShortArray, count: Int): Double {
        var sum = 0.0
        for (i in 0 until count) {
            val value = buffer[i].toDouble()
            sum += value * value
        }
        return sqrt(sum / count)
    }

    private fun currentLatencyMs(): Long? =
        recognitionCycleStartedAt.takeIf { it > 0L }?.let { System.currentTimeMillis() - it }
}
