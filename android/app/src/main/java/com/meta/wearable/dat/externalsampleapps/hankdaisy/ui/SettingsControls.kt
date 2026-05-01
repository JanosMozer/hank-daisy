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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class SettingOption(
    val label: String,
    val description: String = "",
    val enabled: Boolean = true,
)

@Composable
fun SettingsSection(title: String, content: @Composable () -> Unit) {
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
fun ToggleRow(
    label: String,
    subtitle: String,
    value: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.55f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = AppColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = AppColors.TextSecondary, fontSize = 11.sp)
        }
        Switch(
            checked = value,
            enabled = enabled,
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
fun SegmentedRow(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    enabled: Boolean = true,
) {
    Text(label, color = AppColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .alpha(if (enabled) 1f else 0.55f)
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
                        .clickable(enabled = enabled) { onSelect(i) }
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

@Composable
fun OptionList(
    label: String,
    options: List<SettingOption>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    enabled: Boolean = true,
) {
    Text(label, color = AppColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(AppColors.SurfaceAlt, shape = RoundedCornerShape(10.dp))
                .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = index == selectedIndex
            val isEnabled = enabled && option.enabled
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .alpha(if (isEnabled) 1f else 0.45f)
                        .background(
                            if (isSelected) AppColors.Accent.copy(alpha = 0.12f) else AppColors.Surface,
                            shape = RoundedCornerShape(8.dp),
                        )
                        .clickable(enabled = isEnabled) { onSelect(index) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .background(
                                if (isSelected) AppColors.Accent else AppColors.Border,
                                shape = RoundedCornerShape(999.dp),
                            )
                            .width(10.dp)
                            .height(10.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = option.label,
                        color = AppColors.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (option.description.isNotBlank()) {
                        Text(
                            text = option.description,
                            color = AppColors.TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SliderRow(
    label: String,
    subtitle: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueLabel: String,
    enabled: Boolean = true,
) {
    Column(modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.55f)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, color = AppColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = AppColors.TextSecondary, fontSize = 11.sp)
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = valueLabel,
                color = AppColors.TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
        )
    }
}

@Composable
fun TextFieldRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean = true,
    placeholder: String = "",
) {
    Text(label, color = AppColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        placeholder = {
            if (placeholder.isNotBlank()) {
                Text(placeholder, color = AppColors.TextMuted, fontSize = 12.sp)
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun HelperText(text: String) {
    Text(
        text = text,
        color = AppColors.TextSecondary,
        fontSize = 11.sp,
        lineHeight = 16.sp,
    )
}
