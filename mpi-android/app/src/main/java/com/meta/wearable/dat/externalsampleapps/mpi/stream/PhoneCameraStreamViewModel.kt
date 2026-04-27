package com.meta.wearable.dat.externalsampleapps.mpi.stream

import android.annotation.SuppressLint
import android.app.Application
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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.meta.wearable.dat.camera.types.StreamSessionState
import com.meta.wearable.dat.externalsampleapps.mpi.session.AppConfigStore
import com.meta.wearable.dat.externalsampleapps.mpi.session.DomainMode
import com.meta.wearable.dat.externalsampleapps.mpi.session.EvidenceKind
import com.meta.wearable.dat.externalsampleapps.mpi.session.HankMode
import com.meta.wearable.dat.externalsampleapps.mpi.session.InspectionEvidence
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
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
import kotlinx.coroutines.withContext

@SuppressLint("UnsafeOptInUsageError")
class PhoneCameraStreamViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "HankDaisy:PhoneCamera"
        private val INITIAL_STATE = PhoneCameraStreamUiState()
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
    val uiState: StateFlow<PhoneCameraStreamUiState> = _uiState.asStateFlow()

    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraOwner: CameraLifecycleOwner? = null
    private var imageAnalysis: ImageAnalysis? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var lastFrameAt = 0L

    private val geminiService = GeminiService(application)
    private val audio = AudioRouteManager(application)
    private val voiceCommand = VoiceCommandManager(application)
    private var voiceJob: Job? = null
    private var audioStatusJob: Job? = null
    private var speakingJob: Job? = null
    private var analyzeJob: Job? = null
    private var sceneJob: Job? = null
    private var readOnlyJob: Job? = null
    private var autonomousJob: Job? = null
    private var lastTurnAt: Long = 0L
    private val conversationHistory = mutableListOf<GeminiService.Turn>()
    private val pendingReadOnlyNotes = mutableListOf<String>()

    private val sceneWatcher =
        SceneChangeWatcher(
            onSettledAfterMotion = {
                viewModelScope.launch {
                    if (_uiState.value.hankMode == HankMode.READ_ONLY) {
                        performReadOnlyCommentary(HankPromptFactory.CommentaryTrigger.SCENE_CHANGE)
                    } else {
                        autonomousObservation()
                    }
                }
            },
        )

    private val bargeInDetector =
        BargeInDetector(
            onUserSpeaking = {
                viewModelScope.launch {
                    audio.stopSpeaking()
                    voiceCommand.startConversationFollowUp()
                }
            },
        )

    fun startStream() {
        if (_uiState.value.streamSessionState != StreamSessionState.STOPPED) return
        _uiState.update {
            it.copy(
                streamSessionState = StreamSessionState.STARTING,
                hankMode = AppConfigStore.current(getApplication()).capture.hankMode,
            )
        }
        bindCamera()
        observeAudioStatus()
        observeTtsSpeaking()
        startWakeWordListening()
        startSceneWatcher()
        if (_uiState.value.hankMode == HankMode.READ_ONLY) {
            scheduleReadOnlyLoop()
            viewModelScope.launch {
                delay(1_000L)
                performReadOnlyCommentary(HankPromptFactory.CommentaryTrigger.MANUAL_START)
            }
        }
    }

    fun stopStream() {
        voiceJob?.cancel()
        voiceJob = null
        audioStatusJob?.cancel()
        audioStatusJob = null
        speakingJob?.cancel()
        speakingJob = null
        analyzeJob?.cancel()
        analyzeJob = null
        sceneJob?.cancel()
        sceneJob = null
        autonomousJob?.cancel()
        autonomousJob = null
        readOnlyJob?.cancel()
        readOnlyJob = null
        try {
            voiceCommand.stopContinuousListening()
        } catch (_: Exception) {}
        try {
            audio.stopSpeaking()
        } catch (_: Exception) {}
        try {
            audio.releaseSpeechInput()
        } catch (_: Exception) {}
        try {
            bargeInDetector.stop()
        } catch (_: Exception) {}
        sceneWatcher.reset()
        conversationHistory.clear()
        pendingReadOnlyNotes.clear()
        imageAnalysis?.clearAnalyzer()
        imageAnalysis = null
        cameraProvider?.unbindAll()
        cameraProvider = null
        cameraOwner?.stop()
        cameraOwner = null
        _uiState.update { INITIAL_STATE }
    }

    fun askHank() {
        if (_uiState.value.isAnalyzing) return
        voiceCommand.startManualListen()
    }

    fun cancelListening() {
        voiceCommand.stopContinuousListening()
        voiceJob?.cancel()
        _uiState.update { it.copy(isListening = false, isWakeWordActive = false) }
    }

    fun requestCommentNow() {
        viewModelScope.launch {
            performReadOnlyCommentary(HankPromptFactory.CommentaryTrigger.MANUAL_START)
        }
    }

    fun setHankMode(mode: HankMode) {
        _uiState.update { it.copy(hankMode = mode) }
        if (mode == HankMode.READ_ONLY) {
            scheduleReadOnlyLoop()
            viewModelScope.launch {
                performReadOnlyCommentary(HankPromptFactory.CommentaryTrigger.MANUAL_START)
            }
        } else {
            readOnlyJob?.cancel()
            readOnlyJob = null
        }
    }

    fun capturePhoto() {
        val frame = _uiState.value.videoFrame ?: return
        viewModelScope.launch {
            val saved = saveBitmapEvidence(frame, "Phone camera capture")
            if (saved != null) {
                _uiState.update { it.copy(capturedEvidence = it.capturedEvidence + saved) }
            }
        }
    }

    private fun bindCamera() {
        val context = getApplication<Application>()
        val owner = CameraLifecycleOwner().also { it.start() }
        cameraOwner = owner
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener(
            {
                try {
                    val provider = providerFuture.get()
                    cameraProvider = provider
                    provider.unbindAll()

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
                    imageAnalysis = analysis

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
            if (now - lastFrameAt < 66L) return
            lastFrameAt = now
            val bitmap = image.toPhoneBitmap() ?: return
            _uiState.update {
                it.copy(
                    videoFrame = bitmap,
                    videoFrameCount = it.videoFrameCount + 1,
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Phone camera frame conversion failed", e)
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
        val decoded =
            BitmapFactory.decodeByteArray(jpeg.toByteArray(), 0, jpeg.size()) ?: return null
        val rotation = imageInfo.rotationDegrees
        if (rotation == 0) return decoded
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        return Bitmap.createBitmap(
                decoded,
                0,
                0,
                decoded.width,
                decoded.height,
                matrix,
                true,
            )
            .also {
                if (it != decoded) decoded.recycle()
            }
    }

    private fun ImageProxy.toNv21(): ByteArray {
        val yPlane = planes[0]
        val uPlane = planes[1]
        val vPlane = planes[2]
        val ySize = width * height
        val nv21 = ByteArray(ySize + ySize / 2)

        copyYPlane(yPlane.buffer, yPlane.rowStride, width, height, nv21)

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

    private fun copyYPlane(
        source: ByteBuffer,
        rowStride: Int,
        width: Int,
        height: Int,
        output: ByteArray,
    ) {
        var outputOffset = 0
        for (row in 0 until height) {
            val rowStart = row * rowStride
            source.position(rowStart)
            source.get(output, outputOffset, width)
            outputOffset += width
        }
    }

    private fun observeAudioStatus() {
        audioStatusJob?.cancel()
        audioStatusJob =
            viewModelScope.launch {
                audio.audioRouteStatus.collect { status ->
                    _uiState.update { it.copy(audioRouteStatus = status) }
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
                        delay(80L)
                        if (audio.isSpeaking.value) {
                            bargeInDetector.start()
                        }
                    } else {
                        bargeInDetector.stop()
                        if (_uiState.value.hankMode == HankMode.INTERACTIVE) {
                            voiceCommand.startConversationFollowUp()
                        }
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
                        }
                        is VoiceCommandManager.VoiceState.Off -> {
                            _uiState.update { it.copy(isListening = false, isWakeWordActive = false) }
                        }
                    }
                }
            }
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

    private suspend fun autonomousObservation() {
        val frame = _uiState.value.videoFrame ?: return
        if (frame.isRecycled) return
        if (_uiState.value.isAnalyzing || _uiState.value.isListening || _uiState.value.isHankSpeaking) return
        if (conversationHistory.isEmpty()) return
        if (System.currentTimeMillis() - lastTurnAt < 4_000L) return

        autonomousJob?.cancel()
        autonomousJob =
            viewModelScope.launch {
                _uiState.update { it.copy(isAnalyzing = true) }
                val frameCopy = frame.copy(frame.config ?: Bitmap.Config.ARGB_8888, true)
                try {
                    val response =
                        geminiService.analyzeFrame(
                            bitmap = frameCopy,
                            userQuestion = HankPromptFactory.autonomousObservationPrompt(currentDomainMode()),
                            history = conversationHistory.toList(),
                            systemPromptOverride = HankPromptFactory.systemPrompt(currentDomainMode()),
                        )
                    val cleaned = response.trim().lowercase(Locale.US)
                    if (cleaned == "<quiet>" || cleaned == "quiet" || cleaned.length < 6) return@launch
                    appendAssistantResponse(response)
                } finally {
                    frameCopy.recycle()
                    _uiState.update { it.copy(isAnalyzing = false) }
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

    private fun analyzeWithQuestion(question: String) {
        if (_uiState.value.isAnalyzing) return
        val currentFrame = _uiState.value.videoFrame
        _uiState.update { it.copy(isAnalyzing = true, lastGeminiResponse = null) }
        analyzeJob?.cancel()
        analyzeJob =
            viewModelScope.launch {
                val frameCopy = currentFrame?.copy(currentFrame.config ?: Bitmap.Config.ARGB_8888, true)
                try {
                    val response =
                        geminiService.analyzeFrame(
                            bitmap = frameCopy,
                            userQuestion = question,
                            history = conversationHistory.toList(),
                            systemPromptOverride = HankPromptFactory.systemPrompt(currentDomainMode()),
                        )
                    conversationHistory.add(GeminiService.Turn("user", question))
                    appendChatMessages(ChatMessage(ChatMessage.Role.USER, question))
                    appendAssistantResponse(response)
                } finally {
                    frameCopy?.recycle()
                    _uiState.update { it.copy(isAnalyzing = false) }
                }
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
        val fillers =
            listOf("okay", "thanks", "thank you", "go ahead", "continue", "hey hank", "hank")
        if (fillers.any { normalized == it }) return false
        val keywords =
            listOf(
                "leak",
                "noise",
                "crack",
                "code",
                "p0",
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
        audio.speak(cleaned)
        lastTurnAt = System.currentTimeMillis()
    }

    private fun appendChatMessages(vararg messages: ChatMessage) {
        _uiState.update { state ->
            state.copy(chatMessages = (state.chatMessages + messages).takeLast(100))
        }
    }

    private suspend fun saveBitmapEvidence(bitmap: Bitmap, caption: String): InspectionEvidence? =
        withContext(Dispatchers.IO) {
            try {
                val dir = File(getApplication<Application>().filesDir, "capture-evidence").apply {
                    mkdirs()
                }
                val timestamp = System.currentTimeMillis()
                val file = File(dir, "phone_frame_$timestamp.jpg")
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                InspectionEvidence(
                    id = "evidence-image-$timestamp",
                    kind = EvidenceKind.IMAGE,
                    filePath = file.absolutePath,
                    createdAt = timestamp,
                    caption = caption,
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save phone camera evidence", e)
                null
            }
        }

    private fun currentReadOnlyPauseMs(): Long =
        AppConfigStore.current(getApplication()).speech.readOnlyPauseMs

    private fun currentDomainMode(): DomainMode =
        AppConfigStore.current(getApplication()).general.domainMode

    override fun onCleared() {
        super.onCleared()
        stopStream()
        cameraExecutor.shutdown()
        try {
            audio.shutdown()
        } catch (_: Exception) {}
    }

    class Factory(
        private val application: Application,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PhoneCameraStreamViewModel(application) as T
        }
    }
}
