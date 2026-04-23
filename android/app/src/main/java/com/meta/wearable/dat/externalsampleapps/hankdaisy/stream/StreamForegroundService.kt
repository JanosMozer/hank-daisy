/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.hankdaisy.stream

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.meta.wearable.dat.externalsampleapps.hankdaisy.MainActivity
import com.meta.wearable.dat.externalsampleapps.hankdaisy.R

/**
 * Foreground service that keeps the streaming + always-on voice loop alive while
 * the app is backgrounded or the screen is off. Declared with `microphone` and
 * `connectedDevice` types so we can hold the SCO mic and the BLE link to the glasses.
 */
class StreamForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        val notification = buildNotification()
        val type =
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, type)
        } else {
            startForeground(NOTIF_ID, notification)
        }
        return START_STICKY
    }

    private fun ensureChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Hank live diagnostics",
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
    }

    private fun buildNotification(): Notification {
        val pi =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Hank is listening")
            .setContentText("Streaming from glasses, listening for \"Hey Hank\".")
            .setSmallIcon(R.drawable.smart_glasses_icon)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "hank_stream_channel"
        private const val NOTIF_ID = 4711

        fun start(context: Context) {
            val intent = Intent(context, StreamForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, StreamForegroundService::class.java))
        }
    }
}
