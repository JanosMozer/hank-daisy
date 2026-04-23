/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.hankdaisy.wearables

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import com.meta.wearable.dat.core.types.RegistrationState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WearablesViewModel(application: Application) : AndroidViewModel(application) {
  private val _uiState =
      MutableStateFlow(
          WearablesUiState(
              registrationState = RegistrationState.Registered(),
              devices = persistentListOf("phone-camera"),
              hasActiveDevice = true,
          ),
      )
  val uiState: StateFlow<WearablesUiState> = _uiState.asStateFlow()

  fun startRegistration(activity: Activity) {
    _uiState.update {
      it.copy(
          registrationState = RegistrationState.Registered(),
          devices = persistentListOf("phone-camera"),
          hasActiveDevice = true,
          canRegister = true,
          isGettingStartedSheetVisible = true,
      )
    }
  }

  fun startUnregistration(activity: Activity) {
    _uiState.update {
      it.copy(
          registrationState = RegistrationState.Registered(),
          devices = persistentListOf("phone-camera"),
          hasActiveDevice = true,
      )
    }
  }

  fun navigateToStreaming(onRequestWearablesPermission: suspend (Permission) -> PermissionStatus) {
    viewModelScope.launch {
      when (onRequestWearablesPermission(Permission.CAMERA)) {
        PermissionStatus.Granted -> _uiState.update { it.copy(isStreaming = true) }
        PermissionStatus.Denied -> setRecentError("Camera or microphone permission denied")
      }
    }
  }

  fun navigateToDeviceSelection() {
    _uiState.update { it.copy(isStreaming = false) }
  }

  fun showDebugMenu() {
    _uiState.update { it.copy(isDebugMenuVisible = true) }
  }

  fun hideDebugMenu() {
    _uiState.update { it.copy(isDebugMenuVisible = false) }
  }

  fun clearRecentError() {
    _uiState.update { it.copy(recentError = null) }
  }

  private fun setRecentError(error: String) {
    _uiState.update { it.copy(recentError = error) }
  }

  fun onPermissionsResult(permissionsResult: Map<String, Boolean>) {
    val granted = permissionsResult.entries.all { it.value }
    _uiState.update {
      it.copy(
          canRegister = granted,
          registrationState = RegistrationState.Registered(),
          devices = if (granted) persistentListOf("phone-camera") else persistentListOf(),
          hasActiveDevice = granted,
          recentError = if (granted) null else "Allow camera, microphone, internet, and notification permissions",
      )
    }
  }

  fun showGettingStartedSheet() {
    _uiState.update { it.copy(isGettingStartedSheetVisible = true) }
  }

  fun hideGettingStartedSheet() {
    _uiState.update { it.copy(isGettingStartedSheetVisible = false) }
  }
}
