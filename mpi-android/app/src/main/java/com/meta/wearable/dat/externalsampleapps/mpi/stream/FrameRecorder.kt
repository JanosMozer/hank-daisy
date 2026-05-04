package com.meta.wearable.dat.externalsampleapps.mpi.stream

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Environment
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FrameRecorder(private val context: Context) {
  companion object {
    private const val MIME_TYPE = "video/avc"
    private const val TIMEOUT_US = 10_000L
  }

  private enum class InputLayout {
    PLANAR,
    SEMI_PLANAR,
  }

  private var codec: MediaCodec? = null
  private var muxer: MediaMuxer? = null
  private var muxerStarted = false
  private var trackIndex = -1
  private var bufferInfo = MediaCodec.BufferInfo()
  private var outputFile: File? = null
  private var width = 0
  private var height = 0
  private var fps = 24
  private var lastQueuedPtsUs = 0L
  private var frameBytes = ByteArray(0)
  private var convertedBytes = ByteArray(0)
  private var inputLayout = InputLayout.PLANAR
  private var paused = false

  val currentFile: File?
    get() = outputFile

  @Synchronized
  fun start(width: Int, height: Int, fps: Int) {
    check(codec == null) { "Recording already active." }
    val encoderInfo = findEncoder() ?: error("No AVC encoder available.")
    val colorFormat = selectColorFormat(encoderInfo)
    this.width = width
    this.height = height
    this.fps = fps
    this.lastQueuedPtsUs = 0L
    this.paused = false
    this.frameBytes = ByteArray(frameSize(width, height))
    this.convertedBytes = ByteArray(frameSize(width, height))
    this.inputLayout =
        when (colorFormat) {
          MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar -> InputLayout.SEMI_PLANAR
          else -> InputLayout.PLANAR
        }

    val moviesDir =
        context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: context.filesDir
    val evidenceDir = File(moviesDir, "inspection-evidence").apply { mkdirs() }
    val name = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    outputFile = File(evidenceDir, "mpi_glasses_recording_$name.mp4")

    val format =
        MediaFormat.createVideoFormat(MIME_TYPE, width, height).apply {
          setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
          setInteger(MediaFormat.KEY_BIT_RATE, width * height * 5)
          setInteger(MediaFormat.KEY_FRAME_RATE, fps)
          setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

    codec =
        MediaCodec.createByCodecName(encoderInfo.name).apply {
          configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
          start()
        }
    muxer = MediaMuxer(outputFile!!.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    muxerStarted = false
    trackIndex = -1
    bufferInfo = MediaCodec.BufferInfo()
  }

  @Synchronized
  fun recordFrame(i420Buffer: ByteBuffer, width: Int, height: Int, timestampUs: Long) {
    val activeCodec = codec ?: return
    if (paused) return
    require(this.width == width && this.height == height) { "Frame size changed during recording." }

    drainEncoder(endOfStream = false)
    val inputIndex = activeCodec.dequeueInputBuffer(TIMEOUT_US)
    if (inputIndex < 0) return

    val inputBuffer = activeCodec.getInputBuffer(inputIndex) ?: return
    inputBuffer.clear()

    val source = i420Buffer.duplicate()
    source.position(0)
    source.get(frameBytes, 0, frameBytes.size)

    val bytesToWrite =
        when (inputLayout) {
          InputLayout.PLANAR -> frameBytes
          InputLayout.SEMI_PLANAR -> {
            i420ToNv12(frameBytes, convertedBytes, width, height)
            convertedBytes
          }
        }
    inputBuffer.put(bytesToWrite, 0, bytesToWrite.size)
    val ptsUs = timestampUs.coerceAtLeast(lastQueuedPtsUs)
    activeCodec.queueInputBuffer(inputIndex, 0, bytesToWrite.size, ptsUs, 0)
    lastQueuedPtsUs = ptsUs
  }

  @Synchronized
  fun finish(): File {
    val activeCodec = codec ?: return outputFile ?: error("No recording file.")
    val completedFile = outputFile ?: error("No recording file.")

    var eosQueued = false
    while (!eosQueued) {
      val inputIndex = activeCodec.dequeueInputBuffer(TIMEOUT_US)
      if (inputIndex >= 0) {
        val ptsUs = lastQueuedPtsUs + (1_000_000L / fps.coerceAtLeast(1))
        activeCodec.queueInputBuffer(inputIndex, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        eosQueued = true
      } else {
        drainEncoder(endOfStream = false)
      }
    }
    drainEncoder(endOfStream = true)
    releaseCodec()
    return completedFile
  }

  @Synchronized
  fun abort() {
    val partialFile = outputFile
    try {
      codec?.stop()
    } catch (_: Exception) {
    }
    try {
      codec?.release()
    } catch (_: Exception) {
    }
    try {
      muxer?.release()
    } catch (_: Exception) {
    }
    resetState()
    try {
      if (partialFile?.exists() == true) {
        partialFile.delete()
      }
    } catch (_: Exception) {
    }
  }

  private fun drainEncoder(endOfStream: Boolean) {
    val activeCodec = codec ?: return
    while (true) {
      val outputIndex = activeCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
      when {
        outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
          if (!endOfStream) return
        }
        outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
          val activeMuxer = muxer ?: return
          trackIndex = activeMuxer.addTrack(activeCodec.outputFormat)
          activeMuxer.start()
          muxerStarted = true
        }
        outputIndex >= 0 -> {
          val outputBuffer = activeCodec.getOutputBuffer(outputIndex) ?: continue
          if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
            bufferInfo.size = 0
          }
          if (bufferInfo.size > 0 && muxerStarted) {
            outputBuffer.position(bufferInfo.offset)
            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
            muxer?.writeSampleData(trackIndex, outputBuffer, bufferInfo)
          }
          val endOfStreamReached = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
          activeCodec.releaseOutputBuffer(outputIndex, false)
          if (endOfStreamReached) return
        }
      }
    }
  }

  private fun releaseCodec() {
    try {
      codec?.stop()
    } catch (_: Exception) {
    }
    try {
      codec?.release()
    } catch (_: Exception) {
    }
    try {
      if (muxerStarted) {
        muxer?.stop()
      }
    } catch (_: Exception) {
    }
    try {
      muxer?.release()
    } catch (_: Exception) {
    }
    resetState()
  }

  private fun resetState() {
    codec = null
    muxer = null
    muxerStarted = false
    trackIndex = -1
    paused = false
    lastQueuedPtsUs = 0L
    width = 0
    height = 0
    fps = 24
    frameBytes = ByteArray(0)
    convertedBytes = ByteArray(0)
    outputFile = null
  }

  private fun selectColorFormat(codecInfo: MediaCodecInfo): Int {
    val capabilities = codecInfo.getCapabilitiesForType(MIME_TYPE)
    val preferred =
        listOf(
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible,
        )
    return preferred.firstOrNull { capabilities.colorFormats.contains(it) }
        ?: error("No supported YUV420 input format for ${codecInfo.name}.")
  }

  private fun findEncoder(): MediaCodecInfo? {
    val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
    return codecList.codecInfos.firstOrNull { codecInfo ->
      codecInfo.isEncoder && codecInfo.supportedTypes.contains(MIME_TYPE)
    }
  }

  private fun i420ToNv12(source: ByteArray, target: ByteArray, width: Int, height: Int) {
    val ySize = width * height
    val uvSize = ySize / 4
    System.arraycopy(source, 0, target, 0, ySize)
    val uOffset = ySize
    val vOffset = ySize + uvSize
    var out = ySize
    for (i in 0 until uvSize) {
      target[out++] = source[uOffset + i]
      target[out++] = source[vOffset + i]
    }
  }

  private fun frameSize(width: Int, height: Int): Int = width * height * 3 / 2
}
