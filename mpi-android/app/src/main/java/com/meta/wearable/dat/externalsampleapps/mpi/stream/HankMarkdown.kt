/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.mpi.stream

/**
 * Lightweight Markdown parser tailored to Hank's structured-reply format.
 *
 * Not a full CommonMark implementation — just enough to recognise:
 *  - `## Step N: ...` / `## Heading`             → StepHeader / Heading
 *  - `> ⚠️ ...` / `> 💡 ...` / `> 👉 ...`            → Warning / Tip / NextAction
 *  - Plain `> ...`                                → Warning (defensive)
 *  - Unordered lists (`- `, `* `) incl. `- [ ] `  → Checklist
 *  - Markdown tables (`| a | b |` + separator)    → Table
 *  - `---` / `***`                                → Divider
 *  - Anything else                                 → Paragraph
 *
 * Resilient to formatting drift — if Hank emits a callout without its
 * emoji, we still render it as a blockquote warning rather than crashing
 * or silently dropping content.
 *
 * [toSpokenText] produces a TTS-friendly string (no markdown noise,
 * tables read as sentences) so ElevenLabs doesn't narrate pipes + stars.
 */
object HankMarkdown {

    private val STEP_HEADER_REGEX =
        Regex("""^\s*#{1,6}\s*step\s*(\d+)\s*[:.\-]?\s*(.*)$""", RegexOption.IGNORE_CASE)
    private val HEADER_REGEX = Regex("""^\s*(#{1,6})\s+(.*)$""")
    private val DIVIDER_REGEX = Regex("""^\s*(-{3,}|\*{3,}|_{3,})\s*$""")
    private val LIST_ITEM_REGEX = Regex("""^\s*[-*]\s+(.*)$""")
    private val CHECKLIST_ITEM_REGEX = Regex("""^\s*[-*]\s*\[(\s|x|X)\]\s*(.*)$""")
    private val TABLE_SEPARATOR_REGEX = Regex("""^\s*\|?(\s*:?-+:?\s*\|)+\s*:?-+:?\s*\|?\s*$""")
    private val TABLE_ROW_REGEX = Regex("""^\s*\|.*\|\s*$""")
    private val FENCE_OPEN_REGEX = Regex("""^\s*```\s*([A-Za-z0-9_-]*)\s*$""")
    private val FENCE_CLOSE_REGEX = Regex("""^\s*```\s*$""")

    fun parse(raw: String): List<HankBlock> {
        val text = raw.replace("\r\n", "\n").trim()
        if (text.isEmpty()) return emptyList()
        val lines = text.split("\n")
        val blocks = mutableListOf<HankBlock>()
        var i = 0
        val paragraphBuf = mutableListOf<String>()
        val checklistBuf = mutableListOf<ChecklistItem>()

        fun flushParagraph() {
            if (paragraphBuf.isNotEmpty()) {
                val joined = paragraphBuf.joinToString(" ").trim()
                if (joined.isNotEmpty()) blocks.add(HankBlock.Paragraph(joined))
                paragraphBuf.clear()
            }
        }
        fun flushChecklist() {
            if (checklistBuf.isNotEmpty()) {
                blocks.add(HankBlock.Checklist(checklistBuf.toList()))
                checklistBuf.clear()
            }
        }
        fun flushAll() {
            flushParagraph()
            flushChecklist()
        }

        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            // Blank line — paragraph break.
            if (trimmed.isEmpty()) {
                flushAll()
                i++
                continue
            }

            // Fenced code block — `gauge` is the only language we render
            // specially; everything else falls through to a code paragraph.
            val fenceMatch = FENCE_OPEN_REGEX.matchEntire(trimmed)
            if (fenceMatch != null) {
                flushAll()
                val lang = fenceMatch.groupValues[1].lowercase()
                i++ // consume open fence
                val bodyLines = mutableListOf<String>()
                while (i < lines.size && !FENCE_CLOSE_REGEX.matches(lines[i])) {
                    bodyLines.add(lines[i])
                    i++
                }
                if (i < lines.size) i++ // consume close fence
                when (lang) {
                    "gauge" -> parseGauge(bodyLines)?.let { blocks.add(it) }
                    else -> {
                        // Unknown fenced block — surface as a preformatted
                        // paragraph so content is never silently swallowed.
                        val joined = bodyLines.joinToString("\n").trim()
                        if (joined.isNotEmpty()) blocks.add(HankBlock.Paragraph(joined))
                    }
                }
                continue
            }

            // Divider.
            if (DIVIDER_REGEX.matches(trimmed)) {
                flushAll()
                blocks.add(HankBlock.Divider)
                i++
                continue
            }

            // Markdown table: header row followed by separator row.
            if (TABLE_ROW_REGEX.matches(trimmed) &&
                i + 1 < lines.size &&
                TABLE_SEPARATOR_REGEX.matches(lines[i + 1].trim())
            ) {
                flushAll()
                val headers = splitTableRow(trimmed)
                val rows = mutableListOf<List<String>>()
                i += 2 // consume header + separator
                while (i < lines.size && TABLE_ROW_REGEX.matches(lines[i].trim())) {
                    rows.add(splitTableRow(lines[i].trim()))
                    i++
                }
                blocks.add(HankBlock.Table(headers, rows))
                continue
            }

            // Blockquote — callout.
            if (trimmed.startsWith(">")) {
                flushAll()
                // Gather consecutive blockquote lines.
                val buf = StringBuilder()
                while (i < lines.size && lines[i].trim().startsWith(">")) {
                    val body = lines[i].trim().removePrefix(">").trim()
                    if (body.isNotEmpty()) {
                        if (buf.isNotEmpty()) buf.append(' ')
                        buf.append(body)
                    }
                    i++
                }
                blocks.add(classifyCallout(buf.toString()))
                continue
            }

            // Step header ("## Step 3: ...").
            val stepMatch = STEP_HEADER_REGEX.matchEntire(trimmed)
            if (stepMatch != null) {
                flushAll()
                val num = stepMatch.groupValues[1].toIntOrNull()
                val title = stepMatch.groupValues[2].trim().trimEnd('.', ':')
                blocks.add(HankBlock.StepHeader(num, title))
                i++
                continue
            }

            // Generic heading ("## Finding", "### Summary").
            val headerMatch = HEADER_REGEX.matchEntire(trimmed)
            if (headerMatch != null) {
                flushAll()
                val level = headerMatch.groupValues[1].length
                val hText = headerMatch.groupValues[2].trim()
                blocks.add(HankBlock.Heading(level, hText))
                i++
                continue
            }

            // Checklist (checkbox items).
            val checkboxMatch = CHECKLIST_ITEM_REGEX.matchEntire(trimmed)
            if (checkboxMatch != null) {
                flushParagraph()
                val checked = checkboxMatch.groupValues[1].equals("x", ignoreCase = true)
                val itemText = checkboxMatch.groupValues[2].trim()
                checklistBuf.add(ChecklistItem(text = itemText, checked = checked))
                i++
                continue
            }

            // Plain bullet list — treated as an unchecked checklist so it
            // still renders as a structured list rather than a paragraph.
            val listMatch = LIST_ITEM_REGEX.matchEntire(trimmed)
            if (listMatch != null) {
                flushParagraph()
                checklistBuf.add(ChecklistItem(text = listMatch.groupValues[1].trim(), checked = false))
                i++
                continue
            }

            // Regular text — accumulate into the current paragraph.
            flushChecklist()
            paragraphBuf.add(trimmed)
            i++
        }

