/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Palette resolved via CompositionLocal so we can swap light/dark at runtime.
 * Every screen keeps reading `AppColors.X` — the getter looks up the current
 * palette from the composition, so flipping a setting instantly re-skins
 * everything.
 */
data class AppPalette(
    val Background: Color,
    val Surface: Color,
    val SurfaceAlt: Color,
    val Border: Color,
    val TextPrimary: Color,
    val TextSecondary: Color,
    val TextMuted: Color,
    val Accent: Color,
    val AccentSoft: Color,
    val AccentOn: Color,
    val UserBubble: Color,
    val UserBubbleText: Color,
    val HankBubble: Color,
    val HankBubbleText: Color,
)

// Teal-based brand palette — pulled from the Hank & Daisy logo (dark teal
// script strokes + mint body). Everything else stays neutral B&W so the
// teal does the whole job of "app identity".
val LightPalette =
    AppPalette(
        Background = Color(0xFFF9FAFB),
        Surface = Color(0xFFFFFFFF),
        SurfaceAlt = Color(0xFFF3F4F6),
        Border = Color(0xFFE5E7EB),
        TextPrimary = Color(0xFF111827),
        TextSecondary = Color(0xFF6B7280),
        TextMuted = Color(0xFF9CA3AF),
        // Accent = teal-600: saturated enough to read as a headline colour
        // at 13sp, close to the dark outlines of the logo script.
        Accent = Color(0xFF0D9488),
        // Pale mint — matches the interior fill of the logo badge.
        AccentSoft = Color(0xFFCCFBF1),
        AccentOn = Color(0xFFFFFFFF),
        UserBubble = Color(0xFF0D9488),       // brand teal
        UserBubbleText = Color(0xFFFFFFFF),
        HankBubble = Color(0xFFCCFBF1),       // soft teal tint
        HankBubbleText = Color(0xFF0F766E),   // deep teal text (teal-700)
    )

val DarkPalette =
    AppPalette(
        Background = Color(0xFF0B0B0D),
        Surface = Color(0xFF1F2937),
        SurfaceAlt = Color(0xFF374151),
        Border = Color(0xFF4B5563),
        TextPrimary = Color(0xFFF3F4F6),
        TextSecondary = Color(0xFF9CA3AF),
        TextMuted = Color(0xFF6B7280),
        // Brighter teal on dark — teal-400 pops against the near-black
        // background without glowing the way a deeper teal would.
        Accent = Color(0xFF2DD4BF),
        AccentSoft = Color(0xFF134E4A),       // teal-900 surface tint
        AccentOn = Color(0xFF042F2E),         // very dark teal text on accent
        UserBubble = Color(0xFF14B8A6),       // teal-500, saturated
        UserBubbleText = Color(0xFFFFFFFF),
        HankBubble = Color(0xFF0F3D3A),       // dark teal-tinted surface
        HankBubbleText = Color(0xFF99F6E4),   // teal-200 text
    )

val LocalAppPalette = staticCompositionLocalOf { LightPalette }

/** Back-compat façade — existing screens keep using AppColors.X. */
object AppColors {
    val Background: Color
        @Composable @ReadOnlyComposable
        get() = LocalAppPalette.current.Background
    val Surface: Color
        @Composable @ReadOnlyComposable
        get() = LocalAppPalette.current.Surface
    val SurfaceAlt: Color
        @Composable @ReadOnlyComposable
        get() = LocalAppPalette.current.SurfaceAlt
    val Border: Color
        @Composable @ReadOnlyComposable
        get() = LocalAppPalette.current.Border
    val TextPrimary: Color
        @Composable @ReadOnlyComposable
        get() = LocalAppPalette.current.TextPrimary
    val TextSecondary: Color
        @Composable @ReadOnlyComposable
        get() = LocalAppPalette.current.TextSecondary
    val TextMuted: Color
        @Composable @ReadOnlyComposable
        get() = LocalAppPalette.current.TextMuted
    val Accent: Color
        @Composable @ReadOnlyComposable
        get() = LocalAppPalette.current.Accent
    val AccentSoft: Color
        @Composable @ReadOnlyComposable
        get() = LocalAppPalette.current.AccentSoft
    val AccentOn: Color
        @Composable @ReadOnlyComposable
        get() = LocalAppPalette.current.AccentOn
    val UserBubble: Color
        @Composable @ReadOnlyComposable
        get() = LocalAppPalette.current.UserBubble
    val UserBubbleText: Color
        @Composable @ReadOnlyComposable
        get() = LocalAppPalette.current.UserBubbleText
    val HankBubble: Color
        @Composable @ReadOnlyComposable
        get() = LocalAppPalette.current.HankBubble
    val HankBubbleText: Color
        @Composable @ReadOnlyComposable
        get() = LocalAppPalette.current.HankBubbleText
}
