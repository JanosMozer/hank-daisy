/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.hankdaisy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TipsScreen(modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(AppColors.Background)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        Text(
            text = "Tips",
            color = AppColors.TextPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Getting the most out of Hank.",
            color = AppColors.TextSecondary,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(18.dp))

        Tip(
            title = "Just start talking",
            body =
                "No wake word needed. Once a live stream is open, Hank is " +
                    "always listening. Ask anything — if you're not looking " +
                    "at a car, he'll answer as a normal chat.",
        )
        Tip(
            title = "Interrupt him anytime",
            body =
                "Start speaking while he's talking. Hank stops mid-sentence, " +
                    "the phone vibrates briefly, and he'll pick up on what you " +
                    "just said.",
        )
        Tip(
            title = "Move + show, don't describe",
            body =
                "If he can't see something clearly, he'll ask for a better " +
                    "angle. When you reposition and hold still for a second, " +
                    "he looks again on his own.",
        )
        Tip(
            title = "One step at a time",
            body =
                "For procedures, Hank gives you one step and then waits. Tell " +
                    "him \"done\" or just do it and move on — he verifies via " +
                    "the camera when relevant.",
        )
        Tip(
            title = "Export sessions",
            body =
                "Open a past conversation and tap the \u2191 Export button " +
                    "(JSON) or Summarize & Export (PDF report). Sessions are " +
                    "kept across app restarts.",
        )
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun Tip(title: String, body: String) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(AppColors.Surface, shape = RoundedCornerShape(12.dp))
                .padding(16.dp),
    ) {
        Text(title, color = AppColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(body, color = AppColors.TextSecondary, fontSize = 13.sp, lineHeight = 19.sp)
    }
    Spacer(Modifier.height(10.dp))
}
