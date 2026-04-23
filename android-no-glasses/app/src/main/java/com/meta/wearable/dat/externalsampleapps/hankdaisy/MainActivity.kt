/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.hankdaisy

import android.Manifest.permission.CAMERA
import android.Manifest.permission.INTERNET
import android.Manifest.permission.POST_NOTIFICATIONS
import android.Manifest.permission.RECORD_AUDIO
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.activity.viewModels
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import com.meta.wearable.dat.externalsampleapps.hankdaisy.ui.HankDaisyScaffold
import com.meta.wearable.dat.externalsampleapps.hankdaisy.wearables.WearablesViewModel

class MainActivity : ComponentActivity() {
  companion object {
    val PERMISSIONS: Array<String> =
        buildList {
              add(CAMERA)
              add(INTERNET)
              add(RECORD_AUDIO)
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(POST_NOTIFICATIONS)
              }
            }
            .toTypedArray()
  }

  val viewModel: WearablesViewModel by viewModels()

  private val permissionCheckLauncher =
      registerForActivityResult(RequestMultiplePermissions()) { permissionsResult ->
        viewModel.onPermissionsResult(permissionsResult)
      }

  suspend fun requestPhoneCameraPermission(permission: Permission): PermissionStatus {
    return if (permission == Permission.CAMERA && viewModel.uiState.value.canRegister) {
      PermissionStatus.Granted
    } else {
      PermissionStatus.Denied
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      HankDaisyScaffold(
          viewModel = viewModel,
          onRequestWearablesPermission = ::requestPhoneCameraPermission,
      )
    }
  }

  override fun onStart() {
    super.onStart()
    permissionCheckLauncher.launch(PERMISSIONS)
  }
}
