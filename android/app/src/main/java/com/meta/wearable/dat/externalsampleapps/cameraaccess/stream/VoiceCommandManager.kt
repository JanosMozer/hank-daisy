/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.stream

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Always-on voice command manager with "Hey Hank" wake word detection.
 *
 * Continuously listens via SpeechRecognizer. When "Hey Hank" (or just "Hank")
 * is detected, extracts the question that follows and emits it.
 * If the wake word is detected alone, starts a focused follow-up listen for the question.
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
    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false
    private var isFollowUp = false  // true when we detected wake word, now waiting for question
    private var isMuted = false     // pause listening while Hank himself is talking
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
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _state.value = VoiceState.Error("Speech recognition not available")
            return
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

        handler.post {
            destroyRecognizer()

            if (!isFollowUp) {
                _state.value = VoiceState.Passive
            }

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
        try {
            recognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error destroying recognizer", e)
        }
        recognizer = null
    }
}
