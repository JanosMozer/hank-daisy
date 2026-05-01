/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.hankdaisy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import com.meta.wearable.dat.externalsampleapps.hankdaisy.session.AppConfig
import com.meta.wearable.dat.externalsampleapps.hankdaisy.session.AudioInputMode
import com.meta.wearable.dat.externalsampleapps.hankdaisy.session.CaptureMode
import com.meta.wearable.dat.externalsampleapps.hankdaisy.session.EchoCancellationMode
import com.meta.wearable.dat.externalsampleapps.hankdaisy.session.NoiseSuppressionMode
import com.meta.wearable.dat.externalsampleapps.hankdaisy.session.PipelinePreset
import com.meta.wearable.dat.externalsampleapps.hankdaisy.session.PipelineSection
import com.meta.wearable.dat.externalsampleapps.hankdaisy.session.PreferredMicDevice
import com.meta.wearable.dat.externalsampleapps.hankdaisy.session.RemoteSpeechModel
import com.meta.wearable.dat.externalsampleapps.hankdaisy.session.SpeechRecognitionRoute
import com.meta.wearable.dat.externalsampleapps.hankdaisy.session.VadRoute
import com.meta.wearable.dat.externalsampleapps.hankdaisy.session.VideoQualityPreset
import com.meta.wearable.dat.externalsampleapps.hankdaisy.session.audioCapabilities
import com.meta.wearable.dat.externalsampleapps.hankdaisy.session.withPipelinePreset

