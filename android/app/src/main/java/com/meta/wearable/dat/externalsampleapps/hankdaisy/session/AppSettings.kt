/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.hankdaisy.session

enum class ThemeMode { LIGHT, DARK, SYSTEM }

enum class TextScale(val factor: Float, val label: String) {
    SMALL(0.9f, "Small"),
    NORMAL(1.0f, "Normal"),
    LARGE(1.15f, "Large"),
    EXTRA_LARGE(1.35f, "Extra large"),
}

data class AppSettings(
    val captureMode: CaptureMode = CaptureMode.GLASSES,
    val speechRecognitionRoute: SpeechRecognitionRoute = SpeechRecognitionRoute.ANDROID,
    val workDomain: WorkDomain = WorkDomain.CAR,
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val textScale: TextScale = TextScale.NORMAL,
    val highContrast: Boolean = false,
    val hapticFeedback: Boolean = true,
    val demoCommentaryMode: Boolean = false,
)
