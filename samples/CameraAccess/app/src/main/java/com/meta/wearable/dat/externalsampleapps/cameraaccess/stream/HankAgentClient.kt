package com.meta.wearable.dat.externalsampleapps.cameraaccess.stream

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * WebSocket client for real-time communication with the Hank agent server.
 * Sends text queries (with optional image frames) and streams responses back.
 */
class HankAgentClient(private val serverUrl: String) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val _events = MutableSharedFlow<Event>(replay = 0, extraBufferCapacity = 16)
    val events: Flow<Event> = _events.asSharedFlow()

    sealed interface Event {
        data class Chunk(val text: String) : Event
        object Done : Event
        data class Error(val message: String) : Event
        object Connected : Event
        object Disconnected : Event
    }

    fun connect() {
        if (webSocket != null) {
            Log.w(TAG, "Already connected or connecting")
            return
        }

        Log.d(TAG, "Connecting to Hank agent at $serverUrl")

        val request = Request.Builder()
            .url(serverUrl)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                Log.d(TAG, "Connected to Hank agent")
                _events.tryEmit(Event.Connected)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val data = JSONObject(text)
                    when (data.getString("type")) {
                        "chunk" -> {
                            val chunk = data.getString("text")
                            _events.tryEmit(Event.Chunk(chunk))
                        }
                        "done" -> {
                            _events.tryEmit(Event.Done)
                        }
                        "error" -> {
                            val error = data.getString("text")
                            _events.tryEmit(Event.Error(error))
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing WebSocket message: $text", e)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                Log.e(TAG, "WebSocket connection failed", t)
                _events.tryEmit(Event.Error(t.message ?: "Connection failed"))
                this@HankAgentClient.webSocket = null
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code $reason")
                _events.tryEmit(Event.Disconnected)
                this@HankAgentClient.webSocket = null
            }
        })
    }

    /**
     * Send a query with optional image frame.
     * If bitmap is null, sends text-only query.
     */
    fun sendQuery(text: String, bitmap: Bitmap? = null) {
        if (webSocket == null) {
            Log.w(TAG, "WebSocket not connected, ignoring query")
            return
        }

        try {
            val json = JSONObject()
            json.put("text", text)

            if (bitmap != null) {
                // Encode bitmap to JPEG base64
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
                val base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
                json.put("frame", base64)
                json.put("media_type", "image/jpeg")
            }

            webSocket!!.send(json.toString())
            Log.d(TAG, "Query sent: ${text.take(50)}..." + (if (bitmap != null) " (with image)" else ""))
        } catch (e: Exception) {
            Log.e(TAG, "Error sending query", e)
            _events.tryEmit(Event.Error("Failed to send query: ${e.message}"))
        }
    }

    fun disconnect() {
        webSocket?.close(1000, "Client closing")
        webSocket = null
        Log.d(TAG, "Disconnected from Hank agent")
    }

    fun isConnected(): Boolean = webSocket != null

    companion object {
        private const val TAG = "HankAgentClient"
    }
}
