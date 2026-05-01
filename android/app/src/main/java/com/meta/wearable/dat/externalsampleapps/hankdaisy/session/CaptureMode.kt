/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.hankdaisy.session

import android.content.Context

enum class CaptureMode {
    GLASSES,
    PHONE_CAMERA,
    ;

    companion object {
        private const val PREFS = "hank_sessions_v1"
        private const val KEY_SETTINGS = "settings_json"
        internal const val KEY_CAPTURE_MODE = "captureMode"

        fun current(context: Context): CaptureMode {
            val raw =
                context.applicationContext
                    .getSharedPreferences(AppConfigStore.PREFS, Context.MODE_PRIVATE)
                    .getString(AppConfigStore.KEY_SETTINGS, null)
            return fromSettingsJson(raw)
        }

        fun fromSettingsJson(raw: String?): CaptureMode =
            AppConfigStore.fromSettingsJson(raw).demo.captureMode

        fun fromStored(raw: String?): CaptureMode =
            entries.firstOrNull { it.name == raw } ?: GLASSES
    }
}
