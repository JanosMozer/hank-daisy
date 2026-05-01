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

enum class PipelineSection(val label: String) {
    AUDIO("Audio"),
    VIDEO("Video"),
    ADVANCED("Advanced"),
}

enum class AudioInputMode(val label: String, val description: String) {
    ANDROID_SPEECH_RECOGNIZER(
        label = "Android chain",
        description = "Android owns mic capture and recognition. Lowest setup cost, least control.",
    ),
    RAW_AUDIO_RECORD(
        label = "Raw pipeline",
        description = "The app owns capture through AudioRecord, enabling mic preference and DSP controls.",
    ),
}

enum class PreferredMicDevice(val label: String) {
    SYSTEM_DEFAULT("System default"),
    BUILT_IN_MIC("Built-in"),
    WIRED_HEADSET("Wired"),
    USB_MIC("USB"),
    BLUETOOTH_MIC("Bluetooth"),
}

enum class NoiseSuppressionMode(val label: String, val description: String) {
    NONE("None", "No app-managed noise suppression."),
    SYSTEM("System", "Enable Android's built-in noise suppressor when raw capture is active."),
    EXPERIMENTAL("Experimental", "Reserved for future RNNoise / DeepFilterNet style frontends."),
}

enum class EchoCancellationMode(val label: String, val description: String) {
    NONE("None", "Disable app-managed echo cancellation."),
    SYSTEM("System", "Enable Android AcousticEchoCanceler when available on raw capture."),
}

enum class VadRoute(val label: String, val description: String) {
    ENERGY(
        "Energy gate",
        "Current app-managed voice gate. Threshold and speech-hold are adjustable.",
    ),
    SILERO(
        "Silero",
        "Planned local VAD route. Not wired yet; kept here so the config model already supports it.",
    ),
}

enum class RemoteSpeechModel(val label: String, val openRouterModelId: String?) {
    AUTO("Auto", null),
    GPT_4O_TRANSCRIBE_DIARIZE("4o diarize", "openai/gpt-4o-transcribe-diarize"),
    GPT_AUDIO("GPT audio", "openai/gpt-audio"),
}

enum class VideoQualityPreset(val label: String) {
    LOW("Low"),
    MEDIUM("Medium"),
    HIGH("High"),
}

enum class PipelinePreset(val label: String, val description: String) {
    FASTEST(
        "Fastest",
        "Android STT with minimal app-side processing.",
    ),
    BALANCED(
        "Balanced",
        "OpenRouter audio with system AEC and moderate VAD settings.",
    ),
    NOISY_SHOP(
        "Noisy shop",
        "OpenRouter audio with system DSP enabled and stricter speech gating.",
    ),
    REMOTE_BEST_ACCURACY(
        "Remote best",
        "OpenRouter audio with the strongest current remote speech model preference.",
    ),
}

data class AppConfig(
    val demo: DemoConfig = DemoConfig(),
    val audio: AudioPipelineConfig = AudioPipelineConfig(),
    val video: VideoPipelineConfig = VideoPipelineConfig(),
    val general: GeneralSettings = GeneralSettings(),
)

data class DemoConfig(
    val captureMode: CaptureMode = CaptureMode.GLASSES,
    val autoResumeListeningAfterTts: Boolean = true,
)

data class AudioPipelineConfig(
    val capture: AudioCaptureConfig = AudioCaptureConfig(),
    val enhancement: AudioEnhancementConfig = AudioEnhancementConfig(),
    val wakeWord: WakeWordConfig = WakeWordConfig(),
    val intent: SpeechIntentConfig = SpeechIntentConfig(),
    val vad: VadConfig = VadConfig(),
    val transcription: TranscriptionConfig = TranscriptionConfig(),
    val preset: PipelinePreset = PipelinePreset.FASTEST,
)