@Composable
fun PipelineScreen(
    config: AppConfig,
    currentSection: PipelineSection,
    onSectionChange: (PipelineSection) -> Unit,
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
                .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        Text(
            text = "Pipeline",
            color = AppColors.TextPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Experimental control panel for the speech and video chain.",
            color = AppColors.TextSecondary,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(18.dp))
        SegmentedRow(
            label = "Section",
            options = PipelineSection.values().map { it.label },
            selectedIndex = currentSection.ordinal,
            onSelect = { onSectionChange(PipelineSection.values()[it]) },
        )
        Spacer(Modifier.height(18.dp))
        when (currentSection) {
            PipelineSection.AUDIO -> AudioPipelineSection(config = config, onConfigChange = onConfigChange)
            PipelineSection.VIDEO -> VideoPipelineSection(config = config, onConfigChange = onConfigChange)
            PipelineSection.ADVANCED -> AdvancedPipelineSection(config = config)
        }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun AudioPipelineSection(
    config: AppConfig,
    onConfigChange: (AppConfig) -> Unit,
) {
    val capabilities = config.audioCapabilities()

    SettingsSection(title = "Presets") {
        OptionList(
            label = "Starting points",
            options = PipelinePreset.values().map { SettingOption(it.label, it.description) },
            selectedIndex = config.audio.preset.ordinal,
            onSelect = { onConfigChange(config.withPipelinePreset(PipelinePreset.values()[it])) },
        )
    }

    Spacer(Modifier.height(14.dp))

    SettingsSection(title = "Capture") {
        SegmentedRow(
            label = "Capture ownership",
            options = AudioInputMode.values().map { it.label },
            selectedIndex = config.audio.capture.inputMode.ordinal,
            onSelect = { index ->
                val selected = AudioInputMode.values()[index]
                val nextRoute =
                    if (selected == AudioInputMode.ANDROID_SPEECH_RECOGNIZER) {
                        SpeechRecognitionRoute.ANDROID
                    } else {
                        SpeechRecognitionRoute.OPENROUTER
                    }
                onConfigChange(
                    config.copy(
                        audio =
                            config.audio.copy(
                                capture = config.audio.capture.copy(inputMode = selected),
                                transcription = config.audio.transcription.copy(route = nextRoute),
                            ),
                    ),
                )
            },
        )
        Spacer(Modifier.height(8.dp))
        HelperText(
            text =
                if (capabilities.usesRawCapture) {
                    "Raw capture is active. Mic preference, app-side DSP, and VAD tuning apply."
                } else {
                    "Android owns the microphone-to-text chain. Most app-side audio controls are informational only in this mode."
                },
        )
        Spacer(Modifier.height(14.dp))
        OptionList(
            label = "Preferred microphone",
            options =
                PreferredMicDevice.values().map {
                    SettingOption(
                        label = it.label,
                        description =
                            when (it) {
                                PreferredMicDevice.SYSTEM_DEFAULT -> "Let Android choose the active input."
                                PreferredMicDevice.BUILT_IN_MIC -> "Prefer the handset microphone."
                                PreferredMicDevice.WIRED_HEADSET -> "Prefer a wired headset microphone when present."
                                PreferredMicDevice.USB_MIC -> "Prefer a USB microphone when present."
                                PreferredMicDevice.BLUETOOTH_MIC -> "Prefer a Bluetooth input when present."
                            },
                    )
                },
            selectedIndex = config.audio.capture.preferredDevice.ordinal,
            onSelect = {
                onConfigChange(
                    config.copy(
                        audio =
                            config.audio.copy(
                                capture =
                                    config.audio.capture.copy(
                                        preferredDevice = PreferredMicDevice.values()[it],
                                    ),
                            ),
                    ),
                )
            },
            enabled = capabilities.canSelectMic,
        )
    }

    Spacer(Modifier.height(14.dp))

    SettingsSection(title = "Speech") {
        SegmentedRow(
            label = "Speech-to-text route",
            options = SpeechRecognitionRoute.values().map { it.segmentLabel },
            selectedIndex = config.audio.transcription.route.ordinal,
            onSelect = { index ->
                val route = SpeechRecognitionRoute.values()[index]
                onConfigChange(
                    config.copy(
                        audio =
                            config.audio.copy(
                                capture =
                                    config.audio.capture.copy(
                                        inputMode =
                                            if (route == SpeechRecognitionRoute.ANDROID) {
                                                AudioInputMode.ANDROID_SPEECH_RECOGNIZER
                                            } else {
                                                AudioInputMode.RAW_AUDIO_RECORD
                                            },
                                    ),
                                transcription = config.audio.transcription.copy(route = route),
                            ),
                    ),
                )
            },
        )
        Spacer(Modifier.height(8.dp))
        HelperText(text = config.audio.transcription.route.settingsDescription)
        Spacer(Modifier.height(14.dp))
        OptionList(
            label = "OpenRouter model",
            options =
                RemoteSpeechModel.values().map {
                    SettingOption(
                        label = it.label,
                        description =
                            when (it) {
                                RemoteSpeechModel.AUTO -> "Try the best configured remote candidate first, then fall back."
                                RemoteSpeechModel.GPT_4O_TRANSCRIBE_DIARIZE -> "Prefer OpenAI's diarization-capable transcription model."
                                RemoteSpeechModel.GPT_AUDIO -> "Force the GPT audio route for comparison."
                            },
                    )
                },
            selectedIndex = config.audio.transcription.remoteModel.ordinal,
            onSelect = {
                onConfigChange(
                    config.copy(
                        audio =
                            config.audio.copy(
                                transcription =
                                    config.audio.transcription.copy(
                                        remoteModel = RemoteSpeechModel.values()[it],
                                    ),
                            ),
                    ),
                )
            },
            enabled = capabilities.canChooseRemoteSpeechModel,
        )
    }

    Spacer(Modifier.height(14.dp))

    SettingsSection(title = "Enhancement") {
        OptionList(
            label = "Noise suppression",
            options =
                listOf(
                    SettingOption(
                        label = NoiseSuppressionMode.NONE.label,
                        description = NoiseSuppressionMode.NONE.description,
                    ),
                    SettingOption(
                        label = NoiseSuppressionMode.SYSTEM.label,
                        description = NoiseSuppressionMode.SYSTEM.description,
                    ),
                    SettingOption(
                        label = NoiseSuppressionMode.EXPERIMENTAL.label,
                        description = NoiseSuppressionMode.EXPERIMENTAL.description,
                        enabled = false,
                    ),
                ),
            selectedIndex =
                when (config.audio.enhancement.noiseSuppression) {
                    NoiseSuppressionMode.NONE -> 0
                    NoiseSuppressionMode.SYSTEM -> 1
                    NoiseSuppressionMode.EXPERIMENTAL -> 2
                },
            onSelect = { index ->
                val selected =
                    when (index) {
                        1 -> NoiseSuppressionMode.SYSTEM
                        2 -> NoiseSuppressionMode.EXPERIMENTAL
                        else -> NoiseSuppressionMode.NONE
                    }
                onConfigChange(
                    config.copy(
                        audio =
                            config.audio.copy(
                                enhancement =
                                    config.audio.enhancement.copy(noiseSuppression = selected),
                            ),
                    ),
                )
            },
            enabled = capabilities.canUseNoiseSuppression,
        )
        Spacer(Modifier.height(14.dp))
        SegmentedRow(
            label = "Echo cancellation",
            options = EchoCancellationMode.values().map { it.label },
            selectedIndex = config.audio.enhancement.echoCancellation.ordinal,
            onSelect = {
                onConfigChange(
                    config.copy(
                        audio =
                            config.audio.copy(
                                enhancement =
                                    config.audio.enhancement.copy(
                                        echoCancellation = EchoCancellationMode.values()[it],
                                    ),
                            ),
                    ),
                )
            },
            enabled = capabilities.canUseEchoCancellation,
        )
        Spacer(Modifier.height(14.dp))
        ToggleRow(
            label = "Automatic gain control",
            subtitle = "Enable Android AGC when raw capture is active.",
            value = config.audio.enhancement.automaticGainControl,
            onChange = {
                onConfigChange(
                    config.copy(
                        audio =
                            config.audio.copy(
                                enhancement =
                                    config.audio.enhancement.copy(automaticGainControl = it),
                            ),
                    ),
                )
            },
            enabled = capabilities.usesRawCapture,
        )
    }

    Spacer(Modifier.height(14.dp))

    SettingsSection(title = "Wake And Intent") {
        ToggleRow(
            label = "Wake phrase strip",
            subtitle = "If enabled, Hank trims the configured phrase from the start of a spoken request.",
            value = config.audio.wakeWord.enabled,
            onChange = {
                onConfigChange(
                    config.copy(
                        audio = config.audio.copy(wakeWord = config.audio.wakeWord.copy(enabled = it)),
                    ),
                )
            },
        )
        Spacer(Modifier.height(14.dp))
        TextFieldRow(
            label = "Wake phrase",
            value = config.audio.wakeWord.phrase,
            onValueChange = {
                onConfigChange(
                    config.copy(
                        audio = config.audio.copy(wakeWord = config.audio.wakeWord.copy(phrase = it)),
                    ),
                )
            },
            placeholder = "Hey Hank",
        )
        Spacer(Modifier.height(14.dp))
        SegmentedRow(
            label = "Minimum query length",
            options = listOf("1", "2", "3", "4"),
            selectedIndex = (config.audio.intent.minQueryLength.coerceIn(1, 4) - 1),
            onSelect = {
                onConfigChange(
                    config.copy(
                        audio =
                            config.audio.copy(
                                intent = config.audio.intent.copy(minQueryLength = it + 1),
                            ),
                    ),
                )
            },
        )
        Spacer(Modifier.height(14.dp))
        SegmentedRow(
            label = "Duplicate window",
            options = listOf("Off", "1.5s", "2.5s", "4s"),
            selectedIndex =
                when (config.audio.intent.duplicateWindowMs) {
                    0L -> 0
                    1_500L -> 1
                    4_000L -> 3
                    else -> 2
                },
            onSelect = {
                val window =
                    when (it) {
                        0 -> 0L
                        1 -> 1_500L
                        3 -> 4_000L
                        else -> 2_500L
                    }
                onConfigChange(
                    config.copy(
                        audio =
                            config.audio.copy(
                                intent = config.audio.intent.copy(duplicateWindowMs = window),
                            ),
                    ),
                )
            },
        )
    }

    Spacer(Modifier.height(14.dp))

    SettingsSection(title = "VAD") {
        OptionList(
            label = "Voice activity detector",
            options =
                listOf(
                    SettingOption(
                        label = VadRoute.ENERGY.label,
                        description = VadRoute.ENERGY.description,
                    ),
                    SettingOption(
                        label = VadRoute.SILERO.label,
                        description = VadRoute.SILERO.description,
                        enabled = false,
                    ),
                ),
            selectedIndex = if (config.audio.vad.route == VadRoute.SILERO) 1 else 0,
            onSelect = {
                val route = if (it == 1) VadRoute.SILERO else VadRoute.ENERGY
                onConfigChange(
                    config.copy(audio = config.audio.copy(vad = config.audio.vad.copy(route = route))),
                )
            },
            enabled = capabilities.canUseVadTuning,
        )
        Spacer(Modifier.height(14.dp))
        SliderRow(
            label = "Speech threshold",
            subtitle = "Higher values make the raw audio gate stricter.",
            value = config.audio.vad.threshold,
            onValueChange = {
                onConfigChange(
                    config.copy(audio = config.audio.copy(vad = config.audio.vad.copy(threshold = it))),
                )
            },
            valueLabel = "${(config.audio.vad.threshold * 100).toInt()}",
            enabled = capabilities.canUseVadTuning,
        )
        Spacer(Modifier.height(14.dp))
        SegmentedRow(
            label = "Minimum speech hold",
            options = listOf("120", "220", "300", "500"),
            selectedIndex =
                when (config.audio.vad.minSpeechMs) {
                    120L -> 0
                    300L -> 2
                    500L -> 3
                    else -> 1
                },
            onSelect = {
                val minSpeech =
                    when (it) {
                        0 -> 120L
                        2 -> 300L
                        3 -> 500L
                        else -> 220L
                    }
                onConfigChange(
                    config.copy(
                        audio =
                            config.audio.copy(
                                vad = config.audio.vad.copy(minSpeechMs = minSpeech),
                            ),
                    ),
                )
            },
            enabled = capabilities.canUseVadTuning,
        )
    }
}

@Composable
private fun VideoPipelineSection(
    config: AppConfig,
    onConfigChange: (AppConfig) -> Unit,
) {
    SettingsSection(title = "Capture") {
        SegmentedRow(
            label = "Source FPS",
            options = listOf("7", "15", "24", "30"),
            selectedIndex =
                when (config.video.sourceFps) {
                    7 -> 0
                    15 -> 1
                    30 -> 3
                    else -> 2
                },
            onSelect = {
                val fps =
                    when (it) {
                        0 -> 7
                        1 -> 15
                        3 -> 30
                        else -> 24
                    }
                onConfigChange(config.copy(video = config.video.copy(sourceFps = fps)))
            },
        )
        Spacer(Modifier.height(14.dp))
        SegmentedRow(
            label = "Quality",
            options = VideoQualityPreset.values().map { it.label },
            selectedIndex = config.video.quality.ordinal,
            onSelect = {
                onConfigChange(
                    config.copy(video = config.video.copy(quality = VideoQualityPreset.values()[it])),
                )
            },
        )
    }

    Spacer(Modifier.height(14.dp))

    SettingsSection(title = "Model Feed") {
        SegmentedRow(
            label = "Model send FPS",
            options = listOf("1", "2", "3", "5", "8"),
            selectedIndex =
                when (config.video.modelSendFps) {
                    1 -> 0
                    2 -> 1
                    5 -> 3
                    8 -> 4
                    else -> 2
                },
            onSelect = {
                val fps =
                    when (it) {
                        0 -> 1
                        1 -> 2
                        3 -> 5
                        4 -> 8
                        else -> 3
                    }
                onConfigChange(config.copy(video = config.video.copy(modelSendFps = fps)))
            },
        )
        Spacer(Modifier.height(8.dp))
        HelperText(
            text =
                "This governs how aggressively the app samples frames for scene analysis and other model-facing work.",
        )
    }
}

@Composable
private fun AdvancedPipelineSection(config: AppConfig) {
    SettingsSection(title = "Current Effective Route") {
        HelperText(
            text =
                buildString {
                    append("Demo mode: ")
                    append(if (config.demo.captureMode == CaptureMode.GLASSES) "Glasses" else "Phone")
                    append('\n')
                    append("Speech route: ")
                    append(config.audio.transcription.route.settingsLabel)
                    append('\n')
                    append("Capture ownership: ")
                    append(config.audio.capture.inputMode.label)
                    append('\n')
                    append("Preferred mic: ")
                    append(config.audio.capture.preferredDevice.label)
                    append('\n')
                    append("Source FPS: ")
                    append(config.video.sourceFps)
                    append(" · Model FPS: ")
                    append(config.video.modelSendFps)
                },
        )
    }

    Spacer(Modifier.height(14.dp))

    SettingsSection(title = "Notes") {
        HelperText(
            text =
                "Android SpeechRecognizer is still an opaque chain. The raw OpenRouter route is where mic preference, DSP toggles, and app-side VAD tuning currently apply. Local STT and learned VAD remain next-step integrations.",
        )
    }
}
