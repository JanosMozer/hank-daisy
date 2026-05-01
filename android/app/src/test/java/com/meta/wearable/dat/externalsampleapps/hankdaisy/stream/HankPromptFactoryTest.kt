/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.hankdaisy.stream

import com.meta.wearable.dat.externalsampleapps.hankdaisy.session.WorkDomain
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HankPromptFactoryTest {

    @Test
    fun conversationPromptForbidsPipelineMetaLanguage() {
        val prompt = HankPromptFactory.systemPrompt(WorkDomain.CAR)

        assertTrue(prompt.contains("Never mention recordings, audio files, audio clips, transcripts, transcriptions, speech recognition, uploads"))
    }

    @Test
    fun demoPromptForbidsPipelineMetaLanguage() {
        val prompt = HankPromptFactory.demoNarrationSystemPrompt(WorkDomain.CAR)

        assertTrue(prompt.contains("Never mention recordings, audio files, audio clips, transcripts, transcriptions, speech recognition, uploads"))
    }

    @Test
    fun followUpPromptNoLongerHardCodesFourSeconds() {
        val prompt =
            HankPromptFactory.demoNarrationUserPrompt(
                WorkDomain.CAR,
                HankPromptFactory.DemoNarrationTrigger.FOLLOW_UP,
            )

        assertTrue(prompt.contains("a few seconds"))
        assertFalse(prompt.contains("four seconds"))
    }
}