data class AudioCaptureConfig(
    val inputMode: AudioInputMode = AudioInputMode.ANDROID_SPEECH_RECOGNIZER,
    val preferredDevice: PreferredMicDevice = PreferredMicDevice.SYSTEM_DEFAULT,
    val sampleRateHz: Int = 16_000,
)

data class AudioEnhancementConfig(
    val noiseSuppression: NoiseSuppressionMode = NoiseSuppressionMode.NONE,
    val echoCancellation: EchoCancellationMode = EchoCancellationMode.SYSTEM,
    val automaticGainControl: Boolean = false,
)

data class WakeWordConfig(
    val enabled: Boolean = true,
    val phrase: String = "Hey Hank",
)

data class SpeechIntentConfig(
    val minQueryLength: Int = 2,
    val duplicateWindowMs: Long = 2_500L,
    val localCommandsEnabled: Boolean = true,
)

data class VadConfig(
    val route: VadRoute = VadRoute.ENERGY,
    val threshold: Float = 0.50f,
    val minSpeechMs: Long = 220L,
)

data class TranscriptionConfig(
    val route: SpeechRecognitionRoute = SpeechRecognitionRoute.ANDROID,
    val remoteModel: RemoteSpeechModel = RemoteSpeechModel.AUTO,
    val emitPartials: Boolean = true,
)

data class VideoPipelineConfig(
    val sourceFps: Int = 24,
    val modelSendFps: Int = 3,
    val quality: VideoQualityPreset = VideoQualityPreset.MEDIUM,
)

data class GeneralSettings(
    val workDomain: WorkDomain = WorkDomain.CAR,
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val textScale: TextScale = TextScale.NORMAL,
    val highContrast: Boolean = false,
    val hapticFeedback: Boolean = true,
    val demoCommentaryMode: Boolean = false,
)

data class AudioPipelineCapabilities(
    val usesRawCapture: Boolean,
    val canSelectMic: Boolean,
    val canUseNoiseSuppression: Boolean,
    val canUseEchoCancellation: Boolean,
    val canUseVadTuning: Boolean,
    val canChooseRemoteSpeechModel: Boolean,
)

fun AppConfig.normalized(): AppConfig {
    val clampedIntent =
        audio.intent.copy(
            minQueryLength = audio.intent.minQueryLength.coerceIn(1, 8),
            duplicateWindowMs = audio.intent.duplicateWindowMs.coerceIn(0L, 10_000L),
        )
    val clampedVad =
        audio.vad.copy(
            threshold = audio.vad.threshold.coerceIn(0.05f, 1.0f),
            minSpeechMs = audio.vad.minSpeechMs.coerceIn(50L, 2_000L),
        )
    val safeVideo =
        video.copy(
            sourceFps = video.sourceFps.coerceToSupportedFps(default = 24),
            modelSendFps = video.modelSendFps.coerceIn(1, 10),
        )
    val safeCapture =
        audio.capture.copy(
            sampleRateHz = audio.capture.sampleRateHz.coerceIn(8_000, 48_000),
        )
    return copy(
        audio =
            audio.copy(
                capture = safeCapture,
                intent = clampedIntent,
                vad = clampedVad,
            ),
        video = safeVideo,
    )
}

fun AppConfig.audioCapabilities(): AudioPipelineCapabilities {
    val usesRawCapture = audio.transcription.route == SpeechRecognitionRoute.OPENROUTER
    return AudioPipelineCapabilities(
        usesRawCapture = usesRawCapture,
        canSelectMic = usesRawCapture,
        canUseNoiseSuppression = usesRawCapture,
        canUseEchoCancellation = usesRawCapture,
        canUseVadTuning = usesRawCapture,
        canChooseRemoteSpeechModel = audio.transcription.route == SpeechRecognitionRoute.OPENROUTER,
    )
}

