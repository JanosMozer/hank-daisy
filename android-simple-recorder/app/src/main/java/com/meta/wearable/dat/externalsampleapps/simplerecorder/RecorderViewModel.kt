package com.meta.wearable.dat.externalsampleapps.simplerecorder

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meta.wearable.dat.camera.Stream
import com.meta.wearable.dat.camera.addStream
import com.meta.wearable.dat.camera.types.StreamConfiguration
import com.meta.wearable.dat.camera.types.StreamError
import com.meta.wearable.dat.camera.types.StreamSessionState
import com.meta.wearable.dat.camera.types.VideoFrame
import com.meta.wearable.dat.camera.types.VideoQuality
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.AutoDeviceSelector
import com.meta.wearable.dat.core.selectors.DeviceSelector
import com.meta.wearable.dat.core.session.DeviceSessionState
import com.meta.wearable.dat.core.session.Session
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import com.meta.wearable.dat.core.types.RegistrationState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

enum class RecorderMode {
  IDLE,
  RECORDING,
  PAUSED,
  SAVING,
}

data class RecorderUiState(
    val hasAndroidPermissions: Boolean = false,
    val registrationState: RegistrationState = RegistrationState.Unavailable(),
    val deviceCount: Int = 0,
    val hasActiveDevice: Boolean = false,
    val streamState: StreamSessionState = StreamSessionState.STOPPED,
    val previewBitmap: Bitmap? = null,
    val status: String = "Grant Android permissions to begin.",
    val recorderMode: RecorderMode = RecorderMode.IDLE,
    val lastSavedPath: String? = null,
    val activeRecordingName: String? = null,
    val lastSavedName: String? = null,
    val elapsedRecordingMs: Long = 0L,
) {
  val canRegister: Boolean =
      hasAndroidPermissions &&
          registrationState !is RegistrationState.Registered &&
          registrationState !is RegistrationState.Registering

  val canConnect: Boolean =
      hasAndroidPermissions &&
          registrationState is RegistrationState.Registered &&
          streamState !in
              setOf(
                  StreamSessionState.STARTED,
                  StreamSessionState.STARTING,
                  StreamSessionState.STREAMING,
              )

  val canRecord: Boolean =
      streamState == StreamSessionState.STREAMING &&
          previewBitmap != null &&
          recorderMode != RecorderMode.RECORDING &&
          recorderMode != RecorderMode.SAVING

  val canPause: Boolean = recorderMode == RecorderMode.RECORDING
  val canSave: Boolean = recorderMode == RecorderMode.RECORDING || recorderMode == RecorderMode.PAUSED

  val recorderLabel: String =
      when (recorderMode) {
        RecorderMode.IDLE -> "Idle"
        RecorderMode.RECORDING -> "Recording"
        RecorderMode.PAUSED -> "Paused"
        RecorderMode.SAVING -> "Saving"
      }

  val elapsedLabel: String
    get() {
      val totalSeconds = elapsedRecordingMs / 1000
      val hours = totalSeconds / 3600
      val minutes = (totalSeconds % 3600) / 60
      val seconds = totalSeconds % 60
      return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
      } else {
        String.format("%02d:%02d", minutes, seconds)
      }
    }
}

class RecorderViewModel(application: Application) : AndroidViewModel(application) {
  companion object {
    private const val TAG = "SimpleRecorder"
    private const val TARGET_FPS = 24
  }

  private val deviceSelector: DeviceSelector = AutoDeviceSelector()
  private val frameRecorder = FrameRecorder(application.applicationContext)

  private val _uiState = MutableStateFlow(RecorderUiState())
  val uiState: StateFlow<RecorderUiState> = _uiState.asStateFlow()

  private var monitoringStarted = false
  private var session: Session? = null
  private var stream: Stream? = null
  private var sessionJob: Job? = null
  private var streamStateJob: Job? = null
  private var frameJob: Job? = null
  private var streamErrorJob: Job? = null
  private var activeDeviceJob: Job? = null
  private var timerJob: Job? = null
  private var lastFrameWidth = 0
  private var lastFrameHeight = 0
  private var recordingStartedAtMs = 0L
  private var accumulatedRecordingMs = 0L

  fun onAndroidPermissionsResult(granted: Boolean) {
    _uiState.update {
      it.copy(
          hasAndroidPermissions = granted,
          status =
              if (granted) "Register the app with Meta, then connect your glasses."
              else "Bluetooth, camera, internet, and notifications are required.",
      )
    }
    if (granted) {
      startMonitoring()
    }
  }

