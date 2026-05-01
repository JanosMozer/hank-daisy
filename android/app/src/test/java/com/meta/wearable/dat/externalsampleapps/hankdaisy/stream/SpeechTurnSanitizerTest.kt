/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.hankdaisy.stream

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechTurnSanitizerTest {

    @Test
    fun stripsRecordingStylePrefixesFromRecognizedSpeech() {
        assertEquals(
            "hey hank what does p0300 mean",
            SpeechTurnSanitizer.sanitizeRecognizedSpeech(
                "Recording: hey hank what does p0300 mean",
            ),
        )
        assertEquals(
            "check the alternator output",
            SpeechTurnSanitizer.sanitizeRecognizedSpeech(
                "The audio file says check the alternator output",
            ),
        )
    }

    @Test
    fun preservesLegitimateDomainWordsThatAreNotPipelineMeta() {
        assertEquals(
            "rear speaker crackles over bumps",
            SpeechTurnSanitizer.sanitizeRecognizedSpeech("rear speaker crackles over bumps"),
        )
    }

    @Test
    fun unwrapsQuotedSpeechWithoutAddingMetaLanguage() {
        assertEquals(
            "why is the battery light on?",
            SpeechTurnSanitizer.sanitizeRecognizedSpeech("\"why is the battery light on?\""),
        )
    }
}
