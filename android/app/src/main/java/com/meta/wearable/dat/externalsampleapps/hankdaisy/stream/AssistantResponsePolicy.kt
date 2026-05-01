/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.hankdaisy.stream

enum class AssistantResponseMode {
    CONVERSATION,
    VISUAL_DEMO,
}

data class PreparedAssistantResponse(
    val text: String,
    val wasSanitized: Boolean,
    val usedFallback: Boolean,
)

object AssistantResponsePolicy {
    private val leadingMetaPatterns =
        listOf(
            Regex("^(based on|from|according to) the (audio file|audio clip|recording|transcript|transcription)\\b\\s*(,|:|-)?\\s*", RegexOption.IGNORE_CASE),
            Regex("^(based on|from|according to) the (primary|foreground) speaker\\b\\s*(,|:|-)?\\s*", RegexOption.IGNORE_CASE),
            Regex("^the audio (file |clip )?(says|said|is|mentions|mentioned|indicates|indicated)\\s*:?\\s*", RegexOption.IGNORE_CASE),
            Regex("^the recording (says|said|is|mentions|mentioned|indicates|indicated)\\s*:?\\s*", RegexOption.IGNORE_CASE),
            Regex("^the transcription (says|said|is|mentions|mentioned|indicates|indicated)\\s*:?\\s*", RegexOption.IGNORE_CASE),
            Regex("^the transcript (says|said|is|mentions|mentioned|indicates|indicated)\\s*:?\\s*", RegexOption.IGNORE_CASE),
            Regex("^the (primary|foreground) speaker (says|said)\\s*:?\\s*", RegexOption.IGNORE_CASE),
            Regex("^i hear\\s*:?\\s*", RegexOption.IGNORE_CASE),
            Regex("^i heard\\s*:?\\s*", RegexOption.IGNORE_CASE),
        )

    private val residualMetaPatterns =
        listOf(
            Regex("\\b(audio file|audio clip|recording|transcript|transcription)\\b", RegexOption.IGNORE_CASE),
            Regex("\\b(primary|foreground) speaker\\b", RegexOption.IGNORE_CASE),
            Regex("\\bspeech recogn(ition|izer)\\b", RegexOption.IGNORE_CASE),
            Regex("\\bupload(ed|ing)?\\b[^.?!]{0,40}\\baudio\\b", RegexOption.IGNORE_CASE),
        )

    fun prepare(
        raw: String,
        mode: AssistantResponseMode,
    ): PreparedAssistantResponse {
        val original = raw.trim()
        if (original.isBlank()) {
            return fallback(mode, usedSanitizer = false)
        }

        var text =
            original.removePrefix("\"")
                .removeSuffix("\"")
                .removePrefix("'")
                .removeSuffix("'")
                .trim()

        leadingMetaPatterns.forEach { pattern ->
            text = pattern.replace(text, "").trim()
        }

        text = text.replace(Regex("\\s+"), " ").trim()

        val sentences =
            text.split(Regex("(?<=[.!?])\\s+"))
                .map { it.trim() }
                .filter { it.isNotBlank() }
        val filtered =
            sentences.filterNot { sentence ->
                residualMetaPatterns.any { pattern -> pattern.containsMatchIn(sentence) }
            }

        val rebuilt =
            filtered.joinToString(" ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .trim(',', ';', ':', '-', ' ')
                .trim()

        if (rebuilt.isBlank()) {
            return fallback(mode, usedSanitizer = original != text || filtered.size != sentences.size)
        }

        return PreparedAssistantResponse(
            text = rebuilt,
            wasSanitized = rebuilt != original,
            usedFallback = false,
        )
    }

    private fun fallback(
        mode: AssistantResponseMode,
        usedSanitizer: Boolean,
    ): PreparedAssistantResponse {
        val text =
            when (mode) {
                AssistantResponseMode.CONVERSATION ->
                    "Show me the part you want me to inspect a bit closer and ask again."
                AssistantResponseMode.VISUAL_DEMO ->
                    "Point the camera at the most relevant area and I'll keep the demo moving."
            }
        return PreparedAssistantResponse(
            text = text,
            wasSanitized = usedSanitizer,
            usedFallback = true,
        )
    }
}