  private fun startMonitoring() {
    if (monitoringStarted) return
    monitoringStarted = true

    viewModelScope.launch {
      Wearables.registrationState.collect { registrationState ->
        _uiState.update { current ->
          current.copy(
              registrationState = registrationState,
              status =
                  when {
                    !current.hasAndroidPermissions ->
                        "Bluetooth, camera, internet, and notifications are required."
                    registrationState is RegistrationState.Registered && current.hasActiveDevice ->
                        "Glasses connected. Start the stream, then record."
                    registrationState is RegistrationState.Registered ->
                        "Registered. Turn on the glasses and connect them in Meta AI."
                    registrationState is RegistrationState.Registering ->
                        "Finish registration in the Meta AI app."
                    else -> "Register the app with Meta, then connect your glasses."
                  },
          )
        }
      }
    }

    viewModelScope.launch {
      Wearables.devices.collect { devices ->
        _uiState.update { it.copy(deviceCount = devices.size) }
      }
    }

    activeDeviceJob =
        viewModelScope.launch {
          deviceSelector.activeDeviceFlow().collect { device ->
            _uiState.update { current ->
              current.copy(
                  hasActiveDevice = device != null,
                  status =
                      when {
                        !current.hasAndroidPermissions ->
                            "Bluetooth, camera, internet, and notifications are required."
                        current.registrationState !is RegistrationState.Registered ->
                            current.status
                        device == null -> "Registered. Waiting for glasses to become active."
                        current.streamState == StreamSessionState.STREAMING ->
                            "Live camera stream ready."
                        else -> "Glasses ready. Tap Connect to start the live stream."
                      },
              )
            }
          }
        }
  }

  fun startRegistration(activity: Activity) {
    Wearables.startRegistration(activity)
  }

  fun ensureStreaming(onRequestWearablesPermission: suspend (Permission) -> PermissionStatus) {
    viewModelScope.launch {
      if (_uiState.value.streamState == StreamSessionState.STREAMING) return@launch
      val permissionResult = Wearables.checkPermissionStatus(Permission.CAMERA)
      permissionResult.onFailure { error, _ ->
        _uiState.update { it.copy(status = "Permission check failed: ${error.description}") }
        return@launch
      }
      val permissionStatus =
          permissionResult.getOrNull() ?: onRequestWearablesPermission(Permission.CAMERA)
      if (permissionStatus != PermissionStatus.Granted) {
        _uiState.update { it.copy(status = "Meta camera permission is required.") }
        return@launch
      }
      startStream()
    }
  }

  private fun startStream() {
    if (session != null) return
    _uiState.update { it.copy(status = "Starting glasses session...") }
    Wearables.createSession(deviceSelector)
        .onSuccess { createdSession ->
          session = createdSession
          observeSession(createdSession)
          createdSession.start()
        }
        .onFailure { error, _ ->
          _uiState.update { it.copy(status = "Could not create session: ${error.description}") }
        }
  }

  private fun observeSession(createdSession: Session) {
    sessionJob?.cancel()
    sessionJob =
        viewModelScope.launch {
          createdSession.state.collect { deviceState ->
            when (deviceState) {
              DeviceSessionState.STARTED -> attachCameraStream(createdSession)
              DeviceSessionState.STOPPED,
              DeviceSessionState.IDLE -> {
                _uiState.update {
                  it.copy(streamState = StreamSessionState.STOPPED, status = "Session stopped.")
                }
              }
              else -> Unit
            }
          }
        }
  }

  private fun attachCameraStream(createdSession: Session) {
    if (stream != null) return
    createdSession
        .addStream(
            StreamConfiguration(
                videoQuality = VideoQuality.MEDIUM,
                frameRate = TARGET_FPS,
            ),
        )
        .onSuccess { createdStream ->
          stream = createdStream
          observeStream(createdStream)
          createdStream.start()
        }
        .onFailure { error, _ ->
          _uiState.update { it.copy(status = "Could not start stream: ${error.description}") }
        }
  }

  private fun observeStream(createdStream: Stream) {
    frameJob?.cancel()
    streamStateJob?.cancel()
    streamErrorJob?.cancel()

    streamStateJob =
        viewModelScope.launch {
          createdStream.state.collect { streamState ->
            _uiState.update {
              it.copy(
                  streamState = streamState,
                  status =
                      when (streamState) {
                        StreamSessionState.STARTING -> "Opening live preview..."
                        StreamSessionState.STARTED -> "Preview preparing..."
                        StreamSessionState.STREAMING -> "Live camera stream ready."
                        StreamSessionState.STOPPING -> "Stopping stream..."
                        StreamSessionState.STOPPED,
                        StreamSessionState.CLOSED -> "Stream stopped."
                      },
              )
            }
          }
        }

    frameJob =
        viewModelScope.launch(Dispatchers.Default) {
          createdStream.videoStream.collect { frame ->
            handleVideoFrame(frame)
          }
        }

    streamErrorJob =
        viewModelScope.launch {
          createdStream.errorStream.collect { error ->
            val message =
                if (error == StreamError.HINGE_CLOSED) {
                  "The glasses were folded. Re-open them and connect again."
                } else {
                  "Stream error: ${error.description}"
                }
            _uiState.update { it.copy(status = message) }
          }
        }
  }

