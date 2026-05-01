/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.hankdaisy.session

import org.junit.Assert.assertEquals
import org.junit.Test

class AppConfigPresetTest {

    @Test
    fun fastestPresetUsesAndroidSpeechRoute() {
        val config = AppConfig().withPipelinePreset(PipelinePreset.FASTEST)

        assertEquals(SpeechRecognitionRoute.ANDROID, config.audio.transcription.route)
        assertEquals(AudioInputMode.ANDROID_SPEECH_RECOGNIZER, config.audio.capture.inputMode)
    }

    @Test
    fun balancedPresetUsesOpenRouterSpeechRoute() {
        val config = AppConfig().withPipelinePreset(PipelinePreset.BALANCED)

        assertEquals(SpeechRecognitionRoute.OPENROUTER, config.audio.transcription.route)
        assertEquals(AudioInputMode.RAW_AUDIO_RECORD, config.audio.capture.inputMode)
    }
}
