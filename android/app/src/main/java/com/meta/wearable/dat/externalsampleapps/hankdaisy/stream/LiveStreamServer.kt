package com.meta.wearable.dat.externalsampleapps.hankdaisy.stream

import android.graphics.Bitmap
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

class LiveStreamServer(private val port: Int = 8080) {
    private var serverSocket: ServerSocket? = null
    private var executor = Executors.newCachedThreadPool()
    private var isRunning = false
    private val clients = mutableSetOf<Socket>()

    fun start() {
        if (isRunning) return
        if (executor.isShutdown) {
            executor = Executors.newCachedThreadPool()
        }
        isRunning = true
        executor.execute {
            try {
                serverSocket = ServerSocket(port)
                Log.d("LiveStreamServer", "Server started on port $port")
                while (isRunning) {
                    val client = serverSocket?.accept()
                    if (client != null) {
                        Log.d("LiveStreamServer", "New client connected: ${client.inetAddress}")
                        clients.add(client)
                        handleClient(client)
                    }
                }
            } catch (e: Exception) {
                if (isRunning) Log.e("LiveStreamServer", "Server error", e)
            }
        }
    }

    private fun handleClient(client: Socket) {
        executor.execute {
            try {
                val output = client.getOutputStream()
                output.write("HTTP/1.1 200 OK\r\n".toByteArray())
                output.write("Content-Type: multipart/x-mixed-replace; boundary=--frame\r\n".toByteArray())
                output.write("\r\n".toByteArray())
                while (isRunning && !client.isClosed) {
                    // Client remains connected waiting for frames
                    Thread.sleep(100) 
                }
            } catch (e: Exception) {
                Log.d("LiveStreamServer", "Client disconnected")
            } finally {
                clients.remove(client)
                client.close()
            }
        }
    }

    fun sendFrame(bitmap: Bitmap) {
        if (!isRunning || clients.isEmpty()) return

        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream)
        val jpegData = stream.toByteArray()

        val iterator = clients.iterator()
        while (iterator.hasNext()) {
            val client = iterator.next()
            try {
                val output = client.getOutputStream()
                output.write("--frame\r\n".toByteArray())
                output.write("Content-Type: image/jpeg\r\n".toByteArray())
                output.write("Content-Length: ${jpegData.size}\r\n".toByteArray())
                output.write("\r\n".toByteArray())
                output.write(jpegData)
                output.write("\r\n".toByteArray())
                output.flush()
            } catch (e: Exception) {
                Log.d("LiveStreamServer", "Error sending frame, removing client")
                client.close()
                iterator.remove()
            }
        }
    }

    fun stop() {
        isRunning = false
        serverSocket?.close()
        serverSocket = null
        clients.forEach { it.close() }
        clients.clear()
        executor.shutdownNow()
    }
}
