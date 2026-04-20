/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.ui

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Landing tab for starting a live, glasses-mediated conversation.
 * Minimal UI: a hero block explaining what this is, then a prominent
 * purple "+" FAB anchored at the bottom-center to start the call.
 */
@Composable
fun ConvosScreen(
    onNewSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                text = "Convos",
                color = AppColors.Accent,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Live calls with Hank, through your glasses.",
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
                        "Starts the live stream from your Ray-Ban Meta glasses and " +
                            "opens the voice loop with Hank. He sees what you see " +
                            "and listens the whole time — just start talking.",
                    color = AppColors.TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
                Text(
                    text =
                        "Past conversations show up under the Chats tab. To talk to " +
                            "Hank without the glasses, use the Chats tab's + button.",
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
                    .clickable(onClick = onNewSession),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "+",
                color = AppColors.AccentOn,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
