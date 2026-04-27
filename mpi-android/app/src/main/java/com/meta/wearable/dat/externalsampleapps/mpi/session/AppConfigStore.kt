package com.meta.wearable.dat.externalsampleapps.mpi.session

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

    fun save(context: Context, config: AppConfig) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SETTINGS, toJson(config.normalized()).toString())
            .apply()
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
                "capture",
                JSONObject()
                    .put("videoSource", normalized.capture.videoSource.name)
                    .put("audioSource", normalized.capture.audioSource.name)
                    .put("preferredPhoneMic", normalized.capture.preferredPhoneMic.name)
                    .put("hankMode", normalized.capture.hankMode.name),
            )
            .put(
                "speech",
                JSONObject()
                    .put("recognitionRoute", normalized.speech.recognitionRoute.name)
                    .put("voiceSpeed", normalized.speech.voiceSpeed.name)
                    .put("readOnlyPauseMs", normalized.speech.readOnlyPauseMs),
            )
            .put(
                "general",
                JSONObject()
                    .put("domainMode", normalized.general.domainMode.name)
                    .put("theme", normalized.general.themeMode.name)
                    .put("textScale", normalized.general.textScale.name)
                    .put("highContrast", normalized.general.highContrast)
                    .put("hapticFeedback", normalized.general.hapticFeedback),
            )
    }

    private fun fromJson(root: JSONObject): AppConfig {
        if (!root.has("capture") && !root.has("speech") && !root.has("general")) {
            return fromLegacyFlatJson(root)
        }

        val capture = root.optJSONObject("capture")
        val speech = root.optJSONObject("speech")
        val general = root.optJSONObject("general")

        return AppConfig(
                capture =
                    CaptureConfig(
                        videoSource =
                            CaptureVideoSource.entries.firstOrNull {
                                it.name == capture?.optString("videoSource")
                            } ?: CaptureVideoSource.GLASSES,
                        audioSource =
                            CaptureAudioSource.entries.firstOrNull {
                                it.name == capture?.optString("audioSource")
                            } ?: CaptureAudioSource.GLASSES_MIC,
                        preferredPhoneMic =
                            PreferredMicDevice.entries.firstOrNull {
                                it.name == capture?.optString("preferredPhoneMic")
                            } ?: PreferredMicDevice.SYSTEM_DEFAULT,
                        hankMode =
                            HankMode.entries.firstOrNull {
                                it.name == capture?.optString("hankMode")
                            } ?: HankMode.INTERACTIVE,
                    ),
                speech =
                    SpeechConfig(
                        recognitionRoute =
                            SpeechRecognitionRoute.fromStored(
                                speech?.optString(
                                    "recognitionRoute",
                                    SpeechRecognitionRoute.ANDROID.name,
                                ),
                            ),
                        voiceSpeed =
                            VoiceSpeed.entries.firstOrNull {
                                it.name == speech?.optString("voiceSpeed")
                            } ?: VoiceSpeed.NORMAL,
                        readOnlyPauseMs = speech?.optLong("readOnlyPauseMs", 3_000L) ?: 3_000L,
                    ),
                general =
                    GeneralSettings(
                        domainMode =
                            DomainMode.entries.firstOrNull {
                                it.name == general?.optString("domainMode")
                            } ?: DomainMode.CAR_ONLY,
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
                    ),
            )
            .normalized()
    }

    private fun fromLegacyFlatJson(root: JSONObject): AppConfig {
        return AppConfig(
                speech =
                    SpeechConfig(
                        recognitionRoute =
                            SpeechRecognitionRoute.fromStored(
                                root.optString(
                                    SpeechRecognitionRoute.KEY_SPEECH_RECOGNITION_ROUTE,
                                    SpeechRecognitionRoute.ANDROID.name,
                                ),
                            ),
                    ),
                general =
                    GeneralSettings(
                        themeMode =
                            ThemeMode.entries.firstOrNull {
                                it.name == root.optString("theme")
                            } ?: ThemeMode.LIGHT,
                        textScale =
                            TextScale.entries.firstOrNull {
                                it.name == root.optString("textScale")
                            } ?: TextScale.NORMAL,
                        highContrast = root.optBoolean("highContrast", false),
                        hapticFeedback = root.optBoolean("hapticFeedback", true),
                    ),
            )
            .normalized()
    }
}