  private fun handleVideoFrame(frame: VideoFrame) {
    lastFrameWidth = frame.width
    lastFrameHeight = frame.height

    val previewBitmap =
        YuvToBitmapConverter.convert(frame.buffer.duplicate(), frame.width, frame.height)
    _uiState.update { it.copy(previewBitmap = previewBitmap) }

    if (_uiState.value.recorderMode == RecorderMode.RECORDING) {
      try {
        frameRecorder.recordFrame(frame.buffer.duplicate(), frame.width, frame.height)
      } catch (e: Exception) {
        Log.e(TAG, "Recording frame failed", e)
        _uiState.update {
          it.copy(recorderMode = RecorderMode.IDLE, status = "Recording failed: ${e.message}")
        }
      }
    }
  }

  fun startOrResumeRecording() {
    val currentState = _uiState.value
    if (currentState.streamState != StreamSessionState.STREAMING) {
      _uiState.update { it.copy(status = "Connect the glasses stream before recording.") }
      return
    }
    if (lastFrameWidth <= 0 || lastFrameHeight <= 0) {
      _uiState.update { it.copy(status = "Waiting for the first live frame.") }
      return
    }

    when (currentState.recorderMode) {
      RecorderMode.IDLE -> {
        try {
          frameRecorder.start(lastFrameWidth, lastFrameHeight, TARGET_FPS)
          recordingStartedAtMs = SystemClock.elapsedRealtime()
          accumulatedRecordingMs = 0L
          startTimer()
          _uiState.update {
            it.copy(
                recorderMode = RecorderMode.RECORDING,
                lastSavedPath = null,
                lastSavedName = null,
                activeRecordingName = frameRecorder.currentFileName,
                elapsedRecordingMs = 0L,
                status = "Recording started.",
            )
          }
        } catch (e: Exception) {
          _uiState.update { it.copy(status = "Could not start recording: ${e.message}") }
        }
      }
      RecorderMode.PAUSED -> {
        frameRecorder.resume()
        recordingStartedAtMs = SystemClock.elapsedRealtime()
        startTimer()
        _uiState.update {
          it.copy(
              recorderMode = RecorderMode.RECORDING,
              status = "Recording resumed.",
              activeRecordingName = frameRecorder.currentFileName,
          )
        }
      }
      else -> Unit
    }
  }

  fun pauseRecording() {
    if (_uiState.value.recorderMode != RecorderMode.RECORDING) return
    frameRecorder.pause()
    accumulatedRecordingMs += SystemClock.elapsedRealtime() - recordingStartedAtMs
    stopTimer()
    _uiState.update {
      it.copy(
          recorderMode = RecorderMode.PAUSED,
          status = "Recording paused.",
          elapsedRecordingMs = accumulatedRecordingMs,
      )
    }
  }

  fun saveRecording() {
    if (_uiState.value.recorderMode !in setOf(RecorderMode.RECORDING, RecorderMode.PAUSED)) return
    _uiState.update { it.copy(recorderMode = RecorderMode.SAVING, status = "Saving recording...") }
    viewModelScope.launch {
      val savedFile =
          withContext(Dispatchers.IO) {
            frameRecorder.finish()
          }
      stopTimer()
      accumulatedRecordingMs = 0L
      _uiState.update {
        it.copy(
            recorderMode = RecorderMode.IDLE,
            lastSavedPath = savedFile.absolutePath,
            lastSavedName = savedFile.name,
            activeRecordingName = null,
            elapsedRecordingMs = 0L,
            status = "Saved recording.",
        )
      }
    }
  }

  fun flushRecording() {
    if (_uiState.value.recorderMode == RecorderMode.IDLE) return
    viewModelScope.launch(Dispatchers.IO) {
      try {
        frameRecorder.finish()
      } catch (_: Exception) {
      } finally {
        stopTimer()
        accumulatedRecordingMs = 0L
        _uiState.update {
          it.copy(
              recorderMode = RecorderMode.IDLE,
              activeRecordingName = null,
              elapsedRecordingMs = 0L,
          )
        }
      }
    }
  }

  private fun startTimer() {
    timerJob?.cancel()
    timerJob =
        viewModelScope.launch {
          while (true) {
            val elapsed = accumulatedRecordingMs + (SystemClock.elapsedRealtime() - recordingStartedAtMs)
            _uiState.update { it.copy(elapsedRecordingMs = elapsed) }
            delay(250L)
          }
        }
  }

  private fun stopTimer() {
    timerJob?.cancel()
    timerJob = null
  }

  override fun onCleared() {
    super.onCleared()
    flushRecording()
    frameJob?.cancel()
    streamStateJob?.cancel()
    streamErrorJob?.cancel()
    sessionJob?.cancel()
    activeDeviceJob?.cancel()
    timerJob?.cancel()
    try {
      stream?.stop()
    } catch (_: Exception) {
    }
    try {
      session?.stop()
    } catch (_: Exception) {
    }
    stream = null
    session = null
  }
}
