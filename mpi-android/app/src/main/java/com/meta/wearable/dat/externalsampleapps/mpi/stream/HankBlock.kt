/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.mpi.stream

/**
 * Parsed structural representation of one of Hank's replies.
 *
 * Hank emits lightweight Markdown (see [HankMarkdown]) — paragraphs,
 * headings, blockquotes, checklists, tables. We parse that string into
 * a flat list of blocks so the renderer can style each one with a
 * dedicated component (step cards, warning callouts, spec tables, etc.)
 * rather than dumping everything as one wall of text.
 *
 * Inline text inside a block may still contain `**bold**` / `*italic*`
 * spans — those are rendered by the view layer via [InlineSpan].
 */
sealed interface HankBlock {
    /** Numbered procedural header — "## Step 1: Check fuel pressure". */
    data class StepHeader(val number: Int?, val title: String) : HankBlock

    /** Generic heading for non-step sections (findings, summary, etc.). */
    data class Heading(val level: Int, val text: String) : HankBlock

    /** Plain prose paragraph. */
    data class Paragraph(val text: String) : HankBlock

    /** ⚠️ callout — safety / must-not-miss info. */
    data class Warning(val text: String) : HankBlock

    /** 💡 callout — helpful hint, not safety-critical. */
    data class Tip(val text: String) : HankBlock

    /** 👉 callout — "the one thing to do right now". */
    data class NextAction(val text: String) : HankBlock

    /** Unordered list of check items, any of which may be pre-checked. */
    data class Checklist(val items: List<ChecklistItem>) : HankBlock

    /** Markdown table — first row is headers, subsequent rows are data. */
    data class Table(val headers: List<String>, val rows: List<List<String>>) : HankBlock

    /** Inline gauge — a "measured vs spec ± tolerance" bar for diagnostic
     *  readings. Emitted by Hank via a `gauge` fenced code block (see
     *  HankMarkdown). [measured] / [spec] / [tolerance] are numeric,
     *  [unit] is free text (e.g. "rpm", "psi"). */
    data class Gauge(
        val label: String,
        val measured: Double,
        val spec: Double,
        val tolerance: Double,
        val unit: String,
    ) : HankBlock {
        /** Semantic verdict based on where `measured` falls relative to
         *  `spec ± tolerance`. Drives colour in the renderer. */
        val verdict: GaugeVerdict
            get() {
                if (tolerance <= 0.0) {
                    return if (measured == spec) GaugeVerdict.OK else GaugeVerdict.OUT
                }
                val delta = kotlin.math.abs(measured - spec)
                return when {
                    delta <= tolerance -> GaugeVerdict.OK
                    delta <= tolerance * 2.0 -> GaugeVerdict.WARN
                    else -> GaugeVerdict.OUT
                }
            }
    }

    /** Horizontal rule / divider. */
    data object Divider : HankBlock
}

enum class GaugeVerdict { OK, WARN, OUT }

data class ChecklistItem(val text: String, val checked: Boolean)