        flushAll()
        return blocks
    }

    /** Map a blockquote to its semantic callout type by looking at the
     *  emoji / label prefix. Defaults to Warning so Hank's "> plain" quotes
     *  still render as something visible rather than invisible text. */
    private fun classifyCallout(body: String): HankBlock {
        val clean = body.trim()
        // Lead character may be a BMP emoji or part of a surrogate pair;
        // checking startsWith on the raw string handles both because the
        // UTF-16 prefix of the emoji is also a valid comparison target.
        return when {
            clean.startsWith("\uD83D\uDC49") || // 👉
                clean.startsWith("Next:", ignoreCase = true) ||
                clean.lowercase().startsWith("do next") ->
                HankBlock.NextAction(stripPrefix(clean))
            clean.startsWith("\uD83D\uDCA1") || // 💡
                clean.startsWith("Tip:", ignoreCase = true) ||
                clean.startsWith("Hint:", ignoreCase = true) ->
                HankBlock.Tip(stripPrefix(clean))
            clean.startsWith("\u26A0") ||  // ⚠ (U+26A0)
                clean.startsWith("\u26A0\uFE0F") || // ⚠️ (with variation selector)
                clean.startsWith("Warning:", ignoreCase = true) ||
                clean.startsWith("Caution:", ignoreCase = true) ||
                clean.startsWith("Danger:", ignoreCase = true) ->
                HankBlock.Warning(stripPrefix(clean))
            else -> HankBlock.Warning(clean)
        }
    }

    /** Remove the leading emoji + label (e.g. "⚠️ Warning: ") from a
     *  callout body so the UI can apply its own visual prefix. */
    private fun stripPrefix(text: String): String {
        var s = text.trimStart()
        // Known emoji prefixes.
        val emojis =
            listOf("\u26A0\uFE0F", "\u26A0", "\uD83D\uDCA1", "\uD83D\uDC49")
        for (e in emojis) if (s.startsWith(e)) s = s.removePrefix(e).trimStart()
        // Known label prefixes.
        val labels = listOf("Warning:", "Caution:", "Danger:", "Tip:", "Hint:", "Next:", "Note:")
        for (l in labels) {
            if (s.startsWith(l, ignoreCase = true)) {
                s = s.substring(l.length).trimStart()
                break
            }
        }
        return s
    }

    private fun splitTableRow(row: String): List<String> {
        val stripped = row.trim().trim('|')
        return stripped.split('|').map { it.trim() }
    }

    /** Parse the body of a ```gauge fenced block. Accepts `key=value`
     *  lines (any order). Required keys: label, measured, spec.
     *  Optional: tolerance (default 0), unit (default ""). Returns null
     *  if required fields are missing — caller falls back to treating
     *  it as a plain code paragraph. */
    private fun parseGauge(lines: List<String>): HankBlock.Gauge? {
        val kv = mutableMapOf<String, String>()
        for (line in lines) {
            val l = line.trim()
            if (l.isEmpty()) continue
            val eq = l.indexOf('=')
            val colon = l.indexOf(':')
            val sep =
                when {
                    eq in 0..(colon - 1) || (eq >= 0 && colon < 0) -> eq
                    colon >= 0 -> colon
                    else -> -1
                }
            if (sep <= 0) continue
            val key = l.substring(0, sep).trim().lowercase()
            val value = l.substring(sep + 1).trim()
            kv[key] = value
        }
        val label = kv["label"] ?: return null
        val measured = kv["measured"]?.let { parseLooseNumber(it) } ?: return null
        val spec = kv["spec"]?.let { parseLooseNumber(it) } ?: return null
        val tolerance = kv["tolerance"]?.let { parseLooseNumber(it) } ?: 0.0
        val unit = kv["unit"].orEmpty()
        return HankBlock.Gauge(label, measured, spec, tolerance, unit)
    }

    /** Strip leading non-numeric chars and a trailing unit suffix so
     *  "1500 rpm" parses as 1500. Returns null if no number is found. */
    private fun parseLooseNumber(raw: String): Double? {
        val m = Regex("""-?\d+(\.\d+)?""").find(raw) ?: return null
        return m.value.toDoubleOrNull()
    }

    /**
     * Strip Markdown noise so ElevenLabs doesn't narrate pipes, hashes,
     * asterisks, or bullet points. Tables are flattened into "header is
     * value" sentences so the tech still hears the comparison.
     */
    fun toSpokenText(raw: String): String {
        val blocks = parse(raw)
        val sb = StringBuilder()
        blocks.forEach { b ->
            val piece =
                when (b) {
                    is HankBlock.StepHeader -> {
                        val num = b.number
                        if (num != null) "Step $num. ${b.title}." else "${b.title}."
                    }
                    is HankBlock.Heading -> "${b.text}."
                    is HankBlock.Paragraph -> stripInlineMarkdown(b.text)
                    is HankBlock.Warning -> "Warning. ${stripInlineMarkdown(b.text)}"
                    is HankBlock.Tip -> "Tip. ${stripInlineMarkdown(b.text)}"
                    is HankBlock.NextAction -> stripInlineMarkdown(b.text)
                    is HankBlock.Checklist ->
                        b.items.joinToString(" ") { "Check: ${stripInlineMarkdown(it.text)}." }
                    is HankBlock.Table -> tableToSpoken(b)
                    is HankBlock.Gauge -> gaugeToSpoken(b)
                    is HankBlock.Divider -> ""
                }
            val trimmed = piece.trim()
            if (trimmed.isNotEmpty()) {
                if (sb.isNotEmpty()) sb.append(' ')
                sb.append(trimmed)
                // Make sure each block ends with sentence punctuation so
                // the TTS sentence chunker doesn't run blocks together.
                if (!trimmed.endsWith('.') && !trimmed.endsWith('!') && !trimmed.endsWith('?')) {
                    sb.append('.')
                }
            }
        }
        return sb.toString().trim()
    }

    private fun tableToSpoken(t: HankBlock.Table): String {
        if (t.rows.isEmpty()) return t.headers.joinToString(", ")
        return t.rows.joinToString(" ") { row ->
            t.headers
                .zip(row) { h, v -> "${stripInlineMarkdown(h)} is ${stripInlineMarkdown(v)}" }
                .joinToString(", ") + "."
        }
    }

    private fun gaugeToSpoken(g: HankBlock.Gauge): String {
        val unitSuffix = if (g.unit.isNotBlank()) " ${g.unit}" else ""
        val tol =
            if (g.tolerance > 0.0) "${fmtNumber(g.spec)} plus or minus ${fmtNumber(g.tolerance)}"
            else fmtNumber(g.spec)
        return "${g.label}: ${fmtNumber(g.measured)}$unitSuffix (spec $tol$unitSuffix)."
    }

    private fun fmtNumber(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

    private fun stripInlineMarkdown(text: String): String {
        var s = text
        // Remove emphasis markers while keeping the surrounded text.
        s = s.replace(Regex("""\*\*(.+?)\*\*"""), "$1")
        s = s.replace(Regex("""__(.+?)__"""), "$1")
        s = s.replace(Regex("""\*(.+?)\*"""), "$1")
        s = s.replace(Regex("""_(.+?)_"""), "$1")
        // Inline code.
        s = s.replace(Regex("""`([^`]+)`"""), "$1")
        // Strip leftover structural punctuation.
        s = s.replace("|", " ").replace(Regex("""\s{2,}"""), " ")
        return s.trim()
    }
}
