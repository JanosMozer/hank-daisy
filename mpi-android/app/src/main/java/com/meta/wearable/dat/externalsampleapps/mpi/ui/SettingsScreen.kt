package com.meta.wearable.dat.externalsampleapps.mpi.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meta.wearable.dat.externalsampleapps.mpi.session.AppConfig
import com.meta.wearable.dat.externalsampleapps.mpi.session.DomainMode
import com.meta.wearable.dat.externalsampleapps.mpi.session.SpeechRecognitionRoute
import com.meta.wearable.dat.externalsampleapps.mpi.session.TextScale
import com.meta.wearable.dat.externalsampleapps.mpi.session.ThemeMode
import com.meta.wearable.dat.externalsampleapps.mpi.session.VoiceSpeed
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    config: AppConfig,
    onConfigChange: (AppConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(AppColors.Background)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Settings",
            color = AppColors.TextPrimary,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Tune the domain, speech route, Hank’s voice pacing, and the cadence of read-only commentary.",
            color = AppColors.TextSecondary,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )

        SettingsSection(title = "Scope") {
            SegmentedButtons(
                options = DomainMode.entries,
                selected = config.general.domainMode,
                optionLabel = { it.label },
                onSelect = {
                    onConfigChange(config.copy(general = config.general.copy(domainMode = it)))
                },
            )
        }

        SettingsSection(title = "Speech recognition") {
            SegmentedButtons(
                options = SpeechRecognitionRoute.entries,
                selected = config.speech.recognitionRoute,
                optionLabel = { it.segmentLabel },
                onSelect = {
                    onConfigChange(config.copy(speech = config.speech.copy(recognitionRoute = it)))
                },
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = config.speech.recognitionRoute.settingsDescription,
                color = AppColors.TextSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }

        SettingsSection(title = "Hank voice") {
            SegmentedButtons(
                options = VoiceSpeed.entries,
                selected = config.speech.voiceSpeed,
                optionLabel = { it.label },
                onSelect = {
                    onConfigChange(config.copy(speech = config.speech.copy(voiceSpeed = it)))
                },
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Read-only pause: ${(config.speech.readOnlyPauseMs / 1000f).let { String.format("%.1f", it) }}s",
                color = AppColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Slider(
                value = config.speech.readOnlyPauseMs.toFloat(),
                onValueChange = {
                    onConfigChange(
                        config.copy(
                            speech = config.speech.copy(readOnlyPauseMs = it.roundToInt().toLong()),
                        ),
                    )
                },
                valueRange = 1500f..8000f,
            )
            Text(
                text =
                    "This pause controls how long Hank waits before the next read-only comment, effectively spacing the spoken commentary into digestible bursts.",
                color = AppColors.TextSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }

        SettingsSection(title = "Display") {
            SegmentedButtons(
                options = ThemeMode.entries,
                selected = config.general.themeMode,
                optionLabel = { it.name.lowercase().replaceFirstChar(Char::titlecase) },
                onSelect = {
                    onConfigChange(config.copy(general = config.general.copy(themeMode = it)))
                },
            )
            Spacer(Modifier.height(12.dp))
            SegmentedButtons(
                options = TextScale.entries,
                selected = config.general.textScale,
                optionLabel = { it.label },
                onSelect = {
                    onConfigChange(config.copy(general = config.general.copy(textScale = it)))
                },
            )
        }

        SettingsSection(title = "Accessibility") {
            ToggleRow(
                label = "High contrast",
                subtitle = "Increase visual separation on cards, pills, and controls.",
                value = config.general.highContrast,
                onChange = {
                    onConfigChange(config.copy(general = config.general.copy(highContrast = it)))
                },
            )
            Spacer(Modifier.height(12.dp))
            ToggleRow(
                label = "Haptic feedback",
                subtitle = "Short vibration cues when Hank is interrupted or speech is captured.",
                value = config.general.hapticFeedback,
                onChange = {
                    onConfigChange(config.copy(general = config.general.copy(hapticFeedback = it)))
                },
            )
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(AppColors.Surface, RoundedCornerShape(18.dp))
                .padding(16.dp),
    ) {
        Text(
            text = title.uppercase(),
            color = AppColors.TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun ToggleRow(
    label: String,
    subtitle: String,
    value: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = AppColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = AppColors.TextSecondary, fontSize = 12.sp, lineHeight = 17.sp)
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
