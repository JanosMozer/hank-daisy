package com.meta.wearable.dat.externalsampleapps.simplerecorder

import android.Manifest.permission.BLUETOOTH
import android.Manifest.permission.BLUETOOTH_CONNECT
import android.Manifest.permission.CAMERA
import android.Manifest.permission.INTERNET
import android.Manifest.permission.POST_NOTIFICATIONS
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.core.content.FileProvider
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
          onShareLatest = {
            viewModel.latestRecordingPath()?.let { shareRecording(it) }
          },
          onShareRecording = { shareRecording(it) },
          onDeleteRecording = { viewModel.deleteRecording(it) },
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

  private fun shareRecording(path: String) {
    val file = File(path)
    if (!file.exists()) return
    val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
    val shareIntent =
        Intent(Intent.ACTION_SEND).apply {
          type = "video/mp4"
          putExtra(Intent.EXTRA_STREAM, uri)
          putExtra(Intent.EXTRA_SUBJECT, file.name)
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    startActivity(Intent.createChooser(shareIntent, "Share recording"))
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
    onShareLatest: () -> Unit,
    onShareRecording: (String) -> Unit,
    onDeleteRecording: (String) -> Unit,
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
          if (state.canShareLatest) {
            OutlinedButton(onClick = onShareLatest) { Text("Share latest") }
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

      Text(
          text = "Saved recordings",
          color = Color.White,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
      )
      if (state.recordings.isEmpty()) {
        Text(
            text = "No saved recordings yet.",
            color = Color.White.copy(alpha = 0.65f),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
      } else {
        LazyColumn(
            modifier =
                Modifier.fillMaxWidth()
                    .height(160.dp)
                    .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(state.recordings, key = { it.absolutePath }) { recording ->
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
              Box(
                  modifier =
                      Modifier.width(88.dp)
                          .height(56.dp)
                          .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(6.dp)),
                  contentAlignment = Alignment.Center,
              ) {
                if (recording.thumbnail != null) {
                  Image(
                      bitmap = recording.thumbnail.asImageBitmap(),
                      contentDescription = recording.name,
                      modifier = Modifier.fillMaxSize(),
                      contentScale = ContentScale.Crop,
                  )
                } else {
                  Text(
                      text = "No preview",
                      color = Color.White.copy(alpha = 0.5f),
                      style = MaterialTheme.typography.bodySmall,
                  )
                }
              }
              Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = recording.name,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                )
                Text(
                    text = "${formatRecordingTime(recording.modifiedAt)}  •  ${recording.sizeLabel}",
                    color = Color.White.copy(alpha = 0.62f),
                    style = MaterialTheme.typography.bodySmall,
                )
              }
              Row(
                  horizontalArrangement = Arrangement.spacedBy(4.dp),
                  verticalAlignment = Alignment.CenterVertically,
              ) {
                TextButton(onClick = { onShareRecording(recording.absolutePath) }) {
                  Text(
                      text = "Share",
                      color = Color(0xFF8AE6A3),
                      style = MaterialTheme.typography.bodySmall,
                      fontWeight = FontWeight.SemiBold,
                  )
                }
                TextButton(onClick = { onDeleteRecording(recording.absolutePath) }) {
                  Text(
                      text = "Delete",
                      color = Color(0xFFFF8A8A),
                      style = MaterialTheme.typography.bodySmall,
                      fontWeight = FontWeight.SemiBold,
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

private fun formatRecordingTime(timestamp: Long): String {
  return SimpleDateFormat("MMM d, h:mm a", Locale.US).format(Date(timestamp))
}
