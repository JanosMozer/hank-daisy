/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.hankdaisy.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * "Hank & Daisy" intro. Scales + fades in, holds briefly, then calls
 * [onFinished]. Lightweight — no Lottie or image assets.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val scale = remember { Animatable(0.7f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        scale.animateTo(1f, animationSpec = tween(520, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(Unit) {
        alpha.animateTo(1f, animationSpec = tween(380, easing = FastOutSlowInEasing))
        delay(1000)
        alpha.animateTo(0f, animationSpec = tween(280, easing = FastOutSlowInEasing))
        onFinished()
    }

    Box(
        modifier = modifier.fillMaxSize().background(AppColors.Background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Hank & Daisy",
            color = AppColors.Accent,
            fontSize = 42.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.scale(scale.value).alpha(alpha.value),
        )
    }
}
