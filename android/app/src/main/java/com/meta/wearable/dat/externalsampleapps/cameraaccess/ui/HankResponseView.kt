/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meta.wearable.dat.externalsampleapps.cameraaccess.stream.ChecklistItem
import com.meta.wearable.dat.externalsampleapps.cameraaccess.stream.GaugeVerdict
import com.meta.wearable.dat.externalsampleapps.cameraaccess.stream.HankBlock
import com.meta.wearable.dat.externalsampleapps.cameraaccess.stream.HankMarkdown

/**
 * Renders one of Hank's structured replies as a column of dedicated
 * block components — step cards, warning callouts, spec tables, check
 * lists — instead of a flat paragraph of text.
 *
 * Accepts the raw Markdown string that came back from the LLM and
 * memoises the parse per-string so recomposition doesn't re-tokenise.
 * Safe to call with garbage input — anything we can't classify becomes
 * a plain paragraph.
 *
 * Styling leans on [AppColors] so light/dark + high-contrast + text-scale
 * settings all propagate automatically.
 */
@Composable
fun HankResponseView(
    raw: String,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(raw) { HankMarkdown.parse(raw) }
    if (blocks.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        blocks.forEach { block ->
            when (block) {
                is HankBlock.StepHeader -> StepHeaderCard(block)
                is HankBlock.Heading -> HeadingLine(block)
                is HankBlock.Paragraph -> ParagraphText(block.text)
                is HankBlock.Warning -> CalloutBox(CalloutKind.WARNING, block.text)
                is HankBlock.Tip -> CalloutBox(CalloutKind.TIP, block.text)
                is HankBlock.NextAction -> CalloutBox(CalloutKind.NEXT, block.text)
                is HankBlock.Checklist -> ChecklistBlock(block.items)
                is HankBlock.Table -> TableBlock(block)
                is HankBlock.Gauge -> GaugeBlock(block)
                is HankBlock.Divider ->
                    Spacer(
                        modifier =
                            Modifier.fillMaxWidth()
                                .height(1.dp)
                                .background(AppColors.Border),
                    )
            }
        }
    }
}

@Composable
private fun StepHeaderCard(h: HankBlock.StepHeader) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(AppColors.Accent, shape = RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (h.number != null) {
            Box(
                modifier =
                    Modifier
                        .size(28.dp)
                        .background(AppColors.AccentOn, shape = CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = h.number.toString(),
                    color = AppColors.Accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(12.dp))
        }
        Column {
            Text(
                text = if (h.number != null) "STEP ${h.number}" else "STEP",
                color = AppColors.AccentOn.copy(alpha = 0.85f),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = h.title,
                color = AppColors.AccentOn,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 20.sp,
            )
        }
    }
}

