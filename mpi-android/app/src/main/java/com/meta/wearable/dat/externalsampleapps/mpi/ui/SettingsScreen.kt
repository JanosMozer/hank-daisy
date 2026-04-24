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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meta.wearable.dat.externalsampleapps.mpi.session.AppSettings
import com.meta.wearable.dat.externalsampleapps.mpi.session.TextScale
import com.meta.wearable.dat.externalsampleapps.mpi.session.ThemeMode

@Composable
fun SettingsScreen(
    settings: AppSettings,
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
                subtitle = "Darker borders + stronger text contrast.",
                value = settings.highContrast,
                onChange = onHighContrastChange,
            )
            Spacer(Modifier.height(10.dp))
            ToggleRow(
                label = "Haptic feedback",
                subtitle = "Vibrate when Hank barges in or you interrupt.",
                value = settings.hapticFeedback,
                onChange = onHapticChange,
            )
        }

        Spacer(Modifier.height(14.dp))

        SettingsSection(title = "About") {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Version", color = AppColors.TextSecondary, fontSize = 13.sp)
                Text("0.1 · beta", color = AppColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Model", color = AppColors.TextSecondary, fontSize = 13.sp)
                Text("Gemini 3.1 flash lite", color = AppColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Voice", color = AppColors.TextSecondary, fontSize = 13.sp)
                Text("ElevenLabs flash v2.5", color = AppColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Text(
        text = title.uppercase(),
        color = AppColors.TextMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(6.dp))
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(AppColors.Surface, shape = RoundedCornerShape(12.dp))
                .padding(16.dp),
    ) {
        content()
    }
}

@Composable
private fun ToggleRow(label: String, subtitle: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = AppColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = AppColors.TextSecondary, fontSize = 11.sp)
        }
        Switch(
            checked = value,
            onCheckedChange = onChange,
            colors =
                SwitchDefaults.colors(
                    checkedThumbColor = AppColors.AccentOn,
                    checkedTrackColor = AppColors.Accent,
                    uncheckedThumbColor = AppColors.Surface,
                    uncheckedTrackColor = AppColors.Border,
                ),
        )
    }
}

@Composable
private fun SegmentedRow(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Text(label, color = AppColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(AppColors.SurfaceAlt, shape = RoundedCornerShape(10.dp))
                .padding(3.dp),
    ) {
        options.forEachIndexed { i, opt ->
            val isSelected = i == selectedIndex
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .background(
                            if (isSelected) AppColors.Accent else AppColors.SurfaceAlt,
                            shape = RoundedCornerShape(8.dp),
                        )
                        .clickable { onSelect(i) }
                        .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = opt,
                    color = if (isSelected) AppColors.AccentOn else AppColors.TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
