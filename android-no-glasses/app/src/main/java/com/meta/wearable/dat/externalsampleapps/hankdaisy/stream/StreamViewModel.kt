/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.hankdaisy.stream

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Build
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.meta.wearable.dat.camera.types.StreamSessionState
import com.meta.wearable.dat.externalsampleapps.hankdaisy.wearables.WearablesViewModel
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@SuppressLint("AutoCloseableUse")
class StreamViewModel(
    application: Application,
    private val wearablesViewModel: WearablesViewModel,
) : AndroidViewModel(application) {

  companion object {
    private const val TAG = "HankDaisyNoGlasses:Stream"
    private val INITIAL_STATE = StreamUiState()
  }

  private class CameraLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle = registry

    fun start() {
      registry.currentState = Lifecycle.State.CREATED
      registry.currentState = Lifecycle.State.STARTED
      registry.currentState = Lifecycle.State.RESUMED
    }

    fun stop() {
      registry.currentState = Lifecycle.State.DESTROYED
    }
  }

  private val _uiState = MutableStateFlow(INITIAL_STATE)
  val uiState: StateFlow<StreamUiState> = _uiState.asStateFlow()

  private val liveStreamServer = LiveStreamServer(8080)
  private val geminiService = GeminiService()
  private val audio = AudioRouteManager(application)
  private val voiceCommand = VoiceCommandManager(application)
  private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

  private var cameraProvider: ProcessCameraProvider? = null
  private var cameraLifecycleOwner: CameraLifecycleOwner? = null
  private var voiceJob: Job? = null
  private var speakingJob: Job? = null
  private var analyzeJob: Job? = null
  private var sceneJob: Job? = null
  private var autonomousJob: Job? = null
  private var lastFrameAt = 0L

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
            viewModelScope.launch {
              hapticInterruptCue()
              audio.stopSpeaking()
              voiceCommand.startConversationFollowUp()
            }
          },
      )

  fun startStream() {
    if (_uiState.value.streamSessionState == StreamSessionState.STREAMING) return
    _uiState.update { it.copy(streamSessionState = StreamSessionState.STARTING) }
    try {
      liveStreamServer.start()
    } catch (e: Exception) {
      Log.w(TAG, "live stream server failed to start", e)
    }
    observeTtsSpeaking()
    startWakeWordListening()
    startSceneWatcher()
    startPhoneCamera()
  }

  private fun startPhoneCamera() {
    val context = getApplication<Application>()
    val providerFuture = ProcessCameraProvider.getInstance(context)
    providerFuture.addListener(
        {
          try {
            val provider = providerFuture.get()
            cameraProvider = provider
            provider.unbindAll()

            val owner = CameraLifecycleOwner().also { it.start() }
            cameraLifecycleOwner = owner

            val resolutionSelector =
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                    .build()
            val analysis =
                ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setResolutionSelector(resolutionSelector)
                    .build()
                    .also { it.setAnalyzer(cameraExecutor, ::handleCameraFrame) }

            provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, analysis)
            _uiState.update { it.copy(streamSessionState = StreamSessionState.STREAMING) }
          } catch (e: Exception) {
            Log.e(TAG, "Failed to start phone camera", e)
            _uiState.update { it.copy(streamSessionState = StreamSessionState.STOPPED) }
          }
        },
        ContextCompat.getMainExecutor(context),
    )
  }

  private fun handleCameraFrame(image: ImageProxy) {
    try {
      val now = System.currentTimeMillis()
      if (now - lastFrameAt < 90L) return
      lastFrameAt = now
      val bitmap = image.toPhoneBitmap() ?: return
      _uiState.update {
        it.copy(
            videoFrame = bitmap,
            videoFrameCount = it.videoFrameCount + 1,
        )
      }
      liveStreamServer.sendFrame(bitmap)
    } catch (e: Exception) {
      Log.w(TAG, "phone camera frame conversion failed", e)
    } finally {
      image.close()
    }
  }

  private fun ImageProxy.toPhoneBitmap(): Bitmap? {
    if (format != ImageFormat.YUV_420_888) return null
    val nv21 = toNv21()
    val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
    val jpeg = ByteArrayOutputStream()
    if (!yuvImage.compressToJpeg(Rect(0, 0, width, height), 82, jpeg)) return null
    val decoded = BitmapFactory.decodeByteArray(jpeg.toByteArray(), 0, jpeg.size()) ?: return null
    val rotation = imageInfo.rotationDegrees
    if (rotation == 0) return decoded
    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
      if (it != decoded) decoded.recycle()
    }
  }

  private fun ImageProxy.toNv21(): ByteArray {
    val yPlane = planes[0]
    val uPlane = planes[1]
    val vPlane = planes[2]
    val ySize = width * height
    val nv21 = ByteArray(ySize + ySize / 2)

    copyYPlane(yPlane.buffer, yPlane.rowStride, nv21)

    val uBuffer = uPlane.buffer
    val vBuffer = vPlane.buffer
    val chromaHeight = height / 2
    val chromaWidth = width / 2
    var output = ySize
    for (row in 0 until chromaHeight) {
      for (col in 0 until chromaWidth) {
        val vIndex = row * vPlane.rowStride + col * vPlane.pixelStride
        val uIndex = row * uPlane.rowStride + col * uPlane.pixelStride
        nv21[output++] = vBuffer.get(vIndex)
        nv21[output++] = uBuffer.get(uIndex)
      }
    }
    return nv21
  }

  private fun ImageProxy.copyYPlane(buffer: ByteBuffer, rowStride: Int, out: ByteArray) {
    var output = 0
    for (row in 0 until height) {
      val rowStart = row * rowStride
      buffer.position(rowStart)
      buffer.get(out, output, width)
      output += width
    }
    buffer.rewind()
  }

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

  private suspend fun autonomousObservation() {
    val frame = _uiState.value.videoFrame ?: return
    if (frame.isRecycled) return
    if (_uiState.value.isAnalyzing) return
    if (_uiState.value.isListening) return
    if (audio.isSpeaking.value) return
    if (conversationHistory.isEmpty()) return
    if (System.currentTimeMillis() - lastTurnAt < 4_000L) return

    val prompt =
        "(System note: the phone camera moved; here's the new view.) React ONLY if " +
            "it's directly relevant to the conversation so far. If nothing meaningful changed, " +
            "reply with just: <quiet>. If the user visibly completed the prior step, give the next " +
            "single step in one sentence."

    autonomousJob?.cancel()
    autonomousJob =
        viewModelScope.launch {
          _uiState.update { it.copy(isAnalyzing = true) }
          val frameCopy =
              try {
                frame.copy(frame.config ?: Bitmap.Config.ARGB_8888, true)
              } catch (e: Exception) {
                _uiState.update { it.copy(isAnalyzing = false) }
                return@launch
              }
          try {
            val response = geminiService.analyzeFrame(frameCopy, prompt, conversationHistory.toList())
            val cleaned = response.trim().lowercase()
            _uiState.update { it.copy(isAnalyzing = false) }
            if (cleaned.contains("<quiet>") || cleaned == "quiet" || cleaned.length < 6) return@launch
            conversationHistory.add(GeminiService.Turn("assistant", response))
            while (conversationHistory.size > 24) conversationHistory.removeAt(0)
            appendChatMessages(ChatMessage(ChatMessage.Role.ASSISTANT, response))
            _uiState.update { it.copy(lastGeminiResponse = response) }
            audio.speak(response)
            lastTurnAt = System.currentTimeMillis()
          } catch (e: Exception) {
            Log.e(TAG, "autonomous observation failed", e)
            _uiState.update { it.copy(isAnalyzing = false) }
          } finally {
            try {
              frameCopy.recycle()
            } catch (_: Exception) {}
          }
        }
  }

  private fun observeTtsSpeaking() {
    speakingJob?.cancel()
    speakingJob =
        viewModelScope.launch {
          audio.isSpeaking.collect { speaking ->
            _uiState.update { it.copy(isHankSpeaking = speaking) }
            voiceCommand.setMuted(speaking)
            if (speaking) {
              delay(80)
              if (audio.isSpeaking.value) bargeInDetector.start()
            } else {
              bargeInDetector.stop()
              voiceCommand.startConversationFollowUp()
            }
          }
        }
  }

  private fun startWakeWordListening() {
    voiceCommand.startContinuousListening()
    voiceJob?.cancel()
    voiceJob =
        viewModelScope.launch {
          voiceCommand.state.collect { voiceState ->
            when (voiceState) {
              is VoiceCommandManager.VoiceState.Passive ->
                  _uiState.update { it.copy(isListening = false, isWakeWordActive = true) }
              is VoiceCommandManager.VoiceState.Listening ->
                  _uiState.update { it.copy(isListening = true) }
              is VoiceCommandManager.VoiceState.QuestionReady -> {
                _uiState.update { it.copy(isListening = false, spokenQuestion = voiceState.text) }
                analyzeWithQuestion(voiceState.text)
              }
              is VoiceCommandManager.VoiceState.Error ->
                  Log.w(TAG, "Voice error: ${voiceState.message}")
              is VoiceCommandManager.VoiceState.Off ->
                  _uiState.update { it.copy(isListening = false, isWakeWordActive = false) }
            }
          }
        }
  }

  fun stopStream() {
    try {
      cameraProvider?.unbindAll()
    } catch (e: Exception) {
      Log.w(TAG, "camera unbind failed", e)
    }
    cameraProvider = null
    try {
      cameraLifecycleOwner?.stop()
    } catch (_: Exception) {}
    cameraLifecycleOwner = null
    try {
      liveStreamServer.stop()
    } catch (_: Exception) {}
    try {
      voiceCommand.stopContinuousListening()
    } catch (_: Exception) {}
    try {
      audio.stopSpeaking()
    } catch (_: Exception) {}
    try {
      bargeInDetector.stop()
    } catch (_: Exception) {}
    voiceJob?.cancel()
    speakingJob?.cancel()
    analyzeJob?.cancel()
    sceneJob?.cancel()
    autonomousJob?.cancel()
    conversationHistory.clear()
    lastTurnAt = 0L
    clearDraftChat()
    _uiState.update { INITIAL_STATE }
  }

  fun capturePhoto() {
    val frame = _uiState.value.videoFrame ?: return
    if (frame.isRecycled) return
    val captured = frame.copy(frame.config ?: Bitmap.Config.ARGB_8888, true)
    _uiState.update { it.copy(capturedPhoto = captured, isShareDialogVisible = true) }
  }

  fun showShareDialog() {
    _uiState.update { it.copy(isShareDialogVisible = true) }
  }

  fun hideShareDialog() {
    _uiState.update { it.copy(isShareDialogVisible = false) }
  }

  fun askHank() {
    if (_uiState.value.isListening || _uiState.value.isAnalyzing) return
    _uiState.update { it.copy(lastGeminiResponse = null, spokenQuestion = null) }
    voiceCommand.startManualListen()
  }

  fun cancelListening() {
    voiceCommand.stopContinuousListening()
    voiceJob?.cancel()
    _uiState.update { it.copy(isListening = false, isWakeWordActive = false) }
  }

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
          val frameCopy = currentFrame?.copy(currentFrame.config ?: Bitmap.Config.ARGB_8888, true)
          val historySnapshot = conversationHistory.toList()
          val response = geminiService.analyzeFrame(frameCopy, question, historySnapshot)
          frameCopy?.recycle()

          conversationHistory.add(GeminiService.Turn("user", question))
          conversationHistory.add(GeminiService.Turn("assistant", response))
          while (conversationHistory.size > 24) conversationHistory.removeAt(0)
          appendChatMessages(
              ChatMessage(ChatMessage.Role.USER, question),
              ChatMessage(ChatMessage.Role.ASSISTANT, response),
          )

          _uiState.update { it.copy(isAnalyzing = false, lastGeminiResponse = response) }
          voiceCommand.onQuestionHandled()
          audio.speak(response)
          lastTurnAt = System.currentTimeMillis()
        }
  }

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
      val arr = JSONArray()
      for (m in msgs) {
        arr.put(
            JSONObject()
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

  fun exportSession() {
    val messages = _uiState.value.chatMessages
    if (messages.isEmpty()) return
    val context = getApplication<Application>()
    try {
      val dir = File(context.cacheDir, "sessions")
      dir.mkdirs()
      val ts = System.currentTimeMillis()
      val file = File(dir, "hank-session-$ts.json")

      val root = JSONObject()
      root.put("version", 1)
      root.put("exportedAt", ts)
      val arr = JSONArray()
      for (m in messages) {
        arr.put(
            JSONObject()
                .put("role", if (m.role == ChatMessage.Role.USER) "user" else "assistant")
                .put("content", m.text)
                .put("timestamp", m.timestamp),
        )
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
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(ts))
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
      val intent =
          Intent(Intent.ACTION_SEND).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(Intent.EXTRA_STREAM, uri)
            type = "image/png"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
          }
      val chooser = Intent.createChooser(intent, "Share Image")
      chooser.flags = Intent.FLAG_ACTIVITY_NEW_TASK
      context.startActivity(chooser)
    } catch (e: IOException) {
      Log.e(TAG, "Failed to share photo", e)
    }
  }

  private fun hapticInterruptCue() {
    try {
      val app = getApplication<Application>()
      val hapticEnabled =
          try {
            val raw =
                app.getSharedPreferences("hank_sessions_v1", Context.MODE_PRIVATE)
                    .getString("settings_json", null)
            if (raw == null) true else JSONObject(raw).optBoolean("hapticFeedback", true)
          } catch (_: Exception) {
            true
          }
      if (!hapticEnabled) return
      val vibrator =
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager)
                .defaultVibrator
          } else {
            @Suppress("DEPRECATION")
            app.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
          }
      vibrator.vibrate(android.os.VibrationEffect.createOneShot(40L, 80))
    } catch (_: Exception) {}
  }

  override fun onCleared() {
    super.onCleared()
    stopStream()
    cameraExecutor.shutdownNow()
    audio.shutdown()
    voiceCommand.shutdown()
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
