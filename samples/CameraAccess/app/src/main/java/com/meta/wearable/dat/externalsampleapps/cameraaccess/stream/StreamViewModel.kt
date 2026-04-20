/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

// StreamViewModel - DAT Camera Streaming API Demo
//
// This ViewModel demonstrates the DAT Camera Streaming APIs for:
// - Creating and managing stream sessions with wearable devices
// - Receiving video frames from device cameras
// - Capturing photos during streaming sessions
// - Handling different video qualities and formats
// - Processing raw video data (I420 -> ARGB conversion)

package com.meta.wearable.dat.externalsampleapps.cameraaccess.stream

import android.annotation.SuppressLint
import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.meta.wearable.dat.camera.Stream
import com.meta.wearable.dat.camera.addStream
import com.meta.wearable.dat.camera.types.PhotoData
import com.meta.wearable.dat.camera.types.StreamConfiguration
import com.meta.wearable.dat.camera.types.StreamError
import com.meta.wearable.dat.camera.types.StreamSessionState
import com.meta.wearable.dat.camera.types.VideoFrame
import com.meta.wearable.dat.camera.types.VideoQuality
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.DeviceSelector
import com.meta.wearable.dat.core.session.DeviceSessionState
import com.meta.wearable.dat.core.session.Session
import com.meta.wearable.dat.externalsampleapps.cameraaccess.wearables.WearablesViewModel
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@SuppressLint("AutoCloseableUse")
class StreamViewModel(
    application: Application,
    private val wearablesViewModel: WearablesViewModel,
) : AndroidViewModel(application) {

  companion object {
    private const val TAG = "CameraAccess:StreamViewModel"
    private val INITIAL_STATE = StreamUiState()
    private val SESSION_TERMINAL_STATES = setOf(StreamSessionState.CLOSED)
  }

  private val deviceSelector: DeviceSelector = wearablesViewModel.deviceSelector
  private var session: Session? = null

  private val _uiState = MutableStateFlow(INITIAL_STATE)
  val uiState: StateFlow<StreamUiState> = _uiState.asStateFlow()

  private var videoJob: Job? = null
  private var stateJob: Job? = null
  private var errorJob: Job? = null
  private var sessionStateJob: Job? = null
  private var stream: Stream? = null
  private val liveStreamServer = LiveStreamServer(8080)
  private val geminiService = GeminiService()
  private val glassesAudio = GlassesAudioManager(application)
  private val voiceCommand = VoiceCommandManager(application)
  private var voiceJob: Job? = null
  private var audioStatusJob: Job? = null
  private var speakingJob: Job? = null
  private var teardownJob: Job? = null
  private var analyzeJob: Job? = null

  private val conversationHistory = mutableListOf<GeminiService.Turn>()

  private val bargeInDetector =
      BargeInDetector(
          onUserSpeaking = {
            // Posted from a worker thread — bounce to main + viewModelScope.
            viewModelScope.launch {
              Log.d(TAG, "Barge-in detected — cutting Hank off")
              glassesAudio.stopSpeaking()
              voiceCommand.startConversationFollowUp()
            }
          },
      )

  // Presentation queue for buffering frames after color conversion
  private var presentationQueue: PresentationQueue? = null

  fun startStream() {
    if (session != null && videoJob != null) {
      Log.d(TAG, "startStream() called while already streaming — ignoring")
      return
    }
    viewModelScope.launch {
      // If a previous session is still tearing down, wait for it. The DAT SDK
      // doesn't tolerate creating a new session while the previous one is in
      // STOPPING — it crashes the connection thread.
      teardownJob?.join()
      try {
        startStreamLocked()
      } catch (e: Exception) {
        Log.e(TAG, "startStream failed", e)
        _uiState.update { INITIAL_STATE }
      }
    }
  }

  private fun startStreamLocked() {
    videoJob?.cancel()
    stateJob?.cancel()
    errorJob?.cancel()
    sessionStateJob?.cancel()
    presentationQueue?.stop()
    presentationQueue = null

    // Initialize presentation queue - frames are presented based on timestamp, not arrival time
    // Uses IntArray pooling for efficiency - cheaper than Bitmap.copy()
    val queue =
        PresentationQueue(
            bufferDelayMs = 100L,
            maxQueueSize = 15,
            onFrameReady = { frame ->
              // This is called from the presentation thread at regular intervals
              // when a frame's presentation time has arrived
              _uiState.update {
                it.copy(videoFrame = frame.bitmap, videoFrameCount = it.videoFrameCount + 1)
              }
            },
        )
    presentationQueue = queue
    queue.start()
    if (session == null) {
      try {
        Wearables.createSession(deviceSelector)
            .onSuccess { createdSession ->
              session = createdSession
              try {
                session?.start()
              } catch (e: Exception) {
                Log.e(TAG, "session?.start() failed", e)
              }
            }
            .onFailure { error, _ ->
              Log.e(TAG, "Failed to create session: ${error.description}")
            }
      } catch (e: Exception) {
        Log.e(TAG, "createSession threw", e)
      }
      if (session == null) return
    }
    try {
      liveStreamServer.start()
    } catch (e: Exception) {
      Log.w(TAG, "liveStreamServer.start() failed (continuing)", e)
    }
    try {
      StreamForegroundService.start(getApplication())
    } catch (e: Exception) {
      Log.w(TAG, "StreamForegroundService.start() failed (continuing)", e)
    }
    glassesAudio.enableGlassesMic()
    observeGlassesAudio()
    observeTtsSpeaking()
    startWakeWordListening()
    startStreamInternal()
  }

  private fun observeGlassesAudio() {
    audioStatusJob?.cancel()
    audioStatusJob =
        viewModelScope.launch {
          glassesAudio.glassesAudioStatus.collect { status ->
            _uiState.update { it.copy(glassesAudioStatus = status) }
          }
        }
  }

  private fun observeTtsSpeaking() {
    speakingJob?.cancel()
    speakingJob =
        viewModelScope.launch {
          glassesAudio.isSpeaking.collect { speaking ->
            // Mute the recognizer (it can't echo-cancel Hank's own voice) and
            // hand the mic to BargeInDetector, which CAN (via AEC).
            voiceCommand.setMuted(speaking)
            if (speaking) {
              bargeInDetector.start()
            } else {
              bargeInDetector.stop()
              // After Hank finishes speaking naturally, auto-listen for a
              // follow-up turn so the conversation flows without needing
              // "Hey Hank" again.
              if (conversationHistory.isNotEmpty()) {
                voiceCommand.startConversationFollowUp()
              }
            }
          }
        }
  }

  private fun startStreamInternal() {
    Log.d(TAG, "startStreamInternal() - collecting session state")
    sessionStateJob =
        safeLaunch("sessionState") {
          session?.state?.collect { currentState ->
            if (currentState == DeviceSessionState.STARTED) {
              videoJob?.cancel()
              stateJob?.cancel()
              errorJob?.cancel()
              try {
                stream?.stop()
              } catch (e: Exception) {
                Log.w(TAG, "stream?.stop() before re-add failed", e)
              }
              stream = null
              try {
                session
                    ?.addStream(StreamConfiguration(videoQuality = VideoQuality.MEDIUM, 24))
                    ?.onSuccess { addedStream ->
                      stream = addedStream
                      videoJob =
                          safeLaunch("videoStream") {
                            Log.d(TAG, "Collecting video frames from stream")
                            stream?.videoStream?.collect { handleVideoFrame(it) }
                            Log.d(TAG, "Video stream collection ended")
                          }
                      stateJob =
                          safeLaunch("streamState") {
                            stream?.state?.collect { currentState ->
                              val prevState = _uiState.value.streamSessionState
                              Log.d(TAG, "Stream state changed: $prevState -> $currentState")
                              _uiState.update { it.copy(streamSessionState = currentState) }

                              val wasActive = prevState !in SESSION_TERMINAL_STATES
                              val isTerminated = currentState in SESSION_TERMINAL_STATES
                              if (wasActive && isTerminated) {
                                Log.d(TAG, "Terminal state reached, navigating back")
                                stopStream()
                                wearablesViewModel.navigateToDeviceSelection()
                              }
                            }
                          }
                      errorJob =
                          safeLaunch("errorStream") {
                            stream?.errorStream?.collect { error ->
                              Log.d(
                                  TAG,
                                  "Stream error received: $error (description: ${error.description})",
                              )
                              if (error == StreamError.HINGE_CLOSED) {
                                Log.d(
                                    TAG,
                                    "HINGE_CLOSED detected, stopping stream and navigating back",
                                )
                                stopStream()
                                wearablesViewModel.navigateToDeviceSelection()
                              }
                            }
                          }
                      try {
                        stream?.start()
                      } catch (e: Exception) {
                        Log.e(TAG, "stream?.start() failed", e)
                        stopStream()
                      }
                    }
                    ?.onFailure { error, _ ->
                      Log.e(TAG, "Failed to add stream to session: ${error.description}")
                    }
              } catch (e: Exception) {
                Log.e(TAG, "addStream threw", e)
                stopStream()
              }
            }
          }
        }
  }

  /** viewModelScope.launch + try/catch so an exception in any collect doesn't kill the process. */
  private fun safeLaunch(label: String, block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit): Job =
      viewModelScope.launch {
        try {
          block()
        } catch (ce: kotlinx.coroutines.CancellationException) {
          throw ce
        } catch (e: Exception) {
          Log.e(TAG, "Coroutine [$label] crashed (swallowed)", e)
        }
      }

  fun stopStream() {
    if (session == null && videoJob == null && teardownJob == null) {
      return
    }
    val sessionToTearDown = session
    session = null
    try { videoJob?.cancel() } catch (_: Exception) {}
    videoJob = null
    try { stateJob?.cancel() } catch (_: Exception) {}
    stateJob = null
    try { errorJob?.cancel() } catch (_: Exception) {}
    errorJob = null
    try { sessionStateJob?.cancel() } catch (_: Exception) {}
    sessionStateJob = null
    try { presentationQueue?.stop() } catch (_: Exception) {}
    presentationQueue = null
    _uiState.update { INITIAL_STATE }
    try { stream?.stop() } catch (e: Exception) { Log.w(TAG, "stream?.stop() in stopStream", e) }
    stream = null
    try { liveStreamServer.stop() } catch (e: Exception) { Log.w(TAG, "liveStreamServer.stop()", e) }
    try { voiceCommand.stopContinuousListening() } catch (_: Exception) {}
    try { voiceJob?.cancel() } catch (_: Exception) {}
    voiceJob = null
    try { audioStatusJob?.cancel() } catch (_: Exception) {}
    audioStatusJob = null
    try { speakingJob?.cancel() } catch (_: Exception) {}
    speakingJob = null
    try { glassesAudio.stopSpeaking() } catch (_: Exception) {}
    try { bargeInDetector.stop() } catch (_: Exception) {}
    try { analyzeJob?.cancel() } catch (_: Exception) {}
    analyzeJob = null
    conversationHistory.clear()
    try { glassesAudio.disableGlassesMic() } catch (_: Exception) {}
    try { StreamForegroundService.stop(getApplication()) } catch (_: Exception) {}

    if (sessionToTearDown != null) {
      teardownJob =
          viewModelScope.launch {
            try {
              sessionToTearDown.stop()
              withTimeoutOrNull(3000L) {
                sessionToTearDown.state.first { it == DeviceSessionState.IDLE }
              }
            } catch (e: Exception) {
              Log.w(TAG, "Session teardown error (ignored)", e)
            }
          }
    }
  }

  fun capturePhoto() {
    if (uiState.value.isCapturing) {
      Log.d(TAG, "Photo capture already in progress, ignoring request")
      return
    }

    if (uiState.value.streamSessionState == StreamSessionState.STREAMING) {
      Log.d(TAG, "Starting photo capture")
      _uiState.update { it.copy(isCapturing = true) }

      viewModelScope.launch {
        stream
            ?.capturePhoto()
            ?.onSuccess { photoData ->
              Log.d(TAG, "Photo capture successful")
              handlePhotoData(photoData)
              _uiState.update { it.copy(isCapturing = false) }
            }
            ?.onFailure { error, _ ->
              Log.e(TAG, "Photo capture failed: ${error.description}")
              _uiState.update { it.copy(isCapturing = false) }
            }
      }
    } else {
      Log.w(
          TAG,
          "Cannot capture photo: stream not active (state=${uiState.value.streamSessionState})",
      )
    }
  }

  fun showShareDialog() {
    _uiState.update { it.copy(isShareDialogVisible = true) }
  }

  fun hideShareDialog() {
    _uiState.update { it.copy(isShareDialogVisible = false) }
  }

  /**
   * Start always-on "Hey Hank" listening.
   * Called automatically when stream starts.
   */
  private fun startWakeWordListening() {
    voiceCommand.startContinuousListening()

    voiceJob?.cancel()
    voiceJob = viewModelScope.launch {
      voiceCommand.state.collect { voiceState ->
        when (voiceState) {
          is VoiceCommandManager.VoiceState.Passive -> {
            _uiState.update { it.copy(isListening = false, isWakeWordActive = true) }
          }
          is VoiceCommandManager.VoiceState.Listening -> {
            _uiState.update { it.copy(isListening = true) }
          }
          is VoiceCommandManager.VoiceState.QuestionReady -> {
            _uiState.update { it.copy(isListening = false, spokenQuestion = voiceState.text) }
            analyzeWithQuestion(voiceState.text)
          }
          is VoiceCommandManager.VoiceState.Error -> {
            Log.w(TAG, "Voice error: ${voiceState.message}")
            // Don't show errors in UI for passive mode — just keep listening
          }
          is VoiceCommandManager.VoiceState.Off -> {
            _uiState.update { it.copy(isListening = false, isWakeWordActive = false) }
          }
        }
      }
    }
  }

  /** Manual Ask Hank — skips wake word, goes straight to listening for question. */
  fun askHank() {
    if (_uiState.value.isListening || _uiState.value.isAnalyzing) return
    _uiState.update { it.copy(lastGeminiResponse = null, spokenQuestion = null) }
    voiceCommand.startManualListen()
  }

  /** Quick analyze without voice — uses default prompt. */
  fun analyzeCurrentFrame() {
    analyzeWithQuestion("What do you see? Identify any problems and tell me how to fix them step by step.")
  }

  private fun analyzeWithQuestion(question: String) {
    val currentFrame = _uiState.value.videoFrame ?: return
    if (_uiState.value.isAnalyzing) return

    _uiState.update { it.copy(isAnalyzing = true, lastGeminiResponse = null) }

    analyzeJob?.cancel()
    analyzeJob =
        viewModelScope.launch {
          val frameCopy = currentFrame.copy(currentFrame.config ?: Bitmap.Config.ARGB_8888, true)
          // Snapshot the conversation so far; new turn appended below on success.
          val historySnapshot = conversationHistory.toList()
          val response = geminiService.analyzeFrame(frameCopy, question, historySnapshot)
          frameCopy.recycle()

          // Append both sides of the turn to history so Hank remembers it.
          conversationHistory.add(GeminiService.Turn("user", question))
          conversationHistory.add(GeminiService.Turn("assistant", response))
          // Trim if it gets unwieldy — keep last 24 turns (~12 exchanges).
          while (conversationHistory.size > 24) {
            conversationHistory.removeAt(0)
          }

          _uiState.update { it.copy(isAnalyzing = false, lastGeminiResponse = response) }
          glassesAudio.speak(response)
          // Note: voiceCommand.startConversationFollowUp() is fired from the
          // isSpeaking=false transition in observeTtsSpeaking(), AFTER TTS
          // actually finishes — so we don't start listening over Hank's voice.
        }
  }

  fun cancelListening() {
    voiceCommand.stopContinuousListening()
    voiceJob?.cancel()
    _uiState.update { it.copy(isListening = false, isWakeWordActive = false) }
  }


  fun sharePhoto(bitmap: Bitmap) {
    val context = getApplication<Application>()
    val imagesFolder = File(context.cacheDir, "images")
    try {
      imagesFolder.mkdirs()
      val file = File(imagesFolder, "shared_image.png")
      FileOutputStream(file).use { stream ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 90, stream)
      }

      val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
      val intent = Intent(Intent.ACTION_SEND)
      intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
      intent.putExtra(Intent.EXTRA_STREAM, uri)
      intent.type = "image/png"
      intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

      val chooser = Intent.createChooser(intent, "Share Image")
      chooser.flags = Intent.FLAG_ACTIVITY_NEW_TASK
      context.startActivity(chooser)
    } catch (e: IOException) {
      Log.e("StreamViewModel", "Failed to share photo", e)
    }
  }

  private fun handleVideoFrame(videoFrame: VideoFrame) {
    // VideoFrame contains raw I420 video data in a ByteBuffer
    // Use optimized YuvToBitmapConverter for direct I420 to ARGB conversion
    val bitmap =
        YuvToBitmapConverter.convert(
            videoFrame.buffer,
            videoFrame.width,
            videoFrame.height,
        )
    if (bitmap != null) {
      presentationQueue?.enqueue(
          bitmap,
          videoFrame.presentationTimeUs,
      )
      liveStreamServer.sendFrame(bitmap)
    } else {
      Log.e(TAG, "Failed to convert YUV to bitmap")
    }
  }

  private fun handlePhotoData(photo: PhotoData) {
    val capturedPhoto =
        when (photo) {
          is PhotoData.Bitmap -> photo.bitmap
          is PhotoData.HEIC -> {
            val byteArray = ByteArray(photo.data.remaining())
            photo.data.get(byteArray)

            // Extract EXIF transformation matrix and apply to bitmap
            val exifInfo = getExifInfo(byteArray)
            val transform = getTransform(exifInfo)
            decodeHeic(byteArray, transform)
          }
        }
    _uiState.update { it.copy(capturedPhoto = capturedPhoto, isShareDialogVisible = true) }
  }

  // HEIC Decoding with EXIF transformation
  private fun decodeHeic(heicBytes: ByteArray, transform: Matrix): Bitmap {
    val bitmap = BitmapFactory.decodeByteArray(heicBytes, 0, heicBytes.size)
    return applyTransform(bitmap, transform)
  }

  private fun getExifInfo(heicBytes: ByteArray): ExifInterface? {
    return try {
      ByteArrayInputStream(heicBytes).use { inputStream -> ExifInterface(inputStream) }
    } catch (e: IOException) {
      Log.w(TAG, "Failed to read EXIF from HEIC", e)
      null
    }
  }

  private fun getTransform(exifInfo: ExifInterface?): Matrix {
    val matrix = Matrix()

    if (exifInfo == null) {
      return matrix // Identity matrix (no transformation)
    }

    when (
        exifInfo.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
    ) {
      ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> {
        matrix.postScale(-1f, 1f)
      }
      ExifInterface.ORIENTATION_ROTATE_180 -> {
        matrix.postRotate(180f)
      }
      ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
        matrix.postScale(1f, -1f)
      }
      ExifInterface.ORIENTATION_TRANSPOSE -> {
        matrix.postRotate(90f)
        matrix.postScale(-1f, 1f)
      }
      ExifInterface.ORIENTATION_ROTATE_90 -> {
        matrix.postRotate(90f)
      }
      ExifInterface.ORIENTATION_TRANSVERSE -> {
        matrix.postRotate(270f)
        matrix.postScale(-1f, 1f)
      }
      ExifInterface.ORIENTATION_ROTATE_270 -> {
        matrix.postRotate(270f)
      }
      ExifInterface.ORIENTATION_NORMAL,
      ExifInterface.ORIENTATION_UNDEFINED -> {
        // No transformation needed
      }
    }

    return matrix
  }

  private fun applyTransform(bitmap: Bitmap, matrix: Matrix): Bitmap {
    if (matrix.isIdentity) {
      return bitmap
    }

    return try {
      val transformed = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
      if (transformed != bitmap) {
        bitmap.recycle()
      }
      transformed
    } catch (e: OutOfMemoryError) {
      Log.e(TAG, "Failed to apply transformation due to memory", e)
      bitmap
    }
  }

  override fun onCleared() {
    super.onCleared()
    stopStream()
    glassesAudio.shutdown()
    voiceCommand.shutdown()
    voiceJob?.cancel()
    session?.stop()
    session = null
  }

  class Factory(
      private val application: Application,
      private val wearablesViewModel: WearablesViewModel,
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      if (modelClass.isAssignableFrom(StreamViewModel::class.java)) {
        @Suppress("UNCHECKED_CAST", "KotlinGenericsCast")
        return StreamViewModel(
            application = application,
            wearablesViewModel = wearablesViewModel,
        )
            as T
      }
      throw IllegalArgumentException("Unknown ViewModel class")
    }
  }
}
