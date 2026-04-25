/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.hankdaisy.stream

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.meta.wearable.dat.externalsampleapps.hankdaisy.BuildConfig
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Handles Hank's spoken replies for the phone-camera path. Input stays on the
 * phone mic because SpeechRecognizer ignores app-level routing; output follows
 * the active media route so normal Bluetooth speakers/headsets still work.
 */
class AudioRouteManager(private val context: Context) {

    enum class AudioStatus {
        FULL,
        SPEAKER_ONLY,
        MIC_ONLY,
        NONE,
    }

    companion object {
        private const val TAG = "HankDaisy:AudioRoute"
        private const val USE_ELEVENLABS = true
    }

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _audioRouteStatus = MutableStateFlow(AudioStatus.NONE)
    val audioRouteStatus: StateFlow<AudioStatus> = _audioRouteStatus.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val elevenLabs =
        ElevenLabsTtsService(
            context = context,
            voiceId = BuildConfig.ELEVENLABS_VOICE_ID,
            apiKey = BuildConfig.ELEVENLABS_API_KEY,
            onSpeakingChanged = { speaking -> _isSpeaking.value = speaking },
        )

    private val deviceCallback =
        object : android.media.AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                refreshStatus()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                refreshStatus()
            }
        }

    private val scoStateReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val state =
                    intent?.getIntExtra(
                        AudioManager.EXTRA_SCO_AUDIO_STATE,
                        AudioManager.SCO_AUDIO_STATE_ERROR,
                    )
                Log.d(TAG, "SCO state changed: $state")
            }
        }

    init {
        tts =
            TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.US
                    tts?.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build(),
                    )
                    tts?.setOnUtteranceProgressListener(
                        object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {
                                _isSpeaking.value = true
                            }

                            override fun onDone(utteranceId: String?) {
                                _isSpeaking.value = false
                            }

                            @Deprecated("Deprecated in Java")
                            override fun onError(utteranceId: String?) {
                                _isSpeaking.value = false
                            }

                            override fun onError(utteranceId: String?, errorCode: Int) {
                                _isSpeaking.value = false
                            }
                        },
                    )
                    ttsReady = true
                    Log.d(TAG, "TTS initialized")
                } else {
                    Log.e(TAG, "TTS init failed: $status")
                }
            }
        audioManager.registerAudioDeviceCallback(deviceCallback, null)
        context.registerReceiver(
            scoStateReceiver,
            IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED),
        )
        refreshStatus()
    }

    fun stopSpeaking() {
        try {
            elevenLabs.stop()
        } catch (_: Exception) {}
        try {
            tts?.stop()
        } catch (_: Exception) {}
        _isSpeaking.value = false
    }

    fun speak(text: String) {
        val spoken = HankMarkdown.toSpokenText(text)
        val chunks = splitForTts(spoken.ifBlank { text })
        if (chunks.isEmpty()) return
        val a2dp = findA2dpDevice()
        Log.d(
            TAG,
            "speak() -> ${if (a2dp != null) "Bluetooth media" else "phone speaker"} (${chunks.size} chunks)",
        )
        if (USE_ELEVENLABS && elevenLabs.isConfigured) {
            val accepted =
                elevenLabs.speak(chunks, onAllFailed = {
                    Log.w(TAG, "ElevenLabs produced no audio — falling back to Android TTS")
                    speakWithAndroidTts(chunks, a2dp)
                })
            if (accepted) return
            Log.w(TAG, "ElevenLabs rejected speak — falling back to Android TTS immediately")
        }
        speakWithAndroidTts(chunks, a2dp)
    }

    private fun speakWithAndroidTts(chunks: List<String>, a2dp: AudioDeviceInfo?) {
        if (!ttsReady) {
            Log.w(TAG, "Android TTS not ready — dropping reply")
            return
        }
        if (a2dp != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            tts?.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
        }
        tts?.speak(
            chunks.first(),
            TextToSpeech.QUEUE_FLUSH,
            null,
            "hank_${System.currentTimeMillis()}_0",
        )
        for ((i, chunk) in chunks.withIndex().drop(1)) {
            tts?.speak(
                chunk,
                TextToSpeech.QUEUE_ADD,
                null,
                "hank_${System.currentTimeMillis()}_$i",
            )
        }
    }

    private fun splitForTts(text: String): List<String> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()
        val regex = Regex("(?<=[.!?,;:])\\s+")
        return trimmed.split(regex).map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun prepareSpeechInput() {
        Log.d(TAG, "prepareSpeechInput() is a no-op (SpeechRecognizer ignores app routing)")
    }

    fun releaseSpeechInput() {
        // No-op — pairs with prepareSpeechInput above.
    }

    private fun findA2dpDevice(): AudioDeviceInfo? {
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return outputs.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    it.type == AudioDeviceInfo.TYPE_BLE_HEADSET) ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER)
        }
    }

    private fun findScoDevice(): AudioDeviceInfo? {
        val sources = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        return sources.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    it.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
        }
    }

    private fun refreshStatus() {
        val mic = findScoDevice() != null
        val speaker = findA2dpDevice() != null
        _audioRouteStatus.value =
            when {
                mic && speaker -> AudioStatus.FULL
                speaker -> AudioStatus.SPEAKER_ONLY
                mic -> AudioStatus.MIC_ONLY
                else -> AudioStatus.NONE
            }
        Log.d(TAG, "Audio route status: ${_audioRouteStatus.value}")
    }

    fun shutdown() {
        try {
            elevenLabs.shutdown()
        } catch (_: Exception) {}
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {}
        tts = null
        ttsReady = false
        releaseSpeechInput()
        try {
            audioManager.unregisterAudioDeviceCallback(deviceCallback)
        } catch (_: Exception) {}
        try {
            context.unregisterReceiver(scoStateReceiver)
        } catch (_: Exception) {}
    }
}
