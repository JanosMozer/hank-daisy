/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.stream

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class GlassesAudioManager(context: Context) {

    companion object {
        private const val TAG = "CameraAccess:GlassesAudio"
    }

    private var tts: TextToSpeech? = null
    private var isReady = false
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                isReady = true
                routeAudioToGlasses()
                Log.d(TAG, "TTS initialized successfully")
            } else {
                Log.e(TAG, "TTS initialization failed with status: $status")
            }
        }
    }

    private fun routeAudioToGlasses() {
        try {
            val devices = audioManager.availableCommunicationDevices
            val bluetoothDevice = devices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            }

            if (bluetoothDevice != null) {
                audioManager.mode = AudioManager.MODE_NORMAL
                audioManager.setCommunicationDevice(bluetoothDevice)
                Log.d(TAG, "Audio routed to Bluetooth SCO (glasses)")
            } else {
                Log.w(TAG, "No Bluetooth SCO device found, using default speaker")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to route audio to glasses", e)
        }
    }

    fun speak(text: String) {
        if (!isReady) {
            Log.w(TAG, "TTS not ready yet")
            return
        }
        routeAudioToGlasses()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "gemini_response")
        Log.d(TAG, "Speaking: ${text.take(50)}...")
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
        try {
            audioManager.clearCommunicationDevice()
        } catch (_: Exception) {}
    }
}
