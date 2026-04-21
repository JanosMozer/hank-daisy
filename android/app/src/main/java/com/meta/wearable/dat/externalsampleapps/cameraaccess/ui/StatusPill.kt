/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Four possible conversational states, derived by the composable below. */
private enum class Status { Ready, Capturing, Thinking, Speaking }

/**
 * Single always-visible status indicator. Cross-fades between states instead of
 * popping separate banners in and out, which is the source of the UX flicker
 * between "listening → thinking → speaking" turns.
 */
@Composable
fun StatusPill(
    isListening: Boolean,
    isAnalyzing: Boolean,
    isSpeaking: Boolean,
    modifier: Modifier = Modifier,
) {
    val status =
        when {
            isAnalyzing -> Status.Thinking
            isSpeaking -> Status.Speaking
            isListening -> Status.Capturing
            else -> Status.Ready
        }

    AnimatedContent(
        targetState = status,
        modifier = modifier,
        transitionSpec = {
            fadeIn(animationSpec = tween(200, easing = FastOutSlowInEasing)) togetherWith
                fadeOut(animationSpec = tween(200, easing = FastOutSlowInEasing))
        },
        label = "StatusPill",
    ) { s ->
        val (bg, dot, label) =
            when (s) {
                Status.Ready ->
                    Triple(
                        Color(0xFF065F46).copy(alpha = 0.85f),
                        Color(0xFF34D399),
                        "Hank's listening",
                    )
                Status.Capturing ->
                    Triple(
                        Color(0xFF1E40AF).copy(alpha = 0.92f),
                        Color(0xFF60A5FA),
                        "Listening…",
                    )
                Status.Thinking ->
                    Triple(
                        Color(0xFF1F2937).copy(alpha = 0.92f),
                        Color(0xFF9CA3AF),
                        "Thinking…",
                    )
                Status.Speaking ->
                    Triple(
                        // Teal-tinted surface + bright teal dot to match the
                        // new brand palette. Kept saturated so the "Hank's
                        // talking" cue stands out against the camera feed.
                        Color(0xFF134E4A).copy(alpha = 0.92f),
                        Color(0xFF2DD4BF),
                        "Hank speaking",
                    )
            }

        Box(
            modifier =
                Modifier
                    .background(bg, shape = RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (s) {
                    Status.Thinking ->
                        CircularProgressIndicator(
                            color = dot,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(14.dp),
                        )
                    Status.Capturing -> PulsingDot(dot)
                    Status.Speaking -> PulsingDot(dot)
                    Status.Ready -> SteadyDot(dot)
                }
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun PulsingDot(color: Color) {
    val infinite = rememberInfiniteTransition(label = "pulse")
    val alpha by
        infinite.animateFloat(
            initialValue = 0.4f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(600, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "pulseAlpha",
        )
    Box(
        modifier =
            Modifier
                .size(10.dp)
                .alpha(alpha)
                .background(color, shape = RoundedCornerShape(5.dp)),
    )
}

@Composable
private fun SteadyDot(color: Color) {
    Box(
        modifier =
            Modifier
                .size(10.dp)
                .background(color, shape = RoundedCornerShape(5.dp)),
    )
}
