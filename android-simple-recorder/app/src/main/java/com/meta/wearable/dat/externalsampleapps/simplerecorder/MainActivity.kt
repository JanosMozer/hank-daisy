package com.meta.wearable.dat.externalsampleapps.simplerecorder

import android.Manifest.permission.BLUETOOTH
import android.Manifest.permission.BLUETOOTH_CONNECT
import android.Manifest.permission.CAMERA
import android.Manifest.permission.INTERNET
import android.Manifest.permission.POST_NOTIFICATIONS
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MainActivity : ComponentActivity() {
  companion object {
    val PERMISSIONS: Array<String> =
        buildList {
              add(BLUETOOTH)
              add(BLUETOOTH_CONNECT)
              add(CAMERA)
              add(INTERNET)
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(POST_NOTIFICATIONS)
              }
            }
            .toTypedArray()
  }

  private val viewModel: RecorderViewModel by viewModels()

  private val permissionCheckLauncher =
      registerForActivityResult(RequestMultiplePermissions()) { permissionsResult ->
        val granted = permissionsResult.values.all { it }
        viewModel.onAndroidPermissionsResult(granted)
        if (granted) {
          Wearables.initialize(this)
        }
      }

  private var permissionContinuation: CancellableContinuation<PermissionStatus>? = null
  private val permissionMutex = Mutex()

  private val permissionsResultLauncher =
      registerForActivityResult(Wearables.RequestPermissionContract()) { result ->
        val permissionStatus = result.getOrDefault(PermissionStatus.Denied)
        permissionContinuation?.resume(permissionStatus)
        permissionContinuation = null
      }

  private suspend fun requestWearablesPermission(permission: Permission): PermissionStatus {
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
      val uiState by viewModel.uiState.collectAsStateWithLifecycle()
      RecorderApp(
          state = uiState,
          onRegister = { viewModel.startRegistration(this) },
          onConnect = { viewModel.ensureStreaming(::requestWearablesPermission) },
          onRecord = { viewModel.startOrResumeRecording() },
          onPause = { viewModel.pauseRecording() },
          onSave = { viewModel.saveRecording() },
      )
    }
  }

  override fun onStart() {
    super.onStart()
    permissionCheckLauncher.launch(PERMISSIONS)
  }

  override fun onStop() {
    super.onStop()
    viewModel.flushRecording()
  }
}

@Composable
private fun RecorderApp(
    state: RecorderUiState,
    onRegister: () -> Unit,
    onConnect: () -> Unit,
    onRecord: () -> Unit,
    onPause: () -> Unit,
    onSave: () -> Unit,
) {
  Surface(color = Color(0xFF0B0B0C), modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
      Column(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text(
            text = "Simple Meta Recorder",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = state.status,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.8f),
        )
        state.lastSavedPath?.let { savedPath ->
          Text(
              text = "Saved to $savedPath",
              style = MaterialTheme.typography.bodySmall,
              color = Color(0xFF8AE6A3),
          )
        }
        state.activeRecordingName?.let { fileName ->
          Text(
              text = "Recording file: $fileName",
              style = MaterialTheme.typography.bodySmall,
              color = Color.White.copy(alpha = 0.78f),
          )
        } ?: state.lastSavedName?.let { fileName ->
          Text(
              text = "Last file: $fileName",
              style = MaterialTheme.typography.bodySmall,
              color = Color.White.copy(alpha = 0.78f),
          )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          if (state.canRegister) {
            Button(onClick = onRegister) { Text("Register") }
          }
          if (state.canConnect) {
            Button(onClick = onConnect) { Text("Connect") }
          }
        }
      }

      Box(
          modifier =
              Modifier.fillMaxWidth()
                  .weight(1f)
                  .padding(horizontal = 16.dp)
                  .background(Color.Black, RoundedCornerShape(8.dp)),
          contentAlignment = Alignment.Center,
      ) {
        if (state.previewBitmap != null) {
          Image(
              bitmap = state.previewBitmap.asImageBitmap(),
              contentDescription = "Live glasses preview",
              modifier = Modifier.fillMaxSize().aspectRatio(9f / 16f),
              contentScale = ContentScale.Crop,
          )
        } else {
          Text(
              text = "Waiting for Meta glasses camera stream",
              color = Color.White.copy(alpha = 0.7f),
              textAlign = TextAlign.Center,
              modifier = Modifier.padding(24.dp),
          )
        }
      }

      Column(
          modifier =
              Modifier.fillMaxWidth()
                  .navigationBarsPadding()
                  .padding(horizontal = 16.dp, vertical = 16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
          Button(
              onClick = onRecord,
              enabled = state.canRecord,
              modifier = Modifier.weight(1f),
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE03A3A)),
          ) {
            Text("Record")
          }
          Button(
              onClick = onPause,
              enabled = state.canPause,
              modifier = Modifier.weight(1f),
          ) {
            Text("Pause")
          }
          Button(
              onClick = onSave,
              enabled = state.canSave,
              modifier = Modifier.weight(1f),
          ) {
            Text("Save")
          }
        }
        Text(
            text = state.recorderLabel,
            color = Color.White.copy(alpha = 0.82f),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(
              modifier =
                  Modifier.size(10.dp)
                      .background(
                          color =
                              when (state.recorderMode) {
                                RecorderMode.RECORDING -> Color(0xFFE03A3A)
                                RecorderMode.PAUSED -> Color(0xFFF0B44A)
                                else -> Color.White.copy(alpha = 0.22f)
                              },
                          shape = CircleShape,
                      ),
          )
          Spacer(modifier = Modifier.size(8.dp))
          Text(
              text = state.elapsedLabel,
              color = Color.White,
              style = MaterialTheme.typography.headlineSmall,
              textAlign = TextAlign.Center,
              fontWeight = FontWeight.SemiBold,
          )
        }
        Spacer(modifier = Modifier.height(4.dp))
      }
    }
  }
}