@Composable
private fun HeadingLine(h: HankBlock.Heading) {
    val size = if (h.level <= 1) 17.sp else if (h.level == 2) 15.sp else 14.sp
    Text(
        text = h.text.uppercase(),
        color = AppColors.TextPrimary,
        fontSize = size,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun ParagraphText(text: String) {
    Text(
        text = inlineAnnotated(text),
        color = AppColors.TextPrimary,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    )
}

private enum class CalloutKind { WARNING, TIP, NEXT }

@Composable
private fun CalloutBox(kind: CalloutKind, text: String) {
    val (bg, fg, border, prefix) =
        when (kind) {
            CalloutKind.WARNING ->
                Quad(
                    Color(0xFFFEE2E2), // red-100
                    Color(0xFF991B1B), // red-800
                    Color(0xFFEF4444), // red-500 border
                    "Warning",
                )
            CalloutKind.TIP ->
                Quad(
                    AppColors.AccentSoft,
                    AppColors.HankBubbleText,
                    AppColors.Accent,
                    "Tip",
                )
            CalloutKind.NEXT ->
                Quad(
                    AppColors.Accent,
                    AppColors.AccentOn,
                    AppColors.Accent,
                    "Do next",
                )
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(bg, shape = RoundedCornerShape(12.dp))
                .padding(12.dp),
    ) {
        // Left edge indicator — a vertical stripe in the "border" colour.
        Box(
            modifier =
                Modifier
                    .width(3.dp)
                    .height(44.dp)
                    .background(border, shape = RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = prefix.uppercase(),
                color = fg.copy(alpha = 0.85f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = inlineAnnotated(text),
                color = fg,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = if (kind == CalloutKind.NEXT) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

@Composable
private fun ChecklistBlock(items: List<ChecklistItem>) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(AppColors.Surface, shape = RoundedCornerShape(12.dp))
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                val ringColor = if (item.checked) AppColors.Accent else AppColors.Border
                val fillColor = if (item.checked) AppColors.Accent else Color.Transparent
                Box(
                    modifier =
                        Modifier
                            .size(18.dp)
                            .background(fillColor, shape = RoundedCornerShape(4.dp))
                            .border(1.5.dp, ringColor, shape = RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (item.checked) {
                        Text(
                            text = "\u2713", // ✓
                            color = AppColors.AccentOn,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = inlineAnnotated(item.text),
                    color = AppColors.TextPrimary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun TableBlock(t: HankBlock.Table) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(AppColors.Surface, shape = RoundedCornerShape(12.dp))
                .border(
                    width = 1.dp,
                    color = AppColors.Border,
                    shape = RoundedCornerShape(12.dp),
                ),
    ) {
        // Header row — tinted accent background, bold text.
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        AppColors.AccentSoft,
                        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                    )
                    .padding(vertical = 10.dp, horizontal = 12.dp),
        ) {
            t.headers.forEach { h ->
                Text(
                    text = h,
                    color = AppColors.HankBubbleText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        // Data rows — if a column is named "Verdict" / "Result" / "Status",
        // colour-code its cell so PASS / OK reads green, FAIL / NG reads
        // red, and neutral values stay normal. Lets mechanics scan a
        // results table at a glance.
        val verdictColIdx =
            t.headers.indexOfFirst {
                val k = it.trim().lowercase()
                k == "verdict" || k == "result" || k == "status"
            }
        t.rows.forEachIndexed { idx, row ->
            val bg = if (idx % 2 == 0) AppColors.Surface else AppColors.SurfaceAlt
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(bg)
                        .padding(vertical = 10.dp, horizontal = 12.dp),
            ) {
                val padded = row + List((t.headers.size - row.size).coerceAtLeast(0)) { "" }
                padded.take(t.headers.size).forEachIndexed { col, cell ->
                    val color =
                        if (col == verdictColIdx) verdictColor(cell) else AppColors.TextPrimary
                    val weight =
                        if (col == verdictColIdx) FontWeight.SemiBold else FontWeight.Normal
                    Text(
                        text = inlineAnnotated(cell),
                        color = color,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        fontWeight = weight,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** Semantic colour for a verdict-column cell. Matches common mechanic
 *  shorthand (PASS/FAIL, OK/NG, GOOD/BAD) so Hank doesn't have to use
 *  one specific vocabulary. */
@Composable
private fun verdictColor(cell: String): Color {
    val k = cell.trim().lowercase()
    return when {
        k.contains("pass") || k.contains("ok") || k.contains("good") || k == "✓" ->
            Color(0xFF0D9488) // teal-600
        k.contains("fail") ||
            k.contains("bad") ||
            k.contains("ng") ||
            k.contains("critical") ||
            k == "✗" || k == "x" ->
            Color(0xFFB91C1C) // red-700
        k.contains("warn") || k.contains("marginal") || k.contains("hold") ->
            Color(0xFFB45309) // amber-700
        else -> AppColors.TextPrimary
    }
}

@Composable
private fun GaugeBlock(g: HankBlock.Gauge) {
    val verdict = g.verdict
    val (barColor, label) =
        when (verdict) {
            GaugeVerdict.OK -> Color(0xFF0D9488) to "Within spec"
            GaugeVerdict.WARN -> Color(0xFFB45309) to "Near limit"
            GaugeVerdict.OUT -> Color(0xFFB91C1C) to "Out of spec"
        }
    // Domain for the bar. Spec ± (max(tolerance * 3, |measured - spec|))
    // so the measured value is always visible even if it blew past tol.
    val halfRange =
        maxOf(
            if (g.tolerance > 0) g.tolerance * 3.0 else kotlin.math.abs(g.measured - g.spec) * 1.5,
            kotlin.math.abs(g.measured - g.spec) * 1.2,
            1.0,
        )
    val domainLow = g.spec - halfRange
    val domainHigh = g.spec + halfRange
    val measuredFrac =
        ((g.measured - domainLow) / (domainHigh - domainLow)).coerceIn(0.02, 0.98).toFloat()
    val toleranceLowFrac =
        ((g.spec - g.tolerance - domainLow) / (domainHigh - domainLow))
            .coerceIn(0.0, 1.0)
            .toFloat()
    val toleranceHighFrac =
        ((g.spec + g.tolerance - domainLow) / (domainHigh - domainLow))
            .coerceIn(0.0, 1.0)
            .toFloat()

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(AppColors.Surface, shape = RoundedCornerShape(12.dp))
                .border(1.dp, AppColors.Border, RoundedCornerShape(12.dp))
                .padding(12.dp),
    ) {
        // Header: label on the left, numeric readout on the right.
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = g.label,
                color = AppColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${fmtNumberUi(g.measured)}${g.unit.prependIfNotBlank()}",
                color = barColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(6.dp))

        // Track + tolerance window + measured marker. Drawn as three
        // stacked Boxes so we avoid a whole Canvas dependency here.
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .background(AppColors.SurfaceAlt, shape = RoundedCornerShape(6.dp)),
        ) {
            if (g.tolerance > 0) {
                // Green band — the acceptable spec window.
                androidx.compose.foundation.layout.BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth().height(12.dp),
                ) {
                    val w = maxWidth
                    Box(
                        modifier =
                            Modifier
                                .padding(start = w * toleranceLowFrac)
                                .width(w * (toleranceHighFrac - toleranceLowFrac).coerceAtLeast(0f))
                                .height(12.dp)
                                .background(
                                    Color(0xFF0D9488).copy(alpha = 0.25f),
                                    shape = RoundedCornerShape(6.dp),
                                ),
                    )
                    // Measured marker — thin vertical bar at measuredFrac.
                    Box(
                        modifier =
                            Modifier
                                .padding(start = (w * measuredFrac) - 1.5.dp)
                                .width(3.dp)
                                .height(12.dp)
                                .background(barColor, shape = RoundedCornerShape(1.5.dp)),
                    )
                }
            } else {
                // No tolerance — still show the marker so the user has
                // SOMETHING to look at.
                androidx.compose.foundation.layout.BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth().height(12.dp),
                ) {
                    val w = maxWidth
                    Box(
                        modifier =
                            Modifier
                                .padding(start = (w * measuredFrac) - 1.5.dp)
                                .width(3.dp)
                                .height(12.dp)
                                .background(barColor, shape = RoundedCornerShape(1.5.dp)),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))

        // Footer: spec + tolerance + verdict chip.
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val specText =
                if (g.tolerance > 0)
                    "Spec: ${fmtNumberUi(g.spec)} ± ${fmtNumberUi(g.tolerance)}${g.unit.prependIfNotBlank()}"
                else "Spec: ${fmtNumberUi(g.spec)}${g.unit.prependIfNotBlank()}"
            Text(
                text = specText,
                color = AppColors.TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier =
                    Modifier
                        .background(
                            barColor.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(999.dp),
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    text = label,
                    color = barColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private fun String.prependIfNotBlank(): String = if (this.isBlank()) "" else " $this"

private fun fmtNumberUi(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else "%.2f".format(v)

/**
 * Turn a string with `**bold**` / `*italic*` / `` `code` `` into an
 * [AnnotatedString] so the renderer can show inline emphasis without
 * needing a full Markdown library.
 */
private fun inlineAnnotated(text: String): AnnotatedString = buildAnnotatedString {
    val tokens = tokeniseInline(text)
    tokens.forEach { (kind, span) ->
        when (kind) {
            InlineKind.BOLD -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(span) }
            InlineKind.ITALIC -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(span) }
            InlineKind.CODE ->
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(span) }
            InlineKind.PLAIN -> append(span)
        }
    }
}

private enum class InlineKind { PLAIN, BOLD, ITALIC, CODE }

/** Minimal tokenizer: greedy match on `**...**`, `*...*`, and `` `...` ``.
 *  Not strictly CommonMark-correct around edge cases — good enough for
 *  Hank's typical replies. */
private fun tokeniseInline(text: String): List<Pair<InlineKind, String>> {
    val out = mutableListOf<Pair<InlineKind, String>>()
    var i = 0
    val buf = StringBuilder()
    fun flushPlain() {
        if (buf.isNotEmpty()) {
            out.add(InlineKind.PLAIN to buf.toString())
            buf.clear()
        }
    }
    while (i < text.length) {
        // Bold **...**
        if (i + 1 < text.length && text[i] == '*' && text[i + 1] == '*') {
            val close = text.indexOf("**", i + 2)
            if (close >= 0) {
                flushPlain()
                out.add(InlineKind.BOLD to text.substring(i + 2, close))
                i = close + 2
                continue
            }
        }
        // Italic *...*
        if (text[i] == '*') {
            val close = text.indexOf('*', i + 1)
            if (close >= 0) {
                flushPlain()
                out.add(InlineKind.ITALIC to text.substring(i + 1, close))
                i = close + 1
                continue
            }
        }
        // Inline code `...`
        if (text[i] == '`') {
            val close = text.indexOf('`', i + 1)
            if (close >= 0) {
                flushPlain()
                out.add(InlineKind.CODE to text.substring(i + 1, close))
                i = close + 1
                continue
            }
        }
        buf.append(text[i])
        i++
    }
    flushPlain()
    return out
}