fun AppConfig.withPipelinePreset(preset: PipelinePreset): AppConfig =
    when (preset) {
        PipelinePreset.FASTEST ->
            copy(
                audio =
                    audio.copy(
                        preset = preset,
                        capture =
                            audio.capture.copy(
                                inputMode = AudioInputMode.ANDROID_SPEECH_RECOGNIZER,
                                preferredDevice = PreferredMicDevice.SYSTEM_DEFAULT,
                            ),
                        enhancement =
                            audio.enhancement.copy(
                                noiseSuppression = NoiseSuppressionMode.NONE,
                                echoCancellation = EchoCancellationMode.NONE,
                                automaticGainControl = false,
                            ),
                        vad = audio.vad.copy(route = VadRoute.ENERGY, threshold = 0.50f, minSpeechMs = 220L),
                        transcription =
                            audio.transcription.copy(
                                route = SpeechRecognitionRoute.ANDROID,
                                remoteModel = RemoteSpeechModel.AUTO,
                            ),
                    ),
            )
        PipelinePreset.BALANCED ->
            copy(
                audio =
                    audio.copy(
                        preset = preset,
                        capture =
                            audio.capture.copy(
                                inputMode = AudioInputMode.RAW_AUDIO_RECORD,
                                preferredDevice = PreferredMicDevice.SYSTEM_DEFAULT,
                            ),
                        enhancement =
                            audio.enhancement.copy(
                                noiseSuppression = NoiseSuppressionMode.SYSTEM,
                                echoCancellation = EchoCancellationMode.SYSTEM,
                                automaticGainControl = false,
                            ),
                        vad = audio.vad.copy(route = VadRoute.ENERGY, threshold = 0.50f, minSpeechMs = 220L),
                        transcription =
                            audio.transcription.copy(
                                route = SpeechRecognitionRoute.OPENROUTER,
                                remoteModel = RemoteSpeechModel.AUTO,
                            ),
                    ),
            )
        PipelinePreset.NOISY_SHOP ->
            copy(
                audio =
                    audio.copy(
                        preset = preset,
                        capture =
                            audio.capture.copy(
                                inputMode = AudioInputMode.RAW_AUDIO_RECORD,
                                preferredDevice = PreferredMicDevice.BLUETOOTH_MIC,
                            ),
                        enhancement =
                            audio.enhancement.copy(
                                noiseSuppression = NoiseSuppressionMode.SYSTEM,
                                echoCancellation = EchoCancellationMode.SYSTEM,
                                automaticGainControl = true,
                            ),
                        vad = audio.vad.copy(route = VadRoute.ENERGY, threshold = 0.68f, minSpeechMs = 300L),
                        transcription =
                            audio.transcription.copy(
                                route = SpeechRecognitionRoute.OPENROUTER,
                                remoteModel = RemoteSpeechModel.AUTO,
                            ),
                    ),
            )
        PipelinePreset.REMOTE_BEST_ACCURACY ->
            copy(
                audio =
                    audio.copy(
                        preset = preset,
                        capture =
                            audio.capture.copy(
                                inputMode = AudioInputMode.RAW_AUDIO_RECORD,
                                preferredDevice = PreferredMicDevice.SYSTEM_DEFAULT,
                            ),
                        enhancement =
                            audio.enhancement.copy(
                                noiseSuppression = NoiseSuppressionMode.SYSTEM,
                                echoCancellation = EchoCancellationMode.SYSTEM,
                                automaticGainControl = true,
                            ),
                        vad = audio.vad.copy(route = VadRoute.ENERGY, threshold = 0.62f, minSpeechMs = 260L),
                        transcription =
                            audio.transcription.copy(
                                route = SpeechRecognitionRoute.OPENROUTER,
                                remoteModel = RemoteSpeechModel.GPT_4O_TRANSCRIBE_DIARIZE,
                            ),
                    ),
            )
    }.normalized()

fun Int.coerceToSupportedFps(default: Int): Int {
    val supported = listOf(2, 7, 15, 24, 30)
    if (this in supported) return this
    return supported.minByOrNull { kotlin.math.abs(it - this) } ?: default
}
