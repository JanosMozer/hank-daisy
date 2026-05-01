/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.hankdaisy.session

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SessionsUiState(
    val config: AppConfig = AppConfig(),
    val splashShown: Boolean = false,
    val streamRequested: Boolean = false,
    val currentTab: com.meta.wearable.dat.externalsampleapps.hankdaisy.ui.TopLevelTab =
        com.meta.wearable.dat.externalsampleapps.hankdaisy.ui.TopLevelTab.DEMO,
    val pipelineSection: PipelineSection = PipelineSection.AUDIO,
)

class SessionViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val PREFS = AppConfigStore.PREFS
        private const val KEY_SETTINGS = AppConfigStore.KEY_SETTINGS
    }

    private val prefs =
        application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _uiState =
        MutableStateFlow(
            SessionsUiState(
                config = loadConfig(),
            ),
        )
    val uiState: StateFlow<SessionsUiState> = _uiState.asStateFlow()

    fun markSplashDone() = _uiState.update { it.copy(splashShown = true) }

    fun requestNewSession() = _uiState.update { it.copy(streamRequested = true) }

    fun clearStreamRequest() = _uiState.update { it.copy(streamRequested = false) }

    fun selectTab(tab: com.meta.wearable.dat.externalsampleapps.hankdaisy.ui.TopLevelTab) {
        _uiState.update { it.copy(currentTab = tab) }
    }

    fun selectPipelineSection(section: PipelineSection) {
        _uiState.update { it.copy(pipelineSection = section) }
    }

    fun updateConfig(config: AppConfig) {
        val normalized = config.normalized()
        _uiState.update { it.copy(config = normalized) }
        persistConfig(normalized)
    }

    fun updatePipelinePreset(preset: PipelinePreset) {
        updateConfig(_uiState.value.config.withPipelinePreset(preset))
    }

    private fun loadConfig(): AppConfig {
        val raw = prefs.getString(KEY_SETTINGS, null)
        return AppConfigStore.fromSettingsJson(raw)
    }

    private fun persistConfig(config: AppConfig) {
        val json = AppConfigStore.toJson(config)
        prefs.edit().putString(KEY_SETTINGS, json.toString()).apply()
    }
}
