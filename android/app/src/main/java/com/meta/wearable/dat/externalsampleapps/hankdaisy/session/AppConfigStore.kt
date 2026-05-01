/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.hankdaisy.session

import android.content.Context
import org.json.JSONObject

object AppConfigStore {
    const val PREFS = "hank_sessions_v1"
    const val KEY_SETTINGS = "settings_json"

    fun current(context: Context): AppConfig {
        val raw =
            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_SETTINGS, null)
        return fromSettingsJson(raw)
    }

    fun fromSettingsJson(raw: String?): AppConfig {
        if (raw.isNullOrBlank()) return AppConfig()
        return try {
            fromJson(JSONObject(raw))
        } catch (_: Exception) {
            AppConfig()
        }
    }

    fun toJson(config: AppConfig): JSONObject {
        val normalized = config.normalized()
        return JSONObject()
            .put(
                "demo",
                JSONObject()
                    .put(CaptureMode.KEY_CAPTURE_MODE, normalized.demo.captureMode.name)
                    .put("autoResumeListeningAfterTts", normalized.demo.autoResumeListeningAfterTts),
            )
            .put(
                "audio",
                JSONObject()
                    .put(
                        "capture",
                        JSONObject()
                            .put("inputMode", normalized.audio.capture.inputMode.name)
                            .put("preferredDevice", normalized.audio.capture.preferredDevice.name)
                            .put("sampleRateHz", normalized.audio.capture.sampleRateHz),
                    )
                    .put(
                        "enhancement",
                        JSONObject()
                            .put("noiseSuppression", normalized.audio.enhancement.noiseSuppression.name)
                            .put("echoCancellation", normalized.audio.enhancement.echoCancellation.name)
                            .put("automaticGainControl", normalized.audio.enhancement.automaticGainControl),
                    )
                    .put(
                        "wakeWord",
                        JSONObject()
                            .put("enabled", normalized.audio.wakeWord.enabled)
                            .put("phrase", normalized.audio.wakeWord.phrase),
                    )
                    .put(
                        "intent",
                        JSONObject()
                            .put("minQueryLength", normalized.audio.intent.minQueryLength)
                            .put("duplicateWindowMs", normalized.audio.intent.duplicateWindowMs)
                            .put("localCommandsEnabled", normalized.audio.intent.localCommandsEnabled),
                    )
                    .put(
                        "vad",
                        JSONObject()
                            .put("route", normalized.audio.vad.route.name)
                            .put("threshold", normalized.audio.vad.threshold.toDouble())
                            .put("minSpeechMs", normalized.audio.vad.minSpeechMs),
                    )
                    .put(
                        "transcription",
                        JSONObject()
                            .put(
                                SpeechRecognitionRoute.KEY_SPEECH_RECOGNITION_ROUTE,
                                normalized.audio.transcription.route.name,
                            )
                            .put("remoteModel", normalized.audio.transcription.remoteModel.name)
                            .put("emitPartials", normalized.audio.transcription.emitPartials),
                    )
                    .put("preset", normalized.audio.preset.name),
            )
            .put(
                "video",
                JSONObject()
                    .put("sourceFps", normalized.video.sourceFps)
                    .put("modelSendFps", normalized.video.modelSendFps)
                    .put("quality", normalized.video.quality.name),
            )
            .put(
                "general",
                JSONObject()
                    .put(WorkDomain.KEY_WORK_DOMAIN, normalized.general.workDomain.name)
                    .put("theme", normalized.general.themeMode.name)
                    .put("textScale", normalized.general.textScale.name)
                    .put("highContrast", normalized.general.highContrast)
                    .put("hapticFeedback", normalized.general.hapticFeedback)
                    .put("demoCommentaryMode", normalized.general.demoCommentaryMode),
            )
    }

    private fun fromJson(root: JSONObject): AppConfig {
        val demo = root.optJSONObject("demo")
        val audio = root.optJSONObject("audio")
        val video = root.optJSONObject("video")
        val general = root.optJSONObject("general")

        if (demo == null && audio == null && video == null && general == null) {
            return fromLegacyFlatJson(root)
        }

        return AppConfig(
                demo =
                    DemoConfig(
                        captureMode =
                            CaptureMode.fromStored(
                                demo?.optString(CaptureMode.KEY_CAPTURE_MODE, CaptureMode.GLASSES.name),
                            ),
                        autoResumeListeningAfterTts =
                            demo?.optBoolean("autoResumeListeningAfterTts", true) ?: true,
                    ),
                audio =
                    AudioPipelineConfig(
                        capture =
                            AudioCaptureConfig(
                                inputMode =
                                    AudioInputMode.entries.firstOrNull {
                                        it.name == audio?.optJSONObject("capture")?.optString("inputMode")
                                    } ?: AudioInputMode.ANDROID_SPEECH_RECOGNIZER,
                                preferredDevice =
                                    PreferredMicDevice.entries.firstOrNull {
                                        it.name ==
                                            audio?.optJSONObject("capture")?.optString("preferredDevice")
                                    } ?: PreferredMicDevice.SYSTEM_DEFAULT,
                                sampleRateHz =
                                    audio?.optJSONObject("capture")?.optInt("sampleRateHz", 16_000)
                                        ?: 16_000,
                            ),
                        enhancement =
                            AudioEnhancementConfig(
                                noiseSuppression =
                                    NoiseSuppressionMode.entries.firstOrNull {
                                        it.name ==
                                            audio?.optJSONObject("enhancement")
                                                ?.optString("noiseSuppression")
                                    } ?: NoiseSuppressionMode.NONE,
                                echoCancellation =
                                    EchoCancellationMode.entries.firstOrNull {
                                        it.name ==
                                            audio?.optJSONObject("enhancement")
                                                ?.optString("echoCancellation")
                                    } ?: EchoCancellationMode.SYSTEM,
                                automaticGainControl =
                                    audio?.optJSONObject("enhancement")
                                        ?.optBoolean("automaticGainControl", false) ?: false,
                            ),
                        wakeWord =
                            WakeWordConfig(
                                enabled = audio?.optJSONObject("wakeWord")?.optBoolean("enabled", true) ?: true,
                                phrase =
                                    audio?.optJSONObject("wakeWord")?.optString("phrase", "Hey Hank")
                                        ?.ifBlank { "Hey Hank" } ?: "Hey Hank",
                            ),
                        intent =
                            SpeechIntentConfig(
                                minQueryLength =
                                    audio?.optJSONObject("intent")?.optInt("minQueryLength", 2) ?: 2,
                                duplicateWindowMs =
                                    audio?.optJSONObject("intent")?.optLong("duplicateWindowMs", 2_500L)
                                        ?: 2_500L,
                                localCommandsEnabled =
                                    audio?.optJSONObject("intent")
                                        ?.optBoolean("localCommandsEnabled", true) ?: true,
                            ),
                        vad =
                            VadConfig(
                                route =
                                    VadRoute.entries.firstOrNull {
                                        it.name == audio?.optJSONObject("vad")?.optString("route")
                                    } ?: VadRoute.ENERGY,
                                threshold =
                                    audio?.optJSONObject("vad")?.optDouble("threshold", 0.50)
                                        ?.toFloat() ?: 0.50f,
                                minSpeechMs =
                                    audio?.optJSONObject("vad")?.optLong("minSpeechMs", 220L) ?: 220L,
                            ),
                        transcription =
                            TranscriptionConfig(
                                route =
                                    SpeechRecognitionRoute.fromStored(
                                        audio?.optJSONObject("transcription")
                                            ?.optString(
                                                SpeechRecognitionRoute.KEY_SPEECH_RECOGNITION_ROUTE,
                                                SpeechRecognitionRoute.ANDROID.name,
                                            ),
                                    ),
                                remoteModel =
                                    RemoteSpeechModel.entries.firstOrNull {
                                        it.name ==
                                            audio?.optJSONObject("transcription")
                                                ?.optString("remoteModel")
                                    } ?: RemoteSpeechModel.AUTO,
                                emitPartials =
                                    audio?.optJSONObject("transcription")
                                        ?.optBoolean("emitPartials", true) ?: true,
                            ),
                        preset =
                            PipelinePreset.entries.firstOrNull {
                                it.name == audio?.optString("preset")
                            } ?: PipelinePreset.FASTEST,
                    ),
                video =
                    VideoPipelineConfig(
                        sourceFps = video?.optInt("sourceFps", 24) ?: 24,
                        modelSendFps = video?.optInt("modelSendFps", 3) ?: 3,
                        quality =
                            VideoQualityPreset.entries.firstOrNull {
                                it.name == video?.optString("quality")
                            } ?: VideoQualityPreset.MEDIUM,
                    ),
                general =
                    GeneralSettings(
                        workDomain =
                            WorkDomain.fromStored(
                                general?.optString(WorkDomain.KEY_WORK_DOMAIN, WorkDomain.CAR.name),
                            ),
                        themeMode =
                            ThemeMode.entries.firstOrNull {
                                it.name == general?.optString("theme")
                            } ?: ThemeMode.LIGHT,
                        textScale =
                            TextScale.entries.firstOrNull {
                                it.name == general?.optString("textScale")
                            } ?: TextScale.NORMAL,
                        highContrast = general?.optBoolean("highContrast", false) ?: false,
                        hapticFeedback = general?.optBoolean("hapticFeedback", true) ?: true,
                        demoCommentaryMode = general?.optBoolean("demoCommentaryMode", false) ?: false,
                    ),
            )
            .normalized()
    }

    private fun fromLegacyFlatJson(root: JSONObject): AppConfig {
        return AppConfig(
                demo =
                    DemoConfig(
                        captureMode =
                            CaptureMode.fromStored(
                                root.optString(CaptureMode.KEY_CAPTURE_MODE, CaptureMode.GLASSES.name),
                            ),
                    ),
                audio =
                    AudioPipelineConfig(
                        transcription =
                            TranscriptionConfig(
                                route =
                                    SpeechRecognitionRoute.fromStored(
                                        root.optString(
                                            SpeechRecognitionRoute.KEY_SPEECH_RECOGNITION_ROUTE,
                                            SpeechRecognitionRoute.ANDROID.name,
                                        ),
                                    ),
                            ),
                    ),
                general =
                    GeneralSettings(
                        workDomain =
                            WorkDomain.fromStored(
                                root.optString(WorkDomain.KEY_WORK_DOMAIN, WorkDomain.CAR.name),
                            ),
                        themeMode =
                            ThemeMode.entries.firstOrNull { it.name == root.optString("theme") }
                                ?: ThemeMode.LIGHT,
                        textScale =
                            TextScale.entries.firstOrNull { it.name == root.optString("textScale") }
                                ?: TextScale.NORMAL,
                        highContrast = root.optBoolean("highContrast", false),
                        hapticFeedback = root.optBoolean("hapticFeedback", true),
                        demoCommentaryMode = root.optBoolean("demoCommentaryMode", false),
                    ),
            )
            .let { config ->
                when (config.audio.transcription.route) {
                    SpeechRecognitionRoute.ANDROID ->
                        config.copy(
                            audio =
                                config.audio.copy(
                                    preset = PipelinePreset.FASTEST,
                                    capture =
                                        config.audio.capture.copy(
                                            inputMode = AudioInputMode.ANDROID_SPEECH_RECOGNIZER,
                                        ),
                                ),
                        )
                    SpeechRecognitionRoute.OPENROUTER ->
                        config.copy(
                            audio =
                                config.audio.copy(
                                    preset = PipelinePreset.BALANCED,
                                    capture =
                                        config.audio.capture.copy(
                                            inputMode = AudioInputMode.RAW_AUDIO_RECORD,
                                        ),
                                    enhancement =
                                        config.audio.enhancement.copy(
                                            echoCancellation = EchoCancellationMode.SYSTEM,
                                        ),
                                ),
                        )
                }
            }
            .normalized()
    }
}
