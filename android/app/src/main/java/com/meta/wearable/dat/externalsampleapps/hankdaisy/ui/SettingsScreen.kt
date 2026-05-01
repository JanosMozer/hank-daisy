/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.hankdaisy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meta.wearable.dat.externalsampleapps.hankdaisy.session.GeneralSettings
import com.meta.wearable.dat.externalsampleapps.hankdaisy.session.TextScale
import com.meta.wearable.dat.externalsampleapps.hankdaisy.session.ThemeMode
import com.meta.wearable.dat.externalsampleapps.hankdaisy.session.WorkDomain

@Composable
fun SettingsScreen(
    settings: GeneralSettings,
    onWorkDomainChange: (WorkDomain) -> Unit,
    onDemoCommentaryModeChange: (Boolean) -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onTextScaleChange: (TextScale) -> Unit,
    onHighContrastChange: (Boolean) -> Unit,
    onHapticChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
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
            text = "Settings",
            color = AppColors.TextPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(20.dp))

        SettingsSection(title = "Assistant") {
            SegmentedRow(
                label = "Mode",
                options = WorkDomain.values().map { it.segmentLabel },
                selectedIndex = settings.workDomain.ordinal,
                onSelect = { onWorkDomainChange(WorkDomain.values()[it]) },
            )
            Spacer(Modifier.height(8.dp))
            HelperText(text = settings.workDomain.modeDescription)
            Spacer(Modifier.height(14.dp))
            ToggleRow(
                label = "Visual demo mode",
                subtitle =
                    "Turns off the mic loop. Hank narrates scene changes, likely next steps, and common issues when relevant.",
                value = settings.demoCommentaryMode,
                onChange = onDemoCommentaryModeChange,
            )
        }

        Spacer(Modifier.height(14.dp))

        SettingsSection(title = "Appearance") {
            SegmentedRow(
                label = "Theme",
                options = ThemeMode.values().map { it.name.lowercase().replaceFirstChar(Char::titlecase) },
                selectedIndex = settings.themeMode.ordinal,
                onSelect = { onThemeChange(ThemeMode.values()[it]) },
            )
            Spacer(Modifier.height(14.dp))
            SegmentedRow(
                label = "Text size",
                options = TextScale.values().map { it.label },
                selectedIndex = settings.textScale.ordinal,
                onSelect = { onTextScaleChange(TextScale.values()[it]) },
            )
        }

        Spacer(Modifier.height(14.dp))

        SettingsSection(title = "Accessibility") {
            ToggleRow(
                label = "High contrast",
                subtitle = "Darker borders and stronger text contrast.",
                value = settings.highContrast,
                onChange = onHighContrastChange,
            )
            Spacer(Modifier.height(10.dp))
            ToggleRow(
                label = "Haptic feedback",
                subtitle = "Vibrate when Hank barges in or when the demo flips into follow-up listening.",
                value = settings.hapticFeedback,
                onChange = onHapticChange,
            )
        }

        Spacer(Modifier.height(14.dp))

        SettingsSection(title = "About") {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Version", color = AppColors.TextSecondary, fontSize = 13.sp)
                Text("0.1 · demo", color = AppColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Shell", color = AppColors.TextSecondary, fontSize = 13.sp)
                Text("Demo / Pipeline / Settings", color = AppColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Vision model", color = AppColors.TextSecondary, fontSize = 13.sp)
                Text("Gemini 3.1 flash lite", color = AppColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Voice model", color = AppColors.TextSecondary, fontSize = 13.sp)
                Text("ElevenLabs flash v2.5", color = AppColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(80.dp))
    }
}
