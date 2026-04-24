/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

// MPI Android - Main Activity
//
// Initial scaffold entry point for the MPI Android app. This fork still uses
// the Meta Wearables Device Access Toolkit (DAT) and glasses-connected camera
// flow while the dedicated inspection workflow is being built.
//
// Current responsibilities:
// - Initialize the DAT SDK
// - Handle device permissions (Bluetooth, Internet)
// - Request camera permissions from wearable devices (Ray-Ban Meta glasses)
// - Stream video and capture photos from connected wearable devices

package com.meta.wearable.dat.externalsampleapps.mpi

import android.Manifest.permission.BLUETOOTH
import android.Manifest.permission.BLUETOOTH_CONNECT
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
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import com.meta.wearable.dat.externalsampleapps.mpi.ui.HankDaisyScaffold
import com.meta.wearable.dat.externalsampleapps.mpi.wearables.WearablesViewModel
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MainActivity : ComponentActivity() {
  companion object {
    // Required Android permissions for the DAT SDK + glasses-mediated voice loop.
    // RECORD_AUDIO is needed so SpeechRecognizer can read from the SCO mic (glasses).
    // POST_NOTIFICATIONS is required on API 33+ for the foreground service notification.
    val PERMISSIONS: Array<String> =
        buildList {
              add(BLUETOOTH)
              add(BLUETOOTH_CONNECT)
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
        viewModel.onPermissionsResult(permissionsResult) {
          // Initialize the DAT SDK once the permissions are granted
          // This is REQUIRED before using any Wearables APIs
          Wearables.initialize(this)
        }
      }

  private var permissionContinuation: CancellableContinuation<PermissionStatus>? = null
  private val permissionMutex = Mutex()
  // Requesting wearable device permissions via the Meta AI app
  private val permissionsResultLauncher =
      registerForActivityResult(Wearables.RequestPermissionContract()) { result ->
        val permissionStatus = result.getOrDefault(PermissionStatus.Denied)
        permissionContinuation?.resume(permissionStatus)
        permissionContinuation = null
      }

  // Convenience method to make a permission request in a sequential manner
  // Uses a Mutex to ensure requests are processed one at a time, preventing race conditions
  suspend fun requestWearablesPermission(permission: Permission): PermissionStatus {
    return permissionMutex.withLock {
      suspendCancellableCoroutine { continuation ->
        permissionContinuation = continuation
        continuation.invokeOnCancellation { permissionContinuation = null }
        permissionsResultLauncher.launch(permission)
      }
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      HankDaisyScaffold(
          viewModel = viewModel,
          onRequestWearablesPermission = ::requestWearablesPermission,
      )
    }
  }

  override fun onStart() {
    super.onStart()
    // First, ensure the app has necessary Android permissions
    permissionCheckLauncher.launch(PERMISSIONS)
  }
}
