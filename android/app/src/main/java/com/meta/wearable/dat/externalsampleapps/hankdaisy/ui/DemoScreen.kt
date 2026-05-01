/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.hankdaisy.ui

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
import com.meta.wearable.dat.externalsampleapps.hankdaisy.session.AppConfig
import com.meta.wearable.dat.externalsampleapps.hankdaisy.session.CaptureMode
import kotlinx.coroutines.delay

@Composable
fun DemoScreen(
    config: AppConfig,
    isStreaming: Boolean,
    onStartDemo: () -> Unit,
    onCaptureModeChange: (CaptureMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    var starting by remember { mutableStateOf(false) }
    var timedOut by remember { mutableStateOf(false) }
    val captureMode = config.demo.captureMode
    val usingPhoneCamera = captureMode == CaptureMode.PHONE_CAMERA
    val workDomain = config.general.workDomain

    LaunchedEffect(isStreaming) {
        if (isStreaming) {
            starting = false
            timedOut = false
        }
    }
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 24.dp),
        ) {
            Text(
                text = "Demo",
                color = AppColors.Accent,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "One shared live-assist flow, switchable between glasses and phone hardware.",
                color = AppColors.TextSecondary,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(18.dp))
            SegmentedRow(
                label = "Mode",
                options = listOf("Glasses", "Phone"),
                selectedIndex = if (captureMode == CaptureMode.GLASSES) 0 else 1,
                onSelect = {
                    onCaptureModeChange(
                        if (it == 0) CaptureMode.GLASSES else CaptureMode.PHONE_CAMERA,
                    )
                },
            )
            Spacer(Modifier.height(22.dp))
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(AppColors.Surface, shape = RoundedCornerShape(14.dp))
                        .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = if (usingPhoneCamera) "Phone demo" else "Glasses demo",
                    color = AppColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text =
                        if (usingPhoneCamera) {
                            "Runs Hank through the Android camera, microphone, and speaker. No glasses required."
                        } else {
                            "Runs Hank through the DAT glasses flow, with glasses video and glasses-preferred audio routing."
                        },
                    color = AppColors.TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
                Text(
                    text =
                        buildString {
                            append("Work domain: ")
                            append(workDomain.settingsLabel)
                            append('\n')
                            append("Speech route: ")
                            append(config.audio.transcription.route.settingsLabel)
                            append('\n')
                            append("Source FPS: ")
                            append(config.video.sourceFps)
                            append(" · Model FPS: ")
                            append(config.video.modelSendFps)
                        },
                    color = AppColors.TextMuted,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
            }
            Spacer(Modifier.height(14.dp))
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(AppColors.Surface, shape = RoundedCornerShape(14.dp))
                        .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Start flow",
                    color = AppColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text =
                        if (usingPhoneCamera) {
                            "Starts the phone camera stream and the live voice loop. Hank sees the current scene and answers through the phone."
                        } else {
                            workDomain.convoBody()
                        },
                    color = AppColors.TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
            }
        }

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
                        onStartDemo()
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

        if (starting && !timedOut) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 116.dp)
                        .background(AppColors.Surface, shape = RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    text = "Starting demo…",
                    color = AppColors.TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        if (timedOut) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 24.dp, vertical = 120.dp)
                        .background(AppColors.Surface, shape = RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text =
                        if (usingPhoneCamera) {
                            "Phone demo failed to start. Check camera and microphone permissions, then try again."
                        } else {
                            "Glasses demo failed to start. Check Meta AI pairing, registration, and camera permission, then try again."
                        },
                    color = AppColors.TextPrimary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
            }
        }
    }
}
