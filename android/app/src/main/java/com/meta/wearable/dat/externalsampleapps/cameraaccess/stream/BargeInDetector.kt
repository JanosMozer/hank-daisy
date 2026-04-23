/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.stream

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.util.Log
import kotlin.concurrent.thread
import kotlin.math.sqrt

/**
 * Listens to the mic in parallel with TTS playback and fires [onUserSpeaking]
 * the moment the user starts talking, so Hank can be cut off mid-sentence.
 *
 * Uses VOICE_COMMUNICATION audio source + AcousticEchoCanceler (when available)
 * so Hank's own TTS playing through the speaker doesn't trigger the detector.
 *
 * Energy-based VAD with hysteresis: must exceed [rmsThreshold] for
 * [triggerMs] continuous milliseconds to fire. One-shot — call [start] again
 * for the next utterance.
 */
class BargeInDetector(
    private val onUserSpeaking: () -> Unit,
    // Tuned for fast interrupt: 600 RMS is low enough that a normal-volume
    // first syllable crosses it reliably, AEC still filters Hank's own
    // voice. 90ms trigger means we fire ~40ms sooner than before without
    // becoming jumpy on keyboard clicks / throat clears.
    private val rmsThreshold: Double = 600.0,
    private val triggerMs: Long = 90L,
) {
    companion object {
        private const val TAG = "HankDaisy:BargeIn"
        private const val SAMPLE_RATE = 16_000
    }

    @Volatile private var running = false
    private var recorder: AudioRecord? = null
    private var aec: AcousticEchoCanceler? = null
    private var thread: Thread? = null

    @SuppressLint("MissingPermission") // RECORD_AUDIO is enforced at request time in MainActivity
    fun start() {
        if (running) return
        try {
            val minBuf =
                AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                )
            if (minBuf <= 0) {
                Log.w(TAG, "AudioRecord.getMinBufferSize() returned $minBuf; abort")
                return
            }
            val bufferSize = minBuf * 2
            val rec =
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize,
                )
            if (rec.state != AudioRecord.STATE_INITIALIZED) {
                Log.w(TAG, "AudioRecord not initialised; abort")
                rec.release()
                return
            }
            recorder = rec
            if (AcousticEchoCanceler.isAvailable()) {
                aec =
                    AcousticEchoCanceler.create(rec.audioSessionId)?.also {
                        it.enabled = true
                    }
                Log.d(TAG, "AEC enabled: ${aec?.enabled}")
            } else {
                Log.d(TAG, "AEC not available on this device")
            }
            rec.startRecording()
            running = true
            thread = thread(name = "barge-in-vad") { detectLoop() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start BargeInDetector", e)
            stop()
        }
    }

    fun stop() {
        running = false
        try {
            recorder?.stop()
        } catch (_: Exception) {}
        try {
            recorder?.release()
        } catch (_: Exception) {}
        recorder = null
        try {
            aec?.release()
        } catch (_: Exception) {}
        aec = null
        thread?.interrupt()
        thread = null
    }

    private fun detectLoop() {
        val buffer = ShortArray(1024)
        val sampleMs = (1000L * buffer.size) / SAMPLE_RATE  // ~64ms per buffer
        var aboveMs = 0L
        var loggedAt = 0L
        try {
            while (running) {
                val read = recorder?.read(buffer, 0, buffer.size) ?: -1
                if (read <= 0) {
                    Thread.sleep(20)
                    continue
                }
                val rms = computeRms(buffer, read)
                // Log every ~500ms so we can see what the threshold should actually be.
                val now = System.currentTimeMillis()
                if (now - loggedAt > 500) {
                    Log.d(TAG, "rms=${rms.toInt()} (threshold=${rmsThreshold.toInt()}, aboveMs=$aboveMs)")
                    loggedAt = now
                }
                if (rms > rmsThreshold) {
                    aboveMs += sampleMs
                    if (aboveMs >= triggerMs) {
                        Log.d(TAG, "Voice detected (rms=${rms.toInt()}) — firing barge-in")
                        running = false
                        try {
                            onUserSpeaking()
                        } catch (e: Exception) {
                            Log.w(TAG, "onUserSpeaking callback threw", e)
                        }
                        return
                    }
                } else {
                    aboveMs = 0L
                }
            }
        } catch (e: InterruptedException) {
            // normal shutdown
        } catch (e: Exception) {
            Log.w(TAG, "detectLoop error", e)
        }
    }

    private fun computeRms(buffer: ShortArray, count: Int): Double {
        var sum = 0.0
        for (i in 0 until count) {
            val v = buffer[i].toDouble()
            sum += v * v
        }
        return sqrt(sum / count)
    }
}
