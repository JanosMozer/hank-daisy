package com.meta.wearable.dat.externalsampleapps.mpi.ui

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meta.wearable.dat.externalsampleapps.mpi.session.AppConfig
import com.meta.wearable.dat.externalsampleapps.mpi.session.CaptureAudioSource
import com.meta.wearable.dat.externalsampleapps.mpi.session.CaptureVideoSource
import com.meta.wearable.dat.externalsampleapps.mpi.session.PreferredMicDevice
import com.meta.wearable.dat.externalsampleapps.mpi.session.SpeechRecognitionRoute
import com.meta.wearable.dat.externalsampleapps.mpi.session.requiresGlassesConnection

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CaptureScreen(
    config: AppConfig,
    isGlassesRegistered: Boolean,
    hasActiveGlasses: Boolean,
    onConfigChange: (AppConfig) -> Unit,
    onConnectGlasses: () -> Unit,
    onStartCapture: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val availablePhoneMics = remember(context) { availablePhoneMicOptions(context) }
    val needsGlasses = config.requiresGlassesConnection()
    val glassesReady = !needsGlasses || (isGlassesRegistered && hasActiveGlasses)

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(AppColors.Background)
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Capture & Report",
            color = AppColors.TextPrimary,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text =
                "Choose the live capture sources first, then launch either the glasses demo or the phone-camera demo from the same surface.",
            color = AppColors.TextSecondary,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )

        CaptureSection(title = "Video source") {
            SegmentedButtons(
                options = CaptureVideoSource.entries,
                selected = config.capture.videoSource,
                optionLabel = { it.label },
                onSelect = {
                    onConfigChange(config.copy(capture = config.capture.copy(videoSource = it)))
                },
            )
        }

        CaptureSection(title = "Audio source") {
            SegmentedButtons(
                options = CaptureAudioSource.entries,
                selected = config.capture.audioSource,
                optionLabel = { it.label },
                onSelect = {
                    onConfigChange(config.copy(capture = config.capture.copy(audioSource = it)))
                },
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text =
                    if (config.capture.audioSource == CaptureAudioSource.GLASSES_MIC) {
                        "Glasses mic preference is strongest on the OpenRouter raw route. Android local recognition may still follow system routing."
                    } else {
                        "Phone mic selection is applied directly on the raw OpenRouter route. Android local recognition may still follow the system default input."
                    },
                color = AppColors.TextSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }

        if (config.capture.audioSource == CaptureAudioSource.PHONE_MIC) {
            CaptureSection(title = "Phone microphone") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (option in availablePhoneMics) {
                        OptionChip(
                            label = option.label,
                            selected = option == config.capture.preferredPhoneMic,
                            onClick = {
                                onConfigChange(
                                    config.copy(capture = config.capture.copy(preferredPhoneMic = option)),
                                )
                            },
                        )
                    }
                }
            }
        }

        CaptureSection(title = "Glasses connection") {
            val status =
                when {
                    !needsGlasses -> "This configuration can run entirely on the phone."
                    glassesReady -> "Glasses are connected and ready for capture."
                    isGlassesRegistered -> "Registration is complete. Turn on and connect the glasses to start capture."
                    else -> "Registration is still needed before a glasses-backed capture can start."
                }
            Text(status, color = AppColors.TextPrimary, fontSize = 14.sp, lineHeight = 20.sp)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SwitchButton(
                    label = if (glassesReady) "Reconnect glasses" else "Connect glasses",
                    onClick = onConnectGlasses,
                    modifier = Modifier.weight(1f),
                )
                SwitchButton(
                    label = "Start capture",
                    onClick = onStartCapture,
                    enabled = glassesReady,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        CaptureSection(title = "Current setup") {
            SummaryLine("Video", config.capture.videoSource.label)
            SummaryLine("Audio", config.capture.audioSource.label)
            SummaryLine("Speech route", config.speech.recognitionRoute.settingsLabel)
            SummaryLine("Domain", config.general.domainMode.label)
            if (config.capture.audioSource == CaptureAudioSource.PHONE_MIC) {
                SummaryLine("Preferred phone mic", config.capture.preferredPhoneMic.label)
            }
            if (config.speech.recognitionRoute == SpeechRecognitionRoute.ANDROID) {
                Text(
                    text =
                        "Android local recognition is the lowest-friction route, but it owns more of the mic chain. OpenRouter gives you stronger control when the environment is noisy.",
                    color = AppColors.TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
        }
    }
}

@Composable
private fun CaptureSection(
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
        Text(title.uppercase(), color = AppColors.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
fun <T> SegmentedButtons(
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(AppColors.SurfaceAlt, RoundedCornerShape(12.dp))
                .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .background(
                            if (isSelected) AppColors.Accent else Color.Transparent,
                            RoundedCornerShape(10.dp),
                        )
                        .clickable { onSelect(option) }
                        .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    optionLabel(option),
                    color = if (isSelected) AppColors.AccentOn else AppColors.TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun OptionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .background(
                    if (selected) AppColors.AccentSoft else AppColors.SurfaceAlt,
                    RoundedCornerShape(999.dp),
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            color = if (selected) AppColors.Accent else AppColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = AppColors.TextSecondary, fontSize = 13.sp)
        Text(value, color = AppColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
    Spacer(Modifier.height(8.dp))
}

private fun availablePhoneMicOptions(context: Context): List<PreferredMicDevice> {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    if (audioManager == null) return listOf(PreferredMicDevice.SYSTEM_DEFAULT)

    val inputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
    val options = linkedSetOf(PreferredMicDevice.SYSTEM_DEFAULT)
    inputs.forEach { device ->
        when (device.type) {
            AudioDeviceInfo.TYPE_BUILTIN_MIC -> options += PreferredMicDevice.BUILT_IN_MIC
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET -> options += PreferredMicDevice.WIRED_HEADSET
            AudioDeviceInfo.TYPE_USB_DEVICE -> options += PreferredMicDevice.USB_MIC
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> options += PreferredMicDevice.BLUETOOTH_MIC
        }
    }
    return options.toList()
}
