/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.hankdaisy.stream

object SpeechTurnSanitizer {
    private val labelPatterns =
        listOf(
            Regex("^transcript\\s*:\\s*", RegexOption.IGNORE_CASE),
            Regex("^transcription\\s*:\\s*", RegexOption.IGNORE_CASE),
            Regex("^audio\\s*:\\s*", RegexOption.IGNORE_CASE),
            Regex("^recording\\s*:\\s*", RegexOption.IGNORE_CASE),
            Regex("^speaker\\s*:\\s*", RegexOption.IGNORE_CASE),
            Regex("^primary speaker\\s*:\\s*", RegexOption.IGNORE_CASE),
            Regex("^foreground speaker\\s*:\\s*", RegexOption.IGNORE_CASE),
            Regex("^user\\s*:\\s*", RegexOption.IGNORE_CASE),
        )

    private val leadingMetaPatterns =
        listOf(
            Regex("^the audio (file |clip )?(says|said|is|mentions|mentioned|indicates|indicated)\\s*:?\\s*", RegexOption.IGNORE_CASE),
            Regex("^the recording (says|said|is|mentions|mentioned|indicates|indicated)\\s*:?\\s*", RegexOption.IGNORE_CASE),
            Regex("^the transcription (says|said|is|mentions|mentioned|indicates|indicated)\\s*:?\\s*", RegexOption.IGNORE_CASE),
            Regex("^the transcript (says|said|is|mentions|mentioned|indicates|indicated)\\s*:?\\s*", RegexOption.IGNORE_CASE),
            Regex("^the (primary|foreground) speaker (says|said)\\s*:?\\s*", RegexOption.IGNORE_CASE),
            Regex("^i hear\\s*:?\\s*", RegexOption.IGNORE_CASE),
            Regex("^i heard\\s*:?\\s*", RegexOption.IGNORE_CASE),
            Regex("^(based on|from|according to) the (audio file|audio clip|recording|transcript|transcription)\\b\\s*(,|:|-)?\\s*", RegexOption.IGNORE_CASE),
            Regex("^(based on|from|according to) the (primary|foreground) speaker\\b\\s*(,|:|-)?\\s*", RegexOption.IGNORE_CASE),
        )

    fun sanitizeRecognizedSpeech(raw: String): String {
        var text = raw.trim()
        if (text.isEmpty()) return text

        text =
            text.removePrefix("\"")
                .removeSuffix("\"")
                .removePrefix("'")
                .removeSuffix("'")
                .trim()

        labelPatterns.forEach { pattern ->
            text = pattern.replace(text, "").trim()
        }

        leadingMetaPatterns.forEach { pattern ->
            text = pattern.replace(text, "").trim()
        }

        val quoted =
            Regex("[\"“](.+?)[\"”]").find(text)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        if (quoted.isNotBlank()) {
            text = quoted
        }

        return text.replace(Regex("\\s+"), " ").trim().trim(',', '.', ';', ':', '-', ' ').trim()
    }
}
