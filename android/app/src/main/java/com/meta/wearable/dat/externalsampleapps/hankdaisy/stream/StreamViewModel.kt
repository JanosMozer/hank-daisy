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

package com.meta.wearable.dat.externalsampleapps.hankdaisy.stream

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.meta.wearable.dat.externalsampleapps.hankdaisy.BuildConfig
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
import com.meta.wearable.dat.externalsampleapps.hankdaisy.wearables.WearablesViewModel
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
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
  private var sceneJob: Job? = null
  private var autonomousJob: Job? = null

  private val conversationHistory = mutableListOf<GeminiService.Turn>()
  @Volatile private var lastTurnAt: Long = 0L

  private val sceneWatcher =
      SceneChangeWatcher(
          onSettledAfterMotion = {
            viewModelScope.launch { autonomousObservation() }
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
      // Read the user's haptic-feedback preference from the same shared
      // prefs SessionViewModel persists settings into. Default ON for
      // backwards compatibility.
      val hapticEnabled =
          try {
            val raw =
                app.getSharedPreferences("hank_sessions_v1", Context.MODE_PRIVATE)
                    .getString("settings_json", null)
            if (raw == null) true
            else org.json.JSONObject(raw).optBoolean("hapticFeedback", true)
          } catch (_: Exception) {
            true
          }
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
    startSceneWatcher()
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

    val prompt =
        "(System note: the camera moved; here's the new view.) React ONLY if " +
            "it's directly relevant to the conversation so far. " +
            "1) If the current conversation is NOT about something automotive or " +
            "what's visible, or the new view is unrelated (a wall, a person, a " +
            "room, background) — reply with just: <quiet>. Do not describe the " +
            "scene unprompted. " +
            "2) If the user has VISIBLY completed the step you just gave them, " +
            "give the NEXT single step now (one sentence, then stop). " +
            "3) If they repositioned where you asked but the step isn't done " +
            "yet, say one short sentence to acknowledge or guide them. " +
            "4) If something genuinely concerning is visible (new problem, " +
            "danger), say one short sentence about it. " +
            "5) Otherwise — reply with just: <quiet>."

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
                geminiService.analyzeFrame(frameCopy, prompt, conversationHistory.toList())
            val cleaned = response.trim().lowercase()
            _uiState.update { it.copy(isAnalyzing = false) }
            if (cleaned.contains("<quiet>") ||
                cleaned == "quiet" ||
                cleaned.length < 6) {
              Log.d(TAG, "Autonomous observation: Hank chose to stay quiet")
              return@launch
            }
            // Append to conversation so subsequent user turns see this comment.
            conversationHistory.add(GeminiService.Turn("assistant", response))
            while (conversationHistory.size > 24) {
              conversationHistory.removeAt(0)
            }
            appendChatMessages(ChatMessage(ChatMessage.Role.ASSISTANT, response))
            _uiState.update { it.copy(lastGeminiResponse = response) }
            glassesAudio.speak(response)
            lastTurnAt = System.currentTimeMillis()
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
              // Always resume listening the moment TTS ends — no branching on
              // "is this mid-conversation". Steady = always ready for the
              // user's next word.
              voiceCommand.startConversationFollowUp()
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
    sceneWatcher.reset()
    conversationHistory.clear()
    lastTurnAt = 0L
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
          val response = geminiService.analyzeFrame(frameCopy, question, historySnapshot)
          frameCopy?.recycle()

          conversationHistory.add(GeminiService.Turn("user", question))
          conversationHistory.add(GeminiService.Turn("assistant", response))
          while (conversationHistory.size > 24) {
            conversationHistory.removeAt(0)
          }
          appendChatMessages(
              ChatMessage(ChatMessage.Role.USER, question),
              ChatMessage(ChatMessage.Role.ASSISTANT, response),
          )

          _uiState.update { it.copy(isAnalyzing = false, lastGeminiResponse = response) }
          glassesAudio.speak(response)
          lastTurnAt = System.currentTimeMillis()
        }
  }

  fun cancelListening() {
    voiceCommand.stopContinuousListening()
    voiceJob?.cancel()
    _uiState.update { it.copy(isListening = false, isWakeWordActive = false) }
  }


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
