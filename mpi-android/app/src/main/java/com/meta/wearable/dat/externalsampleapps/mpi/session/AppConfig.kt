package com.meta.wearable.dat.externalsampleapps.mpi.session

enum class ThemeMode { LIGHT, DARK, SYSTEM }

enum class TextScale(val factor: Float, val label: String) {
    SMALL(0.9f, "Small"),
    NORMAL(1.0f, "Normal"),
    LARGE(1.15f, "Large"),
    EXTRA_LARGE(1.35f, "Extra large"),
}

enum class CaptureVideoSource(val label: String) {
    GLASSES("Glasses"),
    PHONE_CAMERA("Phone"),
}

enum class CaptureAudioSource(val label: String) {
    GLASSES_MIC("Glasses"),
    PHONE_MIC("Phone"),
}

enum class PreferredMicDevice(val label: String) {
    SYSTEM_DEFAULT("System"),
    BUILT_IN_MIC("Built-in"),
    WIRED_HEADSET("Wired"),
    USB_MIC("USB"),
    BLUETOOTH_MIC("Bluetooth"),
}

enum class DomainMode(val label: String) {
    CAR_ONLY("Car only"),
    GENERAL_DEVICE("General device"),
}

enum class HankMode(val label: String) {
    INTERACTIVE("Interactive"),
    READ_ONLY("Read-only"),
}

enum class VoiceSpeed(val label: String, val factor: Float) {
    SLOW("Slow", 0.9f),
    NORMAL("Normal", 1.0f),
    FAST("Fast", 1.15f),
    VERY_FAST("Very fast", 1.3f),
}

data class AppConfig(
    val capture: CaptureConfig = CaptureConfig(),
    val speech: SpeechConfig = SpeechConfig(),
    val general: GeneralSettings = GeneralSettings(),
)

data class CaptureConfig(
    val videoSource: CaptureVideoSource = CaptureVideoSource.GLASSES,
    val audioSource: CaptureAudioSource = CaptureAudioSource.GLASSES_MIC,
    val preferredPhoneMic: PreferredMicDevice = PreferredMicDevice.SYSTEM_DEFAULT,
    val hankMode: HankMode = HankMode.INTERACTIVE,
)

data class SpeechConfig(
    val recognitionRoute: SpeechRecognitionRoute = SpeechRecognitionRoute.ANDROID,
    val voiceSpeed: VoiceSpeed = VoiceSpeed.NORMAL,
    val readOnlyPauseMs: Long = 3_000L,
)

data class GeneralSettings(
    val domainMode: DomainMode = DomainMode.CAR_ONLY,
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val textScale: TextScale = TextScale.NORMAL,
    val highContrast: Boolean = false,
    val hapticFeedback: Boolean = true,
)

data class CaptureSessionMetadata(
    val videoSource: CaptureVideoSource,
    val audioSource: CaptureAudioSource,
    val preferredPhoneMic: PreferredMicDevice,
    val hankMode: HankMode,
    val domainMode: DomainMode,
    val speechRecognitionRoute: SpeechRecognitionRoute,
)

fun AppConfig.normalized(): AppConfig =
    copy(
        speech = speech.copy(readOnlyPauseMs = speech.readOnlyPauseMs.coerceIn(1_500L, 8_000L)),
    )

fun AppConfig.requiresGlassesConnection(): Boolean =
    capture.videoSource == CaptureVideoSource.GLASSES ||
        capture.audioSource == CaptureAudioSource.GLASSES_MIC

fun AppConfig.captureMetadata(): CaptureSessionMetadata =
    CaptureSessionMetadata(
        videoSource = capture.videoSource,
        audioSource = capture.audioSource,
        preferredPhoneMic = capture.preferredPhoneMic,
        hankMode = capture.hankMode,
        domainMode = general.domainMode,
        speechRecognitionRoute = speech.recognitionRoute,
    )
