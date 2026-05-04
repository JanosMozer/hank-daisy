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

package com.meta.wearable.dat.externalsampleapps.mpi.stream

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.meta.wearable.dat.externalsampleapps.mpi.BuildConfig
import com.meta.wearable.dat.externalsampleapps.mpi.session.AppConfigStore
import com.meta.wearable.dat.externalsampleapps.mpi.session.DomainMode
import com.meta.wearable.dat.externalsampleapps.mpi.session.EvidenceKind
import com.meta.wearable.dat.externalsampleapps.mpi.session.HankMode
import com.meta.wearable.dat.externalsampleapps.mpi.session.InspectionEvidence
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
import com.meta.wearable.dat.externalsampleapps.mpi.wearables.WearablesViewModel
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@SuppressLint("AutoCloseableUse")
class StreamViewModel(
    application: Application,
    private val wearablesViewModel: WearablesViewModel,
) : AndroidViewModel(application) {

  companion object {
    private const val TAG = "HankDaisy:StreamViewModel"
    private val INITIAL_STATE = StreamUiState()
    private val SESSION_TERMINAL_STATES = setOf(StreamSessionState.CLOSED)
    private const val CLIP_CAPTURE_INTERVAL_MS = 250L
    private const val CLIP_MAX_FRAMES = 24
    private const val CLIP_FPS = 4
    private const val VIDEO_EVIDENCE_FPS = 24
  }

  private data class ClipFrameSample(
      val capturedAt: Long,
      val jpegBytes: ByteArray,
  )

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
  private val geminiService = GeminiService(application)
  private val glassesAudio = GlassesAudioManager(application)
  private val voiceCommand = VoiceCommandManager(application)
  private var voiceJob: Job? = null
  private var audioStatusJob: Job? = null
  private var speakingJob: Job? = null
  private var teardownJob: Job? = null
  private var analyzeJob: Job? = null
  private var sceneJob: Job? = null
  private var autonomousJob: Job? = null
  private var readOnlyJob: Job? = null
  private val videoEvidenceRecorder = FrameRecorder(application.applicationContext)
  private var videoEvidenceStartedAt: Long = 0L
  private var videoEvidenceTickerJob: Job? = null
  private var audioEvidenceRecorder: MediaRecorder? = null
  private var audioEvidenceFile: File? = null
  private var audioEvidenceStartedAt: Long = 0L
  private var audioEvidenceTickerJob: Job? = null
  private val rollingClipFrames = ArrayDeque<ClipFrameSample>()
  private var lastClipFrameAt = 0L
  private var lastVideoFrameWidth = 0
  private var lastVideoFrameHeight = 0

  private val conversationHistory = mutableListOf<GeminiService.Turn>()
  private val pendingReadOnlyNotes = mutableListOf<String>()
  private var lastSessionSnapshot: Pair<List<ChatMessage>, List<InspectionEvidence>>? = null
  @Volatile private var lastTurnAt: Long = 0L

  private val sceneWatcher =
      SceneChangeWatcher(
          onSettledAfterMotion = {
            if (_uiState.value.hankMode == HankMode.READ_ONLY) {
              viewModelScope.launch {
                performReadOnlyCommentary(HankPromptFactory.CommentaryTrigger.SCENE_CHANGE)
              }
            }
          },
      )

  private val bargeInDetector =
      BargeInDetector(
          onUserSpeaking = {
            // Posted from a worker thread — bounce to main + viewModelScope.
            viewModelScope.launch {
              Log.d(TAG, "Barge-in detected — cutting Hank off")
              hapticInterruptCue()
              glassesAudio.stopSpeaking()
              voiceCommand.startConversationFollowUp()
            }
          },
      )

  /** Tiny vibration so the user knows we heard them and can speak now —
   * masks the ~200ms gap before the recognizer is actually capturing.
   * Respects the "Haptic feedback" toggle in Settings. */
  private fun hapticInterruptCue() {
    try {
      val app = getApplication<Application>()
      val hapticEnabled = AppConfigStore.current(app).general.hapticFeedback
      if (!hapticEnabled) return
      val vibrator =
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE)
                    as android.os.VibratorManager)
                .defaultVibrator
          } else {
            @Suppress("DEPRECATION")
            app.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
          }
      vibrator.vibrate(android.os.VibrationEffect.createOneShot(40L, 80))
    } catch (_: Exception) {
      // not critical
    }
  }

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
    lastSessionSnapshot = null
    lastVideoFrameWidth = 0
    lastVideoFrameHeight = 0
    rollingClipFrames.clear()
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
    _uiState.update { it.copy(hankMode = AppConfigStore.current(getApplication()).capture.hankMode) }
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
    observeVoiceCommands()
    applyHankMode(_uiState.value.hankMode, triggerInitialCommentary = true)
    startStreamInternal()
  }

  /**
   * Samples the live frame at ~3fps off the main thread and feeds the watcher.
   * When the user moves the camera and then settles, autonomousObservation()
   * fires.
   */
  private fun startSceneWatcher() {
    sceneJob?.cancel()
    sceneWatcher.reset()
    sceneJob =
        viewModelScope.launch(Dispatchers.Default) {
          while (isActive) {
            val frame = _uiState.value.videoFrame
            if (frame != null && !frame.isRecycled) {
              try {
                sceneWatcher.observe(frame)
              } catch (e: Exception) {
                Log.w(TAG, "scene watcher observe failed", e)
              }
            }
            delay(300L)
          }
        }
  }

  /**
   * Triggered by SceneChangeWatcher when the user repositioned and the view
   * settled. Sends the new frame + conversation history to Gemini with a
   * "stay quiet unless meaningful" instruction so Hank only chimes in when
   * something relevant changed (user did a step, repositioned where asked,
   * a new issue is visible). Bypassed entirely when busy with another turn.
   */
  private suspend fun autonomousObservation() {
    val frame = _uiState.value.videoFrame ?: return
    if (frame.isRecycled) return
    if (_uiState.value.isAnalyzing) return
    if (_uiState.value.isListening) return
    if (glassesAudio.isSpeaking.value) return
    if (conversationHistory.isEmpty()) return  // only auto-comment mid-convo
    if (System.currentTimeMillis() - lastTurnAt < 4_000L) return

    autonomousJob?.cancel()
    autonomousJob =
        viewModelScope.launch {
          _uiState.update { it.copy(isAnalyzing = true) }
          val frameCopy =
              try {
                frame.copy(frame.config ?: Bitmap.Config.ARGB_8888, true)
              } catch (e: Exception) {
                Log.w(TAG, "autonomous frame copy failed", e)
                _uiState.update { it.copy(isAnalyzing = false) }
                return@launch
              }
          try {
            val response =
                geminiService.analyzeFrame(
                    bitmap = frameCopy,
                    userQuestion = HankPromptFactory.autonomousObservationPrompt(currentDomainMode()),
                    history = conversationHistory.toList(),
                    systemPromptOverride = HankPromptFactory.systemPrompt(currentDomainMode()),
                )
            val cleaned = response.trim().lowercase(Locale.US)
            _uiState.update { it.copy(isAnalyzing = false) }
            if (cleaned.contains("<quiet>") ||
                cleaned == "quiet" ||
                cleaned.length < 6) {
              Log.d(TAG, "Autonomous observation: Hank chose to stay quiet")
              return@launch
            }
            appendAssistantResponse(response)
          } catch (e: Exception) {
            Log.e(TAG, "autonomousObservation failed", e)
            _uiState.update { it.copy(isAnalyzing = false) }
          } finally {
            try {
              frameCopy.recycle()
            } catch (_: Exception) {}
          }
        }
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
            _uiState.update { it.copy(isHankSpeaking = speaking) }
            // Mute the recognizer (it can't echo-cancel Hank's own voice) and
            // hand the mic to BargeInDetector, which CAN (via AEC).
            voiceCommand.setMuted(speaking)
            if (speaking) {
              // Brief delay so SpeechRecognizer.destroy() releases the mic
              // before BargeInDetector tries to grab it; shorter is better
              // because every ms here is a ms before barge-in can fire.
              kotlinx.coroutines.delay(80)
              if (glassesAudio.isSpeaking.value) {
                bargeInDetector.start()
              }
            } else {
              bargeInDetector.stop()
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

  private fun snapshotCurrentSession() {
    lastSessionSnapshot =
        _uiState.value.chatMessages.toList() to _uiState.value.capturedEvidence.toList()
  }

  fun currentSessionSnapshot(): Pair<List<ChatMessage>, List<InspectionEvidence>> {
    val state = _uiState.value
    return if (state.chatMessages.isNotEmpty() || state.capturedEvidence.isNotEmpty()) {
      state.chatMessages.toList() to state.capturedEvidence.toList()
    } else {
      lastSessionSnapshot ?: (emptyList<ChatMessage>() to emptyList())
    }
  }

  fun stopStream() {
    if (session == null && videoJob == null && teardownJob == null) {
      return
    }
    val sessionToTearDown = session
    finalizePendingEvidenceCapture()
    snapshotCurrentSession()
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
    try { videoEvidenceTickerJob?.cancel() } catch (_: Exception) {}
    videoEvidenceTickerJob = null
    runCatching { videoEvidenceRecorder.abort() }
    videoEvidenceStartedAt = 0L
    try { audioEvidenceTickerJob?.cancel() } catch (_: Exception) {}
    audioEvidenceTickerJob = null
    releaseAudioEvidenceRecorder()
    audioEvidenceFile = null
    audioEvidenceStartedAt = 0L
    clearDraftChat()
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
    try { sceneJob?.cancel() } catch (_: Exception) {}
    sceneJob = null
    try { autonomousJob?.cancel() } catch (_: Exception) {}
    autonomousJob = null
    try { readOnlyJob?.cancel() } catch (_: Exception) {}
    readOnlyJob = null
    sceneWatcher.reset()
    conversationHistory.clear()
    pendingReadOnlyNotes.clear()
    lastTurnAt = 0L
    try { glassesAudio.disableGlassesMic() } catch (_: Exception) {}
    try { StreamForegroundService.stop(getApplication()) } catch (_: Exception) {}
    lastVideoFrameWidth = 0
    lastVideoFrameHeight = 0
    rollingClipFrames.clear()

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

  /** Observe voice-command state so manual Ask Hank and commentary mode share
   * the same speech pipeline without forcing background listening on startup. */
  private fun observeVoiceCommands() {
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
            if (_uiState.value.hankMode == HankMode.READ_ONLY) {
              handleReadOnlyTranscript(voiceState.text)
            } else {
              analyzeWithQuestion(voiceState.text)
            }
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

  fun requestCommentNow() {
    viewModelScope.launch {
      performReadOnlyCommentary(HankPromptFactory.CommentaryTrigger.MANUAL_START)
    }
  }

  fun setHankMode(mode: HankMode) {
    _uiState.update { it.copy(hankMode = mode) }
    applyHankMode(mode, triggerInitialCommentary = mode == HankMode.READ_ONLY)
  }

  /** Append messages to the chat panel UI state, capped at 100 to stay light.
   * Also drafts the running conversation to SharedPreferences so a crash /
   * kill / OOM mid-stream doesn't lose the chat — SessionViewModel picks it
   * up on next launch and surfaces it as a recovered session. */
  private fun appendChatMessages(vararg msgs: ChatMessage) {
    _uiState.update { state ->
      val combined = (state.chatMessages + msgs).takeLast(100)
      state.copy(chatMessages = combined)
    }
    persistDraftChat()
  }

  private fun persistDraftChat() {
    try {
      val prefs =
          getApplication<Application>()
              .getSharedPreferences("hank_sessions_v1", Context.MODE_PRIVATE)
      val msgs = _uiState.value.chatMessages
      if (msgs.isEmpty()) {
        prefs.edit().remove("draft_chat").apply()
        return
      }
      val arr = org.json.JSONArray()
      for (m in msgs) {
        arr.put(
            org.json.JSONObject()
                .put("role", if (m.role == ChatMessage.Role.USER) "user" else "assistant")
                .put("text", m.text)
                .put("ts", m.timestamp),
        )
      }
      prefs.edit().putString("draft_chat", arr.toString()).apply()
    } catch (e: Exception) {
      Log.w(TAG, "persistDraftChat failed", e)
    }
  }

  private fun clearDraftChat() {
    try {
      getApplication<Application>()
          .getSharedPreferences("hank_sessions_v1", Context.MODE_PRIVATE)
          .edit()
          .remove("draft_chat")
          .apply()
    } catch (_: Exception) {}
  }

  private fun saveCapturedBitmap(bitmap: Bitmap): InspectionEvidence? {
    return try {
      val dir = File(getApplication<Application>().cacheDir, "inspection-evidence")
      dir.mkdirs()
      val ts = System.currentTimeMillis()
      val file = File(dir, "finding-image-$ts.jpg")
      FileOutputStream(file).use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
      }
      InspectionEvidence(
          id = "evidence-$ts",
          kind = EvidenceKind.IMAGE,
          filePath = file.absolutePath,
          createdAt = ts,
          caption = "Captured from glasses stream",
          previewImagePath = file.absolutePath,
      )
    } catch (e: Exception) {
      Log.e(TAG, "Failed to save captured bitmap", e)
      null
    }
  }

  fun saveClipEvidence() {
    val samples = rollingClipFrames.toList()
    if (samples.size < 2) {
      Log.d(TAG, "saveClipEvidence(): not enough frames buffered")
      return
    }
    try {
      val dir = File(getApplication<Application>().cacheDir, "inspection-evidence/clip-${System.currentTimeMillis()}")
      dir.mkdirs()
      val framePaths =
          samples.mapIndexed { index, sample ->
            val frameFile = File(dir, "frame-${index.toString().padStart(3, '0')}.jpg")
            frameFile.writeBytes(sample.jpegBytes)
            frameFile.absolutePath
          }
      val durationMs = (samples.last().capturedAt - samples.first().capturedAt).coerceAtLeast(0L)
      val coverPath = framePaths.first()
      val evidence =
          InspectionEvidence(
              id = "evidence-clip-${System.currentTimeMillis()}",
              kind = EvidenceKind.VIDEO,
              filePath = coverPath,
              createdAt = System.currentTimeMillis(),
              caption = "Glasses clip evidence",
              previewImagePath = coverPath,
              clipFramePaths = framePaths,
              clipFps = CLIP_FPS,
              durationMs = durationMs,
          )
      _uiState.update { it.copy(capturedEvidence = it.capturedEvidence + evidence) }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to save clip evidence", e)
    }
  }

  fun toggleVideoEvidenceRecording() {
    if (_uiState.value.isVideoRecording) {
      stopVideoEvidenceRecording()
    } else {
      startVideoEvidenceRecording()
    }
  }

  fun toggleAudioEvidenceRecording() {
    if (_uiState.value.isAudioRecording) {
      stopAudioEvidenceRecording()
    } else {
      startAudioEvidenceRecording()
    }
  }

  fun finalizePendingEvidenceCapture() {
    if (_uiState.value.isVideoRecording) {
      stopVideoEvidenceRecording()
    }
    if (_uiState.value.isAudioRecording) {
      stopAudioEvidenceRecording()
    }
    snapshotCurrentSession()
  }

  private fun startVideoEvidenceRecording() {
    if (_uiState.value.isVideoRecording) return
    if (_uiState.value.streamSessionState != StreamSessionState.STREAMING) {
      Log.d(TAG, "startVideoEvidenceRecording(): stream is not active")
      return
    }
    if (lastVideoFrameWidth <= 0 || lastVideoFrameHeight <= 0) {
      Log.d(TAG, "startVideoEvidenceRecording(): no frame dimensions available yet")
      return
    }
    try {
      videoEvidenceRecorder.start(
          width = lastVideoFrameWidth,
          height = lastVideoFrameHeight,
          fps = VIDEO_EVIDENCE_FPS,
      )
      videoEvidenceStartedAt = System.currentTimeMillis()
      videoEvidenceTickerJob?.cancel()
      videoEvidenceTickerJob =
          viewModelScope.launch {
            while (isActive) {
              _uiState.update {
                it.copy(
                    isVideoRecording = true,
                    videoRecordingDurationMs =
                        (System.currentTimeMillis() - videoEvidenceStartedAt).coerceAtLeast(0L),
                )
              }
              delay(250L)
            }
          }
      _uiState.update { it.copy(isVideoRecording = true, videoRecordingDurationMs = 0L) }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to start video evidence recording", e)
      videoEvidenceTickerJob?.cancel()
      videoEvidenceTickerJob = null
      runCatching { videoEvidenceRecorder.abort() }
      videoEvidenceStartedAt = 0L
      _uiState.update { it.copy(isVideoRecording = false, videoRecordingDurationMs = 0L) }
    }
  }

  private fun stopVideoEvidenceRecording() {
    if (!_uiState.value.isVideoRecording) {
      videoEvidenceTickerJob?.cancel()
      videoEvidenceTickerJob = null
      videoEvidenceStartedAt = 0L
      _uiState.update { it.copy(isVideoRecording = false, videoRecordingDurationMs = 0L) }
      return
    }

    val startedAt = videoEvidenceStartedAt
    _uiState.update { it.copy(isVideoRecording = false) }
    videoEvidenceTickerJob?.cancel()
    videoEvidenceTickerJob = null

    val durationMs = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
    var savedEvidence: InspectionEvidence? = null
    try {
      val file = videoEvidenceRecorder.finish()
      if (file.exists() && file.length() > 0L) {
        val previewPath = saveVideoEvidencePreview(startedAt)
        savedEvidence =
            InspectionEvidence(
                id = "evidence-video-$startedAt",
                kind = EvidenceKind.VIDEO,
                filePath = file.absolutePath,
                createdAt = startedAt,
                caption = "Glasses video recording",
                previewImagePath = previewPath,
                durationMs = durationMs,
            )
      } else {
        runCatching { file.delete() }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to finalize video evidence recording", e)
      runCatching { videoEvidenceRecorder.abort() }
    } finally {
      videoEvidenceStartedAt = 0L
    }

    _uiState.update {
      it.copy(
          isVideoRecording = false,
          videoRecordingDurationMs = 0L,
          capturedEvidence =
              if (savedEvidence != null) it.capturedEvidence + savedEvidence else it.capturedEvidence,
      )
    }
  }

  private fun abortVideoEvidenceRecording() {
    videoEvidenceTickerJob?.cancel()
    videoEvidenceTickerJob = null
    videoEvidenceStartedAt = 0L
    runCatching { videoEvidenceRecorder.abort() }
    _uiState.update { it.copy(isVideoRecording = false, videoRecordingDurationMs = 0L) }
  }

  private fun saveVideoEvidencePreview(startedAt: Long): String? {
    val bitmap = _uiState.value.videoFrame ?: return null
    return try {
      val dir = File(getApplication<Application>().cacheDir, "inspection-evidence").apply { mkdirs() }
      val previewFile = File(dir, "finding-video-preview-$startedAt.jpg")
      FileOutputStream(previewFile).use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
      }
      previewFile.absolutePath
    } catch (e: Exception) {
      Log.w(TAG, "Failed to save video evidence preview", e)
      null
    }
  }

  @SuppressLint("MissingPermission")
  private fun startAudioEvidenceRecording() {
    if (_uiState.value.isAudioRecording) return
    if (_uiState.value.streamSessionState != StreamSessionState.STREAMING) {
      Log.d(TAG, "startAudioEvidenceRecording(): stream is not active")
      return
    }
    val app = getApplication<Application>()
    val startedAt = System.currentTimeMillis()
    val dir = File(app.cacheDir, "inspection-evidence").apply { mkdirs() }
    val file = File(dir, "finding-audio-$startedAt.m4a")
    try {
      val recorder =
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(app)
          } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
          }
      recorder.setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
      recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
      recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
      recorder.setAudioEncodingBitRate(128000)
      recorder.setAudioSamplingRate(44100)
      recorder.setOutputFile(file.absolutePath)
      recorder.prepare()
      recorder.start()
      audioEvidenceRecorder = recorder
      audioEvidenceFile = file
      audioEvidenceStartedAt = startedAt
      audioEvidenceTickerJob?.cancel()
      audioEvidenceTickerJob =
          viewModelScope.launch {
            while (isActive) {
              _uiState.update {
                it.copy(
                    isAudioRecording = true,
                    audioRecordingDurationMs =
                        (System.currentTimeMillis() - audioEvidenceStartedAt).coerceAtLeast(0L),
                )
              }
              delay(250L)
            }
          }
      _uiState.update { it.copy(isAudioRecording = true, audioRecordingDurationMs = 0L) }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to start audio evidence recording", e)
      audioEvidenceTickerJob?.cancel()
      audioEvidenceTickerJob = null
      releaseAudioEvidenceRecorder()
      audioEvidenceFile = null
      audioEvidenceStartedAt = 0L
      runCatching { file.delete() }
      _uiState.update { it.copy(isAudioRecording = false, audioRecordingDurationMs = 0L) }
    }
  }

  private fun stopAudioEvidenceRecording() {
    val recorder = audioEvidenceRecorder
    val file = audioEvidenceFile
    val startedAt = audioEvidenceStartedAt
    if (recorder == null || file == null || startedAt == 0L) {
      audioEvidenceTickerJob?.cancel()
      audioEvidenceTickerJob = null
      releaseAudioEvidenceRecorder()
      audioEvidenceFile = null
      audioEvidenceStartedAt = 0L
      _uiState.update { it.copy(isAudioRecording = false, audioRecordingDurationMs = 0L) }
      return
    }

    audioEvidenceTickerJob?.cancel()
    audioEvidenceTickerJob = null

    val durationMs = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
    var savedEvidence: InspectionEvidence? = null
    try {
      recorder.stop()
      if (file.exists() && file.length() > 0L) {
        savedEvidence =
            InspectionEvidence(
                id = "evidence-audio-$startedAt",
                kind = EvidenceKind.AUDIO,
                filePath = file.absolutePath,
                createdAt = startedAt,
                caption = "Narrated finding audio",
                durationMs = durationMs,
            )
      } else {
        runCatching { file.delete() }
      }
    } catch (e: RuntimeException) {
      Log.w(TAG, "Audio evidence stop failed; dropping partial file", e)
      runCatching { file.delete() }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to finalize audio evidence recording", e)
      runCatching { file.delete() }
    } finally {
      releaseAudioEvidenceRecorder()
      audioEvidenceFile = null
      audioEvidenceStartedAt = 0L
    }

    _uiState.update {
      it.copy(
          isAudioRecording = false,
          audioRecordingDurationMs = 0L,
          capturedEvidence =
              if (savedEvidence != null) it.capturedEvidence + savedEvidence else it.capturedEvidence,
      )
    }
  }

  private fun releaseAudioEvidenceRecorder() {
    val recorder = audioEvidenceRecorder
    audioEvidenceRecorder = null
    if (recorder != null) {
      try {
        recorder.reset()
      } catch (_: Exception) {}
      try {
        recorder.release()
      } catch (_: Exception) {}
    }
  }

  /** Quick analyze without voice — uses default prompt. */
  fun analyzeCurrentFrame() {
    analyzeWithQuestion("What do you see? Identify any problems and tell me how to fix them step by step.")
  }

  private fun analyzeWithQuestion(question: String) {
    if (_uiState.value.isAnalyzing) return
    val currentFrame = _uiState.value.videoFrame
    _uiState.update { it.copy(isAnalyzing = true, lastGeminiResponse = null) }

    analyzeJob?.cancel()
    analyzeJob =
        viewModelScope.launch {
          // Always send the frame when we have one; the system prompt tells
          // Hank to ignore it if the question / scene is unrelated.
          val frameCopy =
              currentFrame?.copy(currentFrame.config ?: Bitmap.Config.ARGB_8888, true)
          val historySnapshot = conversationHistory.toList()
          val response =
              geminiService.analyzeFrame(
                  bitmap = frameCopy,
                  userQuestion = question,
                  history = historySnapshot,
                  systemPromptOverride = HankPromptFactory.systemPrompt(currentDomainMode()),
              )
          frameCopy?.recycle()

          conversationHistory.add(GeminiService.Turn("user", question))
          appendChatMessages(ChatMessage(ChatMessage.Role.USER, question))
          appendAssistantResponse(response)
          _uiState.update { it.copy(isAnalyzing = false, lastGeminiResponse = response) }
        }
  }

  fun cancelListening() {
    voiceCommand.stopContinuousListening()
    _uiState.update { it.copy(isListening = false, isWakeWordActive = false) }
  }

  private fun applyHankMode(mode: HankMode, triggerInitialCommentary: Boolean) {
    if (mode == HankMode.READ_ONLY) {
      startSceneWatcher()
      voiceCommand.startContinuousListening()
      scheduleReadOnlyLoop()
      if (triggerInitialCommentary) {
        viewModelScope.launch {
          delay(1_000L)
          performReadOnlyCommentary(HankPromptFactory.CommentaryTrigger.MANUAL_START)
        }
      }
    } else {
      disableAutomaticCommentary(stopSpeaking = true)
    }
  }

  private fun disableAutomaticCommentary(stopSpeaking: Boolean) {
    readOnlyJob?.cancel()
    readOnlyJob = null
    sceneJob?.cancel()
    sceneJob = null
    sceneWatcher.reset()
    pendingReadOnlyNotes.clear()
    voiceCommand.stopContinuousListening()
    if (stopSpeaking) {
      glassesAudio.stopSpeaking()
    }
    _uiState.update {
      it.copy(
          isListening = false,
          isWakeWordActive = false,
          pendingReadOnlyContext = null,
      )
    }
  }

  private fun scheduleReadOnlyLoop() {
    readOnlyJob?.cancel()
    readOnlyJob =
        viewModelScope.launch {
          while (isActive && _uiState.value.hankMode == HankMode.READ_ONLY) {
            delay(currentReadOnlyPauseMs())
            if (_uiState.value.hankMode != HankMode.READ_ONLY) continue
            performReadOnlyCommentary(HankPromptFactory.CommentaryTrigger.FOLLOW_UP)
          }
        }
  }

  private suspend fun performReadOnlyCommentary(trigger: HankPromptFactory.CommentaryTrigger) {
    val frame = _uiState.value.videoFrame ?: return
    if (frame.isRecycled) return
    if (_uiState.value.hankMode != HankMode.READ_ONLY) return
    if (_uiState.value.isAnalyzing || _uiState.value.isListening || _uiState.value.isHankSpeaking) return
    if (System.currentTimeMillis() - lastTurnAt < currentReadOnlyPauseMs() / 2) return

    val noteContext = pendingReadOnlyNotes.joinToString(". ").trim().ifBlank { null }
    _uiState.update { it.copy(isAnalyzing = true) }
    val frameCopy = frame.copy(frame.config ?: Bitmap.Config.ARGB_8888, true)
    try {
      val response =
          geminiService.analyzeFrame(
              bitmap = frameCopy,
              userQuestion =
                  HankPromptFactory.readOnlyUserPrompt(
                      currentDomainMode(),
                      trigger,
                      noteContext,
                  ),
              history = conversationHistory.toList(),
              systemPromptOverride = HankPromptFactory.readOnlySystemPrompt(currentDomainMode()),
          )
      if (response.isBlank()) return
      appendAssistantResponse(response)
      pendingReadOnlyNotes.clear()
      _uiState.update { it.copy(pendingReadOnlyContext = null) }
    } finally {
      frameCopy.recycle()
      _uiState.update { it.copy(isAnalyzing = false) }
    }
  }

  private fun handleReadOnlyTranscript(text: String) {
    if (!isRelevantReadOnlyTranscript(text)) {
      voiceCommand.onQuestionHandled()
      return
    }
    pendingReadOnlyNotes.add(text)
    if (pendingReadOnlyNotes.size > 4) {
      pendingReadOnlyNotes.removeFirst()
    }
    _uiState.update {
      it.copy(
          pendingReadOnlyContext = pendingReadOnlyNotes.joinToString(" • "),
          spokenQuestion = text,
      )
    }
    voiceCommand.onQuestionHandled()
    viewModelScope.launch {
      delay(250L)
      performReadOnlyCommentary(HankPromptFactory.CommentaryTrigger.TRANSCRIPT_UPDATE)
    }
  }

  private fun isRelevantReadOnlyTranscript(text: String): Boolean {
    val normalized = text.trim().lowercase(Locale.US)
    if (normalized.length < 8) return false
    val fillers = listOf("okay", "thanks", "thank you", "go ahead", "continue", "hey hank", "hank")
    if (fillers.any { normalized == it }) return false
    val keywords =
        listOf(
            "leak",
            "noise",
            "crack",
            "code",
            "battery",
            "brake",
            "chain",
            "wheel",
            "coolant",
            "sensor",
            "connector",
            "wire",
            "bike",
            "bicycle",
            "engine",
            "device",
            "screen",
            "power",
            "smell",
            "hot",
        )
    return keywords.any { normalized.contains(it) } ||
        normalized.any { it.isDigit() } ||
        normalized.split(Regex("\\s+")).size >= 4
  }

  private fun appendAssistantResponse(response: String) {
    val cleaned = response.trim()
    if (cleaned.isBlank() || cleaned.equals("<quiet>", ignoreCase = true)) return
    conversationHistory.add(GeminiService.Turn("assistant", cleaned))
    while (conversationHistory.size > 24) {
      conversationHistory.removeAt(0)
    }
    appendChatMessages(ChatMessage(ChatMessage.Role.ASSISTANT, cleaned))
    _uiState.update { it.copy(lastGeminiResponse = cleaned) }
    glassesAudio.speak(cleaned)
    lastTurnAt = System.currentTimeMillis()
  }

  private fun currentReadOnlyPauseMs(): Long =
      AppConfigStore.current(getApplication()).speech.readOnlyPauseMs

  private fun currentDomainMode(): DomainMode =
      AppConfigStore.current(getApplication()).general.domainMode


  /**
   * Export the current session's chat transcript as JSON and open the Android
   * share sheet so the user can email / AirDrop / Drive it to their laptop,
   * where the bay UI's "Import session" button will pick it up.
   *
   * Schema:
   *   { "version": 1, "exportedAt": <millis>,
   *     "messages": [ { "role": "user"|"assistant",
   *                     "content": "...",
   *                     "timestamp": <millis> }, ... ] }
   */
  fun exportSession() {
    val messages = _uiState.value.chatMessages
    if (messages.isEmpty()) {
      Log.d(TAG, "exportSession(): no messages to export")
      return
    }
    val context = getApplication<Application>()
    try {
      val dir = File(context.cacheDir, "sessions")
      dir.mkdirs()
      val ts = System.currentTimeMillis()
      val file = File(dir, "hank-session-$ts.json")

      val root = org.json.JSONObject()
      root.put("version", 1)
      root.put("exportedAt", ts)
      val arr = org.json.JSONArray()
      for (m in messages) {
        val o = org.json.JSONObject()
        o.put("role", if (m.role == ChatMessage.Role.USER) "user" else "assistant")
        o.put("content", m.text)
        o.put("timestamp", m.timestamp)
        arr.put(o)
      }
      root.put("messages", arr)
      file.writeText(root.toString(2))

      val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
      val send =
          Intent(Intent.ACTION_SEND).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Hank session ${formatTs(ts)}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
          }
      val chooser =
          Intent.createChooser(send, "Export Hank session").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
          }
      context.startActivity(chooser)
    } catch (e: Exception) {
      Log.e(TAG, "exportSession failed", e)
    }
  }

  private fun formatTs(ts: Long): String {
    return java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date(ts))
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
    lastVideoFrameWidth = videoFrame.width
    lastVideoFrameHeight = videoFrame.height
    val recordBuffer =
        if (_uiState.value.isVideoRecording) {
          videoFrame.buffer.duplicate().apply { position(0) }
        } else {
          null
        }
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
      captureClipSample(bitmap)
      if (recordBuffer != null) {
        try {
          videoEvidenceRecorder.recordFrame(
              i420Buffer = recordBuffer,
              width = videoFrame.width,
              height = videoFrame.height,
              timestampUs = videoFrame.presentationTimeUs,
          )
        } catch (e: Exception) {
          Log.e(TAG, "Video evidence frame recording failed", e)
          abortVideoEvidenceRecording()
        }
      }
    } else {
      Log.e(TAG, "Failed to convert YUV to bitmap")
    }
  }

  private fun captureClipSample(bitmap: Bitmap) {
    val now = System.currentTimeMillis()
    if (now - lastClipFrameAt < CLIP_CAPTURE_INTERVAL_MS) return
    lastClipFrameAt = now
    try {
      val out = ByteArrayOutputStream()
      bitmap.compress(Bitmap.CompressFormat.JPEG, 78, out)
      rollingClipFrames.addLast(ClipFrameSample(now, out.toByteArray()))
      while (rollingClipFrames.size > CLIP_MAX_FRAMES) {
        rollingClipFrames.removeFirst()
      }
    } catch (e: Exception) {
      Log.w(TAG, "captureClipSample failed", e)
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
    val evidence = saveCapturedBitmap(capturedPhoto)
    _uiState.update {
      it.copy(
          capturedPhoto = capturedPhoto,
          isShareDialogVisible = true,
          capturedEvidence =
              if (evidence != null) it.capturedEvidence + evidence else it.capturedEvidence,
      )
    }
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
