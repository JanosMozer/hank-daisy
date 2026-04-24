/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.mpi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Landing tab for starting a live glasses-based evidence capture session.
 */
@Composable
fun ConvosScreen(
    onNewSession: () -> Unit,
    /** True once navigateToStreaming has set isStreaming=true. If it stays
     * false for too long after a tap, we surface an error so the user
     * knows the stream never actually kicked off. */
    isStreaming: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var starting by remember { mutableStateOf(false) }
    var timedOut by remember { mutableStateOf(false) }

    // If the scaffold flips to streaming, the whole screen swaps to
    // StreamScreen; this state just resets so the overlay is gone on return.
    LaunchedEffect(isStreaming) {
        if (isStreaming) {
            starting = false
            timedOut = false
        }
    }
    // Timeout: if starting is still true 8s after a tap, assume the stream
    // never kicked off (glasses not paired, permission revoked, etc.).
    LaunchedEffect(starting) {
        if (starting) {
            timedOut = false
            delay(8_000)
            if (starting) timedOut = true
        }
    }
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(AppColors.Background)
                .statusBarsPadding()
                .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 32.dp),
        ) {
            Text(
                text = "Capture",
                color = AppColors.Accent,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Hands-free inspection capture through your glasses.",
                color = AppColors.TextSecondary,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(28.dp))
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(
                            AppColors.Surface,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                        )
                        .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Tap the + below",
                    color = AppColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text =
                        "Starts the live stream from your Meta glasses so you can " +
                            "record findings, show measurements, and narrate the inspection " +
                            "without putting tools down.",
                    color = AppColors.TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
                Text(
                    text =
                        "Saved evidence sessions land under Sessions. Use Inspections to tie " +
                            "capture back to a specific RO and finding set.",
                    color = AppColors.TextMuted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
        }

        // Prominent purple + at the bottom-center
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp)
                    .size(72.dp)
                    .background(AppColors.Accent, shape = CircleShape)
                    .clickable(enabled = !starting) {
                        starting = true
                        timedOut = false
                        onNewSession()
                    },
            contentAlignment = Alignment.Center,
        ) {
            if (starting) {
                CircularProgressIndicator(
                    color = AppColors.AccentOn,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(28.dp),
                )
            } else {
                Text(
                    text = "+",
                    color = AppColors.AccentOn,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // "Starting camera…" pill above the button while we wait for DAT.
        if (starting && !timedOut) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 116.dp)
                        .background(
                            AppColors.Surface,
                            shape = RoundedCornerShape(12.dp),
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    text = "Starting camera…",
                    color = AppColors.TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        // After ~8s with no stream, surface a practical message so the user
        // isn't stuck staring at a spinner.
        if (timedOut) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 116.dp, start = 24.dp, end = 24.dp)
                        .background(
                            androidx.compose.ui.graphics.Color(0xFFB91C1C),
                            shape = RoundedCornerShape(12.dp),
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    text =
                        "Couldn't start the glasses stream. Check that your Meta glasses are " +
                            "paired in the Meta AI app and Camera permission is granted, then " +
                            "tap + again.",
                    color = androidx.compose.ui.graphics.Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
