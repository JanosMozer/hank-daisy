/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.hankdaisy.session

import android.content.Context

enum class SpeechRecognitionRoute(
    val segmentLabel: String,
    val settingsLabel: String,
    val settingsDescription: String,
) {
    ANDROID(
        segmentLabel = "Android",
        settingsLabel = "Android local",
        settingsDescription =
            "On-device Android speech recognition. Lowest latency and no extra audio upload, but weaker in loud bays.",
    ),
    OPENROUTER(
        segmentLabel = "OpenRouter",
        settingsLabel = "OpenRouter OpenAI",
        settingsDescription =
            "Uploads short clips to OpenRouter and asks OpenAI's audio model to focus on the primary speaker and ignore background noise.",
    ),
    ;

    companion object {
        private const val PREFS = "hank_sessions_v1"
        private const val KEY_SETTINGS = "settings_json"
        internal const val KEY_SPEECH_RECOGNITION_ROUTE = "speechRecognitionRoute"

        fun current(context: Context): SpeechRecognitionRoute {
            val raw =
                context.applicationContext
                    .getSharedPreferences(AppConfigStore.PREFS, Context.MODE_PRIVATE)
                    .getString(AppConfigStore.KEY_SETTINGS, null)
            return fromSettingsJson(raw)
        }

        fun fromSettingsJson(raw: String?): SpeechRecognitionRoute =
            AppConfigStore.fromSettingsJson(raw).audio.transcription.route

        fun fromStored(raw: String?): SpeechRecognitionRoute =
            entries.firstOrNull { it.name == raw } ?: ANDROID
    }
}
