/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.stream

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
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Routes the voice I/O loop through the Ray-Ban Meta glasses using their classic
 * Bluetooth profiles (the DAT SDK only exposes camera, not audio):
 *
 * - TTS replies: USAGE_MEDIA → played out via A2DP (the glasses' speakers).
 * - Mic capture: SCO/HFP enabled while listening → SpeechRecognizer reads the
 *   glasses' microphone.
 *
 * Requires the user to have paired the glasses as a normal Bluetooth headset
 * in addition to the DAT pairing. [glassesAudioStatus] reports whether that's
 * the case so the UI can prompt the user to fix it.
 */
class GlassesAudioManager(private val context: Context) {

    enum class AudioStatus {
        /** Both mic (HFP) and speaker (A2DP) routes available — full glasses I/O. */
        FULL,
        /** Speaker available, mic not — TTS plays on glasses, mic falls back to phone. */
        SPEAKER_ONLY,
        /** Mic available, no A2DP speaker — voice in works, replies fall back to phone. */
        MIC_ONLY,
        /** Neither — everything falls back to the phone. */
        NONE,
    }

    companion object {
        private const val TAG = "CameraAccess:GlassesAudio"
    }

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _glassesAudioStatus = MutableStateFlow(AudioStatus.NONE)
    val glassesAudioStatus: StateFlow<AudioStatus> = _glassesAudioStatus.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private var scoActive = false

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

    /** Speak the reply through the glasses (A2DP) if connected, else through the phone. */
    fun speak(text: String) {
        if (!ttsReady) {
            Log.w(TAG, "TTS not ready")
            return
        }
        val a2dp = findA2dpDevice()
        if (a2dp != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            tts?.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "hank_${System.currentTimeMillis()}")
        Log.d(TAG, "speak() → ${if (a2dp != null) "A2DP/glasses" else "phone speaker"}")
    }

    /**
     * Open the SCO link to the glasses so SpeechRecognizer (which reads the active
     * communication device) captures from the glasses' mic instead of the phone.
     */
    fun enableGlassesMic() {
        val sco = findScoDevice()
        if (sco == null) {
            Log.w(TAG, "No SCO device — mic will fall back to phone")
            return
        }
        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.setCommunicationDevice(sco)
            } else {
                @Suppress("DEPRECATION")
                audioManager.startBluetoothSco()
                @Suppress("DEPRECATION")
                audioManager.isBluetoothScoOn = true
            }
            scoActive = true
            Log.d(TAG, "Glasses mic enabled (SCO)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enable glasses mic", e)
        }
    }

    fun disableGlassesMic() {
        if (!scoActive) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                audioManager.isBluetoothScoOn = false
                @Suppress("DEPRECATION")
                audioManager.stopBluetoothSco()
            }
            audioManager.mode = AudioManager.MODE_NORMAL
            scoActive = false
            Log.d(TAG, "Glasses mic disabled")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to disable glasses mic", e)
        }
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
        _glassesAudioStatus.value =
            when {
                mic && speaker -> AudioStatus.FULL
                speaker -> AudioStatus.SPEAKER_ONLY
                mic -> AudioStatus.MIC_ONLY
                else -> AudioStatus.NONE
            }
        Log.d(TAG, "Audio route status: ${_glassesAudioStatus.value}")
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {}
        tts = null
        ttsReady = false
        disableGlassesMic()
        try {
            audioManager.unregisterAudioDeviceCallback(deviceCallback)
        } catch (_: Exception) {}
        try {
            context.unregisterReceiver(scoStateReceiver)
        } catch (_: Exception) {}
    }
}
