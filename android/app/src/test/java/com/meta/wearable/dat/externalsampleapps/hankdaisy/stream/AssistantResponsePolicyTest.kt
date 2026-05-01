/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.hankdaisy.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantResponsePolicyTest {

    @Test
    fun stripsLeadingMetaPhrasesBeforeSpeech() {
        val prepared =
            AssistantResponsePolicy.prepare(
                raw = "Based on the recording, check the PCV hose first.",
                mode = AssistantResponseMode.CONVERSATION,
            )

        assertEquals("check the PCV hose first.", prepared.text)
        assertTrue(prepared.wasSanitized)
        assertFalse(prepared.usedFallback)
    }

    @Test
    fun keepsLegitimateContentAfterTranscriptStyleLeadIn() {
        val prepared =
            AssistantResponsePolicy.prepare(
                raw = "The transcript says the battery light is on. Start by checking charging voltage.",
                mode = AssistantResponseMode.CONVERSATION,
            )

        assertEquals(
            "the battery light is on. Start by checking charging voltage.",
            prepared.text,
        )
        assertTrue(prepared.wasSanitized)
        assertFalse(prepared.usedFallback)
    }

    @Test
    fun fallsBackWhenOnlyPipelineMetaLanguageRemains() {
        val prepared =
            AssistantResponsePolicy.prepare(
                raw = "I heard the primary speaker ask about P0300.",
                mode = AssistantResponseMode.CONVERSATION,
            )

        assertEquals(
            "Show me the part you want me to inspect a bit closer and ask again.",
            prepared.text,
        )
        assertTrue(prepared.wasSanitized)
        assertTrue(prepared.usedFallback)
    }

    @Test
    fun allowsLegitimateSpeakerReferencesForRealDeviceContext() {
        val prepared =
            AssistantResponsePolicy.prepare(
                raw = "The rear speaker is rattling. Pull the door card and check the clips.",
                mode = AssistantResponseMode.CONVERSATION,
            )

        assertEquals(
            "The rear speaker is rattling. Pull the door card and check the clips.",
            prepared.text,
        )
        assertFalse(prepared.wasSanitized)
        assertFalse(prepared.usedFallback)
    }
}
